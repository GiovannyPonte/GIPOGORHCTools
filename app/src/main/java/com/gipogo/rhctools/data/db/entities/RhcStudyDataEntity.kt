package com.gipogo.rhctools.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * RhcStudyDataEntity
 *
 * Tabla 1:1 con StudyEntity (UNIQUE(studyId)).
 *
 * Objetivo clínico:
 * - Guardar un "snapshot" auditable del estudio: inputs crudos + outputs calculados.
 *
 * Reglas de integridad (AUDITORÍA):
 * 1) NO persistir valores no finitos (NaN/Infinity). En SQLite pueden guardarse como REAL y contaminar reportes.
 *    => Si un valor Double no es finito, debe persistirse como null.
 *    => Esta sanitización debe hacerse en la capa de repositorio/mapper ANTES de construir esta entidad.
 * 2) studyId es único: garantiza 1:1 real con StudyEntity.
 * 3) Timestamps createdAtMillis/updatedAtMillis permiten trazabilidad de edición.
 */
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
    val studyId: String,                 // FK -> studies.id (UNIQUE)

    // -------------------------
    // Anthropometrics (snapshot del estudio)
    // -------------------------
    // Regla: si no finito => null
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val bsaM2: Double? = null,           // opcional; si se calcula debe ser >0 y finito

    // -------------------------
    // Fick inputs (snapshot)
    // -------------------------
    // SaO2/SvO2 se esperan en porcentaje 0–100. Regla: si fuera de rango/no finito => null (o no persistir).
    val saO2Percent: Double? = null,      // 0–100
    val svO2Percent: Double? = null,      // 0–100
    val hemoglobinGdl: Double? = null,    // g/dL (>0)
    val heartRateBpm: Double? = null,     // bpm (>0 si se usa para SV)

    // VO2 usado en el cálculo (si aplica)
    val vo2MlMin: Double? = null,         // mL/min (>0)
    val vo2Mode: String? = null,          // "ESTIMATED" | "ENTERED" (opcional)

    // -------------------------
    // Hemodynamics (pressures)
    // -------------------------
    // Regla: si no finito => null
    val mapMmHg: Double? = null,          // Mean arterial pressure
    val rapMmHg: Double? = null,          // Right atrial pressure / CVP

    val paspMmHg: Double? = null,         // Pulmonary artery systolic
    val padpMmHg: Double? = null,         // Pulmonary artery diastolic
    val mpapMmHg: Double? = null,         // Mean pulmonary artery pressure
    val pawpMmHg: Double? = null,         // Pulmonary artery wedge pressure

    // -------------------------
    // Flows / primary outputs
    // -------------------------
    // Regla: si no finito => null
    val cardiacOutputLMin: Double? = null,
    val cardiacIndexLMinM2: Double? = null,

    // -------------------------
    // Derived results (auditables)
    // -------------------------
    // Regla: si no finito => null
    // SVR/PVR: Wood Units y CGS (dyn·s·cm^-5). PAPi y potencia cardíaca.
    val svrWood: Double? = null,
    val svrDyn: Double? = null,           // dyn·s·cm^-5

    val pvrWood: Double? = null,
    val pvrDyn: Double? = null,           // dyn·s·cm^-5

    val papi: Double? = null,

    val cardiacPowerW: Double? = null,        // CPO (W)
    val cardiacPowerIndexWm2: Double? = null, // CPI (W/m²)

    // -------------------------
    // Units / config (metadatos del cálculo)
    // -------------------------
    // Estos campos permiten reconstruir contexto del cálculo.
    val svrUnits: String? = null,         // "WOOD" | "DYN"
    val pvrUnits: String? = null,         // "WOOD" | "DYN"
    val coMethod: String? = null,         // "FICK" | "TD" (futuro)

    // -------------------------
    // Audit timestamps
    // -------------------------
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
