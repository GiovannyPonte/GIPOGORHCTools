package com.gipogo.rhctools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.ForresterPdfBlock
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.PdfLongitudinalReportGenerator
import com.gipogo.rhctools.report.PdfReportGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfGenerationQualityAuditTest {

    private val createdFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        createdFiles.forEach { it.delete() }
        createdFiles.clear()
    }

    @Test
    fun standardPdf_keepsPagedContentRenderable_withLongClinicalContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = newTempPdf(context, "standard_quality.pdf")

        output.outputStream().use { stream ->
            PdfReportGenerator.writePdf(
                context = context,
                outputStream = stream,
                appName = context.getString(R.string.pdf_app_name),
                entries = buildLongClinicalEntries(),
                nowMillis = 1_712_345_678_000L,
                forrester = ForresterPdfBlock(ci = 2.4, pcwp = 22.0)
            )
        }

        assertTrue(output.exists())
        assertTrue(output.length() > 0L)

        openRenderer(output).useRenderer { renderer ->
            assertTrue(
                "Expected the standard report to span multiple pages for pagination coverage.",
                renderer.pageCount >= 2
            )

            for (pageIndex in 0 until renderer.pageCount) {
                val bitmap = renderPage(renderer, pageIndex)
                try {
                    assertTrue(
                        "Page ${pageIndex + 1} body looks empty after rendering.",
                        countInkPixels(bitmap, 0.10f, 0.22f, 0.90f, 0.92f) > 1_000
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun longitudinalPdf_usesExpectedVectorPageCount_andStaysCompact() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = newTempPdf(context, "longitudinal_quality.pdf")
        val progress = mutableListOf<Pair<Int, Int>>()
        val studies = buildLongitudinalStudies(count = 3)
        val expectedPages = 6

        output.outputStream().use { stream ->
            PdfLongitudinalReportGenerator.writeLongitudinalPdf(
                context = context,
                outputStream = stream,
                appName = context.getString(R.string.pdf_app_name),
                patientDisplayName = "Paciente de Auditoria",
                createdAtMillis = 1_712_345_678_000L,
                studies = studies,
                onPageWritten = { current, total -> progress += current to total }
            )
        }

        assertTrue(output.exists())
        assertTrue(output.length() > 0L)
        assertTrue(
            "Vector longitudinal PDF should remain compact and avoid bitmap-sized output.",
            output.length() < 1_500_000L
        )
        assertEquals(expectedPages, progress.size)
        assertEquals((1..expectedPages).toList(), progress.map { it.first })
        assertTrue(progress.all { it.second == expectedPages })

        openRenderer(output).useRenderer { renderer ->
            assertEquals(expectedPages, renderer.pageCount)

            val firstPage = renderPage(renderer, 0)
            val lastPage = renderPage(renderer, renderer.pageCount - 1)
            try {
                assertTrue(
                    "Cover page should contain a bright card area in the body region.",
                    countBrightPixels(firstPage, 0.10f, 0.18f, 0.90f, 0.92f) > 25_000
                )
                assertTrue(
                    "Trend page should contain chart strokes and labels in the body region.",
                    countInkPixels(lastPage, 0.10f, 0.18f, 0.90f, 0.92f) > 5_000
                )
            } finally {
                firstPage.recycle()
                lastPage.recycle()
            }
        }
    }

    @Test
    fun longitudinalPdf_singleStudy_skipsCoverAndTrendPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = newTempPdf(context, "longitudinal_single_study.pdf")
        val progress = mutableListOf<Pair<Int, Int>>()

        output.outputStream().use { stream ->
            PdfLongitudinalReportGenerator.writeLongitudinalPdf(
                context = context,
                outputStream = stream,
                appName = context.getString(R.string.pdf_app_name),
                patientDisplayName = "Paciente Unico",
                createdAtMillis = 1_712_345_678_000L,
                studies = buildLongitudinalStudies(count = 1),
                onPageWritten = { current, total -> progress += current to total }
            )
        }

        assertTrue(output.exists())
        assertTrue(output.length() > 0L)
        assertEquals(listOf(1 to 1), progress)

        openRenderer(output).useRenderer { renderer ->
            assertEquals(1, renderer.pageCount)

            val page = renderPage(renderer, 0)
            try {
                assertTrue(
                    "Single study page should still show a white clinical card.",
                    countBrightPixels(page, 0.10f, 0.18f, 0.90f, 0.92f) > 25_000
                )
            } finally {
                page.recycle()
            }
        }
    }

    private fun newTempPdf(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "pdf_quality_audit").apply { mkdirs() }
        return File(dir, fileName).also(createdFiles::add)
    }

    private fun buildLongClinicalEntries(): List<CalcEntry> {
        val longToken = "SUPERCALIFRAGILISTICEXPANDIDOVALORCLINICOINUSUAL1234567890"
        return (1..7).map { index ->
            CalcEntry(
                type = CalcType.entries[index % CalcType.entries.size],
                timestampMillis = 1_712_345_678_000L + index,
                title = "Calculo hemodinamico $index con seguimiento longitudinal extendido",
                inputs = listOf(
                    LineItem(label = "Hemoglobina", value = "13.$index", unit = "g/dL"),
                    LineItem(label = "Saturacion arterial", value = "${92 + index}", unit = "%"),
                    LineItem(label = "Etiqueta interna muy larga", value = "$longToken-$index", unit = null)
                ),
                outputs = listOf(
                    LineItem(label = "Indice cardiaco", value = "2.${index}", unit = "L/min/m2"),
                    LineItem(label = "Resistencia pulmonar", value = "${3 + index}", unit = "WU"),
                    LineItem(label = "Observacion serial", value = "$longToken-$longToken-$index")
                ),
                notes = List(4) { noteIndex ->
                    "Nota clinica $noteIndex para el estudio $index con texto largo y token $longToken"
                }
            )
        }
    }

    private fun buildLongitudinalStudies(count: Int): List<PdfLongitudinalReportGenerator.StudyRow> {
        return (0 until count).map { index ->
            PdfLongitudinalReportGenerator.StudyRow(
                studyId = "study-${index + 1}",
                studyAtMillis = 1_712_345_678_000L + index * 86_400_000L,
                ciLMinM2 = 1.8 + (index * 0.3),
                coLMin = 3.7 + (index * 0.4),
                pvrWu = 4.6 - (index * 0.6),
                svrWu = 18.2 - index,
                rapMmHg = 14.0 - index,
                mpapMmHg = 33.0 - (index * 2),
                pcwpMmHg = 24.0 - (index * 2),
                cpoW = 0.7 + (index * 0.1)
            )
        }
    }

    private fun openRenderer(file: File): PdfRendererHandle {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        return PdfRendererHandle(descriptor, renderer)
    }

    private fun renderPage(renderer: PdfRenderer, pageIndex: Int, widthPx: Int = 1200): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val scale = widthPx.toFloat() / page.width.toFloat()
            val heightPx = (page.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    private fun countInkPixels(
        bitmap: Bitmap,
        leftFraction: Float,
        topFraction: Float,
        rightFraction: Float,
        bottomFraction: Float
    ): Int {
        return countMatchingPixels(bitmap, leftFraction, topFraction, rightFraction, bottomFraction) { color ->
            Color.alpha(color) > 0 && !isAlmostWhite(color)
        }
    }

    private fun countBrightPixels(
        bitmap: Bitmap,
        leftFraction: Float,
        topFraction: Float,
        rightFraction: Float,
        bottomFraction: Float
    ): Int {
        return countMatchingPixels(bitmap, leftFraction, topFraction, rightFraction, bottomFraction) { color ->
            Color.alpha(color) > 0 && isBright(color)
        }
    }

    private fun countMatchingPixels(
        bitmap: Bitmap,
        leftFraction: Float,
        topFraction: Float,
        rightFraction: Float,
        bottomFraction: Float,
        predicate: (Int) -> Boolean
    ): Int {
        val startX = (bitmap.width * leftFraction).toInt().coerceIn(0, bitmap.width - 1)
        val endX = (bitmap.width * rightFraction).toInt().coerceIn(startX + 1, bitmap.width)
        val startY = (bitmap.height * topFraction).toInt().coerceIn(0, bitmap.height - 1)
        val endY = (bitmap.height * bottomFraction).toInt().coerceIn(startY + 1, bitmap.height)

        var matches = 0
        for (y in startY until endY step 2) {
            for (x in startX until endX step 2) {
                if (predicate(bitmap.getPixel(x, y))) {
                    matches++
                }
            }
        }
        return matches
    }

    private fun isAlmostWhite(color: Int): Boolean {
        return Color.red(color) >= 245 && Color.green(color) >= 245 && Color.blue(color) >= 245
    }

    private fun isBright(color: Int): Boolean {
        return Color.red(color) >= 235 && Color.green(color) >= 235 && Color.blue(color) >= 235
    }

    private class PdfRendererHandle(
        private val descriptor: ParcelFileDescriptor,
        val renderer: PdfRenderer
    ) {
        fun <T> useRenderer(block: (PdfRenderer) -> T): T {
            try {
                return block(renderer)
            } finally {
                renderer.close()
                descriptor.close()
            }
        }
    }
}
