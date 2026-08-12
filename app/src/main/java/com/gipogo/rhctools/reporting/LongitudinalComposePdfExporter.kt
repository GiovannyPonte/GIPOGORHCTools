package com.gipogo.rhctools.reporting.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.report.PdfLongitudinalReportGenerator
import com.gipogo.rhctools.reporting.model.displaySelectedCo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object LongitudinalComposePdfExporter {

    data class ExportResult(
        val file: File,
        val uri: Uri,
        val pageCount: Int
    )

    /**
     * STREAMING export:
     * - Build report model from Room
     * - Render page-by-page (A4 @ 300 DPI) to avoid OOM
     * - Write each page to PdfDocument immediately
     */
    suspend fun export(
        context: Context,
        patientId: String,
        patientDao: PatientDao,
        rhcStudyDao: RhcStudyDao,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): ExportResult {
        val studies = rhcStudyDao.listStudiesWithRhcDataByPatient(patientId).first()
            .sortedBy { it.study.startedAtMillis }
        if (studies.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.patient_reports_no_studies))
        }

        val patient = runCatching { patientDao.getById(patientId) }.getOrNull()
        val patientDisplayName = patient?.displayName?.takeIf { it.isNotBlank() }
            ?: patient?.internalCode?.takeIf { it.isNotBlank() }
            ?: patientId

        val rows = studies.map { studyWithData ->
            val rhc = studyWithData.rhc
            val selectedCo = rhc.displaySelectedCo()

            PdfLongitudinalReportGenerator.StudyRow(
                studyId = studyWithData.study.id,
                studyAtMillis = studyWithData.study.startedAtMillis,
                ciLMinM2 = selectedCo.cardiacIndexLMinM2,
                coLMin = selectedCo.cardiacOutputLMin,
                pvrWu = rhc?.pvrWood,
                svrWu = rhc?.svrWood,
                rapMmHg = rhc?.rapMmHg,
                mpapMmHg = rhc?.mpapMmHg,
                pcwpMmHg = rhc?.pawpMmHg,
                cpoW = rhc?.cardiacPowerW
            )
        }

        val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val file = File(outDir, "RHC_LONG_COMPOSE_${patientId}_${System.currentTimeMillis()}.pdf")
        val totalPages = estimatePageCount(rows.size)

        withContext(Dispatchers.IO) {
            file.outputStream().use { output ->
                PdfLongitudinalReportGenerator.writeLongitudinalPdf(
                    context = context,
                    outputStream = output,
                    appName = context.getString(R.string.pdf_app_name),
                    patientDisplayName = patientDisplayName,
                    createdAtMillis = System.currentTimeMillis(),
                    studies = rows,
                    onPageWritten = { current, total ->
                        onProgress?.invoke(current, total)
                    }
                )
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        onProgress?.invoke(totalPages, totalPages)

        return ExportResult(file = file, uri = uri, pageCount = totalPages)
    }

    private fun estimatePageCount(studyCount: Int): Int {
        if (studyCount <= 0) return 0

        val coverPages = if (studyCount >= 2) 1 else 0
        val trendPages = if (studyCount >= 2) 2 else 0
        return coverPages + studyCount + trendPages
    }
}
