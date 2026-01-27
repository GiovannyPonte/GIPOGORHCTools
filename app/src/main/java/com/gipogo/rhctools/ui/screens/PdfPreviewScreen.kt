package com.gipogo.rhctools.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun PdfPreviewScreen(
    pdfUri: Uri,
    pdfFileForShare: File,
    onClose: () -> Unit
) {
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var currentPage by rememberSaveable { mutableStateOf(0) }

    val context = LocalContext.current

    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfUri) {
        try {
            error = null
            bitmaps = renderPdfToBitmaps(context, pdfUri)
            currentPage = 0
            zoom = 1f
        } catch (e: Exception) {
            error = context.getString(R.string.pdf_error_render, e.message ?: "")
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.pdf_preview_title),
        onBackToMenu = onClose,
        actions = {
            IconButton(onClick = { /* placeholder */ }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.common_more_options)
                )
            }
        }
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (bitmaps.isNotEmpty()) {
                currentPage = currentPage.coerceIn(0, bitmaps.lastIndex)
            }

            PdfControlsRow(
                hasPdf = bitmaps.isNotEmpty(),
                currentPage = currentPage,
                pageCount = bitmaps.size,
                zoom = zoom,
                onPrev = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                onNext = { currentPage = (currentPage + 1).coerceAtMost((bitmaps.size - 1).coerceAtLeast(0)) },
                onZoomOut = { zoom = (zoom - 0.25f).coerceIn(1f, 2f) },
                onZoomIn = { zoom = (zoom + 0.25f).coerceIn(1f, 2f) },
                onShare = { sharePdfFile(context, pdfFileForShare) },
                onOpen = { openInOtherApp(context, pdfFileForShare) }
            )

            if (error != null) {
                ElevatedCard {
                    Text(
                        text = error!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Column
            }

            if (bitmaps.isEmpty()) {
                Text(
                    text = stringResource(R.string.pdf_generating_preview),
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(220)) { it * dir } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(220)) { -it * dir } + fadeOut(tween(220)))
                        },
                        label = "pdf_page_transition"
                    ) { pageIndex ->
                        ZoomablePdfPagePan(
                            bitmap = bitmaps[pageIndex],
                            contentDescription = stringResource(R.string.pdf_page_cd, pageIndex + 1),
                            zoom = zoom,
                            stateKey = pageIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/* =========================
 * BARRA DE CONTROLES PDF
 * ========================= */
@Composable
private fun PdfControlsRow(
    hasPdf: Boolean,
    currentPage: Int,
    pageCount: Int,
    zoom: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Documento
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onShare, enabled = hasPdf) {
                Icon(Icons.Outlined.Share, stringResource(R.string.pdf_btn_share))
            }
            FilledTonalIconButton(onClick = onOpen, enabled = hasPdf) {
                Icon(Icons.Outlined.OpenInNew, stringResource(R.string.pdf_btn_open_other))
            }
        }

        Spacer(Modifier.weight(1f))

        // Navegación
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onPrev, enabled = hasPdf && currentPage > 0) {
                Icon(Icons.Outlined.NavigateBefore, stringResource(R.string.pdf_btn_prev_page))
            }
            Text(
                text = if (hasPdf) "${currentPage + 1}/$pageCount" else "0/0",
                style = MaterialTheme.typography.labelLarge
            )
            FilledTonalIconButton(onClick = onNext, enabled = hasPdf && currentPage < pageCount - 1) {
                Icon(Icons.Outlined.NavigateNext, stringResource(R.string.pdf_btn_next_page))
            }
        }

        Spacer(Modifier.weight(1f))

        // Zoom instrumental
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onZoomOut, enabled = zoom > 1f) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.pdf_btn_zoom_out))
                }
                Text(
                    text = "${(zoom * 100).toInt()}%",
                    modifier = Modifier.widthIn(min = 48.dp, max = 64.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onZoomIn, enabled = zoom < 2f) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.pdf_btn_zoom_in))
                }
            }
        }
    }
}

/* =========================
 * PAN + ZOOM DE PÁGINA
 * ========================= */
@Composable
private fun ZoomablePdfPagePan(
    bitmap: Bitmap,
    contentDescription: String,
    zoom: Float,
    stateKey: Int,
    modifier: Modifier = Modifier
) {
    var offsetX by rememberSaveable(stateKey) { mutableStateOf(0f) }
    var offsetY by rememberSaveable(stateKey) { mutableStateOf(0f) }

    var boxW by remember { mutableStateOf(0) }
    var boxH by remember { mutableStateOf(0) }

    LaunchedEffect(zoom) {
        if (zoom <= 1f) {
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun clampOffsets(x: Float, y: Float): Pair<Float, Float> {
        val maxX = max(0f, (bitmap.width * zoom - boxW) / 2f)
        val maxY = max(0f, (bitmap.height * zoom - boxH) / 2f)
        return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
    }

    Box(
        modifier = modifier
            .onSizeChanged {
                boxW = it.width
                boxH = it.height
                val (cx, cy) = clampOffsets(offsetX, offsetY)
                offsetX = cx
                offsetY = cy
            }
            .then(
                if (zoom > 1f) {
                    Modifier.pointerInput(zoom, stateKey) {
                        detectDragGestures { change, dragAmount ->
                            change.consumeAllChanges()
                            val (cx, cy) = clampOffsets(offsetX + dragAmount.x, offsetY + dragAmount.y)
                            offsetX = cx
                            offsetY = cy
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer(
                scaleX = zoom,
                scaleY = zoom,
                translationX = offsetX,
                translationY = offsetY
            )
        )
    }
}

/* =========================
 * PDF → BITMAPS
 * ========================= */
private suspend fun renderPdfToBitmaps(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
    val pfd: ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException(context.getString(R.string.pdf_error_open_file))

    pfd.use { parcel ->
        PdfRenderer(parcel).use { renderer ->
            val pages = mutableListOf<Bitmap>()
            val targetWidthPx = 1400

            repeat(renderer.pageCount) { i ->
                renderer.openPage(i).use { page ->
                    val scale = targetWidthPx.toFloat() / page.width.toFloat()
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidthPx, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bmp)
                }
            }
            pages
        }
    }
}

private fun sharePdfFile(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    context.startActivity(
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}

private fun openInOtherApp(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}
