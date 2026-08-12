package com.gipogo.rhctools.data.security

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/** Migrates a legacy plaintext Room database without ever opening it as plaintext afterward. */
object DbEncryptionMigrator {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun ensureEncrypted(dbFile: File, passphraseText: String) {
        System.loadLibrary("sqlcipher")

        if (recoverInterruptedMigration(dbFile, passphraseText)) return
        if (!dbFile.exists()) return // Room will create it encrypted.

        if (!hasSqliteHeader(dbFile)) {
            verifyEncrypted(dbFile, passphraseText)
            return
        }

        migratePlaintextDatabase(dbFile, passphraseText)
    }

    private fun recoverInterruptedMigration(dbFile: File, passphraseText: String): Boolean {
        val temp = encryptedTemp(dbFile)
        val backup = plaintextBackup(dbFile)

        if (!dbFile.exists()) {
            if (temp.exists()) {
                val tempIsValid = runCatching { verifyEncrypted(temp, passphraseText) }.isSuccess
                if (tempIsValid) {
                    if (!temp.renameTo(dbFile)) {
                        throw DbEncryptionException(
                            DbEncryptionFailure.RECOVERY_FAILED,
                            "No se pudo recuperar la base cifrada temporal"
                        )
                    }
                    deletePlaintextOrThrow(backup)
                    return true
                }
                deleteFileOrThrow(temp, "temporal cifrado inválido")
            }

            if (backup.exists() && !backup.renameTo(dbFile)) {
                throw DbEncryptionException(
                    DbEncryptionFailure.RECOVERY_FAILED,
                    "No se pudo restaurar la copia de seguridad de la base"
                )
            }
            return false
        }

        if (!hasSqliteHeader(dbFile)) {
            verifyEncrypted(dbFile, passphraseText)
            deletePlaintextOrThrow(backup)
            deleteFileOrThrow(temp, "temporal de migración")
            return true
        }

        // The original plaintext database is authoritative before replacement.
        deletePlaintextOrThrow(backup)
        deleteFileOrThrow(temp, "temporal de migración")
        return false
    }

    private fun migratePlaintextDatabase(dbFile: File, passphraseText: String) {
        val parent = dbFile.parentFile ?: throw DbEncryptionException(
            DbEncryptionFailure.MIGRATION_FAILED,
            "La base de datos no tiene un directorio válido"
        )
        if (!parent.exists() && !parent.mkdirs()) {
            throw DbEncryptionException(
                DbEncryptionFailure.STORAGE_UNAVAILABLE,
                "No se pudo preparar el almacenamiento de la base"
            )
        }

        val temp = encryptedTemp(dbFile)
        val backup = plaintextBackup(dbFile)
        deleteFileOrThrow(temp, "temporal de migración")
        deletePlaintextOrThrow(backup)

        try {
            val userVersion = readUserVersionFromPlaintext(dbFile)
            val plain = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, null, null)
            try {
                runCatching { plain.rawExecSQL("PRAGMA wal_checkpoint(FULL);") }
                val path = sqlLiteral(temp.absolutePath)
                val key = sqlLiteral(passphraseText)
                plain.rawExecSQL("ATTACH DATABASE '$path' AS encrypted KEY '$key';")
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                plain.rawExecSQL("DETACH DATABASE encrypted;")
            } finally {
                plain.close()
            }

            val encrypted = SQLiteDatabase.openOrCreateDatabase(temp, passphraseText, null, null, null)
            try {
                encrypted.rawExecSQL("PRAGMA user_version = $userVersion;")
            } finally {
                encrypted.close()
            }
            verifyEncrypted(temp, passphraseText)
            syncFile(temp)

            deleteAuxFiles(dbFile)
            if (!dbFile.renameTo(backup)) {
                throw DbEncryptionException(
                    DbEncryptionFailure.MIGRATION_FAILED,
                    "No se pudo proteger la base original durante la migración"
                )
            }
            if (!temp.renameTo(dbFile)) {
                backup.renameTo(dbFile)
                throw DbEncryptionException(
                    DbEncryptionFailure.MIGRATION_FAILED,
                    "No se pudo instalar la base cifrada"
                )
            }

            try {
                verifyEncrypted(dbFile, passphraseText)
            } catch (error: Throwable) {
                dbFile.delete()
                backup.renameTo(dbFile)
                throw DbEncryptionException(
                    DbEncryptionFailure.VERIFICATION_FAILED,
                    "La base cifrada no superó la verificación; se restauró el original",
                    error
                )
            }

            deletePlaintextOrThrow(backup)
        } catch (error: DbEncryptionException) {
            throw error
        } catch (error: Throwable) {
            throw DbEncryptionException(
                DbEncryptionFailure.MIGRATION_FAILED,
                "No se pudo cifrar la base de datos",
                error
            )
        } finally {
            deleteFileOrThrow(temp, "temporal de migración")
        }
    }

    private fun verifyEncrypted(file: File, passphraseText: String) {
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(file, passphraseText, null, null, null)
            try {
                db.rawQuery("SELECT count(*) FROM sqlite_master;", null).use { cursor ->
                    if (!cursor.moveToFirst()) error("No se pudo leer el esquema")
                }
            } finally {
                db.close()
            }
        } catch (error: Throwable) {
            throw DbEncryptionException(
                DbEncryptionFailure.KEY_OR_DATABASE_INVALID,
                "La clave no abre la base cifrada o el archivo está dañado",
                error
            )
        }
    }

    private fun readUserVersionFromPlaintext(file: File): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(file, "", null, null, null)
        return try {
            db.rawQuery("PRAGMA user_version;", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            db.close()
        }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(SQLITE_HEADER.size)
                input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
            }
        } catch (error: Throwable) {
            throw DbEncryptionException(
                DbEncryptionFailure.STORAGE_UNAVAILABLE,
                "No se pudo inspeccionar la base de datos",
                error
            )
        }
    }

    private fun syncFile(file: File) {
        RandomAccessFile(file, "rw").use { it.fd.sync() }
    }

    private fun deletePlaintextOrThrow(file: File) {
        if (!file.exists()) return
        if (file.delete()) return
        runCatching {
            RandomAccessFile(file, "rw").use {
                it.setLength(0)
                it.fd.sync()
            }
        }
        if (file.exists() && !file.delete()) {
            throw DbEncryptionException(
                DbEncryptionFailure.PLAINTEXT_CLEANUP_FAILED,
                "No se pudo eliminar una copia temporal sin cifrar"
            )
        }
    }

    private fun deleteFileOrThrow(file: File, description: String) {
        if (file.exists() && !file.delete()) {
            throw DbEncryptionException(
                DbEncryptionFailure.STORAGE_UNAVAILABLE,
                "No se pudo eliminar $description"
            )
        }
        deleteAuxFiles(file)
    }

    private fun deleteAuxFiles(file: File) {
        val base = file.absolutePath
        listOf(File("$base-wal"), File("$base-shm"), File("$base-journal")).forEach { aux ->
            if (aux.exists() && !aux.delete()) {
                throw DbEncryptionException(
                    DbEncryptionFailure.STORAGE_UNAVAILABLE,
                    "No se pudo limpiar un archivo auxiliar de la base"
                )
            }
        }
    }

    private fun encryptedTemp(dbFile: File) = File(dbFile.parentFile, dbFile.name + ".enc_tmp")
    private fun plaintextBackup(dbFile: File) = File(dbFile.parentFile, dbFile.name + ".bak_plain")
    private fun sqlLiteral(value: String) = value.replace("'", "''")
}

enum class DbEncryptionFailure {
    KEY_OR_DATABASE_INVALID,
    MIGRATION_FAILED,
    RECOVERY_FAILED,
    PLAINTEXT_CLEANUP_FAILED,
    STORAGE_UNAVAILABLE,
    VERIFICATION_FAILED
}

class DbEncryptionException(
    val failure: DbEncryptionFailure,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
