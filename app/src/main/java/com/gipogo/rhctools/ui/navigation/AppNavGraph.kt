package com.gipogo.rhctools.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gipogo.rhctools.R
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.PdfReportGenerator
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.reporting.compose.ReportRenderRoute
import com.gipogo.rhctools.ui.screens.CpoScreen
import com.gipogo.rhctools.ui.screens.FickScreen
import com.gipogo.rhctools.ui.screens.HomeCalculatorScreen
import com.gipogo.rhctools.ui.screens.HomeScreen
import com.gipogo.rhctools.ui.screens.PapiScreen
import com.gipogo.rhctools.ui.screens.PatientDetailRoute
import com.gipogo.rhctools.ui.screens.PatientEditScreen
import com.gipogo.rhctools.ui.screens.PatientsScreen
import com.gipogo.rhctools.ui.screens.PdfPreviewScreen
import com.gipogo.rhctools.ui.screens.PvrScreen
import com.gipogo.rhctools.ui.screens.ResistancesScreen
import com.gipogo.rhctools.ui.screens.StudyDetailRoute
import com.gipogo.rhctools.ui.viewmodel.CpoViewModel
import com.gipogo.rhctools.ui.viewmodel.FickViewModel
import com.gipogo.rhctools.ui.viewmodel.PapiViewModel
import com.gipogo.rhctools.ui.viewmodel.PvrViewModel
import com.gipogo.rhctools.ui.viewmodel.ResistancesViewModel
import com.gipogo.rhctools.workshop.WorkshopSession
import com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave
import com.gipogo.rhctools.workshop.persistence.WorkshopStudyFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import com.gipogo.rhctools.report.ForresterPdfBlock
import com.gipogo.rhctools.report.SharedKeys


// Ajusta estos imports si tu package real difiere
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.RhcStudyDao

/* --------------------------------------------------------- */
/* PDF helpers: una sola fuente de verdad para export 1 study */
/* --------------------------------------------------------- */

private suspend fun exportSingleStudyPdfAndOpenPreview(
    context: Context,
    navController: NavHostController,
    patientDao: PatientDao,
    rhcStudyDao: RhcStudyDao,
    patientId: String,
    studyId: String
) {
    val list = rhcStudyDao.listStudiesWithRhcDataByPatient(patientId).first()

    val selected = list.firstOrNull { it.study.id == studyId }
        ?: throw IllegalStateException("Study not found")

    val rhc = selected.rhc

    val patient = runCatching { patientDao.getById(patientId) }.getOrNull()
    val displayName = patient?.displayName?.takeIf { it.isNotBlank() }
    val internalCode = patient?.internalCode?.takeIf { it.isNotBlank() }
    val headerName = displayName ?: internalCode ?: patientId

    val nf0 = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
    val nf1 = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
    val nf2 = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }

    val uMmHg = context.getString(R.string.common_unit_mmhg)
    val uLMinM2 = context.getString(R.string.common_unit_lmin_m2)
    val uWuShort = context.getString(R.string.common_unit_wu_short)
    val uW = context.getString(R.string.common_unit_w)

    val lRap = context.getString(R.string.papi_help_rap_title)
    val lMpap = context.getString(R.string.pvr_help_mpap_title)
    val lPcwp = context.getString(R.string.rhc_label_pcwp_short)
    val lCi = context.getString(R.string.rhc_label_ci_short)
    val lPvr = context.getString(R.string.home_badge_pvr)
    val lCpo = context.getString(R.string.home_badge_cpo)

    val outputs: List<LineItem> = buildList {
        rhc?.rapMmHg?.let {
            add(LineItem(key = SharedKeys.RAP_MMHG, label = lRap, value = nf0.format(it), unit = uMmHg))
        }
        rhc?.mpapMmHg?.let {
            add(LineItem(key = SharedKeys.MPAP_MMHG, label = lMpap, value = nf0.format(it), unit = uMmHg))
        }
        rhc?.pawpMmHg?.let {
            add(LineItem(key = SharedKeys.PAWP_MMHG, label = lPcwp, value = nf0.format(it), unit = uMmHg))
        }
        rhc?.cardiacIndexLMinM2?.let {
            add(LineItem(key = SharedKeys.CI_LMIN_M2, label = lCi, value = nf1.format(it), unit = uLMinM2))
        }
        rhc?.pvrWood?.let {
            add(LineItem(key = SharedKeys.PVR_WOOD, label = lPvr, value = nf1.format(it), unit = uWuShort))
        }
        rhc?.cardiacPowerW?.let {
            add(LineItem(key = SharedKeys.CPO_W, label = lCpo, value = nf2.format(it), unit = uW))
        }
    }

    val entries = listOf(
        CalcEntry(
            type = CalcType.PVR,
            timestampMillis = selected.study.startedAtMillis,
            title = context.getString(R.string.patient_overview_latest_summary),
            inputs = emptyList(),
            outputs = outputs,
            notes = emptyList()
        )
    )

    val outDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
    val file = File(outDir, "RHC_${patientId}_${selected.study.startedAtMillis}_${studyId.take(8)}.pdf")

    file.outputStream().use { os ->
        val appName = context.getString(R.string.pdf_app_name)

        PdfReportGenerator.writePdf(
            context = context,
            outputStream = os,
            appName = "$appName · $headerName",
            entries = entries,
            nowMillis = System.currentTimeMillis(),
            forrester = ForresterPdfBlock(
                ci = rhc?.cardiacIndexLMinM2,
                pcwp = rhc?.pawpMmHg
            )
        )
    }

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    PdfSession.lastPdfFile = file
    PdfSession.lastPdfUri = uri

    navController.navigate(Destinations.PdfPreview.route) { launchSingleTop = true }
}

private suspend fun exportLatestStudyPdfAndOpenPreview(
    context: Context,
    navController: NavHostController,
    patientDao: PatientDao,
    rhcStudyDao: RhcStudyDao,
    patientId: String
) {
    val list = rhcStudyDao.listStudiesWithRhcDataByPatient(patientId).first()
    val latest = list.firstOrNull() ?: throw IllegalStateException("No studies")
    exportSingleStudyPdfAndOpenPreview(
        context = context,
        navController = navController,
        patientDao = patientDao,
        rhcStudyDao = rhcStudyDao,
        patientId = patientId,
        studyId = latest.study.id
    )
}

/* ----------------------------- */
/* AppNavGraph                   */
/* ----------------------------- */

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    // ✅ Declarar aquí (NO dentro de lambdas)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ✅ Autosave a nivel raíz
    LaunchedEffect(Unit) {
        WorkshopRhcAutosave.start(context, scope)
    }

    // ✅ RunId actual del taller
    val workshopCtx by WorkshopSession.context.collectAsState()
    val runId = workshopCtx.workshopRunId

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
        navController.navigate(Destinations.Calculators.route) {
            popUpTo(Destinations.Calculators.route) { inclusive = false }
            launchSingleTop = true
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
                    lastPatient = null
                )
            }

            // ---------------- HOME DEL TALLER ----------------
            composable(Destinations.Calculators.route) {
                key(runId) { HomeCalculatorScreen(navController = navController) }
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

                val appCtx = context.applicationContext
                val db = remember(appCtx) { com.gipogo.rhctools.data.db.DbProvider.get(appCtx) }
                val patientDao = remember(db) { db.patientDao() }
                val rhcStudyDao = remember(db) { db.rhcStudyDao() }

                PatientDetailRoute(
                    patientId = patientId,
                    onBack = { navController.popBackStack() },

                    onNewStudy = { pid: String ->
                        scope.launch {
                            WorkshopStudyFactory.startNewRhcStudy(
                                context = context,
                                patientId = pid
                            )
                            startHemodynamicWorkshop()
                        }
                    },

                    onOpenStudy = { pid: String, sid: String ->
                        invalidateWorkshopSession()
                        navController.navigate(Destinations.StudyDetail.route(pid, sid)) {
                            launchSingleTop = true
                        }
                    },

                    onExportLatestPdf = { pid: String ->
                        scope.launch {
                            runCatching {
                                exportLatestStudyPdfAndOpenPreview(
                                    context = context,
                                    navController = navController,
                                    patientDao = patientDao,
                                    rhcStudyDao = rhcStudyDao,
                                    patientId = pid
                                )
                            }
                        }
                    },

                    onExportStudyPdf = { pid: String, sid: String ->
                        scope.launch {
                            runCatching {
                                exportSingleStudyPdfAndOpenPreview(
                                    context = context,
                                    navController = navController,
                                    patientDao = patientDao,
                                    rhcStudyDao = rhcStudyDao,
                                    patientId = pid,
                                    studyId = sid
                                )
                            }
                        }
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

                val appCtx = context.applicationContext
                val db = remember(appCtx) { com.gipogo.rhctools.data.db.DbProvider.get(appCtx) }
                val patientDao = remember(db) { db.patientDao() }
                val rhcStudyDao = remember(db) { db.rhcStudyDao() }

                // ✅ nombre desde Room (no texto duro)
                val patientName by produceState<String?>(initialValue = null, key1 = patientId) {
                    value = runCatching {
                        val p = patientDao.getById(patientId)
                        val displayName = p?.displayName?.takeIf { it.isNotBlank() }
                        val internalCode = p?.internalCode?.takeIf { it.isNotBlank() }
                        displayName ?: internalCode ?: patientId
                    }.getOrNull()
                }

                StudyDetailRoute(
                    patientId = patientId,
                    patientName = patientName,
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
                        scope.launch {
                            runCatching {
                                exportSingleStudyPdfAndOpenPreview(
                                    context = context,
                                    navController = navController,
                                    patientDao = patientDao,
                                    rhcStudyDao = rhcStudyDao,
                                    patientId = pid,
                                    studyId = sid
                                )
                            }
                        }
                    }
                )
            }

            // ---------------- FICK ----------------
            composable(Destinations.Fick.route) {
                val parentEntry = remember(navController) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
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
            composable(Destinations.Resistances.route) {
                val parentEntry = remember(navController) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
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
            composable(Destinations.Cpo.route) {
                val parentEntry = remember(navController) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
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
            composable(Destinations.Papi.route) {
                val parentEntry = remember(navController) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
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
            composable(Destinations.Pvr.route) {
                val parentEntry = remember(navController) { navController.getBackStackEntry(Destinations.CalcGraph.route) }
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

            // ---------------- PDF PREVIEW ----------------
            composable(Destinations.PdfPreview.route) {
                val file = PdfSession.lastPdfFile
                val uri = PdfSession.lastPdfUri

                if (file != null && uri != null) {
                    PdfPreviewScreen(
                        pdfUri = uri,
                        pdfFileForShare = file,
                        onClose = { navController.popBackStack() }
                    )
                } else {
                    navController.navigate(Destinations.Home.route) { launchSingleTop = true }
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
                    onDoneOpenPreview = {
                        navController.navigate(Destinations.PdfPreview.route) {
                            launchSingleTop = true
                            popUpTo(Destinations.ReportRender.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
