package com.gipogo.rhctools.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.PatientTagCrossRef
import com.gipogo.rhctools.data.db.entities.TagEntity
import kotlinx.coroutines.flow.Flow

data class PatientWithTags(
    @Embedded val patient: PatientEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "key",
        associateBy = Junction(
            value = PatientTagCrossRef::class,
            parentColumn = "patientId",
            entityColumn = "tagKey"
        )
    )
    val tags: List<TagEntity>
)

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPatientTags(refs: List<PatientTagCrossRef>): List<Long>

    @Query("DELETE FROM patient_tags WHERE patientId = :patientId")
    suspend fun clearPatientTags(patientId: String): Int

    @Transaction
    @Query("SELECT * FROM patients WHERE id = :patientId LIMIT 1")
    suspend fun getPatientWithTags(patientId: String): PatientWithTags?

    @Transaction
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
    fun listPatientsWithTags(q: String?): Flow<List<PatientWithTags>>
}
