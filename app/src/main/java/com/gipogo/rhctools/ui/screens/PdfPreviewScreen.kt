package com.gipogo.rhctools.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

@Composable
fun PdfPreviewScreen(
    pdfUri: Uri,
    pdfFileForShare: File,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    val context = LocalContext.current
    val resources = LocalResources.current

    var document by remember { mutableStateOf<PdfPreviewDocument?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderedPageIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestDocument by rememberUpdatedState(document)
    val latestBitmap by rememberUpdatedState(pageBitmap)

    LaunchedEffect(pdfUri) {
        try {
            error = null
            pageBitmap?.recycle()
            pageBitmap = null
            renderedPageIndex = null
            document?.close()
            document = null
            pageCount = 0

            val openedDocument = openPdfPreviewDocument(context, pdfUri)
            document = openedDocument
            pageCount = openedDocument.pageCount
            currentPage = 0
            zoom = 1f
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = resources.getString(R.string.pdf_error_render)
        }
    }

    LaunchedEffect(document, currentPage) {
        val currentDocument = document ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect

        try {
            val renderedPage = renderPdfPageBitmap(currentDocument, currentPage)
            val previous = pageBitmap
            pageBitmap = renderedPage
            renderedPageIndex = currentPage
            previous?.recycle()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = resources.getString(R.string.pdf_error_render)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestBitmap?.recycle()
            latestDocument?.close()
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
                .testTag("pdf_preview_screen")
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (pageCount > 0) {
                currentPage = currentPage.coerceIn(0, pageCount - 1)
            }

            PdfControlsRow(
                hasPdf = pageCount > 0,
                currentPage = currentPage,
                pageCount = pageCount,
                zoom = zoom,
                onPrev = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                onNext = { currentPage = (currentPage + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) },
                onZoomOut = { zoom = (zoom - 0.25f).coerceIn(1f, 2f) },
                onZoomIn = { zoom = (zoom + 0.25f).coerceIn(1f, 2f) },
                onShare = {
                    sharePdfFile(context, pdfFileForShare).onFailure {
                        error = resources.getString(R.string.pdf_external_app_unavailable)
                    }
                },
                onOpen = {
                    openInOtherApp(context, pdfFileForShare).onFailure {
                        error = resources.getString(R.string.pdf_external_app_unavailable)
                    }
                }
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

            if (pageBitmap == null || renderedPageIndex != currentPage) {
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
                            bitmap = requireNotNull(pageBitmap),
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
            FilledTonalIconButton(
                onClick = onShare,
                enabled = hasPdf,
                modifier = Modifier.testTag("pdf_preview_share_button")
            ) {
                Icon(Icons.Outlined.Share, stringResource(R.string.pdf_btn_share))
            }
            FilledTonalIconButton(
                onClick = onOpen,
                enabled = hasPdf,
                modifier = Modifier.testTag("pdf_preview_open_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.pdf_btn_open_other))
            }
        }

        Spacer(Modifier.weight(1f))

        // Navegación
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onPrev,
                enabled = hasPdf && currentPage > 0,
                modifier = Modifier.testTag("pdf_preview_prev_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.NavigateBefore, stringResource(R.string.pdf_btn_prev_page))
            }
            Text(
                text = if (hasPdf) "${currentPage + 1}/$pageCount" else "0/0",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("pdf_preview_page_indicator")
            )
            FilledTonalIconButton(
                onClick = onNext,
                enabled = hasPdf && currentPage < pageCount - 1,
                modifier = Modifier.testTag("pdf_preview_next_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.NavigateNext, stringResource(R.string.pdf_btn_next_page))
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
                IconButton(
                    onClick = onZoomOut,
                    enabled = zoom > 1f,
                    modifier = Modifier.testTag("pdf_preview_zoom_out_button")
                ) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.pdf_btn_zoom_out))
                }
                Text(
                    text = "${(zoom * 100).toInt()}%",
                    modifier = Modifier.widthIn(min = 48.dp, max = 64.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(
                    onClick = onZoomIn,
                    enabled = zoom < 2f,
                    modifier = Modifier.testTag("pdf_preview_zoom_in_button")
                ) {
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
    var offsetX by rememberSaveable(stateKey) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(stateKey) { mutableFloatStateOf(0f) }

    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }

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

private class PdfPreviewDocument(
    private val parcelFileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val scale = targetWidthPx.toFloat() / page.width.toFloat()
            val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    fun close() {
        renderer.close()
        parcelFileDescriptor.close()
    }
}

private suspend fun openPdfPreviewDocument(context: Context, uri: Uri): PdfPreviewDocument =
    withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException(context.getString(R.string.pdf_error_open_file))
        PdfPreviewDocument(
            parcelFileDescriptor = pfd,
            renderer = PdfRenderer(pfd)
        )
    }

private suspend fun renderPdfPageBitmap(
    document: PdfPreviewDocument,
    pageIndex: Int,
    targetWidthPx: Int = 1400
): Bitmap = withContext(Dispatchers.IO) {
    document.renderPage(pageIndex, targetWidthPx)
}

private fun sharePdfFile(context: Context, file: File): Result<Unit> = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(sendIntent, context.getString(R.string.pdf_chooser_share))
    )
}

private fun openInOtherApp(context: Context, file: File): Result<Unit> = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(viewIntent, context.getString(R.string.pdf_chooser_open_with))
    )
}
