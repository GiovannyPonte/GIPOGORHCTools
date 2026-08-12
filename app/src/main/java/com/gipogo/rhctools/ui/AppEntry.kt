package com.gipogo.rhctools.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.ui.screens.DisclaimerScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

@Composable
fun AppEntry(
    onAppReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val appContext = context.applicationContext
    val prefs = remember(appContext) { AppPreferences(appContext) }
    val disclaimerAccepted = remember(prefs) {
        prefs.disclaimerAccepted.map<Boolean, Boolean?> { it }
    }
    val accepted by disclaimerAccepted.collectAsStateWithLifecycle(initialValue = null)
    val language by prefs.storedAppLanguage.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(language) {
        val selectedLanguage = language ?: return@LaunchedEffect
        val locales = selectedLanguage.languageTag?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    val scope = rememberCoroutineScope()

    if (accepted == null) return

    // Si todavía no aceptó: mostramos DisclaimerScreen
    if (accepted == false) {
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
    LaunchedEffect(Unit) {
        onAppReady()
    }
    AppRoot()
}
