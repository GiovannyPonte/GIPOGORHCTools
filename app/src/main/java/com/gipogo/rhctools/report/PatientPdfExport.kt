package com.gipogo.rhctools.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import java.text.NumberFormat

object PatientPdfExport {

    data class ExportResult(
        val pdfFile: File,
        val pdfUri: Uri
    )

    suspend fun exportLatestStudyPdf(
        context: Context,
        patientId: String,
        patientDao: PatientDao,
        rhcStudyDao: RhcStudyDao
    ): ExportResult {

        // 1) Cargar estudios del paciente (Room -> Flow -> first snapshot)
        val studies: List<StudyWithRhcData> =
            rhcStudyDao.listStudiesWithRhcDataByPatient(patientId).first()

        val latest = studies.firstOrNull()
            ?: throw IllegalStateException(context.getString(R.string.patient_reports_no_studies))

        return exportStudyPdfFromSnapshot(
            context = context,
            patientId = patientId,
            patientDao = patientDao,
            latest = latest
        )
    }

    private suspend fun exportStudyPdfFromSnapshot(
        context: Context,
        patientId: String,
        patientDao: PatientDao,
        latest: StudyWithRhcData
    ): ExportResult {

        // 2) Header name (si existe)
        val patient = runCatching { patientDao.getById(patientId) }.getOrNull()
        val patientTitle = patient?.displayName?.takeIf { it.isNotBlank() }
            ?: patient?.internalCode?.takeIf { it.isNotBlank() }
            ?: patientId

        // 3) Construir entries compactos desde RhcStudyDataEntity (auditable)
        val rhc = latest.rhc // puede ser null si el estudio está vacío/incompleto

        val uMmHg = context.getString(R.string.common_unit_mmhg)
        val uLMinM2 = context.getString(R.string.common_unit_lmin_m2)
        val uWuShort = context.getString(R.string.common_unit_wu_short)
        val uW = context.getString(R.string.common_unit_w)

        val lRap = context.getString(R.string.papi_help_rap_title)
        val lMpap = context.getString(R.string.pvr_help_mpap_title)
        val lPcwp = context.getString(R.string.rhc_label_pcwp_short)
        val lCi = context.getString(R.string.rhc_label_ci_short)
        val lPvr = context.getString(R.string.home_badge_pvr)
        val lCpo = context.getString(R.string.home_badge_cpo)

        val nf0 = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        val nf1 = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
        val nf2 = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }

        val outputs: List<LineItem> = buildList {
            rhc?.rapMmHg?.let { add(LineItem(label = lRap, value = nf0.format(it), unit = uMmHg)) }
            rhc?.mpapMmHg?.let { add(LineItem(label = lMpap, value = nf0.format(it), unit = uMmHg)) }
            rhc?.pawpMmHg?.let { add(LineItem(label = lPcwp, value = nf0.format(it), unit = uMmHg)) }
            rhc?.cardiacIndexLMinM2?.let { add(LineItem(label = lCi, value = nf1.format(it), unit = uLMinM2)) }
            rhc?.pvrWood?.let { add(LineItem(label = lPvr, value = nf1.format(it), unit = uWuShort)) }
            rhc?.cardiacPowerW?.let { add(LineItem(label = lCpo, value = nf2.format(it), unit = uW)) }
        }

        val entries = buildList {
            add(
                CalcEntry(
                    type = CalcType.PVR, // usa un valor existente
                    title = context.getString(R.string.patient_overview_latest_summary),
                    timestampMillis = latest.study.startedAtMillis,
                    inputs = emptyList(),
                    outputs = outputs,
                    notes = emptyList()
                )
            )
        }



        // 4) Crear archivo
        val nowMillis = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val stamp = sdf.format(Date(nowMillis))

        val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val file = File(outDir, "RHC_${patientId}_$stamp.pdf")

        file.outputStream().use { os ->
            val appName = context.getString(R.string.pdf_app_name) // si no existe, cambia a R.string.app_name
            val headerName = "$appName · $patientTitle"
            PdfReportGenerator.writePdf(
                context = context,
                outputStream = os,
                appName = headerName,
                entries = entries,
                nowMillis = nowMillis
            )
        }

        // 5) Uri para visor/compartir
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return ExportResult(pdfFile = file, pdfUri = uri)
    }

    private fun MutableList<LineItem>.addLineIfPresent(
        context: Context,
        labelRes: Int,
        value: Double?,
        decimals: Int,
        unitRes: Int,
        key: String
    ) {
        if (value == null) return
        val nf = java.text.NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = 0
        }
        add(
            LineItem(
                key = key,
                label = context.getString(labelRes),
                detail = null,
                value = nf.format(value),
                unit = context.getString(unitRes)
            )
        )
    }
}
