package com.gipogo.rhctools.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gipogo.rhctools.domain.UnitSystem
import com.gipogo.rhctools.domain.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gipogo_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")

        // Unit system persisted across the app
        private val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_PLAY_STORE_UPDATE_LAST_CHECK_EPOCH_DAY =
            longPreferencesKey("play_store_update_last_check_epoch_day")
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

    val storedUnitSystem: Flow<UnitSystem?> = context.dataStore.data
        .map { prefs: Preferences ->
            prefs[KEY_UNIT_SYSTEM]?.let(UnitSystem::fromStored)
        }

    val unitSystem: Flow<UnitSystem> = context.dataStore.data
        .map { prefs: Preferences ->
            UnitSystem.fromStored(prefs[KEY_UNIT_SYSTEM])
        }

    suspend fun setUnitSystem(system: UnitSystem) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNIT_SYSTEM] = system.name
        }
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data
        .map { prefs -> AppLanguage.fromStored(prefs[KEY_APP_LANGUAGE]) }

    val storedAppLanguage: Flow<AppLanguage?> = context.dataStore.data
        .map { prefs -> prefs[KEY_APP_LANGUAGE]?.let(AppLanguage::fromStored) }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs -> prefs[KEY_APP_LANGUAGE] = language.name }
    }

    /* ---------------- Play Store updates ---------------- */

    val playStoreUpdateLastCheckEpochDay: Flow<Long?> = context.dataStore.data
        .map { prefs: Preferences ->
            prefs[KEY_PLAY_STORE_UPDATE_LAST_CHECK_EPOCH_DAY]
        }

    suspend fun setPlayStoreUpdateLastCheckEpochDay(epochDay: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAY_STORE_UPDATE_LAST_CHECK_EPOCH_DAY] = epochDay
        }
    }
}
