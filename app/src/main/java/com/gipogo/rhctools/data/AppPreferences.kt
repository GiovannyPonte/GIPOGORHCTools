package com.gipogo.rhctools.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gipogo.rhctools.domain.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gipogo_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")

        // Unit system persisted across the app
        private val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system")
    }

    /* ---------------- Disclaimer ---------------- */

    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data
        .map { prefs: Preferences ->
            prefs[KEY_DISCLAIMER_ACCEPTED] ?: false
        }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISCLAIMER_ACCEPTED] = accepted
        }
    }

    /* ---------------- Unit System ---------------- */

    val unitSystem: Flow<UnitSystem> = context.dataStore.data
        .map { prefs: Preferences ->
            UnitSystem.fromStored(prefs[KEY_UNIT_SYSTEM])
        }

    suspend fun setUnitSystem(system: UnitSystem) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNIT_SYSTEM] = system.name
        }
    }
}
