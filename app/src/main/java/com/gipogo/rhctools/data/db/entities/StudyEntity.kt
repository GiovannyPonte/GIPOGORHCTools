package com.gipogo.rhctools.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "studies",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["startedAtMillis"])
    ]
)
data class StudyEntity(
    @PrimaryKey val id: String,                 // UUID string
    val patientId: String,
    val type: String,                           // ej "RHC", "PH workup", etc.
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
