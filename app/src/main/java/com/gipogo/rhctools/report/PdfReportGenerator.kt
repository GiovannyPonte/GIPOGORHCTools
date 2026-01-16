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
import kotlin.math.max

object PdfReportGenerator {

    // (Opcional) si en algún momento quieres un reporte de texto plano localizado:
    fun buildPlainTextReport(context: Context, appName: String, entries: List<CalcEntry>, nowMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = sdf.format(Date(nowMillis))

        val sb = StringBuilder()
        sb.appendLine(appName)
        sb.appendLine(context.getString(R.string.pdf_header_datetime, now))
        sb.appendLine()

        for (e in entries) {
            sb.appendLine("=== ${e.title} ===")
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
                e.notes.forEach { sb.appendLine("• $it") }
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    fun writePdf(
        context: Context,
        outputStream: OutputStream,
        appName: String,
        entries: List<CalcEntry>,
        nowMillis: Long
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = sdf.format(Date(nowMillis))

        val headerSubtitle = context.getString(R.string.pdf_header_subtitle)
        val headerDateTime = context.getString(R.string.pdf_header_datetime, now)

        // Palette
        val colorHeader = Color.parseColor("#111827")
        val colorCard = Color.WHITE
        val colorLine = Color.parseColor("#E5E7EB")
        val colorText = Color.parseColor("#111827")
        val colorSubText = Color.parseColor("#6B7280")

        val pHeaderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorHeader; style = Paint.Style.FILL }
        val pCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCard; style = Paint.Style.FILL }
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLine; strokeWidth = 1f; style = Paint.Style.STROKE }

        // ✅ Larger fonts for readability
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

        fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var current = ""
            for (w in words) {
                val test = if (current.isBlank()) w else "$current $w"
                if (paint.measureText(test) <= maxWidth) current = test
                else {
                    if (current.isNotBlank()) lines.add(current)
                    current = w
                }
            }
            if (current.isNotBlank()) lines.add(current)
            return lines
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
            // ✅ Dynamic header based on font sizes (better readability on phones)
            val topPad = 14f
            val lineGap = 8f

            val titleY = topPad + pTitleWhite.textSize
            val sub1Y = titleY + lineGap + pSubWhite.textSize
            val sub2Y = sub1Y + lineGap + pSubWhite.textSize

            val headerH = sub2Y + 14f
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
            val cardWidth = pageWidth - 2 * margin
            val left = margin
            val top = y
            val right = left + cardWidth
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

            drawCard(tableH) { l, t, r, _ ->
                var yy = t + pad + titleH

                // Title
                canvas.drawText(title, l + pad, t + pad + pH2.textSize, pH2)

                // Rows
                for (i in items.indices) {
                    val li = items[i]
                    val left = "${li.label}${li.detail?.let { " ($it)" } ?: ""}"
                    val right = "${li.value}${li.unit?.let { " $it" } ?: ""}"

                    val leftLines = wrap(left, pBody, maxLabelW)
                    val rightLines = wrap(right, pBody, maxValueW)

                    val baseY = yy + pBody.textSize
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
                canvas.drawText(context.getString(R.string.pdf_section_notes), l + pad, t + pad + pH2.textSize, pH2)

                var yy = t + pad + max(22f, pH2.textSize + 6f)
                for (ln in lines) {
                    yy += rowGap
                    canvas.drawText(ln, l + pad, yy, pBodySub)
                }
            }
        }

        // Entries
        for (entry in entries) {
            // Title card (section header)
            val titleLines = wrap(entry.title, pH2, maxTextWidth)
            val titleH = 14f + titleLines.size * max(18f, pH2.textSize + 2f) + 10f

            drawCard(titleH) { l, t, _, _ ->
                var yy = t + 14f + pH2.textSize
                for (ln in titleLines) {
                    canvas.drawText(ln, l + 12f, yy, pH2)
                    yy += max(18f, pH2.textSize + 2f)
                }
            }

            drawTable(context.getString(R.string.pdf_section_inputs), entry.inputs)
            drawTable(context.getString(R.string.pdf_section_outputs), entry.outputs)
            drawNotes(entry.notes)
        }

        drawFooter()
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
