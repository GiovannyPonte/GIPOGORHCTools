package com.gipogo.rhctools.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gipogo_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    }

    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data
        .map { prefs: Preferences -> prefs[KEY_DISCLAIMER_ACCEPTED] ?: false }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISCLAIMER_ACCEPTED] = accepted
        }
    }
}
