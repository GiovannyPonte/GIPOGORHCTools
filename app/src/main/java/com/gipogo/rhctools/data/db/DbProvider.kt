package com.gipogo.rhctools.data.db

import android.content.Context
import android.util.Log
import com.gipogo.rhctools.data.security.DbKeyStore
import com.gipogo.rhctools.workshop.persistence.WorkshopRecoveryStore
import java.io.File

/**
 * DbProvider
 *
 * Fuente única de verdad para obtener la instancia de Room.
 *
 * En Release NO usamos destructive. Si falta una migración, Room puede fallar al abrir.
 * Este provider ofrece un resultado tipado para que la capa superior (UI/arranque)
 * pueda manejar el error de forma controlada (pantalla de DB incompatible, reset bajo confirmación, etc.).
 */
object DbProvider {

    private const val TAG = "DbProvider"

    sealed class DbOpenResult {
        data class Success(val db: AppDatabase) : DbOpenResult()
        data class Failure(
            val error: Throwable,
            val diagnostic: DatabaseErrorDiagnostic = DatabaseErrorDiagnostics.from(error)
        ) : DbOpenResult()
    }

    /**
     * API nueva (recomendada):
     * No revienta la app por defecto; entrega Success/Failure para manejo controlado.
     */
    fun getResult(context: Context): DbOpenResult {
        return try {
            DbOpenResult.Success(AppDatabase.getInstance(context))
        } catch (t: Throwable) {
            val failure = DbOpenResult.Failure(t)
            Log.e(TAG, "Database open failed [${failure.diagnostic.code}] ${failure.diagnostic.technicalDetail}", t)
            failure
        }
    }

    /**
     * API existente (compatibilidad):
     * Mantener para no romper llamados actuales.
     *
     * Recomendación: migra gradualmente a getResult() en el arranque.
     */
    fun get(context: Context): AppDatabase {
        return when (val res = getResult(context)) {
            is DbOpenResult.Success -> res.db
            is DbOpenResult.Failure -> {
                // Lanzamos con un mensaje claro (sin borrar datos).
                // Esto evita "crash opaco" y facilita diagnóstico en logs.
                val cause = res.error
                val msg = buildString {
                    append("Room DB open failed. Possible missing migration in Release. ")
                    append("DB will NOT be destructively recreated automatically. ")
                    append("Cause: ${cause.javaClass.simpleName}: ${cause.message}")
                }
                throw IllegalStateException(msg, cause)
            }
        }
    }

    /**
     * Reset explícito, solo si el usuario lo confirma desde UI.
     * NO se llama automáticamente.
     */
    fun resetDatabase(context: Context) {
        val appContext = context.applicationContext
        AppDatabase.clearInstance()
        deleteDatabaseFiles(appContext, "gipogo_rhc_tools.db")
        WorkshopRecoveryStore.clear(appContext)
        DbKeyStore.clear(appContext)
    }

    /**
     * Irreversibly deletes an unreadable database after explicit user consent,
     * rotates its key and verifies that a new empty encrypted database opens.
     * This method is never called by normal open/retry flows.
     */
    fun permanentlyDeleteUnreadableDatabaseAndStartFresh(context: Context): DbOpenResult.Success {
        val appContext = context.applicationContext
        AppDatabase.clearInstance()
        deleteDatabaseFiles(appContext, "gipogo_rhc_tools.db")
        WorkshopRecoveryStore.clear(appContext)
        DbKeyStore.clear(appContext)

        val result = getResult(appContext)
        if (result is DbOpenResult.Failure) {
            throw IllegalStateException(
                "DB-RESET-CREATE-01: la base anterior se eliminó, pero no se pudo crear la nueva",
                result.error
            )
        }
        return result as DbOpenResult.Success
    }

    private fun deleteDatabaseFiles(context: Context, dbName: String) {
        context.deleteDatabase(dbName)
        val base = context.getDatabasePath(dbName).absolutePath
        File("$base.bak_plain").delete()
        File("$base.enc_tmp").delete()
        File("$base.invalid_encrypted").delete()
        File("$base.enc_tmp.invalid_encrypted").delete()
        File("$base-wal").delete()
        File("$base-shm").delete()
        File("$base-journal").delete()
        val remaining = listOf("", "-wal", "-shm", "-journal", ".bak_plain", ".enc_tmp")
            .map { File(base + it) }
            .filter(File::exists)
        check(remaining.isEmpty()) {
            "DB-RESET-DELETE-01: Android no permitió eliminar ${remaining.joinToString { it.name }}"
        }
    }
}
