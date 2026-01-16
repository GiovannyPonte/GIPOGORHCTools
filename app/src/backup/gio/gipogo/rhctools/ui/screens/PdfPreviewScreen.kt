package com.gipogo.rhctools.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfPreviewScreen(
    pdfUri: Uri,
    pdfFileForShare: File,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val defaultName = stringResource(R.string.pdf_default_filename)

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null) {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                pdfFileForShare.inputStream().use { input -> input.copyTo(out) }
            }
        }
    }

    LaunchedEffect(pdfUri) {
        error = null
        bitmaps = emptyList()

        try {
            bitmaps = renderPdfToBitmaps(context, pdfUri)
        } catch (e: Exception) {
            error = context.getString(R.string.pdf_error_render, e.message ?: "")
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.pdf_preview_title),
        onBackToMenu = onClose
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClose
                ) { Text(stringResource(R.string.pdf_btn_close)) }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { saveLauncher.launch(defaultName) }
                ) { Text(stringResource(R.string.pdf_btn_save)) }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { sharePdfFile(context, pdfFileForShare) }
                ) { Text(stringResource(R.string.pdf_btn_share)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { openInOtherApp(context, pdfFileForShare) }
                ) { Text(stringResource(R.string.pdf_btn_open_other)) }
            }

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
                Text(stringResource(R.string.pdf_generating_preview), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(bitmaps) { index, bmp ->
                    ElevatedCard {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                stringResource(R.string.pdf_page, index + 1),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.pdf_page_cd, index + 1),
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
}

private suspend fun renderPdfToBitmaps(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
    val pfd: ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException(context.getString(R.string.pdf_error_open_file))

    pfd.use { parcel ->
        PdfRenderer(parcel).use { renderer ->
            val pages = mutableListOf<Bitmap>()
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = 2
                    val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
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
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.pdf_chooser_share)))
}

private fun openInOtherApp(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.pdf_chooser_open_with)))
}
