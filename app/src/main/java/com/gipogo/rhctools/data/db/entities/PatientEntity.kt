package com.gipogo.rhctools.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patients",
    indices = [
        Index(value = ["internalCode"], unique = true)
    ]
)
data class PatientEntity(
    @PrimaryKey val id: String,
    val internalCode: String,
    val displayName: String? = null,
    val sex: String? = null,

    // ⛔️ quitar
    // val birthYear: Int? = null,

    // ✅ nuevo
    val birthDateMillis: Long? = null,

    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

