package com.gipogo.rhctools.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import com.gipogo.rhctools.R
import com.gipogo.rhctools.report.PdfReportGenerator
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.ui.components.GipogoTopBar
import com.gipogo.rhctools.ui.components.HomeToolCard
import com.gipogo.rhctools.ui.components.LockedReportCard
import com.gipogo.rhctools.ui.components.QuickPrepCard
import com.gipogo.rhctools.ui.components.SectionHeader
import com.gipogo.rhctools.ui.navigation.Destinations
import com.gipogo.rhctools.ui.theme.AccentCO
import com.gipogo.rhctools.ui.theme.AccentCPO
import com.gipogo.rhctools.ui.theme.AccentPAPI
import com.gipogo.rhctools.ui.theme.AccentSVR
import java.io.File
import java.io.FileOutputStream
import androidx.navigation.compose.currentBackStackEntryAsState
import com.gipogo.rhctools.ui.navigation.CalcFlow


@Composable
fun HomeScreen(navController: NavController) {

    val hasAny by ReportStore.hasAnyResults.collectAsState(initial = false)
    val context = LocalContext.current
    val pdfAppName = stringResource(R.string.pdf_app_name)

    var showPrepDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var helpTitle by remember { mutableStateOf("") }
    var helpBody by remember { mutableStateOf("") }

    val cs = MaterialTheme.colorScheme

    // ✅ CLAVE: abrir calculadoras dentro del calc_graph para que el estado persista
    fun openCalc(leafRoute: String) {
        navController.navigate(leafRoute) {
            launchSingleTop = true
        }
    }

    // -------- Strings precalculados (evita recomposición ruidosa) --------

    // Badges (sin texto duro)
    val badgeCo = stringResource(R.string.home_badge_co)
    val badgeSvr = stringResource(R.string.home_badge_svr)
    val badgeCpo = stringResource(R.string.home_badge_cpo)
    val badgePapi = stringResource(R.string.home_badge_papi)
    val badgePvr = stringResource(R.string.home_badge_pvr)

    // Fick (Hero)
    val fickHeroBadge = stringResource(R.string.home_fick_hero_badge)
    val fickTitle = stringResource(R.string.home_tool_fick_title)
    val fickSubtitle = stringResource(R.string.home_tool_fick_subtitle)
    val fickHelp = stringResource(R.string.home_help_fick)

    // SVR
    val svrTitle = stringResource(R.string.home_tool_svr_title)
    val svrSubtitle = stringResource(R.string.home_tool_svr_subtitle)
    val svrHelp = stringResource(R.string.home_help_svr)

    // CPO
    val cpoTitle = stringResource(R.string.home_tool_cpo_title)
    val cpoSubtitle = stringResource(R.string.home_tool_cpo_subtitle)
    val cpoHelp = stringResource(R.string.home_help_cpo)

    // PAPI
    val papiTitle = stringResource(R.string.home_tool_papi_title)
    val papiSubtitle = stringResource(R.string.home_tool_papi_subtitle)
    val papiHelp = stringResource(R.string.home_help_papi)

    // PVR (nuevo en Home; navegación se mantiene dentro de ResistancesScreen)
    val pvrTitle = stringResource(R.string.home_tool_pvr_title)
    val pvrSubtitle = stringResource(R.string.home_tool_pvr_subtitle)
    val pvrHelp = stringResource(R.string.home_help_pvr)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoTopBar(
                title = stringResource(R.string.home_topbar_title),
                subtitle = stringResource(R.string.home_topbar_subtitle),
                rightGlyph = "👤",
                onRightClick = { /* opcional */ }
            )
        },
        containerColor = cs.background
    ) { padding ->

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 18.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // -------------------- CÁLCULOS --------------------
            item {
                SectionHeader(
                    title = stringResource(R.string.home_section_calcs_title),
                    trailingText = stringResource(R.string.home_section_calcs_trailing)
                )
            }

            // ✅ HERO (Fick) arriba, ancho completo (como tu referencia)
            item {
                HomeToolCard(
                    modifier = Modifier.fillMaxWidth(),
                    badge = badgeCo, // "CO"
                    title = fickTitle,
                    subtitle = fickSubtitle,
                    accent = AccentCO,
                    onCardClick = { openCalc(Destinations.Fick.route) },
                    onInfoClick = {
                        helpTitle = fickTitle
                        helpBody = fickHelp
                        showHelpDialog = true
                    }
                )
            }

            // ✅ Grid 2x2 debajo (SVR/CPO y PAPI/PVR)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = badgeSvr,
                            title = svrTitle,
                            subtitle = svrSubtitle,
                            accent = AccentSVR,
                            onCardClick = { openCalc(Destinations.Resistances.route) },
                            onInfoClick = {
                                helpTitle = svrTitle
                                helpBody = svrHelp
                                showHelpDialog = true
                            }
                        )

                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = badgeCpo,
                            title = cpoTitle,
                            subtitle = cpoSubtitle,
                            accent = AccentCPO,
                            onCardClick = { openCalc(Destinations.Cpo.route) },
                            onInfoClick = {
                                helpTitle = cpoTitle
                                helpBody = cpoHelp
                                showHelpDialog = true
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = badgePapi,
                            title = papiTitle,
                            subtitle = papiSubtitle,
                            accent = AccentPAPI,
                            onCardClick = { openCalc(Destinations.Papi.route) },
                            onInfoClick = {
                                helpTitle = papiTitle
                                helpBody = papiHelp
                                showHelpDialog = true
                            }
                        )

                        // PVR: por ahora navega al mismo "ResistancesScreen" (mantiene funcionalidad y no rompe rutas)
                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = badgePvr,
                            title = pvrTitle,
                            subtitle = pvrSubtitle,
                            // no tenemos AccentPVR todavía: usamos un acento consistente con el tema sin tocar otros ficheros
                            accent = cs.primary,
                            onCardClick = { openCalc(Destinations.Pvr.route) },
                            onInfoClick = {
                                helpTitle = pvrTitle
                                helpBody = pvrHelp
                                showHelpDialog = true
                            }
                        )
                    }
                }
            }

            // -------------------- REPORTE --------------------
            item { SectionHeader(title = stringResource(R.string.home_section_report_title)) }

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
                            com.gipogo.rhctools.reset.AppResetBus.resetAll()
                        }
                    )
                }
            }

            // -------------------- QUICK PREP --------------------
            item { SectionHeader(title = stringResource(R.string.home_section_quick_prep_title)) }
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
        modifier = Modifier.fillMaxWidth()
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
                text = stringResource(R.string.home_report_tip_open_from_reports),
                color = cs.primary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
