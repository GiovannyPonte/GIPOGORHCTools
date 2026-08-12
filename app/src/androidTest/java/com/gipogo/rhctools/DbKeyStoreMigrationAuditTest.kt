package com.gipogo.rhctools

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.data.db.AppDatabase
import com.gipogo.rhctools.data.security.DbKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class DbKeyStoreMigrationAuditTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun prepareIsolatedKeyState() {
        resetDatabaseAndKey()
    }

    @After
    fun cleanup() {
        resetDatabaseAndKey()
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyEncryptedPreferences_areMigratedWithoutChangingTheSqlCipherPassphrase() {
        val raw = ByteArray(32).also(SecureRandom()::nextBytes)
        val legacyPassphrase = Base64.encodeToString(raw, Base64.NO_WRAP)

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val legacyPrefs = EncryptedSharedPreferences.create(
            context,
            LEGACY_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        assertTrue(legacyPrefs.edit().putString(LEGACY_KEY, legacyPassphrase).commit())

        val migrated = DbKeyStore.getOrCreatePassphraseText(context)
        val reloaded = DbKeyStore.getOrCreatePassphraseText(context)

        assertEquals(legacyPassphrase, migrated)
        assertEquals(legacyPassphrase, reloaded)

        val protectedPrefs = context.getSharedPreferences(V2_PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertext = protectedPrefs.getString(V2_CIPHERTEXT_KEY, null)
        assertEquals(2, protectedPrefs.getInt(V2_VERSION_KEY, 0))
        assertFalse(ciphertext.isNullOrBlank())
        assertNotEquals(legacyPassphrase, ciphertext)
        assertFalse(context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).contains(LEGACY_KEY))
    }

    private fun resetDatabaseAndKey() {
        AppDatabase.clearInstance()
        context.deleteDatabase("gipogo_rhc_tools.db")
        DbKeyStore.clear(context)
    }

    private companion object {
        const val LEGACY_PREFS_NAME = "db_crypto_prefs"
        const val LEGACY_KEY = "db_passphrase_b64"
        const val V2_PREFS_NAME = "db_key_material_v2"
        const val V2_CIPHERTEXT_KEY = "wrapped_passphrase_b64"
        const val V2_VERSION_KEY = "format_version"
    }
}
