package com.gipogo.rhctools.workshop.persistence

import android.content.Context

/**
 * Marcador mínimo de recuperación ante muerte del proceso.
 * Sólo contiene IDs internos; los datos clínicos permanecen en Room/SQLCipher.
 */
object WorkshopRecoveryStore {

    data class PendingStudy(
        val patientId: String,
        val studyId: String,
        val startedAtMillis: Long
    )

    @Synchronized
    fun markActive(context: Context, patientId: String, studyId: String, startedAtMillis: Long) {
        require(patientId.isNotBlank()) { "patientId vacío" }
        require(studyId.isNotBlank()) { "studyId vacío" }

        val committed = preferences(context).edit()
            .putString(KEY_PATIENT_ID, patientId)
            .putString(KEY_STUDY_ID, studyId)
            .putLong(KEY_STARTED_AT, startedAtMillis)
            .commit()
        if (!committed) {
            throw WorkshopRecoveryException("No se pudo guardar el marcador de recuperación")
        }
    }

    @Synchronized
    fun read(context: Context): PendingStudy? {
        val prefs = preferences(context)
        val patientId = prefs.getString(KEY_PATIENT_ID, null)
        val studyId = prefs.getString(KEY_STUDY_ID, null)
        if (patientId.isNullOrBlank() && studyId.isNullOrBlank()) return null
        if (patientId.isNullOrBlank() || studyId.isNullOrBlank()) {
            clear(context)
            return null
        }
        return PendingStudy(
            patientId = patientId,
            studyId = studyId,
            startedAtMillis = prefs.getLong(KEY_STARTED_AT, 0L)
        )
    }

    @Synchronized
    fun clear(context: Context) {
        if (!preferences(context).edit().clear().commit()) {
            throw WorkshopRecoveryException("No se pudo limpiar el marcador de recuperación")
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "workshop_recovery_prefs"
    private const val KEY_PATIENT_ID = "patient_id"
    private const val KEY_STUDY_ID = "study_id"
    private const val KEY_STARTED_AT = "started_at"
}

class WorkshopRecoveryException(message: String) : IllegalStateException(message)
