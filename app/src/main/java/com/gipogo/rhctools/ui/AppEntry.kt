package com.gipogo.rhctools.ui

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.ui.screens.DisclaimerScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Resolves persisted startup state before exposing the application's navigation graph.
 *
 * A nullable collected value distinguishes "preferences are still loading" from a stored false.
 * This prevents the disclaimer from flashing briefly for users who have already accepted it.
 */
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

    if (accepted == false) {
        DisclaimerScreen(
            onAccept = {
                // Event callbacks are not composable scopes, so persistence runs in this UI scope.
                scope.launch {
                    prefs.setDisclaimerAccepted(true)
                }
            },
            onDecline = {
                activity?.finish()
            }
        )
        return
    }

    // Update checks start only after preferences and the legal gate have completed.
    LaunchedEffect(Unit) {
        onAppReady()
    }
    AppRoot()
}
