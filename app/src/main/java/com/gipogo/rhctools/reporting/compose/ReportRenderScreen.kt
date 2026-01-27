package com.gipogo.rhctools.reporting.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.reporting.builder.LongitudinalReportBuilder
import com.gipogo.rhctools.reporting.model.ReportDocumentUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val A4_WIDTH_PX_300 = 2480
private const val A4_HEIGHT_PX_300 = 3508

@Composable
fun ReportRenderRoute(
    patientId: String,
    onDoneOpenPreview: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // DB / DAOs here (Route responsibility)
    val appCtx = context.applicationContext
    val db = remember(appCtx) { DbProvider.get(appCtx) }
    val patientDao = remember(db) { db.patientDao() }
    val rhcStudyDao = remember(db) { db.rhcStudyDao() }

    var stage by remember { mutableStateOf<Stage>(Stage.Building) }
    var pageProgress by remember { mutableStateOf(0 to 0) } // (current, total)
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(patientId) {
        runCatching {
            stage = Stage.Building
            errorText = null

            // 1) Build UI model from Room (no Compose logic here)
            val document = LongitudinalReportBuilder.buildFromRoom(
                context = context,
                patientId = patientId,
                patientDao = patientDao,
                rhcStudyDao = rhcStudyDao
            )

            if (document.pages.isEmpty()) {
                throw IllegalStateException(context.getString(R.string.patient_reports_no_studies))
            }

            stage = Stage.Rendering
            pageProgress = 0 to document.pages.size

            // 2) Export PDF streaming (no keeping all bitmaps)
            val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
            val outFile = File(outDir, "RHC_LONG_COMPOSE_${patientId}_${System.currentTimeMillis()}.pdf")

            exportDocumentToPdfStreaming(
                context = context,
                activity = activity,
                document = document,
                outFile = outFile,
                onProgress = { idx1, total -> pageProgress = idx1 to total }
            )

            // 3) Set session and go to preview
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            PdfSession.lastPdfFile = outFile
            PdfSession.lastPdfUri = uri

            stage = Stage.Done
            onDoneOpenPreview()
        }.onFailure { e ->
            stage = Stage.Error
            errorText = e.localizedMessage ?: "PDF export failed"
        }
    }

    // UI: minimal blocking screen
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (stage) {
            Stage.Building -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.report_export_building),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Stage.Rendering -> {
                CircularProgressIndicator()
                val (cur, total) = pageProgress
                Text(
                    text = stringResource(R.string.report_export_rendering, cur, total),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Stage.Done -> {
                Text(
                    text = stringResource(R.string.report_export_done),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Stage.Error -> {
                Text(
                    text = errorText ?: stringResource(R.string.report_export_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                // Back behavior left to your nav (onBack)
            }
        }
    }
}

private enum class Stage { Building, Rendering, Done, Error }

/**
 * Core: render each ReportPageUi to bitmap using a ComposeView ATTACHED to the current window.
 * This avoids "Cannot locate windowRecomposer".
 */
private suspend fun exportDocumentToPdfStreaming(
    context: Context,
    activity: Activity,
    document: ReportDocumentUi,
    outFile: File,
    onProgress: (pageIndex1Based: Int, total: Int) -> Unit
) {
    // Attach temp ComposeView underneath main content
    val root = activity.findViewById<ViewGroup>(android.R.id.content)

    val pdf = PdfDocument()
    try {
        for (i in document.pages.indices) {
            val pageIndex1Based = i + 1
            onProgress(pageIndex1Based, document.pages.size)

            // Render 1 page bitmap (must be Main)
            val bmp = withContext(Dispatchers.Main) {
                renderPageBitmapAttached(
                    context = context,
                    root = root,
                    pageComposable = {
                        // IMPORTANT: reuse your deterministic A4 compose layout
                        ReportPage(page = document.pages[i])
                    }
                )
            }

            // Write to PDF on IO
            withContext(Dispatchers.IO) {
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageIndex1Based).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdf.finishPage(page)
            }

            bmp.recycle()
            // Let UI breathe between pages
            kotlinx.coroutines.yield()
        }

        withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { out -> pdf.writeTo(out) }
        }
    } finally {
        pdf.close()
    }
}

private fun renderPageBitmapAttached(
    context: Context,
    root: ViewGroup,
    pageComposable: @Composable () -> Unit
): Bitmap {
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { pageComposable() }
    }

    // Add underneath everything (index 0)
    root.addView(
        composeView,
        0,
        ViewGroup.LayoutParams(A4_WIDTH_PX_300, A4_HEIGHT_PX_300)
    )

    // Measure/layout to exact px
    val wSpec = View.MeasureSpec.makeMeasureSpec(A4_WIDTH_PX_300, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(A4_HEIGHT_PX_300, View.MeasureSpec.EXACTLY)
    composeView.measure(wSpec, hSpec)
    composeView.layout(0, 0, A4_WIDTH_PX_300, A4_HEIGHT_PX_300)

    // Draw to bitmap
    val bitmap = Bitmap.createBitmap(A4_WIDTH_PX_300, A4_HEIGHT_PX_300, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    composeView.draw(canvas)

    // Remove and dispose
    root.removeView(composeView)
    composeView.disposeComposition()

    return bitmap
}

private fun Context.findActivity(): Activity {
    var cur: Context = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    error("Activity not found from context")
}
