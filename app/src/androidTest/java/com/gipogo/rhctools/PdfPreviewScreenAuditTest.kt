package com.gipogo.rhctools

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.gipogo.rhctools.ui.screens.PdfPreviewScreen
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File

class PdfPreviewScreenAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var createdPdfFile: File? = null

    @After
    fun cleanup() {
        createdPdfFile?.delete()
    }

    @Test
    fun previewLoadsAndNavigatesTwoPagePdf() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pdfFile = createTwoPagePdf(context)
        createdPdfFile = pdfFile

        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        composeRule.setContent {
            GipogoRhctoolsTheme {
                PdfPreviewScreen(
                    pdfUri = pdfUri,
                    pdfFileForShare = pdfFile,
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag("pdf_preview_screen").assertIsDisplayed()
        composeRule.waitUntil(8_000) {
            runCatching {
                composeRule.onNodeWithTag("pdf_preview_page_indicator").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.waitUntil(8_000) {
            runCatching {
                composeRule.onNodeWithTag("pdf_preview_page_indicator").assertTextEquals("1/2")
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("pdf_preview_share_button").assertIsEnabled()
        composeRule.onNodeWithTag("pdf_preview_open_button").assertIsEnabled()
        composeRule.onNodeWithTag("pdf_preview_prev_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("pdf_preview_next_button").assertIsEnabled().performClick()

        composeRule.waitUntil(8_000) {
            runCatching {
                composeRule.onNodeWithTag("pdf_preview_page_indicator").assertTextEquals("2/2")
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("pdf_preview_prev_button").assertIsEnabled()
        composeRule.onNodeWithTag("pdf_preview_next_button").assertIsNotEnabled()
    }

    private fun createTwoPagePdf(context: android.content.Context): File {
        val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val file = File(outDir, "UI_AUDIT_PREVIEW.pdf")
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f }

        try {
            for (pageNumber in 1..2) {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                )
                page.canvas.drawText("PDF audit page $pageNumber", 72f, 120f, paint)
                document.finishPage(page)
            }

            file.outputStream().use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }

        assertNotNull(file.takeIf { it.exists() && it.length() > 0L })
        return file
    }
}
