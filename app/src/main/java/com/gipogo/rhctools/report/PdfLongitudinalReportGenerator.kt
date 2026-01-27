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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PdfLongitudinalReportGenerator {

    data class StudyRow(
        val studyId: String,
        val studyAtMillis: Long,

        // Flow
        val ciLMinM2: Double?,
        val coLMin: Double?,

        // Resistances
        val pvrWu: Double?,
        val svrWu: Double?,

        // Pressures
        val rapMmHg: Double?,
        val mpapMmHg: Double?,
        val pcwpMmHg: Double?,

        // Flow/Performance
        val cpoW: Double?
    )

    /**
     * Longitudinal clinical PDF:
     * - Optional cover if >=2 studies
     * - 1 study per page (header + white card with sections)
     * - Comparison vs previous study
     * - Trends appendix at the end (0–3 pages, based on available data)
     */
    fun writeLongitudinalPdf(
        context: Context,
        outputStream: OutputStream,
        appName: String,
        patientDisplayName: String,
        createdAtMillis: Long,
        studies: List<StudyRow>
    ) {
        if (studies.isEmpty()) return

        val ordered = studies.sortedBy { it.studyAtMillis }

        // A4 portrait
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f

        // Theme (as requested)
        val bgColor = Color.parseColor("#121826")
        val headerBg = Color.parseColor("#0F172A")
        val headerText = Color.parseColor("#E5E7EB")
        val headerSub = Color.parseColor("#9CA3AF")
        val cardBg = Color.WHITE
        val cardBorder = Color.parseColor("#E5E7EB")
        val labelColor = Color.parseColor("#6B7280")
        val valueColor = Color.parseColor("#111827")

        // Paints
        val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL }
        val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = headerBg; style = Paint.Style.FILL }
        val pCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg; style = Paint.Style.FILL }
        val pCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f }
        val pDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBorder; strokeWidth = 1.2f }

        val pCorner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val pHeaderTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerText
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pHeaderSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerSub
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pHeaderMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerSub
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val pSection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = valueColor
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = valueColor
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Chart paints (print-safe, minimal)
        val pChartAxis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBorder; strokeWidth = 2f }
        val pLineDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = valueColor; strokeWidth = 3.5f }
        val pLineMid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#374151"); strokeWidth = 3.0f }
        val pLineLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); strokeWidth = 2.6f }
        val pChartText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val sdfCreated = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sdfStudy = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val createdAtText = sdfCreated.format(Date(createdAtMillis))

        val rangeStart = sdfStudy.format(Date(ordered.first().studyAtMillis))
        val rangeEnd = sdfStudy.format(Date(ordered.last().studyAtMillis))
        val rangeText = "$rangeStart → $rangeEnd"

        fun fmt(value: Double?, decimals: Int): String {
            if (value == null) return context.getString(R.string.common_value_na)
            val nf = java.text.NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = decimals
                minimumFractionDigits = 0
            }
            return nf.format(value)
        }

        fun delta(curr: Double?, prev: Double?, decimals: Int, unit: String?): String {
            if (curr == null || prev == null) return context.getString(R.string.common_value_na)
            val d = curr - prev
            val eps = 1e-9
            val arrow = when {
                abs(d) < eps -> "→"
                d > 0 -> "↑"
                else -> "↓"
            }
            val nf = java.text.NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = decimals
                minimumFractionDigits = 0
            }
            val sign = if (d > 0) "+" else ""
            val u = unit?.let { " $it" } ?: ""
            return "$arrow $sign${nf.format(d)}$u"
        }

        fun drawCornerPage(canvas: android.graphics.Canvas, pageNum: Int) {
            // Discreet: "Página X"
            canvas.drawText("Página $pageNum", margin, 22f, pCorner)
        }

        fun drawHeaderBlock(
            canvas: android.graphics.Canvas,
            pageNum: Int,
            totalPages: Int,
            studyAtText: String,
            extraLine: String
        ): Float {
            val headerH = 140f
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerH, pHeader)

            drawCornerPage(canvas, pageNum)

            val left = margin
            val top = 36f

            // Line 1
            canvas.drawText("$appName · $patientDisplayName", left, top + pHeaderTitle.textSize, pHeaderTitle)

            // Line 2 (subtitle)
            canvas.drawText(
                context.getString(R.string.pdf_header_subtitle),
                left,
                top + pHeaderTitle.textSize + 10f + pHeaderSub.textSize,
                pHeaderSub
            )

            // Line 3 (meta)
            val metaText = "${context.getString(R.string.pdf_header_datetime, createdAtText)} | $studyAtText"
            canvas.drawText(
                metaText,
                left,
                top + pHeaderTitle.textSize + 10f + pHeaderSub.textSize + 10f + pHeaderMeta.textSize,
                pHeaderMeta
            )

            // Line 4 (context)
            canvas.drawText(
                extraLine,
                left,
                top + pHeaderTitle.textSize + 10f + pHeaderSub.textSize + 10f + pHeaderMeta.textSize + 10f + pHeaderMeta.textSize,
                pHeaderMeta
            )

            return headerH
        }

        fun drawCard(canvas: android.graphics.Canvas, top: Float, bottom: Float): RectF {
            val rect = RectF(margin, top, (pageWidth - margin), bottom)
            canvas.drawRoundRect(rect, 22f, 22f, pCard)
            canvas.drawRoundRect(rect, 22f, 22f, pCardBorder)
            return rect
        }

        fun drawSectionTitle(canvas: android.graphics.Canvas, x: Float, y: Float, title: String): Float {
            canvas.drawText(title, x, y + pSection.textSize, pSection)
            return y + max(20f, pSection.textSize + 8f)
        }

        fun drawRowKV(canvas: android.graphics.Canvas, left: Float, right: Float, y: Float, label: String, value: String): Float {
            canvas.drawText(label, left, y + pLabel.textSize, pLabel)
            val w = pValue.measureText(value)
            canvas.drawText(value, right - w, y + pValue.textSize, pValue)
            return y + 18f
        }

        fun drawDivider(canvas: android.graphics.Canvas, left: Float, right: Float, y: Float): Float {
            canvas.drawLine(left, y, right, y, pDivider)
            return y + 12f
        }

        // ---- Chart primitives ----
        data class SeriesLine(val label: String, val values: List<Double?>, val paint: Paint)

        fun drawChart(canvas: android.graphics.Canvas, rect: RectF, title: String, lines: List<SeriesLine>) {
            val pad = 18f
            val titleY = rect.top + pad + pSection.textSize
            canvas.drawText(title, rect.left + pad, titleY, pSection)

            val chartTop = titleY + 12f
            val chartLeft = rect.left + pad
            val chartRight = rect.right - pad
            val chartBottom = rect.bottom - pad - 10f

            canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, pChartAxis)

            val all = lines.flatMap { it.values }.filterNotNull()
            if (all.size < 2) {
                canvas.drawText(
                    context.getString(R.string.patient_trends_not_enough_data),
                    chartLeft,
                    chartTop + pChartText.textSize,
                    pChartText
                )
                return
            }

            val yMin = all.minOrNull() ?: 0.0
            val yMax = all.maxOrNull() ?: 1.0
            val span = (yMax - yMin).takeIf { it != 0.0 } ?: 1.0

            fun xOf(i: Int, n: Int): Float {
                if (n <= 1) return chartLeft
                val step = (chartRight - chartLeft) / (n - 1).toFloat()
                return chartLeft + i * step
            }

            fun yOf(v: Double): Float {
                val norm = ((v - yMin) / span).toFloat()
                return chartBottom - norm * (chartBottom - chartTop)
            }

            val n = lines.maxOf { it.values.size }

            for (line in lines) {
                var prevX: Float? = null
                var prevY: Float? = null
                for (i in 0 until n) {
                    val v = line.values.getOrNull(i) ?: continue
                    val x = xOf(i, n)
                    val y = yOf(v)
                    if (prevX != null && prevY != null) {
                        canvas.drawLine(prevX, prevY, x, y, line.paint)
                    }
                    prevX = x
                    prevY = y
                }
            }

            // min/max labels (minimal but present)
            canvas.drawText(fmt(yMax, 1), chartLeft, chartTop + pChartText.textSize, pChartText)
            canvas.drawText(fmt(yMin, 1), chartLeft, chartBottom - 2f, pChartText)

            // Legend
            var lx = chartLeft
            val ly = chartBottom + 2f
            lines.take(3).forEach { line ->
                canvas.drawCircle(lx + 6f, ly - 6f, 4.5f, line.paint)
                canvas.drawText(line.label, lx + 16f, ly, pChartText)
                lx += min(220f, (chartRight - chartLeft) / max(1, lines.size))
            }
        }

        // ---------------- Build trend series ----------------
        val rapSeries = ordered.map { it.rapMmHg }
        val mpapSeries = ordered.map { it.mpapMmHg }
        val pcwpSeries = ordered.map { it.pcwpMmHg }
        val ciSeries = ordered.map { it.ciLMinM2 }
        val cpoSeries = ordered.map { it.cpoW }
        val pvrSeries = ordered.map { it.pvrWu }

        val hasTrends = ordered.size >= 2

        // Compute how many trend pages we will append (variable)
        val trendPages: Int = if (!hasTrends) 0 else 2 // pressures + flow/resistance (PVR included)
        val coverPages: Int = if (ordered.size >= 2) 1 else 0

        val totalPages = coverPages + ordered.size + trendPages

        val doc = PdfDocument()
        var pageNum = 1

        // ---------- Cover page (only if >=2 studies) ----------
        if (coverPages == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val p = doc.startPage(pageInfo)
            val canvas = p.canvas

            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), pBg)
            drawCornerPage(canvas, pageNum)

            val headerH = drawHeaderBlock(
                canvas = canvas,
                pageNum = pageNum,
                totalPages = totalPages,
                studyAtText = "Longitudinal",
                extraLine = "Seguimiento $rangeText | Estudios: ${ordered.size}"
            )

            val cardTop = headerH + 18f
            val cardBottom = pageHeight - 34f
            val card = drawCard(canvas, cardTop, cardBottom)

            val left = card.left + 22f
            val right = card.right - 22f
            var y = card.top + 22f

            y = drawSectionTitle(canvas, left, y, "Resumen longitudinal")
            y = drawRowKV(canvas, left, right, y, "Periodo", rangeText)
            y = drawRowKV(canvas, left, right, y, "Número de estudios", ordered.size.toString())
            y = drawDivider(canvas, left, right, y)

            // Last study quick glance
            val last = ordered.last()
            y = drawSectionTitle(canvas, left, y, "Último estudio (valores clave)")
            y = drawRowKV(canvas, left, right, y, "Índice cardiaco (CI)", "${fmt(last.ciLMinM2, 1)} ${context.getString(R.string.common_unit_lmin_m2)}")
            y = drawRowKV(canvas, left, right, y, "Resistencia vascular pulmonar (PVR)", "${fmt(last.pvrWu, 1)} ${context.getString(R.string.common_unit_wu_short)}")
            y = drawRowKV(canvas, left, right, y, "Presión auricular derecha (RAP)", "${fmt(last.rapMmHg, 0)} ${context.getString(R.string.common_unit_mmhg)}")
            y = drawRowKV(canvas, left, right, y, "Presión capilar pulmonar en cuña (PCWP)", "${fmt(last.pcwpMmHg, 0)} ${context.getString(R.string.common_unit_mmhg)}")

            doc.finishPage(p)
            pageNum += 1
        }

        // ---------- Study pages (1 study per page) ----------
        ordered.forEachIndexed { idx, s ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val p = doc.startPage(pageInfo)
            val canvas = p.canvas

            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), pBg)

            val studyAtText = sdfStudy.format(Date(s.studyAtMillis))
            val extraLine = "Estudio ${idx + 1} de ${ordered.size} | Seguimiento $rangeText"
            val headerH = drawHeaderBlock(canvas, pageNum, totalPages, studyAtText, extraLine)

            // Dynamic card height: compute needed height, clamp to page
            val pad = 22f
            val leftInset = margin + pad
            val rightInset = pageWidth - margin - pad
            val rowH = 18f
            val sectionGap = 10f
            val dividerGap = 12f

            // We always print A,B,C,D; D can be smaller for first study
            val hasPrev = idx > 0
            val blockRows = if (hasPrev) 3 else 1 // comparison rows (CI, PVR, RAP) or just "No comparable"
            val requiredHeight =
                pad + // top
                        (pSection.textSize + 8f) + 2 * rowH + dividerGap + // A
                        (pSection.textSize + 8f) + 2 * rowH + dividerGap + // B
                        (pSection.textSize + 8f) + 3 * rowH + dividerGap + // C
                        (pSection.textSize + 8f) + blockRows * rowH + // D
                        pad + 16f // bottom breathing

            val cardTop = headerH + 18f
            val maxCardBottom = pageHeight - 34f
            val cardBottom = min(maxCardBottom, cardTop + requiredHeight)
            val card = drawCard(canvas, cardTop, cardBottom)

            var y = card.top + pad

            // Section A: Flow/Performance
            y = drawSectionTitle(canvas, leftInset, y, "Flujo y desempeño")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Índice cardiaco (CI)", "${fmt(s.ciLMinM2, 1)} ${context.getString(R.string.common_unit_lmin_m2)}")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Gasto cardiaco (CO)", "${fmt(s.coLMin, 2)} ${context.getString(R.string.common_unit_lmin)}")
            y = drawDivider(canvas, leftInset, rightInset, y)

            // Section B: Resistances
            y = drawSectionTitle(canvas, leftInset, y, "Resistencias")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Resistencia vascular pulmonar (PVR)", "${fmt(s.pvrWu, 1)} ${context.getString(R.string.common_unit_wu_short)}")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Resistencia vascular sistémica (RVS)", "${fmt(s.svrWu, 1)} ${context.getString(R.string.common_unit_wu)}")
            y = drawDivider(canvas, leftInset, rightInset, y)

            // Section C: Pressures
            y = drawSectionTitle(canvas, leftInset, y, "Presiones y congestión")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Presión auricular derecha (RAP)", "${fmt(s.rapMmHg, 0)} ${context.getString(R.string.common_unit_mmhg)}")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Presión arterial pulmonar media (mPAP)", "${fmt(s.mpapMmHg, 0)} ${context.getString(R.string.common_unit_mmhg)}")
            y = drawRowKV(canvas, leftInset, rightInset, y, "Presión capilar pulmonar en cuña (PCWP)", "${fmt(s.pcwpMmHg, 0)} ${context.getString(R.string.common_unit_mmhg)}")
            y = drawDivider(canvas, leftInset, rightInset, y)

            // Section D: Comparison vs previous
            y = drawSectionTitle(canvas, leftInset, y, "Cambios respecto al estudio previo")
            if (!hasPrev) {
                y = drawRowKV(canvas, leftInset, rightInset, y, "Comparación", "No comparable (primer estudio)")
            } else {
                val prev = ordered[idx - 1]
                y = drawRowKV(
                    canvas, leftInset, rightInset, y,
                    "Índice cardiaco (CI)",
                    delta(s.ciLMinM2, prev.ciLMinM2, 1, context.getString(R.string.common_unit_lmin_m2))
                )
                y = drawRowKV(
                    canvas, leftInset, rightInset, y,
                    "Resistencia vascular pulmonar (PVR)",
                    delta(s.pvrWu, prev.pvrWu, 1, context.getString(R.string.common_unit_wu_short))
                )
                y = drawRowKV(
                    canvas, leftInset, rightInset, y,
                    "Presión auricular derecha (RAP)",
                    delta(s.rapMmHg, prev.rapMmHg, 0, context.getString(R.string.common_unit_mmhg))
                )
            }

            doc.finishPage(p)
            pageNum += 1
        }

        // ---------- Trends appendix (at the end, variable) ----------
        if (hasTrends) {
            // Trends 1: Pressures
            run {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val p = doc.startPage(pageInfo)
                val canvas = p.canvas

                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), pBg)
                val headerH = drawHeaderBlock(
                    canvas, pageNum, totalPages,
                    studyAtText = "Tendencias (Presiones)",
                    extraLine = "Seguimiento $rangeText | Estudios: ${ordered.size}"
                )

                val cardTop = headerH + 18f
                val cardBottom = pageHeight - 34f
                val card = drawCard(canvas, cardTop, cardBottom)

                val chartRect = RectF(card.left + 18f, card.top + 18f, card.right - 18f, card.bottom - 18f)

                drawChart(
                    canvas = canvas,
                    rect = chartRect,
                    title = context.getString(R.string.patient_trends_section_pressures_title),
                    lines = listOf(
                        SeriesLine("RAP", rapSeries, pLineDark),
                        SeriesLine("mPAP", mpapSeries, pLineMid),
                        SeriesLine("PCWP", pcwpSeries, pLineLight)
                    )
                )

                doc.finishPage(p)
                pageNum += 1
            }

            // Trends 2: Flow + Resistance
            run {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val p = doc.startPage(pageInfo)
                val canvas = p.canvas

                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), pBg)
                val headerH = drawHeaderBlock(
                    canvas, pageNum, totalPages,
                    studyAtText = "Tendencias (Flujo / Resistencia)",
                    extraLine = "Seguimiento $rangeText | Estudios: ${ordered.size}"
                )

                val cardTop = headerH + 18f
                val cardBottom = pageHeight - 34f
                val card = drawCard(canvas, cardTop, cardBottom)

                val pad = 18f
                val midY = card.top + card.height() / 2f

                val rectTop = RectF(card.left + pad, card.top + pad, card.right - pad, midY - 10f)
                val rectBot = RectF(card.left + pad, midY + 10f, card.right - pad, card.bottom - pad)

                drawChart(
                    canvas = canvas,
                    rect = rectTop,
                    title = context.getString(R.string.patient_trends_section_flow_title),
                    lines = listOf(
                        SeriesLine("CI", ciSeries, pLineDark),
                        SeriesLine("CPO", cpoSeries, pLineMid)
                    )
                )

                drawChart(
                    canvas = canvas,
                    rect = rectBot,
                    title = context.getString(R.string.patient_trends_section_resistance_title),
                    lines = listOf(
                        SeriesLine("PVR", pvrSeries, pLineDark)
                    )
                )

                doc.finishPage(p)
                pageNum += 1
            }
        }

        doc.writeTo(outputStream)
        doc.close()
    }
}
