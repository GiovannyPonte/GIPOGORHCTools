package com.gipogo.rhctools.report

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.gipogo.rhctools.R
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class ForresterPdfBlock(
    val ci: Double?,
    val pcwp: Double?
)

object PdfReportGenerator {

    fun buildPlainTextReport(
        context: Context,
        appName: String,
        entries: List<CalcEntry>,
        nowMillis: Long
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = sdf.format(Date(nowMillis))

        val sb = StringBuilder()
        sb.appendLine(appName)
        sb.appendLine(context.getString(R.string.pdf_header_datetime, now))
        sb.appendLine()

        val entryHeaderFmt = context.getString(R.string.pdf_plaintext_entry_header_format)
        val bulletLineFmt = context.getString(R.string.pdf_plaintext_bullet_line_format)

        for (e in entries) {
            sb.appendLine(String.format(entryHeaderFmt, e.title))

            if (e.inputs.isNotEmpty()) {
                sb.appendLine(context.getString(R.string.pdf_section_inputs))
                e.inputs.forEach { li ->
                    val line = buildString {
                        append(li.label)
                        li.detail?.let { append(" ($it)") }
                        append(": ")
                        append(li.value)
                        li.unit?.let { append(" $it") }
                    }
                    sb.appendLine(line)
                }
                sb.appendLine()
            }

            if (e.outputs.isNotEmpty()) {
                sb.appendLine(context.getString(R.string.pdf_section_outputs))
                e.outputs.forEach { li ->
                    val line = buildString {
                        append(li.label)
                        li.detail?.let { append(" ($it)") }
                        append(": ")
                        append(li.value)
                        li.unit?.let { append(" $it") }
                    }
                    sb.appendLine(line)
                }
                sb.appendLine()
            }

            if (e.notes.isNotEmpty()) {
                sb.appendLine(context.getString(R.string.pdf_section_notes))
                e.notes.forEach { note ->
                    sb.appendLine(String.format(bulletLineFmt, note))
                }
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    @JvmOverloads
    fun writePdf(
        context: Context,
        outputStream: OutputStream,
        appName: String,
        entries: List<CalcEntry>,
        nowMillis: Long,
        forrester: ForresterPdfBlock? = null
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = sdf.format(Date(nowMillis))

        val headerSubtitle = context.getString(R.string.pdf_header_subtitle)
        val headerDateTime = context.getString(R.string.pdf_header_datetime, now)

        // PDF palette (internal)
        val colorHeader = Color.parseColor("#111827")
        val colorCard = Color.WHITE
        val colorLine = Color.parseColor("#E5E7EB")
        val colorText = Color.parseColor("#111827")
        val colorSubText = Color.parseColor("#6B7280")

        val pHeaderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorHeader; style = Paint.Style.FILL }
        val pCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCard; style = Paint.Style.FILL }
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLine; strokeWidth = 1f; style = Paint.Style.STROKE }

        val pTitleWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pSubWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pH2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 13.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pBodySub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorSubText
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Forrester paints
        val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorSubText
            textSize = 11.0f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pAxisTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 12.0f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pThreshold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 11.0f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pQuadrant = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorSubText
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Small translucent tag background (for CI label only now)
        val pTagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 220
            style = Paint.Style.FILL
        }

        fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            val chunks = text.split("\n")
            val out = mutableListOf<String>()
            for (chunk in chunks) {
                val words = chunk.split(" ")
                var current = ""
                for (w in words) {
                    val test = if (current.isBlank()) w else "$current $w"
                    if (paint.measureText(test) <= maxWidth) current = test
                    else {
                        if (current.isNotBlank()) out.add(current)
                        current = w
                    }
                }
                if (current.isNotBlank()) out.add(current)
            }
            return out
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val contentMaxY = pageHeight - margin - 16f
        var y = 0f

        fun drawFooter() {
            val footerText = context.getString(R.string.pdf_footer_page, pageNumber)
            canvas.drawText(footerText, margin, pageHeight - margin + 6f, pSmall)
        }

        fun drawHeader() {
            val topPad = 14f
            val lineGap = 8f

            val titleY = topPad - pTitleWhite.fontMetrics.ascent
            val sub1Y = titleY + lineGap - pSubWhite.fontMetrics.ascent
            val sub2Y = sub1Y + lineGap - pSubWhite.fontMetrics.ascent

            val headerH = sub2Y + pSubWhite.fontMetrics.descent + 14f
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerH, pHeaderBg)

            canvas.drawText(appName, margin, titleY, pTitleWhite)
            canvas.drawText(headerSubtitle, margin, sub1Y, pSubWhite)
            canvas.drawText(headerDateTime, margin, sub2Y, pSubWhite)

            y = headerH + 14f
        }

        fun newPage() {
            drawFooter()
            doc.finishPage(page)

            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 0f

            drawHeader()
        }

        fun ensureSpace(requiredHeight: Float) {
            if (y + requiredHeight > contentMaxY) newPage()
        }

        fun drawCard(blockHeight: Float, drawer: (left: Float, top: Float, right: Float, bottom: Float) -> Unit) {
            val cardWidthLocal = pageWidth - 2 * margin
            val left = margin
            val top = y
            val right = left + cardWidthLocal
            val bottom = top + blockHeight

            ensureSpace(blockHeight + 8f)

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 18f, 18f, pCard)
            canvas.drawRoundRect(rect, 18f, 18f, pLine)

            drawer(left, top, right, bottom)
            y = bottom + 12f
        }

        // Start page
        drawHeader()

        val cardWidth = pageWidth - 2 * margin
        val maxTextWidth = cardWidth - 24f

        fun drawTable(title: String, items: List<LineItem>) {
            if (items.isEmpty()) return

            val pad = 12f
            val titleH = max(22f, pH2.textSize + 6f)
            val rowGap = max(14f, pBody.textSize + 3f)

            val col1 = 160f
            val col2 = cardWidth - col1

            val maxLabelW = col1 - 2 * pad
            val maxValueW = col2 - 2 * pad

            val rowHeights = items.map { li ->
                val left = "${li.label}${li.detail?.let { " ($it)" } ?: ""}"
                val right = "${li.value}${li.unit?.let { " $it" } ?: ""}"
                val leftLines = wrap(left, pBody, maxLabelW).size
                val rightLines = wrap(right, pBody, maxValueW).size
                max(leftLines, rightLines) * rowGap + 10f
            }

            val tableH = titleH + pad + rowHeights.sum() + pad
            ensureSpace(tableH + 10f)

            drawCard(tableH) { l, t, _, _ ->
                var yy = t + pad + titleH

                canvas.drawText(title, l + pad, t + pad - pH2.fontMetrics.ascent, pH2)

                for (i in items.indices) {
                    val li = items[i]
                    val left = "${li.label}${li.detail?.let { " ($it)" } ?: ""}"
                    val right = "${li.value}${li.unit?.let { " $it" } ?: ""}"

                    val leftLines = wrap(left, pBody, maxLabelW)
                    val rightLines = wrap(right, pBody, maxValueW)

                    val baseY = yy - pBody.fontMetrics.ascent
                    var lineY = baseY

                    for (ln in leftLines) {
                        canvas.drawText(ln, l + pad, lineY, pBody)
                        lineY += rowGap
                    }

                    lineY = baseY
                    for (ln in rightLines) {
                        canvas.drawText(ln, l + col1 + pad, lineY, pBody)
                        lineY += rowGap
                    }

                    yy += rowHeights[i]
                }
            }
        }

        fun drawNotes(notes: List<String>) {
            if (notes.isEmpty()) return

            val bullet = context.getString(R.string.pdf_bullet)
            val sep = context.getString(R.string.pdf_bullet_sep)
            val text = notes.joinToString(sep, prefix = bullet)

            val lines = wrap(text, pBodySub, maxTextWidth)
            val pad = 12f
            val rowGap = max(14f, pBodySub.textSize + 3f)
            val h = max(22f, pH2.textSize + 6f) + pad + lines.size * rowGap + pad

            ensureSpace(h + 10f)

            drawCard(h) { l, t, _, _ ->
                canvas.drawText(context.getString(R.string.pdf_section_notes), l + pad, t + pad - pH2.fontMetrics.ascent, pH2)

                var yy = t + pad + max(22f, pH2.textSize + 6f)
                for (ln in lines) {
                    yy += rowGap
                    canvas.drawText(ln, l + pad, yy - pBodySub.fontMetrics.ascent, pBodySub)
                }
            }
        }

        // -------------------- FORRESTER (PDF) --------------------

        fun drawTag(text: String, x: Float, yBaseline: Float) {
            val padX = 6f
            val padY = 4f
            val w = pThreshold.measureText(text)
            val fm = pThreshold.fontMetrics

            val left = x
            val top = yBaseline + fm.ascent - padY
            val right = x + w + 2 * padX
            val bottom = yBaseline + fm.descent + padY

            canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, pTagBg)
            canvas.drawText(text, x + padX, yBaseline, pThreshold)
        }

        fun drawForresterChart(
            rect: RectF,
            ci: Float,
            pcwp: Float,
            ciThreshold: Float,
            pcwpThreshold: Float,
            xAxisTitle: String,
            yAxisTitle: String,
            // NOTE: xThresholdLabel intentionally not rendered inside plot (per requirement)
            yThresholdLabel: String,
            q1: String,
            q2: String,
            q3: String,
            q4: String
        ) {
            val maxCi = 5f
            val maxPcwp = 40f

            val ciClamped = ci.coerceIn(0f, maxCi)
            val pcwpClamped = pcwp.coerceIn(0f, maxPcwp)

            val tickX = listOf(0f, 10f, 20f, 30f, 40f)
            val tickY = listOf(0f, 1f, 2f, 3f, 4f, 5f)

            val yLabelW = pTick.measureText("5")
            val insetL = max(42f, yLabelW + 22f)
            val insetR = 12f
            val insetT = max(18f, pAxisTitle.textSize + 14f)
            val insetB = max(26f, pTick.textSize + pAxisTitle.textSize + 22f)

            val plot = RectF(
                rect.left + insetL,
                rect.top + insetT,
                rect.right - insetR,
                rect.bottom - insetB
            )

            fun xFromPcwp(v: Float): Float = plot.left + (v / maxPcwp) * plot.width()
            fun yFromCi(v: Float): Float = plot.bottom - (v / maxCi) * plot.height()

            val xT = xFromPcwp(pcwpThreshold.coerceIn(0f, maxPcwp))
            val yT = yFromCi(ciThreshold.coerceIn(0f, maxCi))

            // Quadrant fills
            val pQ1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8F5E9"); style = Paint.Style.FILL }
            val pQ2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E3F2FD"); style = Paint.Style.FILL }
            val pQ3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF8E1"); style = Paint.Style.FILL }
            val pQ4 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEBEE"); style = Paint.Style.FILL }

            canvas.drawRect(RectF(plot.left, plot.top, xT, yT), pQ1)
            canvas.drawRect(RectF(xT, plot.top, plot.right, yT), pQ2)
            canvas.drawRect(RectF(plot.left, yT, xT, plot.bottom), pQ3)
            canvas.drawRect(RectF(xT, yT, plot.right, plot.bottom), pQ4)

            // Border & axes
            val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#9CA3AF")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(plot, pBorder)

            val pAxis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#374151")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, pAxis)
            canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, pAxis)

            // Grid
            val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D1D5DB")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            // X ticks + labels
            for (v in tickX) {
                val x = xFromPcwp(v)
                canvas.drawLine(x, plot.bottom, x, plot.bottom + 6f, pAxis)
                if (v != 0f && v != maxPcwp) canvas.drawLine(x, plot.top, x, plot.bottom, pGrid)

                val label = v.toInt().toString()
                val tw = pTick.measureText(label)
                val tx = (x - tw / 2f).coerceIn(rect.left + 2f, rect.right - tw - 2f)
                val ty = plot.bottom + 10f - pTick.fontMetrics.ascent
                canvas.drawText(label, tx, ty, pTick)
            }

            // Y ticks + labels
            for (v in tickY) {
                val yPx = yFromCi(v)
                canvas.drawLine(plot.left - 6f, yPx, plot.left, yPx, pAxis)
                if (v != 0f && v != maxCi) canvas.drawLine(plot.left, yPx, plot.right, yPx, pGrid)

                val label = v.toInt().toString()
                val tw = pTick.measureText(label)
                val tx = (plot.left - 12f - tw).coerceAtLeast(rect.left + 2f)
                val ty = (yPx - pTick.fontMetrics.ascent).coerceIn(rect.top + insetT, plot.bottom - 2f)
                canvas.drawText(label, tx, ty, pTick)
            }

            // Threshold dashed lines (keep both lines, no text for PCWP)
            val pDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4B5563")
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }
            canvas.drawLine(plot.left, yT, plot.right, yT, pDash)
            canvas.drawLine(xT, plot.top, xT, plot.bottom, pDash)

            // Axis titles
            val xTitleW = pAxisTitle.measureText(xAxisTitle)
            val xTitleX = (plot.centerX() - xTitleW / 2f).coerceIn(rect.left + 2f, rect.right - xTitleW - 2f)
            val xTitleY = rect.bottom - 6f
            canvas.drawText(xAxisTitle, xTitleX, xTitleY, pAxisTitle)

            val yTitleX = rect.left + 6f
            val yTitleY = rect.top + 6f - pAxisTitle.fontMetrics.ascent
            canvas.drawText(yAxisTitle, yTitleX, yTitleY, pAxisTitle)

            // Only CI threshold label (requested: remove PCWP label from plot)
            val yLabX = (rect.left + 6f).coerceAtLeast(rect.left + 2f)
            val yLabY = (yT - 6f).coerceIn(plot.top + pThreshold.textSize, plot.bottom - 2f) - pThreshold.fontMetrics.ascent
            drawTag(yThresholdLabel, yLabX, yLabY)

            // Quadrant labels
            val qPadX = 10f
            val qPadY = 14f - pQuadrant.fontMetrics.ascent
            canvas.drawText(q1, plot.left + qPadX, plot.top + qPadY, pQuadrant)
            canvas.drawText(q2, xT + qPadX, plot.top + qPadY, pQuadrant)
            canvas.drawText(q3, plot.left + qPadX, yT + qPadY, pQuadrant)
            canvas.drawText(q4, xT + qPadX, yT + qPadY, pQuadrant)

            // Patient point
            val px = xFromPcwp(pcwpClamped)
            val py = yFromCi(ciClamped)

            val pHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2563EB")
                alpha = 24
                style = Paint.Style.FILL
            }
            val pPointStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2563EB")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val pPointFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            canvas.drawCircle(px, py, 16f, pHalo)
            canvas.drawCircle(px, py, 7f, pPointFill)
            canvas.drawCircle(px, py, 9f, pPointStroke)
        }

        fun drawForresterCard(ci: Double?, pcwp: Double?) {
            val title = context.getString(R.string.pdf_forrester_title)
            val subtitle = context.getString(R.string.pdf_forrester_subtitle)
            val missing = context.getString(R.string.pdf_forrester_missing_values)

            val uMmHg = context.getString(R.string.common_unit_mmhg)
            val uLMinM2 = context.getString(R.string.common_unit_lmin_m2)

            val xAxisTitle = context.getString(R.string.pdf_forrester_x_axis_title, uMmHg)
            val yAxisTitle = context.getString(R.string.pdf_forrester_y_axis_title, uLMinM2)

            val ciTh = 2.2f
            val pcwpTh = 18f

            val yThLabel = context.getString(R.string.pdf_forrester_ci_threshold_label, ciTh)

            val q1 = context.getString(R.string.pdf_forrester_q1_label)
            val q2 = context.getString(R.string.pdf_forrester_q2_label)
            val q3 = context.getString(R.string.pdf_forrester_q3_label)
            val q4 = context.getString(R.string.pdf_forrester_q4_label)

            // Legend remains the single place where thresholds are shown (requested)
            val legend = context.getString(
                R.string.pdf_forrester_thresholds_legend,
                ciTh, uLMinM2,
                pcwpTh, uMmHg
            )

            val pad = 12f
            val subtitleLines = wrap(subtitle, pBodySub, maxTextWidth)
            val rowGapSub = max(14f, pBodySub.textSize + 3f)

            val headerTitleY = pad - pH2.fontMetrics.ascent
            val headerBase = headerTitleY + pH2.fontMetrics.descent + 6f
            val headerH = headerBase + (subtitleLines.size * rowGapSub) + 10f

            val hasValues = (ci != null && pcwp != null)

            if (!hasValues) {
                val missingLines = wrap(missing, pBodySub, maxTextWidth)
                val bodyH = (missingLines.size * rowGapSub) + pad
                val blockH = headerH + bodyH + pad

                drawCard(blockH) { l, t, _, _ ->
                    val textLeft = l + pad

                    canvas.drawText(title, textLeft, t + headerTitleY, pH2)

                    var yy = t + headerBase
                    for (ln in subtitleLines) {
                        yy += rowGapSub
                        canvas.drawText(ln, textLeft, yy - pBodySub.fontMetrics.ascent, pBodySub)
                    }

                    yy += 10f
                    for (ln in missingLines) {
                        yy += rowGapSub
                        canvas.drawText(ln, textLeft, yy - pBodySub.fontMetrics.ascent, pBodySub)
                    }
                }
                return
            }

            val maxSquare = min(cardWidth - 2 * pad, 360f)
            val chartSize = maxSquare

            val legendLines = wrap(legend, pBodySub, maxTextWidth)
            val legendH = legendLines.size * rowGapSub + 8f

            val blockH = headerH + chartSize + legendH + pad + 8f
            ensureSpace(blockH + 10f)

            drawCard(blockH) { l, t, _, _ ->
                val textLeft = l + pad

                canvas.drawText(title, textLeft, t + headerTitleY, pH2)

                var yy = t + headerBase
                for (ln in subtitleLines) {
                    yy += rowGapSub
                    canvas.drawText(ln, textLeft, yy - pBodySub.fontMetrics.ascent, pBodySub)
                }

                val chartTop = t + headerH + 6f
                val chartLeft = l + (cardWidth - chartSize) / 2f
                val chartRect = RectF(chartLeft, chartTop, chartLeft + chartSize, chartTop + chartSize)

                drawForresterChart(
                    rect = chartRect,
                    ci = ci!!.toFloat(),
                    pcwp = pcwp!!.toFloat(),
                    ciThreshold = ciTh,
                    pcwpThreshold = pcwpTh,
                    xAxisTitle = xAxisTitle,
                    yAxisTitle = yAxisTitle,
                    yThresholdLabel = yThLabel,
                    q1 = q1,
                    q2 = q2,
                    q3 = q3,
                    q4 = q4
                )

                yy = chartRect.bottom + 6f
                for (ln in legendLines) {
                    yy += rowGapSub
                    canvas.drawText(ln, textLeft, yy - pBodySub.fontMetrics.ascent, pBodySub)
                }
            }
        }

        // -------------------- Entries --------------------
        for ((idx, entry) in entries.withIndex()) {
            val titleLines = wrap(entry.title, pH2, maxTextWidth)
            val lineGap = max(18f, pH2.textSize + 2f)
            val titleH = 14f + titleLines.size * lineGap + 10f

            drawCard(titleH) { l, t, _, _ ->
                var yy = t + 14f - pH2.fontMetrics.ascent
                for (ln in titleLines) {
                    canvas.drawText(ln, l + 12f, yy, pH2)
                    yy += lineGap
                }
            }

            drawTable(context.getString(R.string.pdf_section_inputs), entry.inputs)
            drawTable(context.getString(R.string.pdf_section_outputs), entry.outputs)

            if (idx == 0 && forrester != null) {
                drawForresterCard(ci = forrester.ci, pcwp = forrester.pcwp)
            }

            drawNotes(entry.notes)
        }

        drawFooter()
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
