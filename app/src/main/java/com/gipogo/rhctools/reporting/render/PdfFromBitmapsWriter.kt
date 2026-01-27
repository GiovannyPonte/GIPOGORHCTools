package com.gipogo.rhctools.reporting.render

import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfFromBitmapsWriter {

    /**
     * Writes a multi-page PDF from bitmaps.
     * Each bitmap becomes 1 PDF page (same pixel size as page size).
     */
    fun writePdfFromBitmaps(
        outputFile: File,
        bitmaps: List<android.graphics.Bitmap>
    ) {
        require(bitmaps.isNotEmpty()) { "No pages to write." }

        val doc = PdfDocument()
        try {
            bitmaps.forEachIndexed { idx, bmp ->
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, idx + 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawBitmap(bmp, 0f, 0f, null)
                doc.finishPage(page)
            }

            FileOutputStream(outputFile).use { out ->
                doc.writeTo(out)
            }
        } finally {
            doc.close()
        }
    }
}
