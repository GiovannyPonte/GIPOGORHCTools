package com.gipogo.rhctools.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gipogo.rhctools.ui.screens.CpoScreen
import com.gipogo.rhctools.ui.screens.FickScreen
import com.gipogo.rhctools.ui.screens.HomeScreen
import com.gipogo.rhctools.ui.screens.PapiScreen
import com.gipogo.rhctools.ui.screens.ResistancesScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    fun goHome() {
        navController.navigate(Destinations.Home.route) {
            popUpTo(Destinations.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.Home.route
    ) {
        composable(Destinations.Home.route) {
            HomeScreen(navController)
        }

        composable(Destinations.Fick.route) {
            FickScreen(onBackToMenu = { goHome() })
        }

        composable(Destinations.Resistances.route) {
            ResistancesScreen(onBackToMenu = { goHome() })
        }

        composable(Destinations.Cpo.route) {
            CpoScreen(onBackToMenu = { goHome() })
        }

        composable(Destinations.Papi.route) {
            PapiScreen(onBackToMenu = { goHome() })
        }
        composable(Destinations.PdfPreview.route) {
            val file = com.gipogo.rhctools.report.PdfSession.lastPdfFile
            val uri = com.gipogo.rhctools.report.PdfSession.lastPdfUri

            if (file != null && uri != null) {
                com.gipogo.rhctools.ui.screens.PdfPreviewScreen(
                    pdfUri = uri,
                    pdfFileForShare = file,
                    onClose = { navController.popBackStack() }
                )
            } else {
                // fallback
                HomeScreen(navController)
            }
        }

    }
}
