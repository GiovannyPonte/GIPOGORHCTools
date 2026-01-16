package com.gipogo.rhctools.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.ui.screens.CpoScreen
import com.gipogo.rhctools.ui.screens.FickScreen
import com.gipogo.rhctools.ui.screens.HomeScreen
import com.gipogo.rhctools.ui.screens.PapiScreen
import com.gipogo.rhctools.ui.screens.PdfPreviewScreen
import com.gipogo.rhctools.ui.screens.PvrScreen
import com.gipogo.rhctools.ui.screens.ResistancesScreen
import com.gipogo.rhctools.ui.viewmodel.CpoViewModel
import com.gipogo.rhctools.ui.viewmodel.FickViewModel
import com.gipogo.rhctools.ui.viewmodel.PapiViewModel
import com.gipogo.rhctools.ui.viewmodel.PvrViewModel
import com.gipogo.rhctools.ui.viewmodel.ResistancesViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    // Orden de calculadoras para navegación prev/next
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
        val popped = navController.popBackStack(Destinations.Home.route, inclusive = false)
        if (!popped) {
            navController.navigate(Destinations.Home.route) { launchSingleTop = true }
        }
    }

    fun goToCalc(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    fun onNextFrom(route: String): () -> Unit {
        val idx = calcRoutes.indexOf(route)
        return {
            if (idx >= 0 && idx < calcRoutes.lastIndex) {
                goToCalc(calcRoutes[idx + 1])
            } else {
                // Si no se encuentra o es el último, vuelve a Home
                goHomeInsideCalcGraph()
            }
        }
    }

    fun onPrevFrom(route: String): () -> Unit {
        val idx = calcRoutes.indexOf(route)
        return {
            if (idx > 0) {
                goToCalc(calcRoutes[idx - 1])
            } else {
                // Si es el primero (o no se encuentra), vuelve a Home
                goHomeInsideCalcGraph()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.CalcGraph.route
    ) {
        navigation(
            route = Destinations.CalcGraph.route,
            startDestination = Destinations.Home.route
        ) {
            composable(Destinations.Home.route) {
                HomeScreen(navController)
            }

            composable(Destinations.Fick.route) {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry(Destinations.CalcGraph.route)
                }
                val vm: FickViewModel = viewModel(parentEntry)

                FickScreen(
                    onBackToMenu = { goHomeInsideCalcGraph() },
                    onNextCalc = onNextFrom(Destinations.Fick.route),
                    onPrevCalc = onPrevFrom(Destinations.Fick.route),
                    vm = vm
                )
            }

            composable(Destinations.Resistances.route) {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry(Destinations.CalcGraph.route)
                }
                val vm: ResistancesViewModel = viewModel(parentEntry)

                ResistancesScreen(
                    onBackToMenu = { goHomeInsideCalcGraph() },
                    onNextCalc = onNextFrom(Destinations.Resistances.route),
                    onPrevCalc = onPrevFrom(Destinations.Resistances.route),
                    vm = vm
                )
            }

            composable(Destinations.Cpo.route) {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry(Destinations.CalcGraph.route)
                }
                val vm: CpoViewModel = viewModel(parentEntry)

                CpoScreen(
                    onBackToMenu = { goHomeInsideCalcGraph() },
                    onNextCalc = onNextFrom(Destinations.Cpo.route),
                    onPrevCalc = onPrevFrom(Destinations.Cpo.route),
                    vm = vm
                )
            }


            composable(Destinations.Papi.route) {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry(Destinations.CalcGraph.route)
                }
                val vm: PapiViewModel = viewModel(parentEntry)

                PapiScreen(
                    onBackToMenu = { goHomeInsideCalcGraph() },
                    onNextCalc = onNextFrom(Destinations.Papi.route),
                    onPrevCalc = onPrevFrom(Destinations.Papi.route),
                    vm = vm
                )
            }

            // ✅ PVR (persistente dentro del calc_graph)
            composable(Destinations.Pvr.route) {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry(Destinations.CalcGraph.route)
                }
                val vm: PvrViewModel = viewModel(parentEntry)

                PvrScreen(
                    onBackToMenu = { goHomeInsideCalcGraph() },
                    onNextCalc = onNextFrom(Destinations.Pvr.route),
                    onPrevCalc = onPrevFrom(Destinations.Pvr.route),
                    vm = vm
                )
            }
        }

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
    }
}
