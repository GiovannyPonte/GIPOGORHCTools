package com.gipogo.rhctools.data.studies

import android.content.Context
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class StudiesRepository private constructor(
    private val appContext: Context
) {
    private fun rhcStudyDaoOrNull() =
        when (val result = DbProvider.getResult(appContext)) {
            is DbProvider.DbOpenResult.Success -> result.db.rhcStudyDao()
            is DbProvider.DbOpenResult.Failure -> null
        }

    /**
     * Fuente de verdad: Room.
     *
     * Hoy: reutiliza el query existente por paciente + filter en memoria.
     * Mañana: cuando exista un DAO directo por studyId, lo cambias aquí y NO tocas UI/VM.
     */
    fun observeStudyWithRhcData(
        patientId: String,
        studyId: String
    ): Flow<StudyWithRhcData?> {
        val rhcStudyDao = rhcStudyDaoOrNull() ?: return flowOf(null)
        return rhcStudyDao
            .listStudiesWithRhcDataByPatient(patientId)
            .map { list -> list.firstOrNull { it.study.id == studyId } }
    }

    companion object {
        @Volatile private var INSTANCE: StudiesRepository? = null

        fun get(context: Context): StudiesRepository {
            val appCtx = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StudiesRepository(appCtx).also { INSTANCE = it }
            }
        }
    }
}
