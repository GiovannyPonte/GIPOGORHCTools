package com.gipogo.rhctools.reporting.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.reporting.builder.LongitudinalReportBuilder
import com.gipogo.rhctools.reporting.compose.ReportPage
import com.gipogo.rhctools.reporting.render.ComposeA4Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.graphics.pdf.PdfDocument

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
        rhcStudyDao: RhcStudyDao
    ): ExportResult {

        // 1) Build UI model from Room
        val document = LongitudinalReportBuilder.buildFromRoom(
            context = context,
            patientId = patientId,
            patientDao = patientDao,
            rhcStudyDao = rhcStudyDao
        )

        if (document.pages.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.patient_reports_no_studies))
        }

        // 2) Output file
        val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val file = File(outDir, "RHC_LONG_COMPOSE_${patientId}_${System.currentTimeMillis()}.pdf")

        // 3) Stream PDF writing on IO, but each Compose render must happen on Main
        val pdf = PdfDocument()
        try {
            for (i in document.pages.indices) {
                // Render 1 page bitmap (Main thread inside renderer)
                val bmp = ComposeA4Renderer.renderPageToBitmap(
                    context = context,
                    widthPx = ComposeA4Renderer.A4_WIDTH_PX_300,
                    heightPx = ComposeA4Renderer.A4_HEIGHT_PX_300
                ) {
                    ReportPage(page = document.pages[i])
                }

                // Write that bitmap as one PDF page (IO)
                withContext(Dispatchers.IO) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                    val page = pdf.startPage(pageInfo)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    pdf.finishPage(page)
                }

                // Free memory immediately
                bmp.recycle()
            }

            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out -> pdf.writeTo(out) }
            }
        } finally {
            pdf.close()
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return ExportResult(file = file, uri = uri, pageCount = document.pages.size)
    }
}
