package com.gipogo.rhctools.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gipogo.rhctools.data.db.entities.StudyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {

    /**
     * Returns the new rowId.
     * NOTE: Explicit return type avoids KSP/Room "unexpected jvm signature V" issues.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(study: StudyEntity): Long

    /**
     * Returns number of rows updated.
     */
    @Update
    suspend fun update(study: StudyEntity): Int

    /**
     * Returns number of rows deleted.
     */
    @Query("DELETE FROM studies WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * Manual cascade helper (until you add FK CASCADE).
     * Returns number of rows deleted.
     */
    @Query("DELETE FROM studies WHERE patientId = :patientId")
    suspend fun deleteByPatientId(patientId: String): Int

    @Query("SELECT * FROM studies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StudyEntity?

    @Query(
        """
        SELECT * FROM studies
        WHERE patientId = :patientId
        ORDER BY startedAtMillis DESC
        """
    )
    fun listByPatient(patientId: String): Flow<List<StudyEntity>>
}
