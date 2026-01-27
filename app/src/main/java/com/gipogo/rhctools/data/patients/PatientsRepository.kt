package com.gipogo.rhctools.data.patients

import android.content.Context
import androidx.room.withTransaction
import com.gipogo.rhctools.core.result.DataError
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.PatientCodeGenerator
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.dao.PatientWithTags
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.PatientTagCrossRef
import com.gipogo.rhctools.data.db.entities.TagEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PatientsRepository private constructor(
    private val appContext: Context
) {
    private val db by lazy { DbProvider.get(appContext) }
    private val patientDao by lazy { db.patientDao() }
    private val studyDao by lazy { db.studyDao() }
    private val tagDao by lazy { db.tagDao() }

    fun observePatients(query: String?): Flow<List<PatientEntity>> =
        patientDao.list(query)

    fun observePatientsFiltered(
        query: String?,
        tagKeys: Set<String>,
        fromMillis: Long?
    ): Flow<List<PatientDao.PatientWithLastStudyRow>> {
        val cleanedQuery = query?.trim().takeIf { !it.isNullOrBlank() }
        val keys = tagKeys.toList().sorted()
        return patientDao.observePatientsFiltered(
            q = cleanedQuery,
            tagKeys = keys,
            tagKeysCount = keys.size,
            fromMillis = fromMillis
        )
    }

    suspend fun generateUniqueCode(prefix: String = "GIP"): DataResult<String> {
        return try {
            val code = PatientCodeGenerator.generateUniqueInternalCode(patientDao, prefix = prefix)
            DataResult.Success(code)
        } catch (e: Exception) {
            DataResult.Failure(DataError.Unknown(e.message))
        }
    }

    suspend fun getPatient(id: String): DataResult<PatientEntity> {
        return try {
            val p = patientDao.getById(id) ?: return DataResult.Failure(DataError.NotFound)
            DataResult.Success(p)
        } catch (e: Exception) {
            DataResult.Failure(DataError.Db(e.message))
        }
    }

    suspend fun getPatientWithTags(id: String): DataResult<PatientWithTags> {
        return try {
            val p = tagDao.getPatientWithTags(id) ?: return DataResult.Failure(DataError.NotFound)
            DataResult.Success(p)
        } catch (e: Exception) {
            DataResult.Failure(DataError.Db(e.message))
        }
    }

    suspend fun createPatient(
        internalCode: String,
        displayName: String?,
        sex: String?,
        birthDateMillis: Long?,
        notes: String?,
        weightKg: Double? = null,
        heightCm: Double? = null,
        tagKeys: List<String> = emptyList()
    ): DataResult<String> {
        return try {
            val now = System.currentTimeMillis()
            val code = internalCode.trim().uppercase()
            val name = displayName?.trim()?.takeIf { it.isNotBlank() }

            if (code.isBlank()) return DataResult.Failure(DataError.Validation("internalCode", "Empty"))
            if (patientDao.existsByInternalCode(code)) return DataResult.Failure(DataError.DuplicateCode)

            val patientId = UUID.randomUUID().toString()

            db.withTransaction {
                val entity = PatientEntity(
                    id = patientId,
                    internalCode = code,
                    displayName = name,
                    sex = sex,
                    birthDateMillis = birthDateMillis,
                    weightKg = weightKg,
                    heightCm = heightCm,
                    notes = notes,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                patientDao.insert(entity)

                upsertPatientTagsTx(patientId = patientId, tagKeys = tagKeys)
            }

            DataResult.Success(patientId)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            DataResult.Failure(DataError.DuplicateCode)
        } catch (e: Exception) {
            DataResult.Failure(DataError.Unknown(e.message))
        }
    }

    suspend fun updatePatient(
        id: String,
        internalCode: String,
        displayName: String?,
        sex: String?,
        birthDateMillis: Long?,
        notes: String?,
        weightKg: Double? = null,
        heightCm: Double? = null,
        tagKeys: List<String> = emptyList()
    ): DataResult<Unit> {
        return try {
            val existing = patientDao.getById(id)
                ?: return DataResult.Failure(DataError.NotFound)

            val now = System.currentTimeMillis()
            val code = internalCode.trim().uppercase()
            val name = displayName?.trim()?.takeIf { it.isNotBlank() }

            if (code.isBlank()) return DataResult.Failure(DataError.Validation("internalCode", "Empty"))
            if (code != existing.internalCode && patientDao.existsByInternalCode(code)) {
                return DataResult.Failure(DataError.DuplicateCode)
            }

            db.withTransaction {
                val updated = existing.copy(
                    internalCode = code,
                    displayName = name,
                    sex = sex,
                    birthDateMillis = birthDateMillis,
                    notes = notes,
                    weightKg = weightKg,
                    heightCm = heightCm,
                    updatedAtMillis = now
                )

                val rows = patientDao.update(updated)
                if (rows <= 0) throw IllegalStateException("Update returned 0 rows")

                // ✅ Garantía: borra lo anterior y re-inserta solo lo seleccionado.
                upsertPatientTagsTx(patientId = id, tagKeys = tagKeys)
            }

            DataResult.Success(Unit)
        } catch (e: IllegalStateException) {
            DataResult.Failure(DataError.Db(e.message))
        } catch (e: Exception) {
            DataResult.Failure(DataError.Unknown(e.message))
        }
    }

    /**
     * ✅ Replace total (auditabilidad):
     * - limpia tags del paciente
     * - inserta SOLO los actuales (canónicos)
     */
    private suspend fun upsertPatientTagsTx(patientId: String, tagKeys: List<String>) {
        val cleaned = tagKeys
            .mapNotNull { raw -> normalizeTagKey(raw) }
            .distinct()

        tagDao.clearPatientTags(patientId)
        if (cleaned.isEmpty()) return

        tagDao.insertTags(cleaned.map { TagEntity(key = it) })
        tagDao.insertPatientTags(cleaned.map { PatientTagCrossRef(patientId = patientId, tagKey = it) })
    }

    /**
     * ✅ SOLO estos 6 canónicos están permitidos.
     * Cualquier cosa fuera => null (no contamina la BD).
     */
    private fun normalizeTagKey(input: String?): String? {
        val s = input
            ?.trim()
            ?.lowercase()
            ?.replace("-", " ")
            ?.replace("_", " ")
            ?.replace(Regex("\\s+"), " ")
            ?: return null

        if (s.isBlank()) return null

        return when (s) {
            // SHOCK
            "shock", "choque" -> "SHOCK"

            // HF
            "hf", "heart failure", "insuficiencia cardiaca", "insuficiencia cardíaca", "ic" -> "HF"

            // PREOP
            "preop", "pre op", "pre op.", "preoperatorio" -> "PREOP"

            // POST_OP
            "post op", "postop", "post op.", "postoperatorio" -> "POST_OP"

            // PAH_EVAL
            "pah eval", "pah evaluation", "hap eval", "evaluacion hap", "evaluación hap",
            "evaluacion de hap", "evaluación de hap", "hap", "pah" -> "PAH_EVAL"

            // FOLLOW_UP
            "follow up", "followup", "seguimiento" -> "FOLLOW_UP"

            else -> null
        }
    }

    suspend fun deletePatient(id: String): DataResult<Unit> {
        return try {
            db.withTransaction {
                studyDao.deleteByPatientId(id)
                val rows = patientDao.deleteById(id)
                if (rows <= 0) throw IllegalStateException("NotFound")
            }
            DataResult.Success(Unit)
        } catch (e: IllegalStateException) {
            DataResult.Failure(DataError.NotFound)
        } catch (e: Exception) {
            DataResult.Failure(DataError.Unknown(e.message))
        }
    }

    companion object {
        @Volatile private var INSTANCE: PatientsRepository? = null

        fun get(context: Context): PatientsRepository {
            val appCtx = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PatientsRepository(appCtx).also { INSTANCE = it }
            }
        }
    }
}
