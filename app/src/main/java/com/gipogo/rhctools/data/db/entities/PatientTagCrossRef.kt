package com.gipogo.rhctools.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "patient_tags",
    primaryKeys = ["patientId", "tagKey"],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["key"],
            childColumns = ["tagKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("patientId"),
        Index("tagKey")
    ]
)
data class PatientTagCrossRef(
    val patientId: String,
    val tagKey: String
)
