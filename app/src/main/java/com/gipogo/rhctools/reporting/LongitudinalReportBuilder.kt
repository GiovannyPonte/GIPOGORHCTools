package com.gipogo.rhctools.reporting.builder

import android.content.Context
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.reporting.model.*
import com.gipogo.rhctools.reporting.model.displayPvr
import com.gipogo.rhctools.reporting.model.displaySelectedCo
import com.gipogo.rhctools.reporting.model.displaySvr
import kotlinx.coroutines.flow.first
import java.text.NumberFormat

object LongitudinalReportBuilder {

    /**
     * Regla de faltantes: siempre mostrar common_value_na
     * (la fila se mantiene, el valor se materializa como N/A).
     */
    suspend fun buildFromRoom(
        context: Context,
        patientId: String,
        patientDao: PatientDao,
        rhcStudyDao: RhcStudyDao
    ): ReportDocumentUi {

        val list: List<StudyWithRhcData> = rhcStudyDao.listStudiesWithRhcDataByPatient(patientId).first()
        val ordered = list.sortedBy { it.study.startedAtMillis }

        val nowMillis = System.currentTimeMillis()

        if (ordered.isEmpty()) {
            val header = ReportHeaderUi(
                appName = context.getString(R.string.pdf_app_name),
                patientDisplayName = patientId,
                createdAtMillis = nowMillis,
                firstStudyAtMillis = nowMillis,
                lastStudyAtMillis = nowMillis,
                totalStudies = 0
            )
            return ReportDocumentUi(header = header, pages = emptyList())
        }

        val patient = runCatching { patientDao.getById(patientId) }.getOrNull()
        val displayName = patient?.displayName?.takeIf { it.isNotBlank() }
        val internalCode = patient?.internalCode?.takeIf { it.isNotBlank() }
        val patientDisplay = displayName ?: internalCode ?: patientId

        val header = ReportHeaderUi(
            appName = context.getString(R.string.pdf_app_name),
            patientDisplayName = patientDisplay,
            createdAtMillis = nowMillis,
            firstStudyAtMillis = ordered.first().study.startedAtMillis,
            lastStudyAtMillis = ordered.last().study.startedAtMillis,
            totalStudies = ordered.size
        )

        val pages = mutableListOf<ReportPageUi>()

        // Portada solo si >= 2 estudios
        if (ordered.size >= 2) {
            pages += CoverPageUi(pageIndex1Based = 0, pageCount = 0, header = header)
        }

        val na = context.getString(R.string.common_value_na)

        fun fmtDouble(v: Double?, decimals: Int): String {
            if (v == null) return na
            val nf = NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = decimals
                minimumFractionDigits = 0
            }
            return nf.format(v)
        }

        fun pickPvrValue(rhc: RhcStudyDataEntity?): Pair<Double?, Int?> {
            val display = rhc.displayPvr()
            return display.value to display.unitRes
        }

        fun pickSvrValue(rhc: RhcStudyDataEntity?): Pair<Double?, Int?> {
            val display = rhc.displaySvr()
            return display.value to display.unitRes
        }

        // ---- Páginas por estudio (1 por estudio) ----
        ordered.forEachIndexed { idx, sw ->
            val rhc = sw.rhc
            val selectedCo = rhc.displaySelectedCo()

            val (pvrValue, pvrUnitRes) = pickPvrValue(rhc)
            val (svrValue, svrUnitRes) = pickSvrValue(rhc)

            val studyUi = StudyUi(
                studyId = sw.study.id,
                studyAtMillis = sw.study.startedAtMillis,
                studyNumber1Based = idx + 1,
                totalStudies = ordered.size,
                sections = listOf(
                    // A) Flujo y desempeño (CI, CO)
                    SectionUi(
                        titleRes = R.string.patient_trends_section_flow_title,
                        rows = buildList {
                            add(
                                RowUi(
                                    labelRes = R.string.rhc_label_ci_short,
                                    valueText = fmtDouble(selectedCo.cardiacIndexLMinM2, 1),
                                    unitRes = R.string.common_unit_lmin_m2
                                )
                            )
                            add(
                                RowUi(
                                    labelRes = R.string.fick_result_eyebrow_co,
                                    valueText = fmtDouble(selectedCo.cardiacOutputLMin, 2),
                                    unitRes = R.string.common_unit_lmin
                                )
                            )
                            selectedCo.methodLabelRes?.let { methodRes ->
                                add(
                                    RowUi(
                                        labelRes = R.string.co_method_label,
                                        valueText = context.getString(methodRes),
                                        unitRes = null
                                    )
                                )
                            }
                        }
                    ),

                    // B) Resistencias (PVR, SVR)
                    SectionUi(
                        titleRes = R.string.patient_trends_section_resistance_title,
                        rows = listOf(
                            RowUi(
                                labelRes = R.string.home_badge_pvr,
                                valueText = fmtDouble(
                                    pvrValue,
                                    decimals = if (pvrUnitRes == R.string.common_unit_dynes) 0 else 1
                                ),
                                unitRes = pvrUnitRes
                            ),
                            RowUi(
                                labelRes = R.string.svr_result_title,
                                valueText = fmtDouble(
                                    svrValue,
                                    decimals = if (svrUnitRes == R.string.common_unit_dynes) 0 else 1
                                ),
                                unitRes = svrUnitRes
                            )
                        )
                    ),

                    // C) Presiones y congestión (RAP, mPAP, PCWP)
                    SectionUi(
                        titleRes = R.string.patient_trends_section_pressures_title,
                        rows = listOf(
                            RowUi(
                                labelRes = R.string.papi_help_rap_title,
                                valueText = fmtDouble(rhc?.rapMmHg, 0),
                                unitRes = R.string.common_unit_mmhg
                            ),
                            RowUi(
                                labelRes = R.string.pvr_help_mpap_title,
                                valueText = fmtDouble(rhc?.mpapMmHg, 0),
                                unitRes = R.string.common_unit_mmhg
                            ),
                            RowUi(
                                labelRes = R.string.rhc_label_pcwp_short,
                                valueText = fmtDouble(rhc?.pawpMmHg, 0),
                                unitRes = R.string.common_unit_mmhg
                            )
                        )
                    )
                )
            )

            pages += StudyPageUi(
                pageIndex1Based = 0,
                pageCount = 0,
                header = header,
                study = studyUi
            )
        }

        // ---- Tendencias al final (solo si >=2 estudios) ----
        if (ordered.size >= 2) {

            fun buildSeries(
                labelRes: Int,
                selector: (RhcStudyDataEntity) -> Double?
            ): ChartSeriesUi {
                val pts = ordered.mapNotNull { sw ->
                    val rhc = sw.rhc ?: return@mapNotNull null
                    val y = selector(rhc) ?: return@mapNotNull null
                    ChartPointUi(xMillis = sw.study.startedAtMillis, y = y)
                }
                return ChartSeriesUi(labelRes = labelRes, points = pts)
            }

            // Tendencias 1: Presiones
            pages += TrendsPageUi(
                pageIndex1Based = 0,
                pageCount = 0,
                header = header,
                titleRes = R.string.patient_trends_section_pressures_title,
                charts = listOf(
                    ChartUi(
                        titleRes = R.string.patient_trends_section_pressures_title,
                        unitRes = R.string.common_unit_mmhg,
                        series = listOf(
                            buildSeries(R.string.papi_help_rap_title) { it.rapMmHg },
                            buildSeries(R.string.pvr_help_mpap_title) { it.mpapMmHg },
                            buildSeries(R.string.rhc_label_pcwp_short) { it.pawpMmHg }
                        )
                    )
                )
            )

            // Tendencias 2: Flujo y desempeño (CI, CPO)
            pages += TrendsPageUi(
                pageIndex1Based = 0,
                pageCount = 0,
                header = header,
                titleRes = R.string.patient_trends_section_flow_title,
                charts = listOf(
                    ChartUi(
                        titleRes = R.string.patient_trends_section_flow_title,
                        unitRes = null,
                        series = listOf(
                            buildSeries(R.string.rhc_label_ci_short) { it.displaySelectedCo().cardiacIndexLMinM2 },
                            buildSeries(R.string.home_badge_cpo) { it.cardiacPowerW }
                        )
                    )
                )
            )

            val trendPvrUnitSet = ordered
                .mapNotNull { it.rhc.displayPvr().unitRes }
                .toSet()
            val trendPvrUnitRes = trendPvrUnitSet.singleOrNull() ?: R.string.common_unit_wu_short
            val trendPvrSeries = if (trendPvrUnitSet.size <= 1) {
                buildSeries(R.string.home_badge_pvr) { it.displayPvr().value }
            } else {
                // Fallback conservador: mantener tendencia longitudinal visible en una unidad estable.
                buildSeries(R.string.home_badge_pvr) { it.pvrWood }
            }

            if (trendPvrSeries.points.isNotEmpty()) {
                // Tendencias 3: Resistencia (PVR)
                pages += TrendsPageUi(
                    pageIndex1Based = 0,
                    pageCount = 0,
                    header = header,
                    titleRes = R.string.patient_trends_section_resistance_title,
                    charts = listOf(
                        ChartUi(
                            titleRes = R.string.patient_trends_section_resistance_title,
                            unitRes = trendPvrUnitRes,
                            series = listOf(trendPvrSeries)
                        )
                    )
                )
            }
        }

        // ---- Reindexar páginas (variable) ----
        val count = pages.size
        val finalPages = pages.mapIndexed { index, page ->
            when (page) {
                is CoverPageUi -> page.copy(pageIndex1Based = index + 1, pageCount = count)
                is StudyPageUi -> page.copy(pageIndex1Based = index + 1, pageCount = count)
                is TrendsPageUi -> page.copy(pageIndex1Based = index + 1, pageCount = count)
            }
        }

        return ReportDocumentUi(header = header, pages = finalPages)
    }
}
