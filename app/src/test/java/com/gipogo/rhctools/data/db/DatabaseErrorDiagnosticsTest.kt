package com.gipogo.rhctools.data.db

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import com.gipogo.rhctools.data.security.DbEncryptionException
import com.gipogo.rhctools.data.security.DbEncryptionFailure
import com.gipogo.rhctools.data.security.DbKeyStoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseErrorDiagnosticsTest {
    @Test
    fun keystoreFailureHasStableCodeAndKeepsUsefulCause() {
        val diagnostic = DatabaseErrorDiagnostics.from(
            IllegalStateException(
                "Room DB open failed",
                DbKeyStoreException("Android Keystore entry is unavailable")
            )
        )

        assertEquals("DB-KEY-01", diagnostic.code)
        assertEquals(DatabaseErrorCategory.KEY, diagnostic.category)
        assertTrue(diagnostic.technicalDetail.contains("DbKeyStoreException"))
        assertTrue(diagnostic.technicalDetail.contains("unavailable"))
    }

    @Test
    fun encryptionFailureUsesSpecificCodeAndRedactsSensitiveMaterial() {
        val diagnostic = DatabaseErrorDiagnostics.from(
            DbEncryptionException(
                DbEncryptionFailure.KEY_OR_DATABASE_INVALID,
                "Could not open /data/user/0/com.gio.gipogo.rhctools/databases/main; key=secret-value"
            )
        )

        assertEquals("DB-ENC-KEY-01", diagnostic.code)
        assertEquals(DatabaseErrorCategory.ENCRYPTION, diagnostic.category)
        assertTrue(diagnostic.technicalDetail.contains("<datos-app>"))
        assertTrue(diagnostic.technicalDetail.contains("<oculto>"))
        assertFalse(diagnostic.technicalDetail.contains("secret-value"))
    }

    @Test
    fun roomMigrationMessageIsClassifiedAsSchemaFailure() {
        val diagnostic = DatabaseErrorDiagnostics.from(
            IllegalStateException("A migration from 8 to 9 was required but not found")
        )

        assertEquals("DB-SCHEMA-01", diagnostic.code)
        assertEquals(DatabaseErrorCategory.SCHEMA, diagnostic.category)
    }

    @Test
    fun corruptionAndFullStorageHaveActionableStableCodes() {
        assertEquals(
            "DB-CORRUPT-01",
            DatabaseErrorDiagnostics.from(SQLiteDatabaseCorruptException("database disk image is malformed")).code
        )
        assertEquals(
            "DB-STORAGE-FULL-01",
            DatabaseErrorDiagnostics.from(SQLiteFullException("database or disk is full")).code
        )
    }

    @Test
    fun downgradeIsDistinguishedFromGenericMigrationFailure() {
        val diagnostic = DatabaseErrorDiagnostics.from(
            IllegalStateException("Can't downgrade database from version 10 to 9")
        )
        assertEquals("DB-SCHEMA-DOWNGRADE-01", diagnostic.code)
        assertEquals(DatabaseErrorCategory.SCHEMA, diagnostic.category)
    }
}
