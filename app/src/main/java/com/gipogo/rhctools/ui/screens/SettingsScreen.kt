package com.gipogo.rhctools.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.domain.AppLanguage
import com.gipogo.rhctools.ui.components.GipogoTopBar
import com.gipogo.rhctools.ui.update.AppUpdateHost
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) { AppPreferences(context.applicationContext) }
    val selected by preferences.appLanguage.collectAsStateWithLifecycle(initialValue = AppLanguage.SYSTEM)
    val scope = rememberCoroutineScope()
    fun selectLanguage(language: AppLanguage) {
        scope.launch {
            // Persist first: applying locales recreates the Activity and cancels
            // its composition scope. Reversing this order can lose the choice.
            preferences.setAppLanguage(language)
            AppCompatDelegate.setApplicationLocales(
                language.languageTag?.let(LocaleListCompat::forLanguageTags)
                    ?: LocaleListCompat.getEmptyLocaleList()
            )
        }
    }

    Scaffold(
        topBar = { GipogoTopBar(title = stringResource(R.string.settings_title), showBack = true, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_language_description), style = MaterialTheme.typography.bodyMedium)
            listOf(
                AppLanguage.SYSTEM to R.string.settings_language_system,
                AppLanguage.SPANISH to R.string.settings_language_spanish,
                AppLanguage.ENGLISH to R.string.settings_language_english
            ).forEach { (language, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectLanguage(language) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == language,
                        onClick = { selectLanguage(language) }
                    )
                    Text(stringResource(labelRes))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_updates_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_updates_description), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { (context as? AppUpdateHost)?.checkForAppUpdateManually() },
                modifier = Modifier.fillMaxWidth(),
                enabled = context is AppUpdateHost
            ) {
                Text(stringResource(R.string.settings_check_updates))
            }
        }
    }
}
