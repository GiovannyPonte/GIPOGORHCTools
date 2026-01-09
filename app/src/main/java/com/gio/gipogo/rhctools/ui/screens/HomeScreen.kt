package com.gipogo.rhctools.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.gipogo.rhctools.R
import com.gipogo.rhctools.report.PdfReportGenerator
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.ui.navigation.Destinations
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {

    val hasAny by ReportStore.hasAnyResults.collectAsState(initial = false)
    val context = androidx.compose.ui.platform.LocalContext.current

    val pdfAppName = stringResource(R.string.pdf_app_name)


    var showPrepDialog by remember { mutableStateOf(false) }

    // --- Modal fórmulas ---
    var showFormulaDialog by remember { mutableStateOf(false) }
    var formulaTitle by remember { mutableStateOf("") }
    var formulaBody by remember { mutableStateOf("") }

    // --- Modal fórmulas (ayuda por card) ---
    var showFormulaDialog2 by remember { mutableStateOf(false) }
    var formulaTitle2 by remember { mutableStateOf("") }
    var formulaBody2 by remember { mutableStateOf("") }

    // Colors
    val surface = MaterialTheme.colorScheme.surface
    val containerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val containerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // --- Sección: Cálculos ---
            item {
                Text(
                    stringResource(R.string.home_section_calculations),
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurfaceVariant
                )
            }

            item {
                ToolRow(
                    badge = "CO",
                    title = stringResource(R.string.home_tool_fick_title),
                    subtitle = stringResource(R.string.home_tool_fick_subtitle),
                    helpText = stringResource(R.string.home_help_fick),
                    onHelpClick = { t, b ->
                        formulaTitle2 = t
                        formulaBody2 = b
                        showFormulaDialog2 = true
                    },
                    onClick = { navController.navigate(Destinations.Fick.route) }
                )
            }

            item {
                ToolRow(
                    badge = "SVR",
                    title = stringResource(R.string.home_tool_svr_title),
                    subtitle = stringResource(R.string.home_tool_svr_subtitle),
                    helpText = stringResource(R.string.home_help_svr),
                    onHelpClick = { t, b ->
                        formulaTitle2 = t
                        formulaBody2 = b
                        showFormulaDialog2 = true
                    },
                    onClick = { navController.navigate(Destinations.Resistances.route) }
                )
            }

            item {
                ToolRow(
                    badge = "CPO",
                    title = stringResource(R.string.home_tool_cpo_title),
                    subtitle = stringResource(R.string.home_tool_cpo_subtitle),
                    helpText = stringResource(R.string.home_help_cpo),
                    onHelpClick = { t, b ->
                        formulaTitle2 = t
                        formulaBody2 = b
                        showFormulaDialog2 = true
                    },
                    onClick = { navController.navigate(Destinations.Cpo.route) }
                )
            }

            item {
                ToolRow(
                    badge = "PAPi",
                    title = stringResource(R.string.home_tool_papi_title),
                    subtitle = stringResource(R.string.home_tool_papi_subtitle),
                    helpText = stringResource(R.string.home_help_papi),
                    onHelpClick = { t, b ->
                        formulaTitle2 = t
                        formulaBody2 = b
                        showFormulaDialog2 = true
                    },
                    onClick = { navController.navigate(Destinations.Papi.route) }
                )
            }

            // --- Sección: Reporte ---
            item {
                Text(
                    stringResource(R.string.home_section_report),
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (hasAny) MaterialTheme.colorScheme.primaryContainer else containerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.home_report_pdf_title),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            if (hasAny)
                                stringResource(R.string.home_report_ready)
                            else
                                stringResource(R.string.home_report_need_calc),
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = hasAny,
                                onClick = {
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
                                }
                            ) { Text(stringResource(R.string.home_btn_open_pdf)) }

                            OutlinedButton(
                                enabled = hasAny,
                                onClick = {
                                    ReportStore.clear()
                                    com.gipogo.rhctools.reset.AppResetBus.resetAll()
                                }
                            ) { Text(stringResource(R.string.home_btn_reset)) }
                        }
                    }
                }
            }

            // --- Preparación rápida (al final) ---
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = containerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.home_prep_quick_title),
                            style = MaterialTheme.typography.titleSmall
                        )

                        Text(
                            stringResource(R.string.home_prep_quick_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )

                        TextButton(onClick = { showPrepDialog = true }) {
                            Text(stringResource(R.string.home_btn_see_more))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(6.dp)) }
        }
    }

    // --- Modal preparación completa ---
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

    // --- Modal fórmulas por medición ---
    if (showFormulaDialog2) {
        AlertDialog(
            onDismissRequest = { showFormulaDialog2 = false },
            title = { Text(formulaTitle2) },
            text = { Text(formulaBody2, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showFormulaDialog2 = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }
}

@Composable
private fun ToolRow(
    badge: String,
    title: String,
    subtitle: String,
    helpText: String,
    onHelpClick: (title: String, body: String) -> Unit,
    onClick: () -> Unit
) {
    val containerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = containerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(primary.copy(alpha = 0.12f))
                    .clickable { onHelpClick(title, helpText) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "?",
                    color = primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
