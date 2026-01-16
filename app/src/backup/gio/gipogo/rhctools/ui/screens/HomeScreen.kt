package com.gio.gipogo.rhctools.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.gio.gipogo.rhctools.R
import com.gio.gipogo.rhctools.report.PdfReportGenerator
import com.gio.gipogo.rhctools.report.PdfSession
import com.gio.gipogo.rhctools.report.ReportStore
import com.gio.gipogo.rhctools.ui.components.GipogoTopBar
import com.gio.gipogo.rhctools.ui.components.HomeToolCard
import com.gio.gipogo.rhctools.ui.components.LockedReportCard
import com.gio.gipogo.rhctools.ui.components.QuickPrepCard
import com.gio.gipogo.rhctools.ui.components.SectionHeader
import com.gio.gipogo.rhctools.ui.navigation.Destinations
import com.gio.gipogo.rhctools.ui.theme.AccentCO
import com.gio.gipogo.rhctools.ui.theme.AccentCPO
import com.gio.gipogo.rhctools.ui.theme.AccentPAPI
import com.gio.gipogo.rhctools.ui.theme.AccentSVR
import java.io.File
import java.io.FileOutputStream

@Composable
fun HomeScreen(navController: NavController) {

    val hasAny by ReportStore.hasAnyResults.collectAsState(initial = false)
    val context = LocalContext.current
    val pdfAppName = stringResource(R.string.pdf_app_name)

    var showPrepDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var helpTitle by remember { mutableStateOf("") }
    var helpBody by remember { mutableStateOf("") }

    // ✅ Precalcular strings (composable context)
    val fickTitle = stringResource(R.string.home_tool_fick_title)
    val fickSubtitle = stringResource(R.string.home_tool_fick_subtitle)
    val fickHelp = stringResource(R.string.home_help_fick)

    val svrTitle = stringResource(R.string.home_tool_svr_title)
    val svrSubtitle = stringResource(R.string.home_tool_svr_subtitle)
    val svrHelp = stringResource(R.string.home_help_svr)

    val cpoTitle = stringResource(R.string.home_tool_cpo_title)
    val cpoSubtitle = stringResource(R.string.home_tool_cpo_subtitle)
    val cpoHelp = stringResource(R.string.home_help_cpo)

    val papiTitle = stringResource(R.string.home_tool_papi_title)
    val papiSubtitle = stringResource(R.string.home_tool_papi_subtitle)
    val papiHelp = stringResource(R.string.home_help_papi)

    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            GipogoTopBar(
                title = "GIPOGO RHC Tools",
                subtitle = "Hemodinámica rápida (RHC)",
                rightGlyph = "👤",
                onRightClick = { /* opcional */ }
            )
        },
        containerColor = cs.background
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 18.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item { SectionHeader(title = "Cálculos", trailingText = "4 HERRAMIENTAS") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = "CO",
                            title = fickTitle,
                            subtitle = fickSubtitle,
                            accent = AccentCO,
                            onCardClick = { navController.navigate(Destinations.Fick.route) },
                            onInfoClick = {
                                // ✅ ya NO llamas composables aquí
                                helpTitle = fickTitle
                                helpBody = fickHelp
                                showHelpDialog = true
                            }
                        )

                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = "SVR",
                            title = svrTitle,
                            subtitle = svrSubtitle,
                            accent = AccentSVR,
                            onCardClick = { navController.navigate(Destinations.Resistances.route) },
                            onInfoClick = {
                                helpTitle = svrTitle
                                helpBody = svrHelp
                                showHelpDialog = true
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = "CPO",
                            title = cpoTitle,
                            subtitle = cpoSubtitle,
                            accent = AccentCPO,
                            onCardClick = { navController.navigate(Destinations.Cpo.route) },
                            onInfoClick = {
                                helpTitle = cpoTitle
                                helpBody = cpoHelp
                                showHelpDialog = true
                            }
                        )

                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = "PAPI",
                            title = papiTitle,
                            subtitle = papiSubtitle,
                            accent = AccentPAPI,
                            onCardClick = { navController.navigate(Destinations.Papi.route) },
                            onInfoClick = {
                                helpTitle = papiTitle
                                helpBody = papiHelp
                                showHelpDialog = true
                            }
                        )
                    }
                }
            }

            item { SectionHeader(title = "Reporte") }

            item {
                if (!hasAny) {
                    LockedReportCard()
                } else {
                    ReportReadyCard(
                        pdfAppName = pdfAppName,
                        onOpenPdf = {
                            val file = File(context.cacheDir, "GIPOGO_RHC_Report.pdf")
                            FileOutputStream(file).use { os ->
                                PdfReportGenerator.writePdf(
                                    context = context,
                                    outputStream = os,
                                    appName = pdfAppName,
                                    entries = ReportStore.snapshot(),
                                    nowMillis = System.currentTimeMillis()
                                )
                            }

                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )

                            PdfSession.lastPdfFile = file
                            PdfSession.lastPdfUri = uri

                            navController.navigate(Destinations.PdfPreview.route)
                        },
                        onReset = {
                            ReportStore.clear()
                            com.gio.gipogo.rhctools.reset.AppResetBus.resetAll()
                        }
                    )
                }
            }

            item { SectionHeader(title = "Preparación rápida") }

            item { QuickPrepCard(onClick = { showPrepDialog = true }) }
        }
    }

    if (showPrepDialog) {
        AlertDialog(
            onDismissRequest = { showPrepDialog = false },
            title = { Text(stringResource(R.string.home_dialog_prep_title)) },
            text = { Text(stringResource(R.string.home_prep_full), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showPrepDialog = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(helpTitle) },
            text = { Text(helpBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }
}

@Composable
private fun ReportReadyCard(
    pdfAppName: String,
    onOpenPdf: () -> Unit,
    onReset: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.home_report_pdf_title),
                color = cs.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.home_report_ready),
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenPdf) { Text(stringResource(R.string.home_btn_open_pdf)) }
                OutlinedButton(onClick = onReset) { Text(stringResource(R.string.home_btn_reset)) }
            }

            Text(
                text = "Tip: también puedes abrirlo desde Reportes.",
                color = cs.primary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}