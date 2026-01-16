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
        sb.appendLine(context.getString(R.string.pdf_header_subtitle))
        sb.appendLine(context.getString(R.string.pdf_header_datetime, now))
        sb.appendLine()

        for (e in entries) {
            sb.appendLine("=== ${e.title} ===")
            sb.appendLine("${context.getString(R.string.pdf_table_inputs)}:")
            e.inputs.forEach { li ->
                val unit = li.unit?.let { " $it" } ?: ""
                val detail = li.detail?.let { " (${it})" } ?: ""
                sb.appendLine("• ${li.label}: ${li.value}$unit$detail")
            }
            sb.appendLine("${context.getString(R.string.pdf_table_outputs)}:")
            e.outputs.forEach { li ->
                val unit = li.unit?.let { " $it" } ?: ""
                val detail = li.detail?.let { " (${it})" } ?: ""
                sb.appendLine("• ${li.label}: ${li.value}$unit$detail")
            }
            if (e.notes.isNotEmpty()) {
                sb.appendLine("${context.getString(R.string.pdf_notes_title)}:")
                e.notes.forEach { sb.appendLine("• $it") }
            }
            sb.appendLine()
        }

        sb.appendLine(context.getString(R.string.pdf_disclaimer))
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

        // A4 (points)
        val pageWidth = 595
        val pageHeight = 842

        val margin = 36f
        val footerH = 24f
        val contentMaxY = pageHeight - margin - footerH

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = sdf.format(Date(nowMillis))

        val headerSubtitle = context.getString(R.string.pdf_header_subtitle)
        val headerDateTime = context.getString(R.string.pdf_header_datetime, now)
        val footerPageFmt = context.getString(R.string.pdf_footer_page, 1).replace("1", "%d") // placeholder simple

        val inputsTitle = context.getString(R.string.pdf_table_inputs)
        val outputsTitle = context.getString(R.string.pdf_table_outputs)
        val notesTitle = context.getString(R.string.pdf_notes_title)
        val disclaimer = context.getString(R.string.pdf_disclaimer)

        // Colors
        val colorHeaderBg = Color.rgb(20, 70, 120)          // azul sobrio
        val colorCardBg = Color.rgb(245, 247, 250)          // gris muy suave
        val colorLine = Color.rgb(210, 215, 220)            // separadores
        val colorText = Color.rgb(25, 25, 25)

        // Paints
        val pHeaderBg = Paint().apply { color = colorHeaderBg; style = Paint.Style.FILL }
        val pCardBg = Paint().apply { color = colorCardBg; style = Paint.Style.FILL }
        val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLine; strokeWidth = 1f; style = Paint.Style.STROKE }

        val pTitleWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pSubWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pH2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorText
            textSize = 9.5f
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
        var y = 0f

        fun drawFooter() {
            val footerText = context.getString(R.string.pdf_footer_page, pageNumber)
            canvas.drawText(footerText, margin, pageHeight - margin + 6f, pSmall)
        }

        fun drawHeader() {
            val headerH = 78f
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerH, pHeaderBg)

            canvas.drawText(appName, margin, 28f, pTitleWhite)
            canvas.drawText(headerSubtitle, margin, 48f, pSubWhite)
            canvas.drawText(headerDateTime, margin, 66f, pSubWhite)

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

        fun drawRoundedCard(x: Float, top: Float, w: Float, h: Float) {
            val r = 14f
            val rect = RectF(x, top, x + w, top + h)
            canvas.drawRoundRect(rect, r, r, pCardBg)
            canvas.drawRoundRect(rect, r, r, pBorder)
        }

        // Start page
        drawHeader()

        val cardWidth = pageWidth - 2 * margin
        val maxTextWidth = cardWidth - 24f

        fun drawTable(title: String, items: List<LineItem>) {
            if (items.isEmpty()) return

            val pad = 12f
            val titleH = 22f
            val rowGap = 14f
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

            val top = y
            drawRoundedCard(margin, top, cardWidth, tableH)

            canvas.drawText(title, margin + pad, top + 18f, pH2)

            val tableTop = top + titleH
            canvas.drawLine(margin + col1, tableTop, margin + col1, top + tableH - pad, pBorder)

            var yy = tableTop + pad

            for (i in items.indices) {
                val li = items[i]
                val left = "${li.label}${li.detail?.let { " ($it)" } ?: ""}"
                val right = "${li.value}${li.unit?.let { " $it" } ?: ""}"

                val leftLines = wrap(left, pBody, maxLabelW)
                val rightLines = wrap(right, pBody, maxValueW)

                if (i > 0) {
                    canvas.drawLine(margin, yy, margin + cardWidth, yy, pBorder)
                }

                var tyL = yy + 16f
                leftLines.forEach {
                    canvas.drawText(it, margin + pad, tyL, pBody)
                    tyL += 14f
                }

                var tyR = yy + 16f
                rightLines.forEach {
                    canvas.drawText(it, margin + col1 + pad, tyR, pBody)
                    tyR += 14f
                }

                yy += rowHeights[i]
            }

            y = top + tableH + 12f
        }

        fun drawNotes(notes: List<String>) {
            if (notes.isEmpty()) return
            val text = notes.joinToString(" • ", prefix = "• ")
            val lines = wrap(text, pBody, maxTextWidth)
            val pad = 12f
            val h = 22f + pad + lines.size * 14f + pad

            ensureSpace(h + 8f)

            val top = y
            drawRoundedCard(margin, top, cardWidth, h)
            canvas.drawText(notesTitle, margin + pad, top + 18f, pH2)

            var yy = top + 22f + pad + 12f
            lines.forEach {
                canvas.drawText(it, margin + pad, yy, pBody)
                yy += 14f
            }

            y = top + h + 12f
        }

        fun drawEntry(entry: CalcEntry) {
            val titleLines = wrap(entry.title, pH2, cardWidth)
            val titleH = titleLines.size * 18f + 6f
            ensureSpace(titleH)

            titleLines.forEach {
                canvas.drawText(it, margin, y, pH2)
                y += 18f
            }
            y += 6f

            drawTable(inputsTitle, entry.inputs)
            drawTable(outputsTitle, entry.outputs)
            drawNotes(entry.notes)

            y += 6f
        }

        entries.forEach { drawEntry(it) }

        val dLines = wrap(disclaimer, pSmall, cardWidth)
        val dH = dLines.size * 12f + 10f
        ensureSpace(dH)
        dLines.forEach {
            canvas.drawText(it, margin, y, pSmall)
            y += 12f
        }

        drawFooter()
        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
