package com.gipogo.rhctools

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.data.db.AppDatabase
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.security.DbKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.runBlocking

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

    @Test
    fun incompleteMaterialCanBeReplacedWhenCallerProvesNoDatabaseExists() {
        context.getSharedPreferences(V2_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(V2_VERSION_KEY, 2)
            .putString(V2_CIPHERTEXT_KEY, "incomplete")
            .commit()

        val recovered = DbKeyStore.getOrCreatePassphraseText(
            context = context,
            mayReplaceUnusableMaterial = true
        )

        assertTrue(recovered.isNotBlank())
        assertEquals(recovered, DbKeyStore.getOrCreatePassphraseText(context))
    }

    @Test
    fun appDatabaseRecoversFromOrphanedKeyMaterialOnADeviceWithoutClinicalDatabase() {
        context.getSharedPreferences(V2_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(V2_VERSION_KEY, 2)
            .putString(V2_CIPHERTEXT_KEY, "incomplete")
            .commit()

        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Success)
        val db = (result as DbProvider.DbOpenResult.Success).db
        assertTrue(db.openHelper.writableDatabase.isOpen)
        assertEquals(
            false,
            runBlocking { db.patientDao().existsByInternalCode("nonexistent-audit-code") }
        )
    }

    @Test(expected = com.gipogo.rhctools.data.security.DbKeyStoreException::class)
    fun incompleteMaterialFailsClosedWhenDatabaseMayContainData() {
        context.getSharedPreferences(V2_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(V2_VERSION_KEY, 2)
            .putString(V2_CIPHERTEXT_KEY, "incomplete")
            .commit()

        DbKeyStore.getOrCreatePassphraseText(
            context = context,
            mayReplaceUnusableMaterial = false
        )
    }

    @Test
    fun lostKeyForClinicalDatabaseFailsClosedWithoutDeletingOrChangingTheFile() = runBlocking {
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        db.patientDao().insert(
            PatientEntity(
                id = UUID.randomUUID().toString(),
                internalCode = "KEY-LOSS-AUDIT",
                displayName = "Paciente de auditoría",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        AppDatabase.clearInstance()

        val databaseFile = context.getDatabasePath("gipogo_rhc_tools.db")
        assertTrue(databaseFile.exists())
        val originalHash = sha256(databaseFile.readBytes())

        // Simula pérdida del material criptográfico por restauración incompleta o
        // limpieza del almacén seguro, conservando la base clínica cifrada.
        DbKeyStore.clear(context)
        val result = DbProvider.getResult(context)

        assertTrue(result is DbProvider.DbOpenResult.Failure)
        assertTrue(databaseFile.exists())
        assertEquals(originalHash, sha256(databaseFile.readBytes()))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

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
