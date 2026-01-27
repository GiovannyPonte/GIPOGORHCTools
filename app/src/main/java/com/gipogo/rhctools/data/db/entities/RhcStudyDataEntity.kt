package com.gipogo.rhctools.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rhc_study_data",
    foreignKeys = [
        ForeignKey(
            entity = StudyEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["studyId"], unique = true), // 1:1 con Study
        Index(value = ["updatedAtMillis"])
    ]
)
data class RhcStudyDataEntity(
    @PrimaryKey val id: String,          // UUID
    val studyId: String,                 // FK -> studies.id (único)

    // -------------------------
    // Anthropometrics used (snapshot for this study)
    // -------------------------
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val bsaM2: Double? = null,           // opcional, si lo calculas/permitas

    // -------------------------
    // Fick inputs
    // -------------------------
    val saO2Percent: Double? = null,      // 0–100
    val svO2Percent: Double? = null,      // 0–100
    val hemoglobinGdl: Double? = null,    // g/dL
    val heartRateBpm: Double? = null,     // bpm

    // Si en tu UI VO2 es estimado por fórmulas, puedes guardar solo el resultado usado
    val vo2MlMin: Double? = null,         // mL/min (si aplicas)
    val vo2Mode: String? = null,          // "ESTIMATED" | "ENTERED" (opcional)

    // -------------------------
    // Hemodynamics (pressures)
    // -------------------------
    val mapMmHg: Double? = null,          // mean arterial pressure
    val rapMmHg: Double? = null,          // right atrial pressure / CVP

    val paspMmHg: Double? = null,         // pulmonary artery systolic
    val padpMmHg: Double? = null,         // pulmonary artery diastolic
    val mpapMmHg: Double? = null,         // mean pulmonary artery pressure
    val pawpMmHg: Double? = null,         // wedge

    // -------------------------
    // Flows / outputs
    // -------------------------
    val cardiacOutputLMin: Double? = null,
    val cardiacIndexLMinM2: Double? = null,

    // -------------------------
    // Derived results
    // -------------------------
    val svrWood: Double? = null,
    val svrDyn: Double? = null,           // dyn·s·cm^-5
    val pvrWood: Double? = null,
    val pvrDyn: Double? = null,           // dyn·s·cm^-5
    val papi: Double? = null,
    val cardiacPowerW: Double? = null,
    val cardiacPowerIndexWm2: Double? = null,

    // -------------------------
    // Units / config
    // -------------------------
    val svrUnits: String? = null,         // "WOOD" | "DYN"
    val pvrUnits: String? = null,         // "WOOD" | "DYN"
    val coMethod: String? = null,         // "FICK" (por ahora), futuro "TD"

    // -------------------------
    // Audit
    // -------------------------
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
