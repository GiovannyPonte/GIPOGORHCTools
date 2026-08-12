package com.gipogo.rhctools.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.ui.screens.DisclaimerScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppEntry() {
    val context = LocalContext.current
    val activity = context as? Activity

    val prefs = AppPreferences(context)
    val accepted by prefs.disclaimerAccepted.collectAsState(initial = false)

    val scope = rememberCoroutineScope()

    // Si todavía no aceptó: mostramos DisclaimerScreen
    if (!accepted) {
        DisclaimerScreen(
            onAccept = {
                // ✅ Correcto: lanzar coroutine desde scope (no LaunchedEffect aquí)
                scope.launch {
                    prefs.setDisclaimerAccepted(true)
                }
            },
            onDecline = {
                // Cerrar la app
                activity?.finish()
            }
        )
        return
    }

    // Si aceptó: entrar a la app normal
    AppRoot()
}
