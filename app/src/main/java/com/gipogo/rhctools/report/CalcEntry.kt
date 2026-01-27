package com.gipogo.rhctools.report

data class CalcEntry(
    val type: CalcType,
    val timestampMillis: Long,
    val title: String,
    val inputs: List<LineItem>,
    val outputs: List<LineItem>,
    val notes: List<String> = emptyList()
)

enum class CalcType { FICK, SVR, CPO, PAPI, PVR }

/**
 * key = identificador estable para autofill + persistencia auditable.
 * label = texto UI (puede cambiar sin romper BD).
 */
data class LineItem(
    val key: String? = null,
    val label: String,
    val value: String,
    val unit: String? = null,
    val detail: String? = null
)

/**
 * Keys estables compartidas entre calculadoras.
 *
 * ⚠️ REGLAS:
 * - NUNCA incluir studyId aquí
 * - Estas keys deben mapear 1:1 a columnas en RhcStudyDataEntity
 * - Las pantallas solo publican keys; la persistencia las recoge
 */
object SharedKeys {

    // =========================================================
    // CORE FLOW / ANTHROPOMETRICS
    // =========================================================

    /** Cardiac Output (L/min) */
    const val CO_LMIN = "CO_LMIN"

    /** Body Surface Area (m²) */
    const val BSA_M2 = "BSA_M2"

    /** Cardiac Index (L/min/m²) – derivado */
    const val CI_LMIN_M2 = "CI_LMIN_M2"

    /** CO method: "FICK" | "TD" */
    const val CO_METHOD = "CO_METHOD"

    // =========================================================
    // FICK – INPUTS (auditoría completa)
    // =========================================================

    /** Arterial oxygen saturation (%) */
    const val SAO2_PERCENT = "SAO2_PERCENT"

    /** Mixed venous oxygen saturation (%) */
    const val SVO2_PERCENT = "SVO2_PERCENT"

    /** Hemoglobin (g/dL) */
    const val HB_GDL = "HB_GDL"

    /** Heart rate (beats per minute) */
    const val HR_BPM = "HR_BPM"

    /** Oxygen consumption used (mL/min) */
    const val VO2_MLMIN = "VO2_MLMIN"

    /**
     * VO2 mode:
     * - "MEASURED"
     * - "ESTIMATED"
     */
    const val VO2_MODE = "VO2_MODE"

    // =========================================================
    // PRESSURES (mmHg – unidad canónica)
    // =========================================================

    /** Mean arterial pressure */
    const val MAP_MMHG = "MAP_MMHG"

    /** Central venous pressure */
    const val CVP_MMHG = "CVP_MMHG"

    /** Right atrial pressure (preferente sobre CVP) */
    const val RAP_MMHG = "RAP_MMHG"

    /** Pulmonary artery systolic pressure */
    const val PASP_MMHG = "PASP_MMHG"

    /** Pulmonary artery diastolic pressure */
    const val PADP_MMHG = "PADP_MMHG"

    /** Mean pulmonary artery pressure */
    const val MPAP_MMHG = "MPAP_MMHG"

    /** Pulmonary artery wedge pressure */
    const val PAWP_MMHG = "PAWP_MMHG"

    // =========================================================
    // SVR – Systemic Vascular Resistance (OUTPUTS)
    // =========================================================

    /** SVR in Wood Units */
    const val SVR_WOOD = "SVR_WOOD"

    /** SVR in dyn·s·cm⁻⁵ */
    const val SVR_DYN = "SVR_DYN"

    /** SVR units used: "WOOD" | "DYN" */
    const val SVR_UNITS = "SVR_UNITS"

    // =========================================================
    // PVR – Pulmonary Vascular Resistance (OUTPUTS)
    // =========================================================

    /** PVR in Wood Units */
    const val PVR_WOOD = "PVR_WOOD"

    /** PVR in dyn·s·cm⁻⁵ */
    const val PVR_DYN = "PVR_DYN"

    /** PVR units used: "WOOD" | "DYN" */
    const val PVR_UNITS = "PVR_UNITS"

    // =========================================================
    // CPO / CPI – Cardiac Power
    // =========================================================

    /** Cardiac Power Output (W) */
    const val CPO_W = "CPO_W"

    /** Cardiac Power Index (W/m²) */
    const val CPI_W_M2 = "CPI_W_M2"

    // =========================================================
    // PAPi – Pulmonary Artery Pulsatility Index
    // =========================================================

    const val PAPI = "PAPI"
}
