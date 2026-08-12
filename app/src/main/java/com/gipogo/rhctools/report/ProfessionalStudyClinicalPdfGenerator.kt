package com.gipogo.rhctools.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.gipogo.rhctools.R
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

internal object StudyClinicalPdfGenerator {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f
    private const val TOP = 84f
    private const val BOTTOM = 54f

    fun writePdf(
        context: Context,
        outputStream: OutputStream,
        document: StudyClinicalPdfDocument,
        format: StudyClinicalPdfFormat
    ) {
        val pdf = PdfDocument()
        try {
            Renderer(context, pdf, document, format).render()
            pdf.writeTo(outputStream)
        } finally {
            pdf.close()
        }
    }

    private class Renderer(
        private val context: Context,
        private val pdf: PdfDocument,
        private val model: StudyClinicalPdfDocument,
        private val format: StudyClinicalPdfFormat
    ) {
        private val navy = Color.rgb(24, 50, 68)
        private val blue = Color.rgb(33, 105, 132)
        private val grey = Color.rgb(93, 105, 113)
        private val line = Color.rgb(205, 213, 218)
        private val light = Color.rgb(245, 247, 248)
        private val alert = Color.rgb(166, 56, 43)
        private val previous = Color.rgb(159, 169, 176)
        private val title = paint(19f, navy, true)
        private val section = paint(12.5f, navy, true)
        private val body = paint(9.5f, Color.rgb(28, 36, 41))
        private val bodyBold = paint(9.5f, Color.rgb(28, 36, 41), true)
        private val small = paint(8f, grey)
        private val tiny = paint(7f, grey)
        private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line; strokeWidth = 0.8f }
        private val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        private val canvas get() = page.canvas
        private var y = TOP

        fun render() {
            startPage()
            if (format == StudyClinicalPdfFormat.COMPLETE) {
                identity()
                summary()
                findings()
                quickRead()
                newPage()
                sectionTitle("Datos hemodinamicos")
                metricTable("Presiones", model.pressureRows)
                metricTable("Flujo y oxigenacion", model.flowRows)
                metricTable("Resistencias y rendimiento", model.resistanceRows)
                traceability()
                newPage()
                charts()
                newPage()
                forresterChart(compact = false)
                interpretation()
                comparison()
                notesAndLimitations()
                signature()
            } else {
                compactReport()
            }
            finishPage()
        }

        private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun startPage() {
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 5f, Paint().apply { color = navy })
            canvas.drawText(if (format == StudyClinicalPdfFormat.COMPLETE) "Informe hemodinamico - cateterismo derecho" else "Resumen hemodinamico", MARGIN, 34f, title)
            canvas.drawText("${model.patientDisplayName} | ${date.format(Date(model.studyAtMillis))}", MARGIN, 53f, small)
            canvas.drawLine(MARGIN, 68f, PAGE_W - MARGIN, 68f, rule)
            y = TOP
        }

        private fun finishPage() {
            canvas.drawLine(MARGIN, PAGE_H - 39f, PAGE_W - MARGIN, PAGE_H - 39f, rule)
            canvas.drawText(context.getString(R.string.study_pdf_footer_disclaimer), MARGIN, PAGE_H - 25f, tiny)
            val number = "Pagina $pageNo"
            canvas.drawText(number, PAGE_W - MARGIN - tiny.measureText(number), PAGE_H - 25f, tiny)
            pdf.finishPage(page)
        }

        private fun newPage() { finishPage(); startPage() }
        private fun room(height: Float) { if (y + height > PAGE_H - BOTTOM) newPage() }

        private fun sectionTitle(text: String) {
            room(26f)
            canvas.drawText(text, MARGIN, y, section)
            y += 8f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint(rule).apply { color = blue; strokeWidth = 1.2f })
            y += 15f
        }

        private fun identity() {
            sectionTitle("Identificacion")
            val rows = listOf(
                "Paciente" to model.patientDisplayName,
                "Codigo interno" to model.patientInternalCode.orEmpty().ifBlank { "No registrado" },
                "Sexo" to model.patientSex.orEmpty().ifBlank { "No registrado" },
                "Nacimiento / edad" to model.patientBirthDateAndAge.orEmpty().ifBlank { "No registrado" },
                "Peso / talla" to physicalSummary(),
                "ID del estudio" to model.studyId,
                "Tipo" to model.studyType,
                "Fecha del informe" to date.format(Date(model.reportAtMillis))
            )
            twoColumnRows(rows)
        }

        private fun compactReport() {
            val identityRows = listOf(
                "Paciente" to model.patientDisplayName,
                "Codigo" to model.patientInternalCode.orEmpty().ifBlank { "No registrado" },
                "Sexo / edad" to listOfNotNull(model.patientSex, model.patientBirthDateAndAge).joinToString(" | ").ifBlank { "No registrado" },
                "Peso / talla" to physicalSummary(),
                "Fecha del estudio" to date.format(Date(model.studyAtMillis)),
                "ID" to model.studyId
            )
            sectionTitle("Identificacion del paciente y estudio")
            twoColumnRows(identityRows)
            findings()

            val chartTop = y
            val chartWidth = 245f
            forresterChartAt(MARGIN, chartTop, chartWidth, 218f, compact = true)
            val textLeft = MARGIN + chartWidth + 18f
            canvas.drawText("Lectura clinica", textLeft, chartTop, section)
            canvas.drawLine(textLeft, chartTop + 8f, PAGE_W - MARGIN, chartTop + 8f, Paint(rule).apply { color = blue; strokeWidth = 1.1f })
            var yy = chartTop + 27f
            model.quickReadRows.take(5).forEach { row ->
                canvas.drawText(row.label, textLeft, yy, small)
                yy += 12f
                val value = ellipsize(row.value, bodyBold, PAGE_W - MARGIN - textLeft)
                canvas.drawText(value, textLeft, yy, bodyBold)
                yy += 20f
            }
            canvas.drawText("Nota del cateterismo", textLeft, yy, section)
            yy += 15f
            val note = model.studyNote ?: "Sin nota adicional registrada."
            wrap(note, body, PAGE_W - MARGIN - textLeft).take(5).forEach {
                canvas.drawText(it, textLeft, yy, body); yy += 13f
            }
            y = max(chartTop + 228f, yy + 5f)
            signature(compact = true)
        }

        private fun physicalSummary(): String = listOfNotNull(
            model.patientWeightKg?.let { "${number(it)} kg" },
            model.patientHeightCm?.let { "${number(it)} cm" }
        ).joinToString(" | ").ifBlank { "No registrado" }

        private fun summary() {
            sectionTitle("Resumen ejecutivo")
            paragraph(model.executiveSummary, bodyBold, inset = 0f)
            y += 5f
        }

        private fun findings() {
            sectionTitle("Hallazgos principales")
            val cols = 4
            val gap = 8f
            val width = (PAGE_W - 2 * MARGIN - gap * (cols - 1)) / cols
            val rows = ceil(model.keyMetrics.size / cols.toDouble()).toInt()
            room(rows * 55f)
            model.keyMetrics.forEachIndexed { index, item ->
                val col = index % cols
                val row = index / cols
                val left = MARGIN + col * (width + gap)
                val top = y + row * 55f
                canvas.drawRect(RectF(left, top, left + width, top + 47f), Paint().apply { color = light })
                canvas.drawLine(left, top + 47f, left + width, top + 47f, rule)
                canvas.drawText(item.label, left + 7f, top + 12f, small)
                val valuePaint = if (item.tone == StudyClinicalMetricTone.ALERT) Paint(bodyBold).apply { color = alert } else bodyBold
                canvas.drawText(item.value, left + 7f, top + 29f, valuePaint)
                item.unit?.let { canvas.drawText(it, left + 7f, top + 41f, tiny) }
            }
            y += rows * 55f + 12f
        }

        private fun quickRead() {
            sectionTitle("Lectura clinica estructurada")
            twoColumnRows(model.quickReadRows.map { it.label to it.value })
        }

        private fun twoColumnRows(rows: List<Pair<String, String>>) {
            rows.forEachIndexed { index, (label, value) ->
                room(18f)
                if (index % 2 == 0) canvas.drawRect(MARGIN, y - 11f, PAGE_W - MARGIN, y + 5f, Paint().apply { color = light })
                canvas.drawText(label, MARGIN + 5f, y, body)
                val fitted = ellipsize(value, bodyBold, 250f)
                canvas.drawText(fitted, PAGE_W - MARGIN - 5f - bodyBold.measureText(fitted), y, bodyBold)
                y += 18f
            }
            y += 4f
        }

        private fun metricTable(heading: String, rows: List<StudyClinicalMetricRow>) {
            room(30f)
            canvas.drawText(heading, MARGIN, y, bodyBold)
            y += 13f
            rows.forEachIndexed { index, row ->
                room(17f)
                if (index % 2 == 0) canvas.drawRect(MARGIN, y - 10f, PAGE_W - MARGIN, y + 5f, Paint().apply { color = light })
                canvas.drawText(row.label, MARGIN + 5f, y, body)
                val value = listOfNotNull(row.value, row.unit).joinToString(" ")
                canvas.drawText(value, PAGE_W - MARGIN - 5f - bodyBold.measureText(value), y, bodyBold)
                y += 17f
            }
            y += 10f
        }

        private fun traceability() {
            sectionTitle("Trazabilidad y condiciones")
            twoColumnRows(model.traceabilityRows.map { it.label to it.value })
        }

        private fun charts() {
            sectionTitle("Visualizacion hemodinamica")
            canvas.drawRect(MARGIN, y - 7f, MARGIN + 10f, y + 1f, Paint().apply { color = blue })
            canvas.drawText("Actual", MARGIN + 15f, y, small)
            canvas.drawRect(MARGIN + 67f, y - 7f, MARGIN + 77f, y + 1f, Paint().apply { color = previous })
            canvas.drawText("Previo", MARGIN + 82f, y, small)
            canvas.drawLine(MARGIN + 132f, y - 3f, MARGIN + 147f, y - 3f, Paint(rule).apply { color = alert; strokeWidth = 1.3f })
            canvas.drawText("Referencia", MARGIN + 152f, y, small)
            y += 20f
            chartGroup("Presiones (cada panel conserva su escala)", model.pressureChart)
            chartGroup("Rendimiento (unidades independientes)", model.performanceChart)
            paragraph("Las referencias son puntos de orientacion, no sustituyen la interpretacion clinica. Las escalas no deben compararse entre paneles.", small)
        }

        private fun forresterChart(compact: Boolean) {
            val data = model.forrester ?: return
            if (data.currentCi == null || data.currentPcwp == null) return
            sectionTitle("Clasificacion de Forrester")
            forresterChartAt(MARGIN, y, PAGE_W - 2 * MARGIN, if (compact) 210f else 245f, compact)
            y += if (compact) 220f else 255f
        }

        private fun forresterChartAt(left: Float, top: Float, width: Float, height: Float, compact: Boolean) {
            val data = model.forrester
            if (data?.currentCi == null || data.currentPcwp == null) {
                canvas.drawText("Forrester no disponible: faltan CI o PCWP.", left, top + 20f, body)
                return
            }
            val plotLeft = left + 34f
            val plotTop = top + 20f
            val plotRight = left + width - 12f
            val plotBottom = top + height - 34f
            val xMax = 40.0
            val yMax = 5.0
            fun px(pcwp: Double) = plotLeft + (pcwp.coerceIn(0.0, xMax) / xMax * (plotRight - plotLeft)).toFloat()
            fun py(ci: Double) = plotBottom - (ci.coerceIn(0.0, yMax) / yMax * (plotBottom - plotTop)).toFloat()
            val xThreshold = px(18.0)
            val yThreshold = py(2.2)
            val quadrant = arrayOf(
                RectF(plotLeft, plotTop, xThreshold, yThreshold) to Color.rgb(239, 246, 241),
                RectF(xThreshold, plotTop, plotRight, yThreshold) to Color.rgb(249, 242, 226),
                RectF(plotLeft, yThreshold, xThreshold, plotBottom) to Color.rgb(239, 243, 248),
                RectF(xThreshold, yThreshold, plotRight, plotBottom) to Color.rgb(249, 235, 235)
            )
            quadrant.forEach { (rect, color) -> canvas.drawRect(rect, Paint().apply { this.color = color }) }
            canvas.drawRect(RectF(plotLeft, plotTop, plotRight, plotBottom), Paint(rule).apply { style = Paint.Style.STROKE })
            canvas.drawLine(xThreshold, plotTop, xThreshold, plotBottom, Paint(rule).apply { color = grey; strokeWidth = 1.2f })
            canvas.drawLine(plotLeft, yThreshold, plotRight, yThreshold, Paint(rule).apply { color = grey; strokeWidth = 1.2f })
            canvas.drawText("I: tibio-seco", plotLeft + 5f, plotTop + 13f, tiny)
            canvas.drawText("II: tibio-humedo", xThreshold + 5f, plotTop + 13f, tiny)
            canvas.drawText("III: frio-seco", plotLeft + 5f, yThreshold + 14f, tiny)
            canvas.drawText("IV: frio-humedo", xThreshold + 5f, yThreshold + 14f, tiny)
            val cx = px(data.currentPcwp); val cy = py(data.currentCi)
            if (data.previousPcwp != null && data.previousCi != null) {
                val ox = px(data.previousPcwp); val oy = py(data.previousCi)
                canvas.drawLine(ox, oy, cx, cy, Paint(rule).apply { color = previous; strokeWidth = 1.8f })
                canvas.drawCircle(ox, oy, 3.5f, Paint().apply { color = previous })
            }
            canvas.drawCircle(cx, cy, if (compact) 4.5f else 5.5f, Paint().apply { color = blue })
            canvas.drawCircle(cx, cy, if (compact) 2f else 2.5f, Paint().apply { color = Color.WHITE })
            canvas.drawText("Actual", cx + 7f, cy - 4f, small)
            canvas.drawText("PCWP (mmHg)", plotRight - small.measureText("PCWP (mmHg)"), plotBottom + 17f, small)
            canvas.drawText("CI (L/min/m2)", plotLeft, plotTop - 7f, small)
            canvas.drawText("18", xThreshold - tiny.measureText("18") / 2f, plotBottom + 10f, tiny)
            canvas.drawText("2.2", plotLeft - 18f, yThreshold + 3f, tiny)
        }

        private fun chartGroup(heading: String, points: List<StudyClinicalChartPoint>) {
            val visible = points.filter { it.current != null || it.previous != null }
            if (visible.isEmpty()) return
            room(185f)
            canvas.drawText(heading, MARGIN, y, bodyBold)
            y += 15f
            val gap = 12f
            val width = (PAGE_W - 2 * MARGIN - gap * (visible.size - 1)) / visible.size
            val height = 125f
            visible.forEachIndexed { i, p ->
                val left = MARGIN + i * (width + gap)
                drawChart(left, y, width, height, p)
            }
            y += height + 24f
        }

        private fun drawChart(left: Float, top: Float, width: Float, height: Float, p: StudyClinicalChartPoint) {
            canvas.drawRect(RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })
            canvas.drawRect(RectF(left, top, left + width, top + height), Paint(rule).apply { style = Paint.Style.STROKE })
            val x0 = left + 30f; val x1 = left + width - 12f; val y0 = top + 18f; val y1 = top + height - 29f
            canvas.drawLine(x0, y0, x0, y1, rule); canvas.drawLine(x0, y1, x1, y1, rule)
            p.reference?.let {
                val ry = y1 - ((it / p.axisMax).coerceIn(0.0, 1.0) * (y1 - y0)).toFloat()
                canvas.drawLine(x0, ry, x1, ry, Paint(rule).apply { color = alert; strokeWidth = 1.3f })
                canvas.drawText(number(it), left + 3f, ry + 3f, tiny)
            }
            canvas.drawText(number(p.axisMax), left + 3f, y0 + 3f, tiny)
            val values = listOf(p.previous to previous, p.current to blue)
            val barW = 22f; val gap = 9f
            var x = x0 + (x1 - x0 - (barW * 2 + gap)) / 2f
            values.forEach { (value, color) ->
                value?.let {
                    val by = y1 - ((it / p.axisMax).coerceIn(0.0, 1.0) * (y1 - y0)).toFloat()
                    canvas.drawRect(x, by, x + barW, y1, Paint().apply { this.color = color })
                    val label = number(it)
                    canvas.drawText(label, x + (barW - tiny.measureText(label)) / 2f, by - 3f, tiny)
                }
                x += barW + gap
            }
            val caption = "${p.label} (${p.unit})"
            canvas.drawText(caption, left + (width - small.measureText(caption)) / 2f, top + height - 9f, small)
        }

        private fun interpretation() {
            sectionTitle("Interpretacion clinica")
            bullets(model.interpretationBullets)
        }

        private fun comparison() {
            if (model.comparisonRows.isEmpty()) return
            sectionTitle("Comparacion con estudio previo")
            val xPrev = 360f; val xNow = 430f; val xDelta = PAGE_W - MARGIN
            canvas.drawText("Previo", xPrev, y, small); canvas.drawText("Actual", xNow, y, small)
            canvas.drawText("Cambio", xDelta - small.measureText("Cambio"), y, small); y += 16f
            model.comparisonRows.forEachIndexed { i, row ->
                room(17f)
                if (i % 2 == 0) canvas.drawRect(MARGIN, y - 10f, PAGE_W - MARGIN, y + 5f, Paint().apply { color = light })
                canvas.drawText(row.label, MARGIN + 5f, y, body)
                canvas.drawText(row.previousValue, xPrev, y, body)
                canvas.drawText(row.currentValue, xNow, y, bodyBold)
                canvas.drawText(row.deltaValue, xDelta - body.measureText(row.deltaValue), y, body)
                y += 17f
            }
            y += 5f
        }

        private fun notesAndLimitations(compact: Boolean = false) {
            model.patientNote?.let {
                sectionTitle("Antecedente o nota del paciente")
                paragraph(it, body, continuationHeading = "Nota del paciente (continuacion)")
            }
            model.studyNote?.let {
                sectionTitle("Nota del cateterismo derecho")
                paragraph(
                    if (compact && it.length > 300) it.take(297) + "..." else it,
                    body,
                    continuationHeading = "Nota del cateterismo derecho (continuacion)"
                )
            }
            sectionTitle("Limitaciones y control de calidad")
            bullets(model.limitations.ifEmpty { listOf(context.getString(R.string.study_pdf_no_major_limitations)) })
        }

        private fun signature(compact: Boolean = false) {
            val needed = if (compact) 74f else 88f
            room(needed)
            y += if (compact) 12f else 25f
            val lineLeft = MARGIN + 90f
            val lineRight = PAGE_W - MARGIN - 90f
            canvas.drawLine(lineLeft, y + 30f, lineRight, y + 30f, rule)
            val label = "Nombre y firma del medico"
            canvas.drawText(label, (PAGE_W - small.measureText(label)) / 2f, y + 45f, small)
            y += needed
        }

        private fun bullets(items: List<String>) {
            items.forEach { text ->
                val lines = wrap(text, body, PAGE_W - 2 * MARGIN - 14f)
                lines.forEachIndexed { index, lineText ->
                    room(14f)
                    canvas.drawText(if (index == 0) "-" else "", MARGIN, y, body)
                    canvas.drawText(lineText, MARGIN + 13f, y, body)
                    y += 14f
                }
                y += 3f
            }
        }

        private fun paragraph(
            text: String,
            p: Paint,
            inset: Float = 0f,
            continuationHeading: String? = null
        ) {
            wrap(text, p, PAGE_W - 2 * MARGIN - inset).forEach { lineText ->
                val lineHeight = max(13f, p.textSize + 3f)
                if (y + lineHeight > PAGE_H - BOTTOM) {
                    newPage()
                    continuationHeading?.let(::sectionTitle)
                }
                canvas.drawText(lineText, MARGIN + inset, y, p)
                y += lineHeight
            }
        }

        private fun wrap(text: String, p: Paint, width: Float): List<String> = buildList {
            text.replace('\n', ' ').trim().split(Regex("\\s+")).forEach { word ->
                val current = lastOrNull()
                when {
                    current == null -> add(word)
                    p.measureText("$current $word") <= width -> this[lastIndex] = "$current $word"
                    else -> add(word)
                }
            }
        }.ifEmpty { listOf("") }

        private fun ellipsize(text: String, p: Paint, width: Float): String {
            if (p.measureText(text) <= width) return text
            var value = text
            while (value.isNotEmpty() && p.measureText("$value...") > width) value = value.dropLast(1)
            return "$value..."
        }

        private fun number(value: Double): String = when {
            value in 0.0..0.99 && value % 0.1 != 0.0 -> "%.2f".format(Locale.US, value)
            value >= 10 || value % 1.0 == 0.0 -> "%.0f".format(Locale.US, value)
            else -> "%.1f".format(Locale.US, value)
        }
    }
}
