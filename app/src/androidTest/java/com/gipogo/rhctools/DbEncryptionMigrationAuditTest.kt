package com.gipogo.rhctools

import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.data.security.DbEncryptionException
import com.gipogo.rhctools.data.security.DbEncryptionMigrator
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class DbEncryptionMigrationAuditTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile = File(context.cacheDir, "db-encryption-audit.db")
    private val passphrase = "audit-only-passphrase-0123456789"

    init {
        System.loadLibrary("sqlcipher")
    }

    @After
    fun cleanUp() {
        listOf("", "-wal", "-shm", "-journal", ".enc_tmp", ".bak_plain", ".invalid_encrypted", ".enc_tmp.invalid_encrypted").forEach {
            File(dbFile.absolutePath + it).delete()
        }
    }

    @Test
    fun plaintextDatabaseIsMigratedAndRemainsReadableOnlyWithTheKey() {
        cleanUp()
        AndroidSQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE audit_record (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO audit_record(value) VALUES ('clinical-value')")
            db.execSQL("PRAGMA user_version = 8")
        }

        assertTrue(hasPlaintextHeader(dbFile))
        DbEncryptionMigrator.ensureEncrypted(dbFile, passphrase)

        assertFalse(hasPlaintextHeader(dbFile))
        assertFalse(File(dbFile.absolutePath + ".bak_plain").exists())
        assertFalse(File(dbFile.absolutePath + ".enc_tmp").exists())

        CipherSQLiteDatabase.openOrCreateDatabase(dbFile, passphrase, null, null, null).use { db ->
            db.rawQuery("SELECT value FROM audit_record", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("clinical-value", cursor.getString(0))
            }
            db.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(8, cursor.getInt(0))
            }
        }

        var rejectedWrongKey = false
        try {
            DbEncryptionMigrator.ensureEncrypted(dbFile, "wrong-key")
        } catch (_: DbEncryptionException) {
            rejectedWrongKey = true
        }
        assertTrue("An encrypted database must fail closed with a wrong key", rejectedWrongKey)
    }

    @Test
    fun wrongKeyDoesNotModifyExistingEncryptedDatabase() {
        createEncryptedDatabase(dbFile, passphrase, "protected-value")
        val before = sha256(dbFile)

        val failure = runCatching {
            DbEncryptionMigrator.ensureEncrypted(dbFile, "wrong-key")
        }.exceptionOrNull()

        assertTrue(failure is DbEncryptionException)
        assertEquals(before, sha256(dbFile))
        assertEquals("protected-value", readEncryptedValue(dbFile, passphrase))
    }

    @Test
    fun interruptedMigrationWithMissingMainPromotesValidEncryptedTemp() {
        val temp = File(dbFile.absolutePath + ".enc_tmp")
        createEncryptedDatabase(temp, passphrase, "from-temp")

        DbEncryptionMigrator.ensureEncrypted(dbFile, passphrase)

        assertTrue(dbFile.exists())
        assertFalse(temp.exists())
        assertEquals("from-temp", readEncryptedValue(dbFile, passphrase))
    }

    @Test
    fun unreadableMainWithPlaintextBackupFailsClosedWithoutReplacingEitherArtifact() {
        val backup = File(dbFile.absolutePath + ".bak_plain")
        createPlaintextDatabase(backup, "from-backup")
        dbFile.writeBytes(ByteArray(64) { 0x5A })
        val corruptHash = sha256(dbFile)
        val backupHash = sha256(backup)

        val failure = runCatching {
            DbEncryptionMigrator.ensureEncrypted(dbFile, passphrase)
        }.exceptionOrNull()

        assertTrue(failure is DbEncryptionException)
        assertEquals(corruptHash, sha256(dbFile))
        assertEquals(backupHash, sha256(backup))
        assertFalse(File(dbFile.absolutePath + ".invalid_encrypted").exists())
    }

    @Test
    fun missingMainWithCorruptTempRestoresValidPlaintextBackupAndPreservesTempForensics() {
        val temp = File(dbFile.absolutePath + ".enc_tmp")
        val backup = File(dbFile.absolutePath + ".bak_plain")
        temp.writeBytes(ByteArray(64) { 0x41 })
        val tempHash = sha256(temp)
        createPlaintextDatabase(backup, "backup-after-interruption")

        DbEncryptionMigrator.ensureEncrypted(dbFile, passphrase)

        val archivedTemp = File(temp.parentFile, temp.name + ".invalid_encrypted")
        assertTrue(archivedTemp.exists())
        assertEquals(tempHash, sha256(archivedTemp))
        assertFalse(backup.exists())
        assertEquals("backup-after-interruption", readEncryptedValue(dbFile, passphrase))
    }

    @Test
    fun existingForensicArchiveStopsRecoveryWithoutOverwritingAnyArtifact() {
        val temp = File(dbFile.absolutePath + ".enc_tmp")
        val backup = File(dbFile.absolutePath + ".bak_plain")
        val archivedTemp = File(dbFile.absolutePath + ".enc_tmp.invalid_encrypted")
        temp.writeBytes(ByteArray(64) { 0x42 })
        archivedTemp.writeBytes(ByteArray(64) { 0x43 })
        createPlaintextDatabase(backup, "backup-must-remain")
        val tempHash = sha256(temp)
        val archiveHash = sha256(archivedTemp)
        val backupHash = sha256(backup)

        val failure = runCatching {
            DbEncryptionMigrator.ensureEncrypted(dbFile, passphrase)
        }.exceptionOrNull()

        assertTrue(failure is DbEncryptionException)
        assertEquals(tempHash, sha256(temp))
        assertEquals(archiveHash, sha256(archivedTemp))
        assertEquals(backupHash, sha256(backup))
        assertFalse(dbFile.exists())
    }

    @Test
    fun corruptMainWithoutValidRecoveryCopyFailsClosedAndPreservesHash() {
        dbFile.writeBytes(ByteArray(64) { 0x33 })
        val before = sha256(dbFile)

        val failure = runCatching {
            DbEncryptionMigrator.ensureEncrypted(dbFile, passphrase)
        }.exceptionOrNull()

        assertTrue(failure is DbEncryptionException)
        assertEquals(before, sha256(dbFile))
        assertFalse(File(dbFile.absolutePath + ".invalid_encrypted").exists())
    }

    private fun createPlaintextDatabase(file: File, value: String) {
        AndroidSQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE audit_record (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO audit_record(value) VALUES (?)", arrayOf(value))
            db.execSQL("PRAGMA user_version = 8")
        }
    }

    private fun createEncryptedDatabase(file: File, key: String, value: String) {
        CipherSQLiteDatabase.openOrCreateDatabase(file, key, null, null, null).use { db ->
            db.rawExecSQL("CREATE TABLE audit_record (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO audit_record(value) VALUES (?)", arrayOf(value))
            db.rawExecSQL("PRAGMA user_version = 8")
        }
    }

    private fun readEncryptedValue(file: File, key: String): String =
        CipherSQLiteDatabase.openDatabase(
            file.absolutePath,
            key,
            null,
            CipherSQLiteDatabase.OPEN_READONLY,
            null,
            null
        ).use { db ->
            db.rawQuery("SELECT value FROM audit_record LIMIT 1", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
        }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private fun hasPlaintextHeader(file: File): Boolean {
        val expected = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val actual = ByteArray(expected.size)
        FileInputStream(file).use { input ->
            assertEquals(expected.size, input.read(actual))
        }
        return actual.contentEquals(expected)
    }
}
