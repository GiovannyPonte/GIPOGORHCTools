package com.gipogo.rhctools.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * RhcStudyDao
 *
 * Punto de escritura principal para RhcStudyDataEntity (snapshot 1:1 por studyId).
 *
 * Regla de auditoría crítica (B.4):
 * - SQLite permite guardar NaN/Infinity en columnas REAL.
 * - Eso contamina reportes/PDF y rompe trazabilidad clínica.
 *
 * => Este DAO actúa como "último firewall":
 *    antes de INSERT/UPDATE se sanitizan todos los Double?:
 *    si no es finito (NaN/Inf) -> null.
 *
 * Nota: Este guard rail NO reemplaza la validación del dominio/UI,
 * solo evita que datos inválidos queden persistidos.
 */
@Dao
interface RhcStudyDao {

    /**
     * Inserta un snapshot RHC.
     * OJO: rhc_study_data tiene UNIQUE(studyId). Si ya existe un registro para ese studyId,
     * ABORT lanzará error. Por eso para uso normal debes usar upsertByStudyId().
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(data: RhcStudyDataEntity): Long

    /**
     * Actualiza un snapshot existente (por PK = id).
     * Devuelve número de filas actualizadas.
     */
    @Update
    suspend fun update(data: RhcStudyDataEntity): Int

    @Query("DELETE FROM rhc_study_data WHERE studyId = :studyId")
    suspend fun deleteByStudyId(studyId: String): Int

    @Query("SELECT * FROM rhc_study_data WHERE studyId = :studyId LIMIT 1")
    suspend fun getByStudyId(studyId: String): RhcStudyDataEntity?

    /**
     * ✅ UPSERT 1:1 por studyId.
     *
     * - Si no existe snapshot para el studyId: insert.
     * - Si existe: update manteniendo el mismo 'id' (PK) y el mismo createdAtMillis original.
     *
     * Guard rail adicional:
     * - Sanitiza Double? no finitos (NaN/Inf) -> null antes de persistir.
     */
    @Transaction
    suspend fun upsertByStudyId(data: RhcStudyDataEntity) {
        // B.4: último firewall: evitar NaN/Inf persistidos
        val sanitized = data.sanitizedForDb()

        val existing = getByStudyId(sanitized.studyId)
        if (existing == null) {
            insert(sanitized)
        } else {
            // Mantén PK e instante de creación original.
            // (updatedAtMillis puede cambiar en cada guardado)
            update(
                sanitized.copy(
                    id = existing.id,
                    createdAtMillis = existing.createdAtMillis
                )
            )
        }
    }

    /**
     * ✅ Método opcional: "touch" para marcar updatedAtMillis sin recalcular todo.
     * Útil si quieres forzar orden temporal en queries.
     */
    @Query("UPDATE rhc_study_data SET updatedAtMillis = :nowMillis WHERE studyId = :studyId")
    suspend fun touchByStudyId(studyId: String, nowMillis: Long): Int

    @Transaction
    @Query("SELECT * FROM studies WHERE id = :studyId LIMIT 1")
    suspend fun getStudyWithRhcData(studyId: String): StudyWithRhcData?

    @Transaction
    @Query(
        """
        SELECT * FROM studies
        WHERE patientId = :patientId
        ORDER BY startedAtMillis DESC
        """
    )
    fun listStudiesWithRhcDataByPatient(patientId: String): Flow<List<StudyWithRhcData>>
}
