package com.gipogo.rhctools.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.domain.BirthDateCodec
import com.gipogo.rhctools.domain.ClinicalUnitNormalizer
import com.gipogo.rhctools.domain.UnitSystem
import com.gipogo.rhctools.reporting.model.displayPvr
import com.gipogo.rhctools.reporting.model.displaySelectedCo
import com.gipogo.rhctools.reporting.model.displaySvr
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.Period
import kotlin.math.max

enum class StudyClinicalPdfFormat {
    COMPACT,
    COMPLETE
}

object StudyClinicalPdfExport {

    data class ExportResult(
        val pdfFile: File,
        val pdfUri: Uri
    )

    suspend fun exportLatestStudyPdf(
        context: Context,
        patientId: String,
        format: StudyClinicalPdfFormat
    ): ExportResult {
        val db = when (val result = DbProvider.getResult(context.applicationContext)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> throw IllegalStateException(
                result.error.message ?: "Database unavailable",
                result.error
            )
        }

        val studies = db.rhcStudyDao()
            .listStudiesWithRhcDataByPatient(patientId)
            .first()
            .sortedByDescending { it.study.startedAtMillis }

        val selected = studies.firstOrNull()
            ?: throw IllegalStateException(context.getString(R.string.patient_reports_no_studies))

        return exportStudyPdf(
            context = context,
            patientId = patientId,
            studyId = selected.study.id,
            format = format
        )
    }

    suspend fun exportStudyPdf(
        context: Context,
        patientId: String,
        studyId: String,
        format: StudyClinicalPdfFormat
    ): ExportResult {
        val db = when (val result = DbProvider.getResult(context.applicationContext)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> throw IllegalStateException(
                result.error.message ?: "Database unavailable",
                result.error
            )
        }

        val patientDao = db.patientDao()
        val rhcStudyDao = db.rhcStudyDao()

        val studies = rhcStudyDao.listStudiesWithRhcDataByPatient(patientId)
            .first()
            .sortedBy { it.study.startedAtMillis }

        val selectedIndex = studies.indexOfFirst { it.study.id == studyId }
        if (selectedIndex < 0) {
            throw IllegalStateException("Study not found")
        }

        val selected = studies[selectedIndex]
        val previous = studies.take(selectedIndex).lastOrNull()
        // The longitudinal record has no arbitrary clinical maximum. The compact
        // renderer decides how to summarize dense series without discarding the
        // complete history from the document model.
        val history = studies.take(selectedIndex + 1)

        val patient = runCatching { patientDao.getById(patientId) }.getOrNull()
        val unitSystem = AppPreferences(context.applicationContext).unitSystem.first()
        val patientDisplayName = patient?.displayName?.takeIf { it.isNotBlank() }
            ?: patient?.internalCode?.takeIf { it.isNotBlank() }
            ?: patientId

        val document = StudyClinicalPdfDocumentBuilder.build(
            context = context,
            patientDisplayName = patientDisplayName,
            patientInternalCode = patient?.internalCode,
            patientSex = patient?.sex,
            patientBirthDateMillis = patient?.birthDateMillis,
            patientWeightKg = patient?.weightKg,
            patientHeightCm = patient?.heightCm,
            patientNote = patient?.notes,
            unitSystem = unitSystem,
            selected = selected,
            previous = previous,
            history = history
        )

        val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val styleSuffix = when (format) {
            StudyClinicalPdfFormat.COMPACT -> "COMPACT"
            StudyClinicalPdfFormat.COMPLETE -> "COMPLETE"
        }
        val file = File(
            outDir,
            "RHC_${patientId}_${selected.study.startedAtMillis}_${styleSuffix}_${studyId.take(8)}.pdf"
        )

        val partial = File(outDir, "${file.name}.part")
        runCatching {
            partial.outputStream().buffered().use { output ->
                StudyClinicalPdfGenerator.writePdf(context, output, document, format)
            }
            check(partial.length() > 4L && partial.inputStream().use { input ->
                ByteArray(4).also(input::read).contentEquals("%PDF".toByteArray())
            }) { "Invalid PDF output" }
            if (file.exists() && !file.delete()) error("Unable to replace previous report")
            check(partial.renameTo(file)) { "Unable to publish completed report" }
        }.onFailure {
            partial.delete()
            throw it
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return ExportResult(pdfFile = file, pdfUri = uri)
    }
}

internal data class StudyClinicalPdfDocument(
    val appName: String,
    val patientDisplayName: String,
    val studyId: String,
    val studyType: String,
    val studyAtMillis: Long,
    val reportAtMillis: Long,
    val executiveSummary: String,
    val quickReadRows: List<StudyClinicalQuickReadRow>,
    val keyMetrics: List<StudyClinicalMetricChip>,
    val traceabilityRows: List<StudyClinicalKvRow>,
    val pressureRows: List<StudyClinicalMetricRow>,
    val flowRows: List<StudyClinicalMetricRow>,
    val resistanceRows: List<StudyClinicalMetricRow>,
    val interpretationBullets: List<String>,
    val comparisonRows: List<StudyClinicalComparisonRow>,
    val studyNote: String?,
    val limitations: List<String>,
    val pressureChart: List<StudyClinicalChartPoint> = emptyList(),
    val performanceChart: List<StudyClinicalChartPoint> = emptyList(),
    val patientInternalCode: String? = null,
    val patientSex: String? = null,
    val patientBirthDateAndAge: String? = null,
    val patientWeightKg: Double? = null,
    val patientHeightCm: Double? = null,
    val patientNote: String? = null,
    val patientWeightDisplayValue: Double? = null,
    val patientWeightDisplayUnit: String? = null,
    val patientHeightDisplayValue: Double? = null,
    val patientHeightDisplayUnit: String? = null,
    val forrester: StudyClinicalForrester? = null,
    val trendStudies: List<StudyClinicalTrendStudy> = emptyList()
)

internal data class StudyClinicalForrester(
    val currentCi: Double?,
    val currentPcwp: Double?,
    val previousCi: Double?,
    val previousPcwp: Double?
)

internal data class StudyClinicalTrendStudy(
    val dateLabel: String,
    val studyId: String,
    val method: String?,
    val rap: Double?,
    val pasp: Double?,
    val padp: Double?,
    val mpap: Double?,
    val pcwp: Double?,
    val map: Double?,
    val co: Double?,
    val ci: Double?,
    val sao2: Double?,
    val svo2: Double?,
    val hemoglobin: Double?,
    val pvr: Double?,
    val svr: Double?,
    val cpo: Double?,
    val papi: Double?,
    val tpg: Double?,
    val dpg: Double?,
    val note: String?,
    val pvrDisplayValue: Double? = pvr,
    val pvrDisplayUnit: String = "WU",
    val svrDisplayValue: Double? = svr,
    val svrDisplayUnit: String = "WU"
)

internal data class StudyClinicalChartPoint(
    val label: String,
    val unit: String,
    val current: Double?,
    val previous: Double?,
    val axisMax: Double,
    val reference: Double?
)

internal data class StudyClinicalQuickReadRow(
    val label: String,
    val value: String
)

internal data class StudyClinicalMetricChip(
    val label: String,
    val value: String,
    val unit: String?,
    val tone: StudyClinicalMetricTone
)

internal enum class StudyClinicalMetricTone {
    NEUTRAL,
    ALERT
}

internal data class StudyClinicalKvRow(
    val label: String,
    val value: String
)

internal data class StudyClinicalMetricRow(
    val label: String,
    val value: String,
    val unit: String?
)

internal data class StudyClinicalComparisonRow(
    val label: String,
    val previousValue: String,
    val currentValue: String,
    val deltaValue: String
)

private object StudyClinicalPdfDocumentBuilder {

    fun build(
        context: Context,
        patientDisplayName: String,
        patientInternalCode: String?,
        patientSex: String?,
        patientBirthDateMillis: Long?,
        patientWeightKg: Double?,
        patientHeightCm: Double?,
        patientNote: String?,
        unitSystem: UnitSystem,
        selected: StudyWithRhcData,
        previous: StudyWithRhcData?,
        history: List<StudyWithRhcData>
    ): StudyClinicalPdfDocument {
        val rhc = selected.rhc
        val selectedCo = rhc.displaySelectedCo()
        val displayPvr = rhc.displayPvr()
        val displaySvr = rhc.displaySvr()

        val studyAtMillis = selected.study.startedAtMillis
        val reportAtMillis = System.currentTimeMillis()

        val pressures = buildList {
            add(metricRow(context.getString(R.string.study_input_ra), rhc?.rapMmHg, 0, context.getString(R.string.common_unit_mmhg)))
            add(metricRow(context.getString(R.string.pvr_help_mpap_title), rhc?.mpapMmHg, 0, context.getString(R.string.common_unit_mmhg)))
            add(metricRow(context.getString(R.string.rhc_label_pcwp_short), rhc?.pawpMmHg, 0, context.getString(R.string.common_unit_mmhg)))
            add(metricRow(context.getString(R.string.study_input_pa_systolic), rhc?.paspMmHg, 0, context.getString(R.string.common_unit_mmhg)))
            add(metricRow(context.getString(R.string.study_input_pa_diastolic), rhc?.padpMmHg, 0, context.getString(R.string.common_unit_mmhg)))
            add(metricRow(context.getString(R.string.study_input_map), rhc?.mapMmHg, 0, context.getString(R.string.common_unit_mmhg)))
        }

        val flowAndOxygenation = buildList {
            add(metricRow(context.getString(R.string.home_badge_co), selectedCo.cardiacOutputLMin, 2, context.getString(R.string.common_unit_lmin)))
            add(metricRow(context.getString(R.string.rhc_label_ci_short), selectedCo.cardiacIndexLMinM2, 1, context.getString(R.string.common_unit_lmin_m2)))
            add(metricTextRow(context.getString(R.string.co_method_label), selectedCo.methodLabelRes?.let(context::getString)))
            add(metricRow(context.getString(R.string.fick_label_sao2_short), rhc?.saO2Percent, 0, context.getString(R.string.unit_percent)))
            add(metricRow(context.getString(R.string.fick_label_svo2_short), rhc?.svO2Percent, 0, context.getString(R.string.unit_percent)))
            add(metricRow(context.getString(R.string.fick_label_hb_short), rhc?.hemoglobinGdl, 1, context.getString(R.string.unit_g_dl)))
            add(metricRow(context.getString(R.string.study_pdf_field_vo2_used), rhc?.vo2MlMin, 0, context.getString(R.string.study_pdf_unit_ml_min)))
            add(metricTextRow(context.getString(R.string.study_pdf_field_vo2_mode), rhc?.vo2Mode))
        }

        val tpg = calcDifference(rhc?.mpapMmHg, rhc?.pawpMmHg)
        val dpg = calcDifference(rhc?.padpMmHg, rhc?.pawpMmHg)

        val resistanceAndPerformance = buildList {
            add(metricTextRow(
                context.getString(R.string.study_out_pvr),
                formatMetricValue(displayPvr.value, if (displayPvr.unitRes == R.string.common_unit_dynes) 0 else 1),
                displayPvr.unitRes?.let(context::getString)
            ))
            add(metricTextRow(
                context.getString(R.string.study_out_svr),
                formatMetricValue(displaySvr.value, if (displaySvr.unitRes == R.string.common_unit_dynes) 0 else 1),
                displaySvr.unitRes?.let(context::getString)
            ))
            add(metricRow(context.getString(R.string.study_out_cpo), rhc?.cardiacPowerW, 2, context.getString(R.string.common_unit_w)))
            add(metricRow(context.getString(R.string.study_out_papi), rhc?.papi, 2, null))
            add(metricRow(context.getString(R.string.study_out_tpg), tpg, 0, context.getString(R.string.common_unit_mmhg)))
            add(metricRow(context.getString(R.string.study_out_dpg), dpg, 0, context.getString(R.string.common_unit_mmhg)))
        }

        val pvrWood = rhc?.pvrWood
        val quickRead = buildQuickRead(context, rhc, pvrWood)
        val executiveSummary = quickRead.joinToString(separator = " · ") { "${it.label}: ${it.value}" }

        val keyMetrics = listOfNotNull(
            metricChip(context.getString(R.string.papi_help_rap_title), rhc?.rapMmHg, 0, context.getString(R.string.common_unit_mmhg), highlightHigh = (rhc?.rapMmHg ?: 0.0) > 8.0),
            metricChip(context.getString(R.string.pvr_help_mpap_title), rhc?.mpapMmHg, 0, context.getString(R.string.common_unit_mmhg), highlightHigh = (rhc?.mpapMmHg ?: 0.0) > 20.0),
            metricChip(context.getString(R.string.rhc_label_pcwp_short), rhc?.pawpMmHg, 0, context.getString(R.string.common_unit_mmhg), highlightHigh = (rhc?.pawpMmHg ?: 0.0) > 15.0),
            metricChip(context.getString(R.string.home_badge_co), selectedCo.cardiacOutputLMin, 2, context.getString(R.string.common_unit_lmin), highlightLow = selectedCo.cardiacOutputLMin?.let { it < 4.0 } == true),
            metricChip(context.getString(R.string.rhc_label_ci_short), selectedCo.cardiacIndexLMinM2, 1, context.getString(R.string.common_unit_lmin_m2), highlightLow = selectedCo.cardiacIndexLMinM2?.let { it < 2.2 } == true),
            metricChip(context.getString(R.string.home_badge_pvr), displayPvr.value, if (displayPvr.unitRes == R.string.common_unit_dynes) 0 else 1, displayPvr.unitRes?.let(context::getString), highlightHigh = pvrWood?.let { it > 2.0 } == true),
            metricChip(context.getString(R.string.fick_label_svo2_short), rhc?.svO2Percent, 0, context.getString(R.string.unit_percent), highlightLow = rhc?.svO2Percent?.let { it < 60.0 } == true),
            metricChip(context.getString(R.string.home_badge_cpo), rhc?.cardiacPowerW, 2, context.getString(R.string.common_unit_w), highlightLow = rhc?.cardiacPowerW?.let { it < 0.8 } == true)
        )

        val traceabilityRows = buildList {
            add(StudyClinicalKvRow(context.getString(R.string.study_pdf_field_patient), patientDisplayName))
            add(StudyClinicalKvRow(context.getString(R.string.study_pdf_field_study_id), selected.study.id))
            add(StudyClinicalKvRow(context.getString(R.string.study_pdf_field_study_type), selected.study.type.ifBlank { context.getString(R.string.common_value_na) }))
            add(StudyClinicalKvRow(context.getString(R.string.study_pdf_field_study_date), formatDateTime(studyAtMillis)))
            add(StudyClinicalKvRow(context.getString(R.string.study_pdf_field_report_date), formatDateTime(reportAtMillis)))
            add(
                StudyClinicalKvRow(
                    context.getString(R.string.study_pdf_field_co_method),
                    selectedCo.methodLabelRes?.let(context::getString) ?: context.getString(R.string.common_value_na)
                )
            )
            add(
                StudyClinicalKvRow(
                    context.getString(R.string.study_pdf_field_previous_study),
                    previous?.study?.startedAtMillis?.let(::formatDateTime)
                        ?: context.getString(R.string.study_pdf_no_previous_study)
                )
            )
            add(
                StudyClinicalKvRow(
                    context.getString(R.string.study_pdf_field_note_status),
                    if (selected.study.notes.isNullOrBlank()) {
                        context.getString(R.string.study_pdf_note_absent)
                    } else {
                        context.getString(R.string.study_pdf_note_present)
                    }
                )
            )
        }

        return StudyClinicalPdfDocument(
            appName = context.getString(R.string.pdf_app_name),
            patientDisplayName = patientDisplayName,
            studyId = selected.study.id,
            studyType = selected.study.type.ifBlank { context.getString(R.string.common_value_na) },
            studyAtMillis = studyAtMillis,
            reportAtMillis = reportAtMillis,
            executiveSummary = executiveSummary,
            quickReadRows = quickRead,
            keyMetrics = keyMetrics,
            traceabilityRows = traceabilityRows,
            pressureRows = pressures,
            flowRows = flowAndOxygenation,
            resistanceRows = resistanceAndPerformance,
            interpretationBullets = buildInterpretationBullets(context, rhc, quickRead),
            comparisonRows = buildComparisonRows(context, selected.rhc, previous?.rhc),
            studyNote = selected.study.notes?.trim()?.takeIf { it.isNotBlank() },
            limitations = buildLimitations(context, rhc),
            pressureChart = listOf(
                StudyClinicalChartPoint("RAP", "mmHg", rhc?.rapMmHg, previous?.rhc?.rapMmHg, 25.0, 8.0),
                StudyClinicalChartPoint("mPAP", "mmHg", rhc?.mpapMmHg, previous?.rhc?.mpapMmHg, 60.0, 20.0),
                StudyClinicalChartPoint("PCWP", "mmHg", rhc?.pawpMmHg, previous?.rhc?.pawpMmHg, 35.0, 15.0)
            ),
            performanceChart = listOf(
                StudyClinicalChartPoint("CI", "L/min/m2", selectedCo.cardiacIndexLMinM2, previous?.rhc.displaySelectedCo().cardiacIndexLMinM2, 5.0, 2.2),
                StudyClinicalChartPoint("PVR", "WU", pvrWood, previous?.rhc?.pvrWood, 8.0, 2.0),
                StudyClinicalChartPoint("CPO", "W", rhc?.cardiacPowerW, previous?.rhc?.cardiacPowerW, 2.0, 0.8)
            ),
            patientInternalCode = patientInternalCode?.trim()?.takeIf { it.isNotBlank() },
            patientSex = patientSex?.trim()?.takeIf { it.isNotBlank() },
            patientBirthDateAndAge = patientBirthDateMillis?.let(::formatBirthDateAndAge),
            patientWeightKg = patientWeightKg,
            patientHeightCm = patientHeightCm,
            patientNote = patientNote?.trim()?.takeIf { it.isNotBlank() },
            patientWeightDisplayValue = patientWeightKg?.let { kg ->
                if (unitSystem == UnitSystem.Imperial) ClinicalUnitNormalizer.weightFromKg(kg, ClinicalUnitNormalizer.WeightUnit.LB) else kg
            },
            patientWeightDisplayUnit = if (unitSystem == UnitSystem.Imperial) "lb" else "kg",
            patientHeightDisplayValue = patientHeightCm?.let { cm ->
                if (unitSystem == UnitSystem.Imperial) ClinicalUnitNormalizer.heightFromCm(cm, ClinicalUnitNormalizer.HeightUnit.IN) else cm
            },
            patientHeightDisplayUnit = if (unitSystem == UnitSystem.Imperial) "in" else "cm",
            forrester = StudyClinicalForrester(
                currentCi = selectedCo.cardiacIndexLMinM2,
                currentPcwp = rhc?.pawpMmHg,
                previousCi = previous?.rhc.displaySelectedCo().cardiacIndexLMinM2,
                previousPcwp = previous?.rhc?.pawpMmHg
            ),
            trendStudies = history.map { study ->
                val co = study.rhc.displaySelectedCo()
                StudyClinicalTrendStudy(
                    dateLabel = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(study.study.startedAtMillis)),
                    studyId = study.study.id,
                    method = co.methodLabelRes?.let(context::getString),
                    rap = study.rhc?.rapMmHg,
                    pasp = study.rhc?.paspMmHg,
                    padp = study.rhc?.padpMmHg,
                    mpap = study.rhc?.mpapMmHg,
                    pcwp = study.rhc?.pawpMmHg,
                    map = study.rhc?.mapMmHg,
                    co = co.cardiacOutputLMin,
                    ci = co.cardiacIndexLMinM2,
                    sao2 = study.rhc?.saO2Percent,
                    svo2 = study.rhc?.svO2Percent,
                    hemoglobin = study.rhc?.hemoglobinGdl,
                    pvr = study.rhc?.pvrWood,
                    svr = study.rhc?.svrWood,
                    cpo = study.rhc?.cardiacPowerW,
                    papi = study.rhc?.papi,
                    tpg = calcDifference(study.rhc?.mpapMmHg, study.rhc?.pawpMmHg),
                    dpg = calcDifference(study.rhc?.padpMmHg, study.rhc?.pawpMmHg),
                    note = study.study.notes?.trim()?.takeIf { it.isNotBlank() },
                    pvrDisplayValue = study.rhc.displayPvr().value,
                    pvrDisplayUnit = study.rhc.displayPvr().unitRes?.let(context::getString) ?: "WU",
                    svrDisplayValue = study.rhc.displaySvr().value,
                    svrDisplayUnit = study.rhc.displaySvr().unitRes?.let(context::getString) ?: "WU"
                )
            }
        )
    }

    private fun formatBirthDateAndAge(storageMillis: Long): String? = runCatching {
        val birthDate = BirthDateCodec.fromStorageMillis(storageMillis)
        val age = Period.between(birthDate, LocalDate.now()).years.coerceAtLeast(0)
        "%02d/%02d/%04d (%d años)".format(
            Locale.getDefault(), birthDate.dayOfMonth, birthDate.monthValue, birthDate.year, age
        )
    }.getOrNull()

    private fun buildQuickRead(
        context: Context,
        rhc: RhcStudyDataEntity?,
        pvrValue: Double?
    ): List<StudyClinicalQuickReadRow> {
        val rightFilling = when (val rap = rhc?.rapMmHg) {
            null -> context.getString(R.string.study_pdf_status_unavailable)
            else -> if (rap > 8.0) context.getString(R.string.study_pdf_status_elevated) else context.getString(R.string.study_pdf_status_normal)
        }
        val leftFilling = when (val pcwp = rhc?.pawpMmHg) {
            null -> context.getString(R.string.study_pdf_status_unavailable)
            else -> if (pcwp > 15.0) context.getString(R.string.study_pdf_status_elevated) else context.getString(R.string.study_pdf_status_normal)
        }
        val flow = when (val ci = rhc.displaySelectedCo().cardiacIndexLMinM2) {
            null -> context.getString(R.string.study_pdf_status_unavailable)
            else -> when {
                ci < 2.2 -> context.getString(R.string.study_pdf_status_low)
                ci > 4.0 -> context.getString(R.string.study_pdf_status_high)
                else -> context.getString(R.string.study_pdf_status_preserved)
            }
        }
        val pulmonaryVascular = when (pvrValue) {
            null -> context.getString(R.string.study_pdf_status_unavailable)
            else -> if (pvrValue > 2.0) context.getString(R.string.study_pdf_status_elevated) else context.getString(R.string.study_pdf_status_normal)
        }

        return listOf(
            StudyClinicalQuickReadRow(context.getString(R.string.study_pdf_quick_right_filling), rightFilling),
            StudyClinicalQuickReadRow(context.getString(R.string.study_pdf_quick_left_filling), leftFilling),
            StudyClinicalQuickReadRow(context.getString(R.string.study_pdf_quick_flow), flow),
            StudyClinicalQuickReadRow(context.getString(R.string.study_pdf_quick_pulmonary_vascular), pulmonaryVascular),
            StudyClinicalQuickReadRow(context.getString(R.string.study_pdf_quick_pattern), buildPatternText(context, rhc, pvrValue))
        )
    }

    private fun buildPatternText(
        context: Context,
        rhc: RhcStudyDataEntity?,
        pvrValue: Double?
    ): String {
        val mpap = rhc?.mpapMmHg
        val pcwp = rhc?.pawpMmHg
        return when {
            mpap == null || pcwp == null || pvrValue == null -> context.getString(R.string.study_pdf_status_unavailable)
            mpap <= 20.0 -> context.getString(R.string.study_pdf_pattern_no_ph)
            pcwp <= 15.0 && pvrValue > 2.0 -> context.getString(R.string.study_pdf_pattern_precapillary)
            pcwp > 15.0 && pvrValue > 2.0 -> context.getString(R.string.study_pdf_pattern_combined_post_pre)
            pcwp > 15.0 -> context.getString(R.string.study_pdf_pattern_isolated_postcapillary)
            else -> context.getString(R.string.study_pdf_status_unavailable)
        }
    }

    private fun buildInterpretationBullets(
        context: Context,
        rhc: RhcStudyDataEntity?,
        quickReadRows: List<StudyClinicalQuickReadRow>
    ): List<String> {
        val bullets = quickReadRows.map { "${it.label}: ${it.value}." }.toMutableList()

        val ci = rhc.displaySelectedCo().cardiacIndexLMinM2
        val pcwp = rhc?.pawpMmHg
        if (ci != null && pcwp != null) {
            val forrester = when {
                ci >= 2.2 && pcwp <= 18.0 -> context.getString(R.string.study_detail_forrester_profile_i_title)
                ci >= 2.2 && pcwp > 18.0 -> context.getString(R.string.study_detail_forrester_profile_ii_title)
                ci < 2.2 && pcwp <= 18.0 -> context.getString(R.string.study_detail_forrester_profile_iii_title)
                else -> context.getString(R.string.study_detail_forrester_profile_iv_title)
            }
            bullets += context.getString(R.string.study_pdf_forrester_profile_line, forrester)
        }

        if (!rhc?.vo2Mode.isNullOrBlank()) {
            bullets += context.getString(R.string.study_pdf_vo2_mode_line, rhc?.vo2Mode)
        }

        return bullets
    }

    private fun buildComparisonRows(
        context: Context,
        current: RhcStudyDataEntity?,
        previous: RhcStudyDataEntity?
    ): List<StudyClinicalComparisonRow> {
        if (previous == null) return emptyList()

        val currentCo = current.displaySelectedCo()
        val previousCo = previous.displaySelectedCo()

        return listOfNotNull(
            comparisonRow(context.getString(R.string.papi_help_rap_title), current?.rapMmHg, previous.rapMmHg, 0, context.getString(R.string.common_unit_mmhg)),
            comparisonRow(context.getString(R.string.pvr_help_mpap_title), current?.mpapMmHg, previous.mpapMmHg, 0, context.getString(R.string.common_unit_mmhg)),
            comparisonRow(context.getString(R.string.rhc_label_pcwp_short), current?.pawpMmHg, previous.pawpMmHg, 0, context.getString(R.string.common_unit_mmhg)),
            comparisonRow(context.getString(R.string.rhc_label_ci_short), currentCo.cardiacIndexLMinM2, previousCo.cardiacIndexLMinM2, 1, context.getString(R.string.common_unit_lmin_m2)),
            comparisonRow(context.getString(R.string.home_badge_pvr), current?.pvrWood, previous.pvrWood, 1, context.getString(R.string.common_unit_wu_short)),
            comparisonRow(context.getString(R.string.home_badge_cpo), current?.cardiacPowerW, previous.cardiacPowerW, 2, context.getString(R.string.common_unit_w))
        )
    }

    private fun buildLimitations(
        context: Context,
        rhc: RhcStudyDataEntity?
    ): List<String> {
        val missing = mutableListOf<String>()
        if (rhc?.rapMmHg == null) missing += context.getString(R.string.papi_help_rap_title)
        if (rhc?.mpapMmHg == null) missing += context.getString(R.string.pvr_help_mpap_title)
        if (rhc?.pawpMmHg == null) missing += context.getString(R.string.rhc_label_pcwp_short)
        if (rhc.displaySelectedCo().cardiacIndexLMinM2 == null) missing += context.getString(R.string.rhc_label_ci_short)
        if (rhc?.pvrWood == null) missing += context.getString(R.string.home_badge_pvr)
        if (rhc?.svO2Percent == null) missing += context.getString(R.string.fick_label_svo2_short)

        if (missing.isEmpty()) return emptyList()
        return listOf(
            context.getString(
                R.string.study_pdf_missing_data_line,
                missing.joinToString(separator = ", ")
            )
        )
    }

    private fun metricRow(
        label: String,
        value: Double?,
        decimals: Int,
        unit: String?
    ): StudyClinicalMetricRow {
        return StudyClinicalMetricRow(
            label = label,
            value = formatMetricValue(value, decimals),
            unit = unit
        )
    }

    private fun metricTextRow(
        label: String,
        value: String?,
        unit: String? = null
    ): StudyClinicalMetricRow {
        return StudyClinicalMetricRow(
            label = label,
            value = value?.takeIf { it.isNotBlank() } ?: "—",
            unit = unit
        )
    }

    private fun metricChip(
        label: String,
        value: Double?,
        decimals: Int,
        unit: String?,
        highlightHigh: Boolean = false,
        highlightLow: Boolean = false
    ): StudyClinicalMetricChip {
        val text = formatMetricValue(value, decimals)
        val tone = when {
            text == "—" -> StudyClinicalMetricTone.NEUTRAL
            highlightHigh || highlightLow -> StudyClinicalMetricTone.ALERT
            else -> StudyClinicalMetricTone.NEUTRAL
        }
        return StudyClinicalMetricChip(label = label, value = text, unit = unit, tone = tone)
    }

    private fun comparisonRow(
        label: String,
        current: Double?,
        previous: Double?,
        decimals: Int,
        unit: String?
    ): StudyClinicalComparisonRow? {
        if (current == null && previous == null) return null
        return StudyClinicalComparisonRow(
            label = label,
            previousValue = formatMetricValue(previous, decimals),
            currentValue = formatMetricValue(current, decimals),
            deltaValue = formatDelta(current, previous, decimals, unit)
        )
    }

    private fun calcDifference(a: Double?, b: Double?): Double? {
        if (a == null || b == null) return null
        return a - b
    }

    private fun formatMetricValue(value: Double?, decimals: Int): String {
        if (value == null) return "—"
        val nf = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = 0
        }
        return nf.format(value)
    }

    private fun formatDelta(
        current: Double?,
        previous: Double?,
        decimals: Int,
        unit: String?
    ): String {
        if (current == null || previous == null) return "—"
        val delta = current - previous
        val nf = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = 0
        }
        val sign = if (delta > 0) "+" else ""
        val arrow = when {
            delta > 0 -> "↑"
            delta < 0 -> "↓"
            else -> "→"
        }
        val suffix = unit?.let { " $it" } ?: ""
        return "$arrow $sign${nf.format(delta)}$suffix"
    }

    private fun formatDateTime(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

private object LegacyStudyClinicalPdfGenerator {

    fun writePdf(
        context: Context,
        outputStream: OutputStream,
        document: StudyClinicalPdfDocument,
        format: StudyClinicalPdfFormat
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentMaxY = pageHeight - margin - 18f
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val paletteHeader = Color.parseColor("#123046")
        val paletteLine = Color.parseColor("#D9E1E7")
        val paletteText = Color.parseColor("#1D2730")
        val paletteMuted = Color.parseColor("#5E6B75")
        val paletteCard = Color.WHITE
        val paletteSoft = Color.parseColor("#F4F7F9")
        val paletteSoftBlue = Color.parseColor("#EAF2F8")
        val paletteSoftAmber = Color.parseColor("#FBF3E6")
        val paletteSoftRed = Color.parseColor("#FBEAEA")

        val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteHeader; style = Paint.Style.FILL }
        val pCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteCard; style = Paint.Style.FILL }
        val pSoft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteSoft; style = Paint.Style.FILL }
        val pSoftBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteSoftBlue; style = Paint.Style.FILL }
        val pSoftAmber = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteSoftAmber; style = Paint.Style.FILL }
        val pSoftRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteSoftRed; style = Paint.Style.FILL }
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteLine; style = Paint.Style.STROKE; strokeWidth = 1f }
        val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pSubtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8E2EA")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pSection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paletteHeader
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paletteText
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pBodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paletteText
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paletteMuted
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pTiny = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paletteMuted
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val lines = mutableListOf<String>()
            text.split("\n").forEach { chunk ->
                if (chunk.isBlank()) {
                    lines += ""
                    return@forEach
                }
                var current = ""
                chunk.split(" ").forEach { word ->
                    val candidate = if (current.isBlank()) word else "$current $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotBlank()) lines += current
                        current = word
                    }
                }
                if (current.isNotBlank()) lines += current
            }
            return lines.ifEmpty { listOf("") }
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 0f

        fun drawFooter() {
            canvas.drawLine(margin, pageHeight - margin - 8f, pageWidth - margin, pageHeight - margin - 8f, pLine)
            canvas.drawText(
                context.getString(R.string.study_pdf_footer_disclaimer),
                margin,
                pageHeight - margin,
                pTiny
            )
            val pageText = context.getString(R.string.pdf_footer_page, pageNumber)
            canvas.drawText(
                pageText,
                pageWidth - margin - pTiny.measureText(pageText),
                pageHeight - margin,
                pTiny
            )
        }

        fun drawHeader() {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, pHeader)
            val title = when (format) {
                StudyClinicalPdfFormat.COMPACT -> context.getString(R.string.study_pdf_compact_title)
                StudyClinicalPdfFormat.COMPLETE -> context.getString(R.string.study_pdf_complete_title)
            }
            canvas.drawText(title, margin, 42f, pTitle)
            val subtitle = "${document.patientDisplayName} · ${dateFormat.format(Date(document.studyAtMillis))}"
            canvas.drawText(subtitle, margin, 63f, pSubtitle)
            y = 112f
        }

        fun newPage() {
            drawFooter()
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            drawHeader()
        }

        fun ensureSpace(height: Float) {
            if (y + height > contentMaxY) newPage()
        }

        fun drawCard(
            height: Float,
            background: Paint = pCard,
            draw: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
        ) {
            ensureSpace(height + 8f)
            val left = margin
            val top = y
            val right = pageWidth - margin
            val bottom = top + height
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 18f, 18f, background)
            canvas.drawRoundRect(rect, 18f, 18f, pLine)
            draw(left, top, right, bottom)
            y = bottom + 12f
        }

        fun drawSectionLabel(title: String, left: Float, top: Float): Float {
            val baseline = top + 16f - pSection.fontMetrics.ascent
            canvas.drawText(title, left + 16f, baseline, pSection)
            return baseline + pSection.fontMetrics.descent + 8f
        }

        fun drawIdentityCard() {
            val rows = listOf(
                StudyClinicalKvRow(context.getString(R.string.study_pdf_field_patient), document.patientDisplayName),
                StudyClinicalKvRow(context.getString(R.string.study_pdf_field_study_id), document.studyId),
                StudyClinicalKvRow(context.getString(R.string.study_pdf_field_study_date), dateFormat.format(Date(document.studyAtMillis))),
                StudyClinicalKvRow(context.getString(R.string.study_pdf_field_report_date), dateFormat.format(Date(document.reportAtMillis)))
            )
            val rowGap = if (format == StudyClinicalPdfFormat.COMPACT) 16f else 18f
            val height = 22f + 18f + rows.size * rowGap + 16f
            drawCard(height = height, background = pSoft) { left, top, right, _ ->
                var yy = drawSectionLabel(context.getString(R.string.study_pdf_section_identity), left, top)
                rows.forEach { row ->
                    canvas.drawText(row.label, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    val valueWidth = pBodyBold.measureText(row.value)
                    canvas.drawText(row.value, right - 16f - valueWidth, yy - pBodyBold.fontMetrics.ascent, pBodyBold)
                    yy += rowGap
                }
            }
        }

        fun drawSummaryCard() {
            val width = pageWidth - 2 * margin - 32f
            val lines = wrap(document.executiveSummary, pBodyBold, width)
            val rowGap = max(15f, pBodyBold.textSize + 3f)
            val height = 22f + 18f + lines.size * rowGap + 16f
            drawCard(height = height, background = pSoftBlue) { left, top, _, _ ->
                val startY = drawSectionLabel(context.getString(R.string.study_pdf_section_summary), left, top)
                var yy = startY
                lines.forEach { line ->
                    canvas.drawText(line, left + 16f, yy - pBodyBold.fontMetrics.ascent, pBodyBold)
                    yy += rowGap
                }
            }
        }

        fun drawMetricGrid(metrics: List<StudyClinicalMetricChip>, columns: Int = 4) {
            if (metrics.isEmpty()) return
            val cardWidth = pageWidth - 2 * margin
            val gap = 8f
            val cellWidth = (cardWidth - 32f - ((columns - 1) * gap)) / columns
            val cellHeight = if (format == StudyClinicalPdfFormat.COMPACT) 76f else 84f
            val rowCount = (metrics.size + columns - 1) / columns
            val height = 22f + 18f + rowCount * cellHeight + (rowCount - 1) * gap + 16f
            drawCard(height = height) { left, top, _, _ ->
                var yy = drawSectionLabel(context.getString(R.string.study_pdf_section_key_metrics), left, top)
                metrics.chunked(columns).forEach { row ->
                    var xx = left + 16f
                    row.forEach { metric ->
                        val background = when (metric.tone) {
                            StudyClinicalMetricTone.ALERT -> pSoftRed
                            StudyClinicalMetricTone.NEUTRAL -> pSoft
                        }
                        val rect = RectF(xx, yy, xx + cellWidth, yy + cellHeight)
                        canvas.drawRoundRect(rect, 14f, 14f, background)
                        canvas.drawRoundRect(rect, 14f, 14f, pLine)

                        val labelLines = wrap(metric.label, pSmall, cellWidth - 16f)
                        var lineY = yy + 12f - pSmall.fontMetrics.ascent
                        labelLines.take(2).forEach { line ->
                            val lineWidth = pSmall.measureText(line)
                            canvas.drawText(line, rect.centerX() - lineWidth / 2f, lineY, pSmall)
                            lineY += 11f
                        }

                        val valueWidth = pBodyBold.measureText(metric.value)
                        val valueBaseline = yy + (if (format == StudyClinicalPdfFormat.COMPACT) 43f else 47f) - pBodyBold.fontMetrics.ascent
                        canvas.drawText(metric.value, rect.centerX() - valueWidth / 2f, valueBaseline, pBodyBold)

                        val unit = metric.unit.orEmpty()
                        if (unit.isNotBlank()) {
                            val unitWidth = pSmall.measureText(unit)
                            val unitBaseline = yy + (if (format == StudyClinicalPdfFormat.COMPACT) 61f else 69f) - pSmall.fontMetrics.ascent
                            canvas.drawText(unit, rect.centerX() - unitWidth / 2f, unitBaseline, pSmall)
                        }

                        xx += cellWidth + gap
                    }
                    yy += cellHeight + gap
                }
            }
        }

        fun drawQuickReadCard() {
            val rowGap = if (format == StudyClinicalPdfFormat.COMPACT) 16f else 18f
            val height = 22f + 18f + document.quickReadRows.size * rowGap + 16f
            drawCard(height = height) { left, top, right, _ ->
                var yy = drawSectionLabel(context.getString(R.string.study_pdf_quick_read_title), left, top)
                document.quickReadRows.forEach { row ->
                    canvas.drawText(row.label, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    val wrapped = wrap(row.value, pBodyBold, (right - left) - 210f)
                    val first = wrapped.firstOrNull().orEmpty()
                    val valueWidth = pBodyBold.measureText(first)
                    canvas.drawText(first, right - 16f - valueWidth, yy - pBodyBold.fontMetrics.ascent, pBodyBold)
                    yy += rowGap
                }
            }
        }

        fun drawCompactLimitationsCard(text: String) {
            val safeText = if (text.length > 150) text.take(147).trimEnd() + "..." else text
            val maxTextWidth = pageWidth - 2 * margin - 32f
            val lines = wrap(safeText, pBody, maxTextWidth)
            val rowGap = max(14f, pBody.textSize + 3f)
            val height = 22f + 18f + lines.size * rowGap + 16f
            drawCard(height = height, background = pSoftAmber) { left, top, _, _ ->
                var yy = drawSectionLabel(context.getString(R.string.study_pdf_section_limitations), left, top)
                lines.forEach { line ->
                    canvas.drawText(line, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    yy += rowGap
                }
            }
        }

        fun drawMetricTable(title: String, rows: List<StudyClinicalMetricRow>) {
            if (rows.isEmpty()) return
            val rowGap = 18f
            val height = 22f + 18f + rows.size * rowGap + 16f
            drawCard(height = height) { left, top, right, _ ->
                var yy = drawSectionLabel(title, left, top)
                rows.forEach { row ->
                    canvas.drawText(row.label, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    val valueUnit = listOfNotNull(row.value.takeIf { it.isNotBlank() }, row.unit?.takeIf { it.isNotBlank() }).joinToString(" ")
                    val wrapped = wrap(valueUnit, pBodyBold, (right - left) - 210f)
                    val first = wrapped.firstOrNull().orEmpty()
                    val valueWidth = pBodyBold.measureText(first)
                    canvas.drawText(first, right - 16f - valueWidth, yy - pBodyBold.fontMetrics.ascent, pBodyBold)
                    yy += rowGap
                }
            }
        }

        fun drawKvTable(title: String, rows: List<StudyClinicalKvRow>) {
            if (rows.isEmpty()) return
            val rowGap = 18f
            val height = 22f + 18f + rows.size * rowGap + 16f
            drawCard(height = height, background = pSoft) { left, top, right, _ ->
                var yy = drawSectionLabel(title, left, top)
                rows.forEach { row ->
                    canvas.drawText(row.label, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    val wrapped = wrap(row.value, pBodyBold, (right - left) - 210f)
                    val first = wrapped.firstOrNull().orEmpty()
                    val valueWidth = pBodyBold.measureText(first)
                    canvas.drawText(first, right - 16f - valueWidth, yy - pBodyBold.fontMetrics.ascent, pBodyBold)
                    yy += rowGap
                }
            }
        }

        fun drawBulletsCard(title: String, bullets: List<String>, amber: Boolean = false) {
            if (bullets.isEmpty()) return
            val maxTextWidth = pageWidth - 2 * margin - 48f
            val rowGap = max(14f, pBody.textSize + 3f)
            val lines = bullets.sumOf { wrap(it, pBody, maxTextWidth - 10f).size }
            val height = 22f + 18f + lines * rowGap + 18f
            drawCard(height = height, background = if (amber) pSoftAmber else pCard) { left, top, _, _ ->
                var yy = drawSectionLabel(title, left, top)
                bullets.forEach { bullet ->
                    wrap(bullet, pBody, maxTextWidth - 10f).forEachIndexed { index, line ->
                        val prefix = if (index == 0) "• " else "  "
                        canvas.drawText(prefix + line, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                        yy += rowGap
                    }
                }
            }
        }

        fun drawComparisonCard(rows: List<StudyClinicalComparisonRow>) {
            if (rows.isEmpty()) return
            val rowGap = 18f
            val height = 22f + 18f + (rows.size + 1) * rowGap + 16f
            drawCard(height = height) { left, top, right, _ ->
                var yy = drawSectionLabel(context.getString(R.string.study_pdf_section_comparison), left, top)
                val xPrev = right - 170f
                val xCurr = right - 105f
                val xDelta = right - 16f
                val previousHeader = context.getString(R.string.study_pdf_header_previous)
                val currentHeader = context.getString(R.string.study_pdf_header_current)
                val deltaHeader = context.getString(R.string.study_pdf_header_delta)
                canvas.drawText(previousHeader, xPrev - pSmall.measureText(previousHeader), yy - pSmall.fontMetrics.ascent, pSmall)
                canvas.drawText(currentHeader, xCurr - pSmall.measureText(currentHeader), yy - pSmall.fontMetrics.ascent, pSmall)
                canvas.drawText(deltaHeader, xDelta - pSmall.measureText(deltaHeader), yy - pSmall.fontMetrics.ascent, pSmall)
                yy += rowGap

                rows.forEach { row ->
                    canvas.drawText(row.label, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    canvas.drawText(row.previousValue, xPrev - pBody.measureText(row.previousValue), yy - pBody.fontMetrics.ascent, pBody)
                    canvas.drawText(row.currentValue, xCurr - pBodyBold.measureText(row.currentValue), yy - pBodyBold.fontMetrics.ascent, pBodyBold)
                    canvas.drawText(row.deltaValue, xDelta - pBody.measureText(row.deltaValue), yy - pBody.fontMetrics.ascent, pBody)
                    yy += rowGap
                }
            }
        }

        fun drawNoteCard(note: String, compact: Boolean) {
            val text = if (compact && note.length > 260) note.take(257).trimEnd() + "..." else note
            val maxTextWidth = pageWidth - 2 * margin - 32f
            val lines = wrap(text, pBody, maxTextWidth)
            val rowGap = max(14f, pBody.textSize + 3f)
            val height = 22f + 18f + lines.size * rowGap + 16f
            drawCard(height = height, background = pSoft) { left, top, _, _ ->
                var yy = drawSectionLabel(context.getString(R.string.study_pdf_section_note), left, top)
                lines.forEach { line ->
                    canvas.drawText(line, left + 16f, yy - pBody.fontMetrics.ascent, pBody)
                    yy += rowGap
                }
            }
        }

        drawHeader()
        drawIdentityCard()
        drawSummaryCard()
        drawMetricGrid(document.keyMetrics)
        drawQuickReadCard()

        if (format == StudyClinicalPdfFormat.COMPACT) {
            drawCompactLimitationsCard(
                document.limitations.firstOrNull() ?: context.getString(R.string.study_pdf_no_major_limitations)
            )
        } else {
            drawKvTable(context.getString(R.string.study_pdf_section_traceability), document.traceabilityRows)
            drawMetricTable(context.getString(R.string.study_pdf_section_pressures), document.pressureRows)
            drawMetricTable(context.getString(R.string.study_pdf_section_flow), document.flowRows)
            drawMetricTable(context.getString(R.string.study_pdf_section_resistance), document.resistanceRows)
            drawBulletsCard(context.getString(R.string.study_pdf_section_interpretation), document.interpretationBullets)
            if (document.comparisonRows.isNotEmpty()) {
                drawComparisonCard(document.comparisonRows)
            }
            if (document.studyNote != null) {
                drawNoteCard(document.studyNote, compact = false)
            }
            drawBulletsCard(
                title = context.getString(R.string.study_pdf_section_limitations),
                bullets = document.limitations.ifEmpty { listOf(context.getString(R.string.study_pdf_no_major_limitations)) },
                amber = true
            )
        }

        drawFooter()
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
