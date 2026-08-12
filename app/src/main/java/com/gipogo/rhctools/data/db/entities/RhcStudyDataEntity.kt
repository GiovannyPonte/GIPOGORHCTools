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

    // --------------------------------------------------------------------
    // CO / CI por método + CO activo (seleccionado)
    // --------------------------------------------------------------------
    // NOTA: estos campos permiten coexistencia Fick + TD sin pisarse.
    val cardiacOutputFickLMin: Double? = null,
    val cardiacIndexFickLMinM2: Double? = null,

    val cardiacOutputTdLMin: Double? = null,
    val cardiacIndexTdLMinM2: Double? = null,

    // CO que alimenta cálculos derivados (SVR/PVR/CPO/etc.)
    val cardiacOutputSelectedLMin: Double? = null,
    val cardiacIndexSelectedLMinM2: Double? = null,
    val coSelectedMethod: String? = null,      // "FICK" | "TD"
    val coSelectionReason: String? = null,     // "ONLY_ONE_AVAILABLE" | "LAST_CALCULATED" | "AUTO_DEFAULT" | "USER_SELECTED" | "LEGACY_MIGRATION"

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
) {

    /**
     * Último firewall local: evitar NaN/Infinity en columnas REAL.
     * Si un Double? no es finito => null.
     *
     * Nota: si ya existe una extensión con el mismo nombre, el miembro tiene prioridad.
     */
    fun sanitizedForDb(): RhcStudyDataEntity {
        fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
        fun Double?.finitePositiveOrNull(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

        return this.copy(
            // Anthropometrics
            weightKg = weightKg.finitePositiveOrNull(),
            heightCm = heightCm.finitePositiveOrNull(),
            bsaM2 = bsaM2.finitePositiveOrNull(),

            // Fick inputs
            saO2Percent = saO2Percent.finiteOrNull(),
            svO2Percent = svO2Percent.finiteOrNull(),
            hemoglobinGdl = hemoglobinGdl.finitePositiveOrNull(),
            heartRateBpm = heartRateBpm.finitePositiveOrNull(),
            vo2MlMin = vo2MlMin.finitePositiveOrNull(),

            // Pressures
            mapMmHg = mapMmHg.finiteOrNull(),
            rapMmHg = rapMmHg.finiteOrNull(),
            paspMmHg = paspMmHg.finiteOrNull(),
            padpMmHg = padpMmHg.finiteOrNull(),
            mpapMmHg = mpapMmHg.finiteOrNull(),
            pawpMmHg = pawpMmHg.finiteOrNull(),

            // Legacy outputs (mantener como selected)
            cardiacOutputLMin = cardiacOutputLMin.finitePositiveOrNull(),
            cardiacIndexLMinM2 = cardiacIndexLMinM2.finitePositiveOrNull(),

            // Per-method outputs
            cardiacOutputFickLMin = cardiacOutputFickLMin.finitePositiveOrNull(),
            cardiacIndexFickLMinM2 = cardiacIndexFickLMinM2.finitePositiveOrNull(),
            cardiacOutputTdLMin = cardiacOutputTdLMin.finitePositiveOrNull(),
            cardiacIndexTdLMinM2 = cardiacIndexTdLMinM2.finitePositiveOrNull(),

            // Selected outputs
            cardiacOutputSelectedLMin = cardiacOutputSelectedLMin.finitePositiveOrNull(),
            cardiacIndexSelectedLMinM2 = cardiacIndexSelectedLMinM2.finitePositiveOrNull(),

            // Derived
            svrWood = svrWood.finiteOrNull(),
            svrDyn = svrDyn.finiteOrNull(),
            pvrWood = pvrWood.finiteOrNull(),
            pvrDyn = pvrDyn.finiteOrNull(),
            papi = papi.finiteOrNull(),
            cardiacPowerW = cardiacPowerW.finiteOrNull(),
            cardiacPowerIndexWm2 = cardiacPowerIndexWm2.finiteOrNull()
        )
    }
}
