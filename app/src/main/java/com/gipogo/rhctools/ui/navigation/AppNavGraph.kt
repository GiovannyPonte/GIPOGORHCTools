package com.gipogo.rhctools.ui.navigation

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gipogo.rhctools.R
import com.gipogo.rhctools.domain.BirthDateCodec
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.StudyClinicalPdfExport
import com.gipogo.rhctools.report.StudyClinicalPdfFormat
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.PdfReportGenerator
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.reporting.compose.ReportRenderRoute
import com.gipogo.rhctools.ui.screens.CpoScreen
import com.gipogo.rhctools.ui.screens.FickScreen
import com.gipogo.rhctools.ui.screens.HomeCalculatorScreen
import com.gipogo.rhctools.ui.screens.LastPatientUi
import com.gipogo.rhctools.ui.screens.HomeScreen
import com.gipogo.rhctools.ui.screens.PapiScreen
import com.gipogo.rhctools.ui.screens.PatientDetailRoute
import com.gipogo.rhctools.ui.screens.PatientEditScreen
import com.gipogo.rhctools.ui.screens.PatientsScreen
import com.gipogo.rhctools.ui.screens.PdfPreviewActivity
import com.gipogo.rhctools.ui.screens.PdfPreviewScreen
import com.gipogo.rhctools.ui.screens.PvrScreen
import com.gipogo.rhctools.ui.screens.ResistancesScreen
import com.gipogo.rhctools.ui.screens.StudyDetailRoute
import com.gipogo.rhctools.ui.screens.SettingsScreen
import com.gipogo.rhctools.ui.viewmodel.CpoViewModel
import com.gipogo.rhctools.ui.viewmodel.FickViewModel
import com.gipogo.rhctools.ui.viewmodel.PapiViewModel
import com.gipogo.rhctools.ui.viewmodel.PvrViewModel
import com.gipogo.rhctools.ui.viewmodel.ResistancesViewModel
import com.gipogo.rhctools.workshop.WorkshopSession
import com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave
import com.gipogo.rhctools.workshop.persistence.WorkshopRecoveryStore
import com.gipogo.rhctools.workshop.persistence.WorkshopStudyFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import com.gipogo.rhctools.report.ForresterPdfBlock
import com.gipogo.rhctools.report.SharedKeys


import com.gipogo.rhctools.data.db.DbProvider

/* --------------------------------------------------------- */
/* PDF helpers: una sola fuente de verdad para export 1 study */
/* --------------------------------------------------------- */

private const val PDF_EXPORT_TAG = "PdfExport"
private const val APP_NAV_GRAPH_TAG = "AppNavGraph"
private const val LAST_PATIENT_RETRY_DELAY_MS = 5_000L

private sealed interface PendingStudyPdfExport {
    val patientId: String

    data class Latest(
        override val patientId: String
    ) : PendingStudyPdfExport

    data class Selected(
        override val patientId: String,
        val studyId: String
    ) : PendingStudyPdfExport
}

private fun notifyPdfExportFailure(
    context: Context,
    error: Throwable
) {
    Log.e(PDF_EXPORT_TAG, "PDF export failed.", error)
    Toast.makeText(
        context.applicationContext,
        context.getString(R.string.report_export_failed),
        Toast.LENGTH_LONG
    ).show()
}

private suspend fun exportSingleStudyPdfAndOpenPreview(
    context: Context,
    patientId: String,
    studyId: String,
    format: StudyClinicalPdfFormat
) {
    val result = StudyClinicalPdfExport.exportStudyPdf(
        context = context,
        patientId = patientId,
        studyId = studyId,
        format = format
    )

    PdfSession.lastPdfFile = result.pdfFile
    PdfSession.lastPdfUri = result.pdfUri

    context.startActivity(
        PdfPreviewActivity.createIntent(
            context,
            result.pdfFile,
            result.pdfUri
        )
    )
}

private suspend fun exportLatestStudyPdfAndOpenPreview(
    context: Context,
    patientId: String,
    format: StudyClinicalPdfFormat
) {
    val result = StudyClinicalPdfExport.exportLatestStudyPdf(
        context = context,
        patientId = patientId,
        format = format
    )

    PdfSession.lastPdfFile = result.pdfFile
    PdfSession.lastPdfUri = result.pdfUri

    context.startActivity(
        PdfPreviewActivity.createIntent(
            context,
            result.pdfFile,
            result.pdfUri
        )
    )
}

/* ----------------------------- */
/* AppNavGraph                   */
/* ----------------------------- */

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {

    // ✅ Declarar aquí (NO dentro de lambdas)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingStudyPdfExport by remember { mutableStateOf<PendingStudyPdfExport?>(null) }

    // ✅ Autosave a nivel raíz
    LaunchedEffect(Unit) { WorkshopRhcAutosave.start(context) }

    // ✅ RunId actual del taller
    val workshopCtx by WorkshopSession.context.collectAsStateWithLifecycle()
    val runId = workshopCtx.workshopRunId
    val appContext = context.applicationContext

    // Si el proceso terminó durante un estudio, volver al snapshot seguro que
    // ya existe en Room. Nunca restaurar ReportStore vacío como sesión activa.
    LaunchedEffect(appContext, navController) {
        val pending = WorkshopRecoveryStore.read(appContext)
        pending ?: return@LaunchedEffect
        val db = when (val result = DbProvider.getResult(appContext)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> {
                Log.w(APP_NAV_GRAPH_TAG, "Recovery deferred because the database is unavailable")
                return@LaunchedEffect
            }
        }
        val validStudy = withContext(Dispatchers.IO) {
            db.studyDao().getById(pending.studyId)
                ?.takeIf { it.patientId == pending.patientId }
        }

        if (validStudy == null) {
            Log.w(APP_NAV_GRAPH_TAG, "Discarding stale process recovery marker")
            WorkshopRecoveryStore.clear(appContext)
            return@LaunchedEffect
        }

        WorkshopSession.clear()
        com.gipogo.rhctools.report.ReportStore.clear()
        com.gipogo.rhctools.reset.AppResetBus.resetAll()
        withContext(Dispatchers.Main.immediate) {
            // Esperar a que NavHost termine de instalar/restaurar su grafo evita
            // modificar el back stack concurrentemente durante un arranque lento.
            navController.currentBackStackEntryFlow.first()
            navController.navigate(
                Destinations.StudyDetail.route(pending.patientId, pending.studyId)
            ) {
                popUpTo(Destinations.CalcGraph.route) { inclusive = false }
                launchSingleTop = true
            }
        }
        WorkshopRecoveryStore.clear(appContext)
    }

    val lastPatientUi by produceState<LastPatientUi?>(initialValue = null, key1 = appContext) {
        while (true) {
            val db = when (val result = DbProvider.getResult(appContext)) {
                is DbProvider.DbOpenResult.Success -> result.db
                is DbProvider.DbOpenResult.Failure -> {
                    Log.w(
                        APP_NAV_GRAPH_TAG,
                        "Unable to load last patient shortcut. Retrying.",
                        result.error
                    )
                    value = null
                    delay(LAST_PATIENT_RETRY_DELAY_MS)
                    continue
                }
            }

            try {
                db.patientDao()
                    .observePatientsFiltered(
                        q = null,
                        tagKeys = emptyList(),
                        tagKeysCount = 0,
                        fromMillis = null
                    )
                    .collect { rows ->
                        val row = rows.firstOrNull()
                        value = row?.let {
                            val patient = it.patient
                            val displayName = patient.displayName?.takeIf { name -> name.isNotBlank() } ?: patient.internalCode
                            val ageYears = patient.birthDateMillis?.let { birthDateMillis ->
                                runCatching {
                                    val zoneId = ZoneId.systemDefault()
                                    val birthDate = BirthDateCodec.fromStorageMillis(birthDateMillis)
                                    val today = LocalDate.now(zoneId)
                                    Period.between(birthDate, today).years.coerceAtLeast(0)
                                }.getOrNull()
                            }
                            val lastStudyLabel = it.lastStudyAtMillis?.let { millis ->
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(millis)
                            }

                            LastPatientUi(
                                patientId = patient.id,
                                displayName = displayName,
                                ageYears = ageYears,
                                lastStudyLabel = lastStudyLabel
                            )
                        }
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(
                    APP_NAV_GRAPH_TAG,
                    "Last patient shortcut observer failed. Retrying.",
                    error
                )
                value = null
            }

            delay(LAST_PATIENT_RETRY_DELAY_MS)
        }
    }

    val calcRoutes = remember {
        listOf(
            Destinations.Fick.route,
            Destinations.Resistances.route,
            Destinations.Cpo.route,
            Destinations.Papi.route,
            Destinations.Pvr.route
        )
    }

    fun goHomeInsideCalcGraph() {
        val popped = navController.popBackStack(Destinations.Calculators.route, inclusive = false)
        if (!popped) navController.navigate(Destinations.Calculators.route) { launchSingleTop = true }
    }

    fun goToCalc(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    fun onNextFrom(route: String): () -> Unit {
        val idx = calcRoutes.indexOf(route)
        return {
            if (idx >= 0 && idx < calcRoutes.lastIndex) goToCalc(calcRoutes[idx + 1])
            else goHomeInsideCalcGraph()
        }
    }

    fun onPrevFrom(route: String): () -> Unit {
        val idx = calcRoutes.indexOf(route)
        return {
            if (idx > 0) goToCalc(calcRoutes[idx - 1])
            else goHomeInsideCalcGraph()
        }
    }

    fun startHemodynamicWorkshop() {
        com.gipogo.rhctools.report.ReportStore.clear()
        com.gipogo.rhctools.reset.AppResetBus.resetAll()
        val popped = navController.popBackStack(Destinations.Calculators.route, inclusive = false)
        if (!popped) {
            navController.navigate(Destinations.Calculators.route) { launchSingleTop = true }
        }
    }

    fun invalidateWorkshopSession() {
        WorkshopSession.clear()
        com.gipogo.rhctools.report.ReportStore.clear()
        com.gipogo.rhctools.reset.AppResetBus.resetAll()
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.CalcGraph.route
    ) {
        navigation(
            route = Destinations.CalcGraph.route,
            startDestination = Destinations.Home.route
        ) {

            // ---------------- HOME PRINCIPAL ----------------
            composable(Destinations.Home.route) {
                HomeScreen(
                    onOpenCalculators = {
                        WorkshopSession.startQuick()
                        startHemodynamicWorkshop()
                    },
                    onOpenPatients = {
                        invalidateWorkshopSession()
                        navController.navigate(Destinations.Patients.route)
                    },
                    onOpenLastPatient = { patientId ->
                        invalidateWorkshopSession()
                        navController.navigate(Destinations.PatientDetail.route(patientId))
                    },
                    lastPatient = lastPatientUi,
                    showBottomNav = true,
                    onTabHome = { },
                    onTabHistory = {
                        invalidateWorkshopSession()
                        navController.navigate(Destinations.Patients.route)
                    },
                    onTabSettings = { navController.navigate(Destinations.Settings.route) }
                )
            }

            composable(Destinations.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            // ---------------- HOME DEL TALLER ----------------
            composable(Destinations.Calculators.route) { backStackEntry ->
                val calcGraphEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Destinations.CalcGraph.route)
                }
                key(runId) {
                    HomeCalculatorScreen(
                        navController = navController,
                        calcGraphEntry = calcGraphEntry
                    )
                }
            }

            // ---------------- PATIENTS LIST ----------------
            composable(Destinations.Patients.route) {
                PatientsScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Destinations.PatientNew.route) { launchSingleTop = true } },
                    onOpenPatient = { id -> navController.navigate(Destinations.PatientDetail.route(id)) { launchSingleTop = true } },
                    onEditPatient = { id -> navController.navigate(Destinations.PatientEdit.route(id)) { launchSingleTop = true } }
                )
            }

            // ---------------- PATIENT NEW ----------------
            composable(Destinations.PatientNew.route) {
                PatientEditScreen(
                    isEdit = false,
                    onBack = { navController.popBackStack() },
                    onSave = { navController.popBackStack() }
                )
            }

            // ---------------- PATIENT EDIT ----------------
            composable(
                route = Destinations.PatientEdit.route,
                arguments = listOf(navArgument(Destinations.PatientEdit.ARG_PATIENT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val patientId = backStackEntry.arguments?.getString(Destinations.PatientEdit.ARG_PATIENT_ID)

                PatientEditScreen(
                    isEdit = true,
                    patientId = patientId,
                    onBack = { navController.popBackStack() },
                    onSave = { navController.popBackStack() }
                )
            }

            // ---------------- PATIENT DETAIL ----------------
            composable(
                route = Destinations.PatientDetail.route,
                arguments = listOf(navArgument(Destinations.PatientDetail.ARG_PATIENT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val patientId = requireNotNull(
                    backStackEntry.arguments?.getString(Destinations.PatientDetail.ARG_PATIENT_ID)
                ) { "Missing patientId for PatientDetail" }

                PatientDetailRoute(
                    patientId = patientId,
                    onBack = { navController.popBackStack() },

                    onNewStudy = { pid: String ->
                        scope.launch {
                            try {
                                WorkshopStudyFactory.startNewRhcStudy(
                                    context = context,
                                    patientId = pid
                                )
                                startHemodynamicWorkshop()
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Throwable) {
                                Log.e(
                                    APP_NAV_GRAPH_TAG,
                                    "Study creation failed: ${error.javaClass.simpleName}"
                                )
                                Toast.makeText(
                                    appContext,
                                    R.string.patient_error_create_study,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },

                    onOpenStudy = { pid: String, sid: String ->
                        invalidateWorkshopSession()
                        navController.navigate(Destinations.StudyDetail.route(pid, sid)) {
                            launchSingleTop = true
                        }
                    },

                    onExportLatestPdf = { pid: String ->
                        pendingStudyPdfExport = PendingStudyPdfExport.Latest(patientId = pid)
                    },

                    onExportStudyPdf = { pid: String, sid: String ->
                        pendingStudyPdfExport = PendingStudyPdfExport.Selected(
                            patientId = pid,
                            studyId = sid
                        )
                    },

                    onExportLongitudinalPdf = { pid: String ->
                        navController.navigate(Destinations.ReportRender.route(pid)) { launchSingleTop = true }
                    },
                )

            }

            // ---------------- STUDY DETAIL ----------------
            composable(
                route = Destinations.StudyDetail.route,
                arguments = listOf(
                    navArgument(Destinations.StudyDetail.ARG_PATIENT_ID) { type = NavType.StringType },
                    navArgument(Destinations.StudyDetail.ARG_STUDY_ID) { type = NavType.StringType },
                )
            ) { backStackEntry ->

                val patientId = requireNotNull(
                    backStackEntry.arguments?.getString(Destinations.StudyDetail.ARG_PATIENT_ID)
                ) { "Missing patientId for StudyDetail" }

                val studyId = requireNotNull(
                    backStackEntry.arguments?.getString(Destinations.StudyDetail.ARG_STUDY_ID)
                ) { "Missing studyId for StudyDetail" }

                StudyDetailRoute(
                    patientId = patientId,
                    patientName = null,
                    studyId = studyId,
                    onBack = {
                        val popped = navController.popBackStack(
                            Destinations.PatientDetail.route(patientId),
                            inclusive = false
                        )
                        if (!popped) {
                            navController.navigate(Destinations.PatientDetail.route(patientId)) { launchSingleTop = true }
                        }
                    },
                    onExportStudyPdf = { pid, sid ->
                        pendingStudyPdfExport = PendingStudyPdfExport.Selected(
                            patientId = pid,
                            studyId = sid
                        )
                    }
                )
            }

            // ---------------- FICK ----------------
            composable(Destinations.Fick.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
                val vm: FickViewModel = viewModel(viewModelStoreOwner = parentEntry, key = "Fick_$runId")
                key(runId) {
                    FickScreen(
                        onBackToMenu = { goHomeInsideCalcGraph() },
                        onNextCalc = onNextFrom(Destinations.Fick.route),
                        onPrevCalc = onPrevFrom(Destinations.Fick.route),
                        vm = vm
                    )
                }
            }

            // ---------------- SVR ----------------
            composable(Destinations.Resistances.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
                val vm: ResistancesViewModel = viewModel(viewModelStoreOwner = parentEntry, key = "Resistances_$runId")
                key(runId) {
                    ResistancesScreen(
                        onBackToMenu = { goHomeInsideCalcGraph() },
                        onNextCalc = onNextFrom(Destinations.Resistances.route),
                        onPrevCalc = onPrevFrom(Destinations.Resistances.route),
                        vm = vm
                    )
                }
            }

            // ---------------- CPO ----------------
            composable(Destinations.Cpo.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
                val vm: CpoViewModel = viewModel(viewModelStoreOwner = parentEntry, key = "Cpo_$runId")
                key(runId) {
                    CpoScreen(
                        onBackToMenu = { goHomeInsideCalcGraph() },
                        onNextCalc = onNextFrom(Destinations.Cpo.route),
                        onPrevCalc = onPrevFrom(Destinations.Cpo.route),
                        vm = vm
                    )
                }
            }

            // ---------------- PAPI ----------------
            composable(Destinations.Papi.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
                val vm: PapiViewModel = viewModel(viewModelStoreOwner = parentEntry, key = "Papi_$runId")
                key(runId) {
                    PapiScreen(
                        onBackToMenu = { goHomeInsideCalcGraph() },
                        onNextCalc = onNextFrom(Destinations.Papi.route),
                        onPrevCalc = onPrevFrom(Destinations.Papi.route),
                        vm = vm
                    )
                }
            }

            // ---------------- PVR ----------------
            composable(Destinations.Pvr.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
                val vm: PvrViewModel = viewModel(viewModelStoreOwner = parentEntry, key = "Pvr_$runId")
                key(runId) {
                    PvrScreen(
                        onBackToMenu = { goHomeInsideCalcGraph() },
                        onNextCalc = {
                            parentEntry.savedStateHandle.set(Destinations.NAV_FLAG_SCROLL_TO_EXIT, true)
                            onNextFrom(Destinations.Pvr.route).invoke()
                        },
                        onPrevCalc = onPrevFrom(Destinations.Pvr.route),
                        vm = vm
                    )
                }
            }

            // ---------------- REPORT RENDER ----------------
            composable(
                route = Destinations.ReportRender.route,
                arguments = listOf(navArgument(Destinations.ReportRender.ARG_PATIENT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val pid = backStackEntry.arguments?.getString(Destinations.ReportRender.ARG_PATIENT_ID).orEmpty()

                ReportRenderRoute(
                    patientId = pid,
                    onDoneOpenPreview = { file, uri ->
                        context.startActivity(PdfPreviewActivity.createIntent(context, file, uri))
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (pendingStudyPdfExport != null) {
        StudyPdfFormatPickerDialog(
            onDismiss = { pendingStudyPdfExport = null },
            onPick = { format ->
                val pending = pendingStudyPdfExport ?: return@StudyPdfFormatPickerDialog
                pendingStudyPdfExport = null
                scope.launch {
                    runCatching {
                        when (pending) {
                            is PendingStudyPdfExport.Latest -> {
                                exportLatestStudyPdfAndOpenPreview(
                                    context = context,
                                    patientId = pending.patientId,
                                    format = format
                                )
                            }

                            is PendingStudyPdfExport.Selected -> {
                                exportSingleStudyPdfAndOpenPreview(
                                    context = context,
                                    patientId = pending.patientId,
                                    studyId = pending.studyId,
                                    format = format
                                )
                            }
                        }
                    }.onFailure { error ->
                        notifyPdfExportFailure(context, error)
                    }
                }
            }
        )
    }
}

@Composable
private fun StudyPdfFormatPickerDialog(
    onDismiss: () -> Unit,
    onPick: (StudyClinicalPdfFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = androidx.compose.ui.res.stringResource(R.string.study_pdf_picker_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = androidx.compose.ui.Modifier.testTag("study_pdf_format_picker_dialog")
            ) {
                Text(text = androidx.compose.ui.res.stringResource(R.string.study_pdf_picker_body))

                Button(
                    onClick = { onPick(StudyClinicalPdfFormat.COMPLETE) },
                    modifier = androidx.compose.ui.Modifier.testTag("study_pdf_format_complete_button")
                ) {
                    Text(text = androidx.compose.ui.res.stringResource(R.string.study_pdf_complete_title))
                }

                OutlinedButton(
                    onClick = { onPick(StudyClinicalPdfFormat.COMPACT) },
                    modifier = androidx.compose.ui.Modifier.testTag("study_pdf_format_compact_button")
                ) {
                    Text(text = androidx.compose.ui.res.stringResource(R.string.study_pdf_compact_title))
                }

                Spacer(modifier = androidx.compose.ui.Modifier.height(2.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.study_pdf_picker_hint),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = androidx.compose.ui.Modifier.testTag("study_pdf_format_cancel_button")
            ) {
                Text(text = androidx.compose.ui.res.stringResource(R.string.common_cancel))
            }
        }
    )
}
