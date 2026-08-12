package com.gipogo.rhctools.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme
import java.io.File

class PdfPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra(EXTRA_PDF_FILE_PATH)
        val pdfFile = filePath?.let(::File)?.takeIf { it.exists() }
        if (pdfFile == null) {
            finish()
            return
        }

        val pdfUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PDF_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PDF_URI)
        }
            ?: FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                pdfFile
            )

        setContent {
            GipogoRhctoolsTheme {
                PdfPreviewScreen(
                    pdfUri = pdfUri,
                    pdfFileForShare = pdfFile,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PDF_FILE_PATH = "pdf_file_path"
        private const val EXTRA_PDF_URI = "pdf_uri"

        fun createIntent(
            context: Context,
            pdfFile: File,
            pdfUri: Uri
        ): Intent = Intent(context, PdfPreviewActivity::class.java).apply {
            putExtra(EXTRA_PDF_FILE_PATH, pdfFile.absolutePath)
            putExtra(EXTRA_PDF_URI, pdfUri)
        }
    }
}
