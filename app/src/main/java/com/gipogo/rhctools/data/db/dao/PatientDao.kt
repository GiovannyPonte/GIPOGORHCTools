package com.gipogo.rhctools.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gipogo.rhctools.data.db.entities.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: PatientEntity): Long

    @Update
    suspend fun update(patient: PatientEntity): Int

    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE internalCode = :code LIMIT 1")
    suspend fun getByInternalCode(code: String): PatientEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM patients WHERE internalCode = :code)")
    suspend fun existsByInternalCode(code: String): Boolean

    @Query(
        """
        SELECT * FROM patients
        WHERE (:q IS NULL OR :q = '' 
            OR internalCode LIKE '%' || :q || '%' 
            OR displayName LIKE '%' || :q || '%'
            OR notes LIKE '%' || :q || '%')
        ORDER BY updatedAtMillis DESC
        """
    )
    fun list(q: String?): Flow<List<PatientEntity>>

    // ---------------------------
    // Projection: patient + lastStudyAtMillis
    // lastStudyAtMillis = MAX(COALESCE(endedAtMillis, startedAtMillis))
    // ---------------------------
    data class PatientWithLastStudyRow(
        @Embedded val patient: PatientEntity,
        val lastStudyAtMillis: Long?
    )

    /**
     * Filtro combinado:
     * - búsqueda opcional (q)
     * - tags OR (si tagKeysCount>0)
     * - fecha opcional: si fromMillis != null => lastStudyAtMillis >= fromMillis
     *   (y excluye pacientes sin estudio: lastStudyAtMillis IS NOT NULL)
     */
    @Query(
        """
        SELECT p.*,
               MAX(COALESCE(s.endedAtMillis, s.startedAtMillis)) AS lastStudyAtMillis
        FROM patients p
        LEFT JOIN studies s ON s.patientId = p.id
        LEFT JOIN patient_tags pt ON pt.patientId = p.id
        WHERE
            (:q IS NULL OR :q = ''
                OR p.internalCode LIKE '%' || :q || '%'
                OR p.displayName LIKE '%' || :q || '%'
                OR p.notes LIKE '%' || :q || '%')
            AND
            (:tagKeysCount = 0 OR pt.tagKey IN (:tagKeys))
        GROUP BY p.id
        HAVING
            (:fromMillis IS NULL OR (lastStudyAtMillis IS NOT NULL AND lastStudyAtMillis >= :fromMillis))
        ORDER BY
            lastStudyAtMillis DESC,
            p.updatedAtMillis DESC
        """
    )
    fun observePatientsFiltered(
        q: String?,
        tagKeys: List<String>,
        tagKeysCount: Int,
        fromMillis: Long?
    ): Flow<List<PatientWithLastStudyRow>>
}
