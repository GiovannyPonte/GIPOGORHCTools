package com.gipogo.rhctools.data.studies

import android.content.Context
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StudiesRepository private constructor(
    private val appContext: Context
) {
    private val db by lazy { DbProvider.get(appContext) }
    private val rhcStudyDao by lazy { db.rhcStudyDao() }

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

