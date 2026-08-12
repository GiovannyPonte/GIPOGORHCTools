package com.gipogo.rhctools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.report.StudyClinicalComparisonRow
import com.gipogo.rhctools.report.StudyClinicalForrester
import com.gipogo.rhctools.report.StudyClinicalChartPoint
import com.gipogo.rhctools.report.StudyClinicalKvRow
import com.gipogo.rhctools.report.StudyClinicalMetricChip
import com.gipogo.rhctools.report.StudyClinicalMetricRow
import com.gipogo.rhctools.report.StudyClinicalMetricTone
import com.gipogo.rhctools.report.StudyClinicalPdfDocument
import com.gipogo.rhctools.report.StudyClinicalPdfFormat
import com.gipogo.rhctools.report.StudyClinicalPdfGenerator
import com.gipogo.rhctools.report.StudyClinicalQuickReadRow
import com.gipogo.rhctools.report.StudyClinicalTrendStudy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StudyClinicalPdfExportAuditTest {

    private val createdFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        createdFiles.forEach(File::delete)
        createdFiles.clear()
    }

    @Test
    fun compactStudyPdf_isSinglePageAndRenderable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = newTempPdf(context, "study_compact_audit.pdf")

        file.outputStream().use { output ->
            StudyClinicalPdfGenerator.writePdf(
                context = context,
                outputStream = output,
                document = sampleDocument(context),
                format = StudyClinicalPdfFormat.COMPACT
            )
        }

        openRenderer(file).useRenderer { renderer ->
            assertEquals(1, renderer.pageCount)
            val bitmap = renderPage(renderer, 0)
            try {
                assertTrue(countInkPixels(bitmap, 0.10f, 0.12f, 0.90f, 0.92f) > 2_500)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun compactSingleStudyPdf_isSinglePageAndRenderable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = newTempPdf(context, "study_single_compact_audit.pdf")
        val document = sampleDocument(context).let { it.copy(trendStudies = it.trendStudies.take(1)) }
        file.outputStream().use { output ->
            StudyClinicalPdfGenerator.writePdf(context, output, document, StudyClinicalPdfFormat.COMPACT)
        }
        openRenderer(file).useRenderer { renderer ->
            assertEquals(1, renderer.pageCount)
            val bitmap = renderPage(renderer, 0)
            try {
                assertTrue(countInkPixels(bitmap, 0.10f, 0.12f, 0.90f, 0.92f) > 2_500)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun completeStudyPdf_spansMultiplePagesAndRendersContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = newTempPdf(context, "study_complete_audit.pdf")

        file.outputStream().use { output ->
            StudyClinicalPdfGenerator.writePdf(
                context = context,
                outputStream = output,
                document = sampleDocument(context, longNote = true),
                format = StudyClinicalPdfFormat.COMPLETE
            )
        }

        openRenderer(file).useRenderer { renderer ->
            assertTrue(renderer.pageCount >= 2)

            val first = renderPage(renderer, 0)
            val last = renderPage(renderer, renderer.pageCount - 1)
            try {
                assertTrue(countInkPixels(first, 0.10f, 0.12f, 0.90f, 0.92f) > 2_500)
                assertTrue(countInkPixels(last, 0.10f, 0.12f, 0.90f, 0.92f) > 2_000)
            } finally {
                first.recycle()
                last.recycle()
            }
        }
    }

    @Test
    fun writeProfessionalCompletePdf_visualAuditFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "pdf_audit_output").apply { mkdirs() }
        val file = File(dir, "professional_clinical_report.pdf")
        file.outputStream().use { output ->
            StudyClinicalPdfGenerator.writePdf(
                context = context,
                outputStream = output,
                document = sampleDocument(context, longNote = true),
                format = StudyClinicalPdfFormat.COMPLETE
            )
        }
        assertTrue(file.length() > 10_000)
    }

    @Test
    fun writeProfessionalCompactPdf_visualAuditFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "pdf_audit_output").apply { mkdirs() }
        val file = File(dir, "professional_compact_report.pdf")
        file.outputStream().use { output ->
            StudyClinicalPdfGenerator.writePdf(
                context, output, sampleDocument(context), StudyClinicalPdfFormat.COMPACT
            )
        }
        openRenderer(file).useRenderer { assertEquals(1, it.pageCount) }
    }

    private fun sampleDocument(context: Context, longNote: Boolean = false): StudyClinicalPdfDocument {
        val note = if (longNote) {
            List(18) {
                "Nota clínica de auditoría con contexto hemodinámico, correlación clínica y comentario de trazabilidad $it."
            }.joinToString(" ")
        } else {
            "Nota clínica breve para validación visual del formato."
        }

        return StudyClinicalPdfDocument(
            appName = context.getString(R.string.pdf_app_name),
            patientDisplayName = "Paciente de auditoría",
            studyId = "study-audit-01",
            studyType = "RHC",
            studyAtMillis = 1_712_345_678_000L,
            reportAtMillis = 1_712_345_999_000L,
            executiveSummary = "Llenado derecho: Elevado · Llenado izquierdo: Elevado · Flujo: Bajo · Componente vascular pulmonar: Elevado.",
            quickReadRows = listOf(
                StudyClinicalQuickReadRow("Llenado derecho", "Elevado"),
                StudyClinicalQuickReadRow("Llenado izquierdo", "Elevado"),
                StudyClinicalQuickReadRow("Flujo", "Bajo"),
                StudyClinicalQuickReadRow("Componente vascular pulmonar", "Elevado"),
                StudyClinicalQuickReadRow("Patrón hemodinámico", "Compatible con patrón pos y precapilar combinado")
            ),
            keyMetrics = listOf(
                StudyClinicalMetricChip("RAP", "14", "mmHg", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("mPAP", "38", "mmHg", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("PCWP", "24", "mmHg", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("CO", "3.4", "L/min", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("CI", "1.8", "L/min/m2", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("PVR", "4.1", "WU", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("SvO2", "54", "%", StudyClinicalMetricTone.ALERT),
                StudyClinicalMetricChip("CPO", "0.62", "W", StudyClinicalMetricTone.ALERT)
            ),
            traceabilityRows = listOf(
                StudyClinicalKvRow("Paciente", "Paciente de auditoría"),
                StudyClinicalKvRow("ID de estudio", "study-audit-01"),
                StudyClinicalKvRow("Tipo de estudio", "RHC"),
                StudyClinicalKvRow("Fecha del estudio", "2026-04-10 09:15"),
                StudyClinicalKvRow("Fecha del reporte", "2026-04-10 09:30"),
                StudyClinicalKvRow("Método de CO", "Termodilución"),
                StudyClinicalKvRow("Estudio previo", "2026-03-29 08:20"),
                StudyClinicalKvRow("Nota clínica", "Documentada")
            ),
            pressureRows = listOf(
                StudyClinicalMetricRow("RAP", "14", "mmHg"),
                StudyClinicalMetricRow("mPAP", "38", "mmHg"),
                StudyClinicalMetricRow("PCWP", "24", "mmHg"),
                StudyClinicalMetricRow("PASP", "58", "mmHg"),
                StudyClinicalMetricRow("PADP", "28", "mmHg"),
                StudyClinicalMetricRow("MAP", "72", "mmHg")
            ),
            flowRows = listOf(
                StudyClinicalMetricRow("CO", "3.4", "L/min"),
                StudyClinicalMetricRow("CI", "1.8", "L/min/m2"),
                StudyClinicalMetricRow("SaO2", "95", "%"),
                StudyClinicalMetricRow("SvO2", "54", "%"),
                StudyClinicalMetricRow("Hb", "12.8", "g/dL"),
                StudyClinicalMetricRow("VO2", "210", "mL/min")
            ),
            resistanceRows = listOf(
                StudyClinicalMetricRow("PVR", "4.1", "WU"),
                StudyClinicalMetricRow("SVR", "20.0", "WU"),
                StudyClinicalMetricRow("CPO", "0.62", "W"),
                StudyClinicalMetricRow("PAPi", "2.1", null),
                StudyClinicalMetricRow("TPG", "14", "mmHg"),
                StudyClinicalMetricRow("DPG", "4", "mmHg")
            ),
            interpretationBullets = listOf(
                "Llenado derecho: elevado.",
                "Llenado izquierdo: elevado.",
                "Flujo: bajo.",
                "Componente vascular pulmonar: elevado.",
                "Perfil de Forrester sugerido: Perfil IV."
            ),
            comparisonRows = listOf(
                StudyClinicalComparisonRow("RAP", "10", "14", "↑ +4 mmHg"),
                StudyClinicalComparisonRow("mPAP", "32", "38", "↑ +6 mmHg"),
                StudyClinicalComparisonRow("PCWP", "19", "24", "↑ +5 mmHg"),
                StudyClinicalComparisonRow("CI", "2.1", "1.8", "↓ -0.3 L/min/m2")
            ),
            studyNote = note,
            limitations = listOf("Datos faltantes relevantes: acceso vascular, sedación y calidad formal de wedge."),
            pressureChart = listOf(
                StudyClinicalChartPoint("RAP", "mmHg", 14.0, 10.0, 25.0, 8.0),
                StudyClinicalChartPoint("mPAP", "mmHg", 38.0, 32.0, 60.0, 20.0),
                StudyClinicalChartPoint("PCWP", "mmHg", 24.0, 19.0, 35.0, 15.0)
            ),
            performanceChart = listOf(
                StudyClinicalChartPoint("CI", "L/min/m2", 1.8, 2.1, 5.0, 2.2),
                StudyClinicalChartPoint("PVR", "WU", 4.1, 3.2, 8.0, 2.0),
                StudyClinicalChartPoint("CPO", "W", 0.62, 0.78, 2.0, 0.8)
            ),
            patientInternalCode = "AUD-001",
            patientSex = "Masculino",
            patientBirthDateAndAge = "1964-04-10 (62 años)",
            patientWeightKg = 78.0,
            patientHeightCm = 172.0,
            patientNote = "Antecedente breve relevante para contextualizar el estudio, sin pretender sustituir la historia clínica.",
            forrester = StudyClinicalForrester(
                currentCi = 1.8,
                currentPcwp = 24.0,
                previousCi = 2.1,
                previousPcwp = 19.0
            ),
            trendStudies = List(30) { index ->
                StudyClinicalTrendStudy(
                    dateLabel = "%02d/04/26".format(index + 1),
                    studyId = "audit-${index + 1}",
                    method = "Termodilucion",
                    rap = (16.0 - index * 0.3).coerceAtLeast(5.0),
                    pasp = (60.0 - index * 0.7).coerceAtLeast(30.0),
                    padp = (30.0 - index * 0.4).coerceAtLeast(12.0),
                    mpap = (40.0 - index * 0.5).coerceAtLeast(18.0),
                    pcwp = (27.0 - index * 0.4).coerceAtLeast(8.0),
                    map = 72.0 + index,
                    co = 3.2 + index * 0.2,
                    ci = 1.7 + index * 0.1,
                    sao2 = 95.0,
                    svo2 = 53.0 + index,
                    hemoglobin = 12.8,
                    pvr = (4.2 - index * 0.08).coerceAtLeast(1.2),
                    svr = (20.0 - index * 0.2).coerceAtLeast(12.0),
                    cpo = 0.6 + index * 0.04,
                    papi = 1.9 + index * 0.1,
                    tpg = 13.0 - index * 0.3,
                    dpg = 3.0 - index * 0.1,
                    note = "Seguimiento ${index + 1}"
                )
            }
        )
    }

    private fun newTempPdf(context: Context, name: String): File {
        val dir = File(context.cacheDir, "pdf_study_audit").apply { mkdirs() }
        return File(dir, name).also(createdFiles::add)
    }

    private fun openRenderer(file: File): RendererHandle {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return RendererHandle(pfd, PdfRenderer(pfd))
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
        val startX = (bitmap.width * leftFraction).toInt().coerceIn(0, bitmap.width - 1)
        val endX = (bitmap.width * rightFraction).toInt().coerceIn(startX + 1, bitmap.width)
        val startY = (bitmap.height * topFraction).toInt().coerceIn(0, bitmap.height - 1)
        val endY = (bitmap.height * bottomFraction).toInt().coerceIn(startY + 1, bitmap.height)

        var count = 0
        for (y in startY until endY step 2) {
            for (x in startX until endX step 2) {
                val color = bitmap.getPixel(x, y)
                val isInk = Color.alpha(color) > 0 &&
                    !(Color.red(color) >= 245 && Color.green(color) >= 245 && Color.blue(color) >= 245)
                if (isInk) count++
            }
        }
        return count
    }

    private class RendererHandle(
        private val descriptor: ParcelFileDescriptor,
        private val renderer: PdfRenderer
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
