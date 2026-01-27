package com.gipogo.rhctools.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
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
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.report.PdfReportGenerator
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.reset.AppResetBus
import com.gipogo.rhctools.ui.components.GipogoTopBar
import com.gipogo.rhctools.ui.components.HomeToolCard
import com.gipogo.rhctools.ui.components.LockedReportCard
import com.gipogo.rhctools.ui.components.QuickPrepCard
import com.gipogo.rhctools.ui.components.SectionHeader
import com.gipogo.rhctools.ui.components.StudySummaryCard
import com.gipogo.rhctools.ui.navigation.Destinations
import com.gipogo.rhctools.ui.reports.RhcVisualReports
import com.gipogo.rhctools.ui.theme.AccentCO
import com.gipogo.rhctools.ui.theme.AccentCPO
import com.gipogo.rhctools.ui.theme.AccentPAPI
import com.gipogo.rhctools.ui.theme.AccentSVR
import com.gipogo.rhctools.workshop.WorkshopMode
import com.gipogo.rhctools.workshop.WorkshopPrefill
import com.gipogo.rhctools.workshop.WorkshopPrefillStore
import com.gipogo.rhctools.workshop.WorkshopSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.withFrameNanos
import com.gipogo.rhctools.ui.navigation.Destinations.Companion.NAV_FLAG_SCROLL_TO_EXIT


@Composable
fun HomeCalculatorScreen(navController: NavController) {

    val hasAny by ReportStore.hasAnyResults.collectAsState(initial = false)
    val context = LocalContext.current
    val pdfAppName = stringResource(R.string.pdf_app_name)

    val workshopCtx by WorkshopSession.context.collectAsState()
    val isQuickMode = workshopCtx.mode == WorkshopMode.QUICK
    val isPatientMode = workshopCtx.mode == WorkshopMode.PATIENT_STUDY

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val allDone = isWorkshopCompleteForExit()
    val bringFinishIntoView = remember { BringIntoViewRequester() }
    val entry = navController.currentBackStackEntry





    var autoScrolledToFinish by rememberSaveable { mutableStateOf(false) }




    // Prefill (se queda igual por ahora para no romper nada).
    LaunchedEffect(workshopCtx.mode, workshopCtx.patientId) {
        if (workshopCtx.mode == WorkshopMode.PATIENT_STUDY && !workshopCtx.patientId.isNullOrBlank()) {
            val pid = workshopCtx.patientId!!

            val p = withContext(Dispatchers.IO) {
                DbProvider.get(context).patientDao().getById(pid)
            }

            if (p != null) {
                WorkshopPrefillStore.set(
                    WorkshopPrefill(
                        weightKg = p.weightKg,
                        heightCm = p.heightCm,
                        sex = p.sex,
                        birthDateMillis = p.birthDateMillis
                    )
                )
            } else {
                WorkshopPrefillStore.clear()
            }
        } else {
            WorkshopPrefillStore.clear()
        }
    }

    var showPrepDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var helpTitle by remember { mutableStateOf("") }
    var helpBody by remember { mutableStateOf("") }
    var showExitConfirm by remember { mutableStateOf(false) }
    fun handleExitAttempt() {
        if (!isPatientMode) {
            navController.popBackStack()
            return
        }

        val pid = workshopCtx.patientId
        val sid = workshopCtx.studyId
        val hasIds = !pid.isNullOrBlank() && !sid.isNullOrBlank()

        // Si por alguna razón no hay IDs válidos, salida segura.
        if (!hasIds) {
            WorkshopSession.clear()
            ReportStore.clear()
            AppResetBus.resetAll()
            navController.navigate(Destinations.Patients.route) { launchSingleTop = true }
            return
        }

        val done = ReportStore.entries.value.keys
        val hasAny = done.isNotEmpty()
        val allDone = isWorkshopCompleteForExit()

        when {
            // Regla A1: VACÍO -> NO guardar, borrar estudio y volver a PatientDetail
            !hasAny -> {
                showExitConfirm = false
                // reutilizamos el mismo diálogo state, pero aquí salimos directo
                scope.launch {
                    discardStudyAndExit(
                        context = context,
                        patientId = pid!!,
                        studyId = sid!!,
                        navController = navController
                    )
                }
            }

            // Regla A2: COMPLETO -> guardar y volver a StudyDetail
            allDone -> {
                showExitConfirm = false
                scope.launch {
                    saveStudyAndExit(
                        context = context,
                        patientId = pid!!,
                        studyId = sid!!,
                        navController = navController
                    )
                }
            }

            // Regla A3: INCOMPLETO con progreso -> preguntar Guardar/Descartar
            else -> {
                showExitConfirm = true
            }
        }
    }


    val cs = MaterialTheme.colorScheme

    fun openCalc(leafRoute: String) {
        navController.navigate(leafRoute) { launchSingleTop = true }
    }

    // Badges
    val badgeCo = stringResource(R.string.home_badge_co)
    val badgeSvr = stringResource(R.string.home_badge_svr)
    val badgeCpo = stringResource(R.string.home_badge_cpo)
    val badgePapi = stringResource(R.string.home_badge_papi)
    val badgePvr = stringResource(R.string.home_badge_pvr)

    // Fick
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

    // PVR
    val pvrTitle = stringResource(R.string.home_tool_pvr_title)
    val pvrSubtitle = stringResource(R.string.home_tool_pvr_subtitle)
    val pvrHelp = stringResource(R.string.home_help_pvr)

    val isComplete = rememberWorkshopComplete()

    BackHandler(enabled = true) {
        handleExitAttempt()
    }

    // 1) Obtener el entry del graph padre (donde realmente guardaste el flag)
    val calcGraphEntry = remember(navController) {
        navController.getBackStackEntry(Destinations.CalcGraph.route)
    }

// 2) Leerlo de forma reactiva (StateFlow)
    val shouldScrollToExit by calcGraphEntry.savedStateHandle
        .getStateFlow(Destinations.NAV_FLAG_SCROLL_TO_EXIT, false)
        .collectAsState()

// 3) Ejecutar scroll one-shot y consumir el flag
    LaunchedEffect(shouldScrollToExit, isPatientMode) {
        if (!shouldScrollToExit || !isPatientMode) return@LaunchedEffect

        // Espera a que LazyColumn tenga items medidos (layoutInfo válido)
        // (un frame suele bastar, pero usamos dos para hacerlo más robusto)
        withFrameNanos { }
        withFrameNanos { }

        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }

        // Ahora sí: asegura que el botón quede visible (si quedó justo cortado)
        bringFinishIntoView.bringIntoView()

        // Consume flag (one-shot)
        calcGraphEntry.savedStateHandle.set(Destinations.NAV_FLAG_SCROLL_TO_EXIT, false)
    }




    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoTopBar(
                title = stringResource(R.string.home_topbar_title),
                subtitle = stringResource(R.string.home_topbar_subtitle),
                showBack = true,
                onBack = {
                    handleExitAttempt()
                }
                ,
                rightGlyph = "👤",
                onRightClick = { /* opcional */ }
            )
        },
        containerColor = cs.background
    ) { padding ->

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 18.dp, bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // -------- MODE BANNER (NO PII) --------
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.home_workshop_entry_hint),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }


            // -------------------- CALCS --------------------
            item {
                SectionHeader(
                    title = stringResource(R.string.home_section_calcs_title),
                    trailingText = stringResource(R.string.home_section_calcs_trailing)
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = cs.surface,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, AccentCO.copy(alpha = 0.65f))
                ) {
                    HomeToolCard(
                        modifier = Modifier.fillMaxWidth(),
                        badge = badgeCo,
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
            }



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

                        HomeToolCard(
                            modifier = Modifier.weight(1f),
                            badge = badgePvr,
                            title = pvrTitle,
                            subtitle = pvrSubtitle,
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

            // -------------------- SUMMARY (computed) --------------------
            item {
                val groups = RhcVisualReports.collectAvailableGroups()
                groups.forEach { g ->
                    RhcVisualReports.SummaryGroupCard(group = g)
                }

            }

            // -------------------- PATIENT FLOW CARD (back/finish) --------------------
            if (isPatientMode) {
                item {
                    val pid = workshopCtx.patientId
                    val sid = workshopCtx.studyId
                    val canGoBack = !pid.isNullOrBlank() && !sid.isNullOrBlank()

                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()

                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isComplete) "Taller completo"
                                else "Taller en curso",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onBackground
                            )

                            Text(
                                text = if (isComplete)
                                    "Ya tienes los cálculos principales. Finaliza el taller y vuelve al detalle del estudio para revisar y generar el reporte."
                                else
                                    "Puedes volver al detalle del estudio en cualquier momento. El reporte se gestiona desde el detalle del estudio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    modifier = Modifier.bringIntoViewRequester(bringFinishIntoView),
                                    onClick = {
                                        val p = pid
                                        val s = sid

                                        if (canGoBack && !p.isNullOrBlank() && !s.isNullOrBlank()) {
                                            // ✅ Finaliza taller: limpia estado del taller antes de salir
                                            WorkshopSession.clear()
                                            com.gipogo.rhctools.report.ReportStore.clear()
                                            com.gipogo.rhctools.reset.AppResetBus.resetAll()

                                            // ✅ Ir al detalle del estudio recién creado
                                            navController.navigate(Destinations.StudyDetail.route(p, s)) {
                                                launchSingleTop = true
                                            }
                                        } else {
                                            // Fallback seguro: no hay IDs válidos o el taller no está listo
                                            WorkshopSession.clear()
                                            com.gipogo.rhctools.report.ReportStore.clear()
                                            com.gipogo.rhctools.reset.AppResetBus.resetAll()
                                            navController.navigate(Destinations.Patients.route) { launchSingleTop = true }
                                        }
                                    }
                                    ,
                                    enabled = canGoBack
                                ) {
                                    Text(if (isComplete) "Finalizar taller y volver" else "Volver al estudio")
                                }

                                OutlinedButton(
                                    onClick = { /* seguir en taller, no hace nada */ }
                                ) {
                                    Text("Seguir en taller")
                                }
                            }
                        }
                    }
                }
            }

            // -------------------- REPORT --------------------
            // ✅ Regla: en modo paciente NO se exporta aquí.
            if (isQuickMode) {
                item { SectionHeader(title = stringResource(R.string.home_section_report_title)) }

                item {
                    if (!hasAny) {
                        LockedReportCard()
                    } else {
                        ReportReadyCard(
                            pdfAppName = pdfAppName,
                            isQuickMode = true,
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
                                AppResetBus.resetAll()
                            }
                        )
                    }
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
    if (showExitConfirm) {
        val pid = workshopCtx.patientId
        val sid = workshopCtx.studyId
        val hasIds = !pid.isNullOrBlank() && !sid.isNullOrBlank()

        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.workshop_exit_save_or_discard_title)) },
            text = { Text(stringResource(R.string.workshop_exit_save_or_discard_body)) },
            confirmButton = {
                TextButton(
                    enabled = hasIds,
                    onClick = {
                        showExitConfirm = false
                        scope.launch {
                            saveStudyAndExit(
                                context = context,
                                patientId = pid!!,
                                studyId = sid!!,
                                navController = navController
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.workshop_exit_save_incomplete_confirm))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = hasIds,
                        onClick = {
                            showExitConfirm = false
                            scope.launch {
                                discardStudyAndExit(
                                    context = context,
                                    patientId = pid!!,
                                    studyId = sid!!,
                                    navController = navController
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.workshop_exit_discard_confirm))
                    }

                    TextButton(onClick = { showExitConfirm = false }) {
                        Text(stringResource(R.string.workshop_exit_cancel))
                    }
                }
            }
        )
    }



}
@Composable
private fun ReportReadyCard(
    pdfAppName: String,
    isQuickMode: Boolean,
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
                text = if (isQuickMode) stringResource(R.string.home_report_pdf_title_quick)
                else stringResource(R.string.home_report_pdf_title),
                color = cs.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (isQuickMode) stringResource(R.string.home_report_ready_quick)
                else stringResource(R.string.home_report_ready),
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

@Composable
private fun rememberWorkshopComplete(): Boolean {
    val entries by ReportStore.entries.collectAsState()

    fun hasD(key: String): Boolean =
        ReportStore.latestValueDoubleByKey(key) != null

    val hasCO = hasD(SharedKeys.CO_LMIN)
    val hasCI = hasD(SharedKeys.CI_LMIN_M2)

    val hasSVR = hasD(SharedKeys.SVR_WOOD) || hasD(SharedKeys.SVR_DYN)
    val hasPVR = hasD(SharedKeys.PVR_WOOD) || hasD(SharedKeys.PVR_DYN)

    val hasCPO = hasD(SharedKeys.CPO_W) || hasD(SharedKeys.CPI_W_M2)
    val hasPAPI = hasD(SharedKeys.PAPI)

    return hasCO && hasCI && hasSVR && hasPVR && hasCPO && hasPAPI
}
private fun isWorkshopCompleteForExit(): Boolean {
    val done = ReportStore.entries.value.keys
    val required = setOf(
        com.gipogo.rhctools.report.CalcType.FICK,
        com.gipogo.rhctools.report.CalcType.SVR,
        com.gipogo.rhctools.report.CalcType.CPO,
        com.gipogo.rhctools.report.CalcType.PAPI,
        com.gipogo.rhctools.report.CalcType.PVR
    )
    return done.containsAll(required)
}
private suspend fun saveStudyAndExit(
    context: android.content.Context,
    patientId: String,
    studyId: String,
    navController: NavController
) {
    // Flush antes de limpiar el ReportStore (si limpias primero, pierdes datos).
    com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave.flushNow(
        context = context,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    )

    // Limpieza de sesión
    WorkshopSession.clear()
    ReportStore.clear()
    AppResetBus.resetAll()
    com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave.clearCoMethod()

    // Navegación determinista (A)
    navController.navigate(Destinations.StudyDetail.route(patientId, studyId)) {
        launchSingleTop = true
    }
}

private suspend fun discardStudyAndExit(
    context: android.content.Context,
    patientId: String,
    studyId: String,
    navController: NavController
) {
    // Borrado en DB (Study -> cascade a rhc_study_data por FK)
    withContext(Dispatchers.IO) {
        val db = DbProvider.get(context.applicationContext)
        db.rhcStudyDao().deleteByStudyId(studyId) // robusto
        db.studyDao().deleteById(studyId)
    }

    // Limpieza de sesión
    WorkshopSession.clear()
    ReportStore.clear()
    AppResetBus.resetAll()
    com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave.clearCoMethod()

    // Navegación determinista (A)
    navController.navigate(Destinations.PatientDetail.route(patientId)) {
        launchSingleTop = true
    }
}


