package com.gipogo.rhctools.workshop.persistence

import android.content.Context
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.workshop.WorkshopSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object WorkshopStudyFactory {

    suspend fun startNewRhcStudy(context: Context, patientId: String): String = withContext(Dispatchers.IO) {
        val db = when (val result = DbProvider.getResult(context.applicationContext)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> throw IllegalStateException(
                result.error.message ?: "Database unavailable",
                result.error
            )
        }
        val studyDao = db.studyDao()

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        val study = StudyEntity(
            id = id,
            patientId = patientId,
            type = "RHC",
            startedAtMillis = now,
            endedAtMillis = null,
            notes = null,
            createdAtMillis = now,
            updatedAtMillis = now
        )

        studyDao.insert(study)
        try {
            WorkshopRecoveryStore.markActive(
                context = context.applicationContext,
                patientId = patientId,
                studyId = id,
                startedAtMillis = now
            )
        } catch (error: Throwable) {
            // No dejar un estudio huérfano si no puede garantizarse recuperación.
            studyDao.deleteById(id)
            throw error
        }
        WorkshopSession.startPatientStudy(patientId = patientId, studyId = id)

// limpiar "workspace" del taller para que el nuevo Study arranque vacío
        ReportStore.clear()
        com.gipogo.rhctools.reset.AppResetBus.resetAll()
// si usas autosave/persistence con coMethod global
        com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave.clearCoMethod()

        id
    }
}
