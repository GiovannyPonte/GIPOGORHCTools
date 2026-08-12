package com.gipogo.rhctools.reporting.compose

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.ui.components.DatabaseErrorDetails
import com.gipogo.rhctools.reporting.export.LongitudinalComposePdfExporter
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable
fun ReportRenderRoute(
    patientId: String,
    onDoneOpenPreview: (File, Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val dbResult = remember(appCtx) { DbProvider.getResult(appCtx) }
    if (dbResult is DbProvider.DbOpenResult.Failure) {
        DatabaseErrorDetails(
            diagnostic = dbResult.diagnostic,
            modifier = Modifier.fillMaxSize(),
            onBack = onBack
        )
        return
    }
    val db = (dbResult as DbProvider.DbOpenResult.Success).db
    val patientDao = remember(db) { db.patientDao() }
    val rhcStudyDao = remember(db) { db.rhcStudyDao() }

    var stage by remember { mutableStateOf<Stage>(Stage.Building) }
    var pageProgress by remember { mutableStateOf(0 to 0) } // (current, total)

    LaunchedEffect(patientId) {
        try {
            stage = Stage.Building

            stage = Stage.Rendering
            pageProgress = 0 to 0

            val result = LongitudinalComposePdfExporter.export(
                context = context,
                patientId = patientId,
                patientDao = patientDao,
                rhcStudyDao = rhcStudyDao,
                onProgress = { current, total -> pageProgress = current to total }
            )

            stage = Stage.Done
            onDoneOpenPreview(result.file, result.uri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            stage = Stage.Error
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
                    text = stringResource(R.string.report_export_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                // Back behavior left to your nav (onBack)
            }
        }
    }
}

private enum class Stage { Building, Rendering, Done, Error }
