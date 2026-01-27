package com.gipogo.rhctools.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.dao.StudyDao
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface PatientDetailUiState {
    data object Loading : PatientDetailUiState

    data class Error(
        @StringRes val messageRes: Int,
        val debugMessage: String? = null
    ) : PatientDetailUiState

    data class Content(
        val patientId: String,
        val topBarTitle: String,

        // Secondary line under ID (NOT the name again). Examples: internal code or "—".
        val headerSecondaryLine: String?,

        val lastUpdateMillis: Long?,
        val studies: List<StudyItemUi>,
        val latestSummary: LatestStudySummaryUi?,
        val trends: TrendsUi?
    ) : PatientDetailUiState
}

data class StudyItemUi(
    val studyId: String,
    @StringRes val titleRes: Int,
    val startedAtMillis: Long,
    val updatedAtMillis: Long?,
    val inlineMetrics: List<InlineMetricUi>
)

data class InlineMetricUi(
    @StringRes val labelRes: Int,
    val value: Double?,
    val decimals: Int,
    @StringRes val unitRes: Int?
)

data class LatestStudySummaryUi(
    val studyId: String,
    val startedAtMillis: Long,
    val rows: List<SummaryRowUi>,
    val hasAnyValue: Boolean
)

data class SummaryRowUi(
    @StringRes val labelRes: Int,
    val value: Double?,
    val decimals: Int,
    @StringRes val unitRes: Int?
)

data class TrendsUi(
    val series: List<TrendSeriesUi>,
    val opinion: TrendsClinicalOpinionUi
)

data class TrendSeriesUi(
    val metric: TrendMetric,
    val points: List<TrendPointUi>
)

data class TrendPointUi(
    val xMillis: Long,
    val y: Double
)

enum class TrendMetric(
    @StringRes val labelRes: Int,
    @StringRes val unitRes: Int?
) {
    RAP(labelRes = R.string.papi_help_rap_title, unitRes = R.string.common_unit_mmhg),
    MPAP(labelRes = R.string.pvr_help_mpap_title, unitRes = R.string.common_unit_mmhg),
    PCWP(labelRes = R.string.rhc_label_pcwp_short, unitRes = R.string.common_unit_mmhg),
    CI(labelRes = R.string.rhc_label_ci_short, unitRes = R.string.common_unit_lmin_m2),
    PVR(labelRes = R.string.home_badge_pvr, unitRes = R.string.common_unit_wu_short),
    CPO(labelRes = R.string.home_badge_cpo, unitRes = R.string.common_unit_w),
}

enum class TrendDirection(@StringRes val labelRes: Int) {
    Increasing(R.string.trend_direction_increasing),
    Decreasing(R.string.trend_direction_decreasing),
    Stable(R.string.trend_direction_stable),
    Insufficient(R.string.trend_direction_insufficient),
}

enum class TrendInsight(@StringRes val textRes: Int) {
    PostCapillaryPattern(R.string.patient_trends_insight_postcap),
    PreCapillaryPattern(R.string.patient_trends_insight_precap),
    RightCongestionLowFlow(R.string.patient_trends_insight_rv_congestion_low_flow),
    FavorableResponse(R.string.patient_trends_insight_favorable),
    None(R.string.patient_trends_insight_none),
}

data class MetricDirectionUi(
    val metric: TrendMetric,
    val direction: TrendDirection
)

data class TrendsClinicalOpinionUi(
    val directions: List<MetricDirectionUi>,
    val insights: List<TrendInsight>
)

sealed interface PatientDetailEvent {
    data class Snackbar(
        @StringRes val messageRes: Int,
        val debugMessage: String? = null
    ) : PatientDetailEvent
}

class PatientDetailViewModel(
    private val patientId: String,
    private val patientDao: PatientDao,
    private val rhcStudyDao: RhcStudyDao,
    private val studyDao: StudyDao
) : ViewModel() {

    private val refreshToken = MutableStateFlow(0)

    private val _events = MutableSharedFlow<PatientDetailEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<PatientDetailEvent> = _events

    private val patientHeaderFlow: Flow<PatientHeaderModel> = refreshToken.flatMapLatest {
        kotlinx.coroutines.flow.flow {
            emit(loadPatientHeader())
        }.catch { e ->
            emit(PatientHeaderModel(topBarTitle = patientId, secondaryLine = null))
            _events.tryEmit(PatientDetailEvent.Snackbar(R.string.patient_error_generic, e.localizedMessage))
        }
    }

    private val studiesFlow: Flow<List<StudyWithRhcData>> = refreshToken.flatMapLatest {
        rhcStudyDao.listStudiesWithRhcDataByPatient(patientId)
    }.catch { e ->
        _events.tryEmit(PatientDetailEvent.Snackbar(R.string.patient_error_generic, e.localizedMessage))
        emit(emptyList())
    }

    val uiState: Flow<PatientDetailUiState> =
        combine(patientHeaderFlow, studiesFlow) { header, studies ->
            if (patientId.isBlank()) {
                return@combine PatientDetailUiState.Error(messageRes = R.string.patient_error_invalid_id)
            }

            val studyItems = studies.map { sw ->
                StudyItemUi(
                    studyId = sw.study.id,
                    titleRes = sw.study.type.toStudyTitleRes(),
                    startedAtMillis = sw.study.startedAtMillis,
                    updatedAtMillis = maxOf(sw.study.updatedAtMillis, sw.rhc?.updatedAtMillis ?: 0L).takeIf { it > 0L },
                    inlineMetrics = buildInlineMetrics(sw.rhc)
                )
            }

            val latest = studies.firstOrNull()
            val lastUpdateMillis = latest?.let { sw ->
                maxOf(sw.study.updatedAtMillis, sw.rhc?.updatedAtMillis ?: 0L).takeIf { it > 0L }
            }

            val latestSummary = latest?.let { sw ->
                buildLatestSummary(sw.study.id, sw.study.startedAtMillis, sw.rhc)
            }

            val trends = buildTrends(studies)

            PatientDetailUiState.Content(
                patientId = patientId,
                topBarTitle = header.topBarTitle,
                headerSecondaryLine = header.secondaryLine,
                lastUpdateMillis = lastUpdateMillis,
                studies = studyItems,
                latestSummary = latestSummary,
                trends = trends
            )
        }.catch { e ->
            emit(PatientDetailUiState.Error(R.string.patient_error_generic, e.localizedMessage))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PatientDetailUiState.Loading
        )

    fun retry() {
        viewModelScope.launch { refreshToken.value = refreshToken.value + 1 }
    }

    fun deleteStudy(studyId: String) {
        viewModelScope.launch {
            runCatching {
                // Robusto: elimina snapshot 1:1 y luego el estudio.
                rhcStudyDao.deleteByStudyId(studyId)
                studyDao.deleteById(studyId)
            }.onSuccess {
                _events.tryEmit(PatientDetailEvent.Snackbar(R.string.patient_msg_study_deleted))
            }.onFailure { e ->
                _events.tryEmit(PatientDetailEvent.Snackbar(R.string.patient_error_generic, e.localizedMessage))
            }
        }
    }

    private suspend fun loadPatientHeader(): PatientHeaderModel {
        val patient = runCatching { patientDao.getById(patientId) }.getOrNull()

        val displayName = patient?.displayName?.takeIf { it.isNotBlank() }
        val internalCode = patient?.internalCode?.takeIf { it.isNotBlank() }

        val topBarTitle = displayName ?: internalCode ?: patientId

        // No duplicar: si title es displayName, usamos internalCode como secundaria (si existe)
        val secondary = internalCode?.takeIf { it != topBarTitle }

        return PatientHeaderModel(
            topBarTitle = topBarTitle,
            secondaryLine = secondary
        )
    }

    private fun String.toStudyTitleRes(): Int {
        return if (equals("RHC", ignoreCase = true)) {
            R.string.patient_study_title_rhc
        } else {
            R.string.patient_study_title_generic
        }
    }

    private fun buildInlineMetrics(rhc: RhcStudyDataEntity?): List<InlineMetricUi> {
        // Short, space-saving: prefer PVR + CI (if present), else fall back to mPAP/PCWP/RAP.
        val candidates = listOf(
            InlineMetricUi(R.string.home_badge_pvr, rhc?.pvrWood, decimals = 1, unitRes = R.string.common_unit_wu_short),
            InlineMetricUi(R.string.rhc_label_ci_short, rhc?.cardiacIndexLMinM2, decimals = 1, unitRes = R.string.common_unit_lmin_m2),
            InlineMetricUi(R.string.pvr_help_mpap_title, rhc?.mpapMmHg, decimals = 0, unitRes = R.string.common_unit_mmhg),
            InlineMetricUi(R.string.rhc_label_pcwp_short, rhc?.pawpMmHg, decimals = 0, unitRes = R.string.common_unit_mmhg),
            InlineMetricUi(R.string.papi_help_rap_title, rhc?.rapMmHg, decimals = 0, unitRes = R.string.common_unit_mmhg),
        )

        return candidates.filter { it.value != null }.take(2)
    }

    private fun buildLatestSummary(
        studyId: String,
        startedAtMillis: Long,
        rhc: RhcStudyDataEntity?
    ): LatestStudySummaryUi {
        val rows = listOf(
            SummaryRowUi(R.string.papi_help_rap_title, rhc?.rapMmHg, decimals = 0, unitRes = R.string.common_unit_mmhg),
            SummaryRowUi(R.string.pvr_help_mpap_title, rhc?.mpapMmHg, decimals = 0, unitRes = R.string.common_unit_mmhg),
            SummaryRowUi(R.string.rhc_label_pcwp_short, rhc?.pawpMmHg, decimals = 0, unitRes = R.string.common_unit_mmhg),
            SummaryRowUi(R.string.rhc_label_ci_short, rhc?.cardiacIndexLMinM2, decimals = 1, unitRes = R.string.common_unit_lmin_m2),
            SummaryRowUi(R.string.home_badge_pvr, rhc?.pvrWood, decimals = 1, unitRes = R.string.common_unit_wu_short),
            SummaryRowUi(R.string.home_badge_cpo, rhc?.cardiacPowerW, decimals = 2, unitRes = R.string.common_unit_w),
        )

        val hasAny = rows.any { it.value != null }
        return LatestStudySummaryUi(
            studyId = studyId,
            startedAtMillis = startedAtMillis,
            rows = rows,
            hasAnyValue = hasAny
        )
    }

    private fun buildTrends(studies: List<StudyWithRhcData>): TrendsUi? {
        if (studies.size < 2) return null

        val series = listOf(
            TrendSeriesUi(TrendMetric.RAP, studies.pointsOf { it.rapMmHg }),
            TrendSeriesUi(TrendMetric.MPAP, studies.pointsOf { it.mpapMmHg }),
            TrendSeriesUi(TrendMetric.PCWP, studies.pointsOf { it.pawpMmHg }),
            TrendSeriesUi(TrendMetric.CI, studies.pointsOf { it.cardiacIndexLMinM2 }),
            TrendSeriesUi(TrendMetric.PVR, studies.pointsOf { it.pvrWood }),
            TrendSeriesUi(TrendMetric.CPO, studies.pointsOf { it.cardiacPowerW }),
        ).map { it.copy(points = it.points.sortedBy { p -> p.xMillis }) }

        val directions = series.map { s ->
            MetricDirectionUi(metric = s.metric, direction = classifyTrend(s.points))
        }

        val insightList = detectInsights(directions)

        return TrendsUi(
            series = series,
            opinion = TrendsClinicalOpinionUi(
                directions = directions,
                insights = insightList
            )
        )
    }

    private fun List<StudyWithRhcData>.pointsOf(selector: (RhcStudyDataEntity) -> Double?): List<TrendPointUi> {
        return this.mapNotNull { sw ->
            val rhc = sw.rhc ?: return@mapNotNull null
            val y = selector(rhc) ?: return@mapNotNull null
            TrendPointUi(xMillis = sw.study.startedAtMillis, y = y)
        }
    }

    private fun classifyTrend(points: List<TrendPointUi>): TrendDirection {
        if (points.size < 2) return TrendDirection.Insufficient
        val first = points.first().y
        val last = points.last().y
        val delta = last - first

        // Not a clinical cutoff; only numeric tolerance to avoid floating jitter.
        val eps = 1e-9
        return when {
            abs(delta) < eps -> TrendDirection.Stable
            delta > 0 -> TrendDirection.Increasing
            else -> TrendDirection.Decreasing
        }
    }

    private fun detectInsights(directions: List<MetricDirectionUi>): List<TrendInsight> {
        fun dir(metric: TrendMetric): TrendDirection? = directions.firstOrNull { it.metric == metric }?.direction

        val mpap = dir(TrendMetric.MPAP)
        val pcwp = dir(TrendMetric.PCWP)
        val pvr = dir(TrendMetric.PVR)
        val rap = dir(TrendMetric.RAP)
        val ci = dir(TrendMetric.CI)
        val cpo = dir(TrendMetric.CPO)

        val insights = mutableListOf<TrendInsight>()

        if (mpap == TrendDirection.Increasing && pcwp == TrendDirection.Increasing) {
            insights += TrendInsight.PostCapillaryPattern
        }

        if (mpap == TrendDirection.Increasing && pvr == TrendDirection.Increasing && pcwp != TrendDirection.Increasing) {
            insights += TrendInsight.PreCapillaryPattern
        }

        if (rap == TrendDirection.Increasing && (ci == TrendDirection.Decreasing || cpo == TrendDirection.Decreasing)) {
            insights += TrendInsight.RightCongestionLowFlow
        }

        if (pvr == TrendDirection.Decreasing && (ci == TrendDirection.Increasing || cpo == TrendDirection.Increasing)) {
            insights += TrendInsight.FavorableResponse
        }

        if (insights.isEmpty()) insights += TrendInsight.None
        return insights
    }

    private data class PatientHeaderModel(
        val topBarTitle: String,
        val secondaryLine: String?
    )

    class Factory(
        private val patientId: String,
        private val patientDao: PatientDao,
        private val rhcStudyDao: RhcStudyDao,
        private val studyDao: StudyDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PatientDetailViewModel(
                patientId = patientId,
                patientDao = patientDao,
                rhcStudyDao = rhcStudyDao,
                studyDao = studyDao
            ) as T
        }
    }
}
