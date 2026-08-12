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

@RunWith(AndroidJUnit4::class)
class DbEncryptionMigrationAuditTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile = File(context.cacheDir, "db-encryption-audit.db")
    private val passphrase = "audit-only-passphrase-0123456789"

    @After
    fun cleanUp() {
        listOf("", "-wal", "-shm", "-journal", ".enc_tmp", ".bak_plain").forEach {
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

    private fun hasPlaintextHeader(file: File): Boolean {
        val expected = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val actual = ByteArray(expected.size)
        FileInputStream(file).use { input ->
            assertEquals(expected.size, input.read(actual))
        }
        return actual.contentEquals(expected)
    }
}
