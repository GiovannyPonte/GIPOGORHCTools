package com.gipogo.rhctools.data.db

import android.database.sqlite.SQLiteException
import com.gipogo.rhctools.data.security.DbEncryptionException
import com.gipogo.rhctools.data.security.DbEncryptionFailure
import com.gipogo.rhctools.data.security.DbKeyStoreException
import java.io.IOException
import java.security.GeneralSecurityException

data class DatabaseErrorDiagnostic(
    val code: String,
    val category: DatabaseErrorCategory,
    val technicalDetail: String
)

enum class DatabaseErrorCategory {
    KEY, ENCRYPTION, SCHEMA, SQL, STORAGE, NATIVE_COMPONENT, OPEN
}

object DatabaseErrorDiagnostics {
    fun from(error: Throwable): DatabaseErrorDiagnostic {
        val causes = error.causeSequence().toList()
        val encryption = causes.filterIsInstance<DbEncryptionException>().firstOrNull()

        val code = when {
            encryption != null -> encryption.failure.errorCode
            causes.any { it is DbKeyStoreException || it is GeneralSecurityException } -> "DB-KEY-01"
            causes.any { cause ->
                cause.javaClass.name.contains("Migration", ignoreCase = true) ||
                    cause.message?.contains("migration", ignoreCase = true) == true ||
                    cause.message?.contains("schema", ignoreCase = true) == true
            } -> "DB-SCHEMA-01"
            causes.any { it is SQLiteException || it.javaClass.name.contains("SQLite", ignoreCase = true) } -> "DB-SQL-01"
            causes.any { it is IOException } -> "DB-STORAGE-01"
            causes.any { it is UnsatisfiedLinkError } -> "DB-NATIVE-01"
            else -> "DB-OPEN-01"
        }

        val category = when {
            code.startsWith("DB-KEY") -> DatabaseErrorCategory.KEY
            code.startsWith("DB-ENC") -> DatabaseErrorCategory.ENCRYPTION
            code.startsWith("DB-SCHEMA") -> DatabaseErrorCategory.SCHEMA
            code.startsWith("DB-SQL") -> DatabaseErrorCategory.SQL
            code.startsWith("DB-STORAGE") -> DatabaseErrorCategory.STORAGE
            code.startsWith("DB-NATIVE") -> DatabaseErrorCategory.NATIVE_COMPONENT
            else -> DatabaseErrorCategory.OPEN
        }

        val detail = causes
            .take(4)
            .joinToString(" → ") { cause ->
                val type = cause.javaClass.simpleName.ifBlank { cause.javaClass.name.substringAfterLast('.') }
                val message = cause.message
                    ?.replace(Regex("(?i)(key|passphrase|password)\\s*[=:]\\s*[^,;\\s]+"), "$1=<oculto>")
                    ?.replace(Regex("/data/user/\\d+/[^/]+"), "<datos-app>")
                    ?.replace(Regex("/data/data/[^/]+"), "<datos-app>")
                    ?.take(240)
                    ?.takeIf { it.isNotBlank() }
                if (message == null) type else "$type: $message"
            }

        return DatabaseErrorDiagnostic(
            code = code,
            category = category,
            technicalDetail = detail.ifBlank { error.javaClass.name }
        )
    }

    private fun Throwable.causeSequence(): Sequence<Throwable> = sequence {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = this@causeSequence
        while (current != null && seen.add(current)) {
            yield(current)
            current = current.cause
        }
    }

    private val DbEncryptionFailure.errorCode: String
        get() = when (this) {
            DbEncryptionFailure.KEY_OR_DATABASE_INVALID -> "DB-ENC-KEY-01"
            DbEncryptionFailure.MIGRATION_FAILED -> "DB-ENC-MIG-01"
            DbEncryptionFailure.RECOVERY_FAILED -> "DB-ENC-REC-01"
            DbEncryptionFailure.PLAINTEXT_CLEANUP_FAILED -> "DB-ENC-CLEAN-01"
            DbEncryptionFailure.STORAGE_UNAVAILABLE -> "DB-STORAGE-01"
            DbEncryptionFailure.VERIFICATION_FAILED -> "DB-ENC-VERIFY-01"
        }
}
