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
 * key = identificador estable para autofill entre pantallas.
 * label = texto para mostrar (puede cambiar por idioma/UX sin romper autofill).
 */
data class LineItem(
    val key: String? = null,
    val label: String,
    val value: String,
    val unit: String? = null,
    val detail: String? = null
)

/**
 * Keys estables para compartir datos entre calculadoras.
 */
object SharedKeys {
    // Fick
    const val CO_LMIN = "CO_LMIN"
    const val BSA_M2 = "BSA_M2"

    // SVR / CPO
    const val MAP_MMHG = "MAP_MMHG"
    const val CVP_MMHG = "CVP_MMHG"

    // PVR
    const val MPAP_MMHG = "MPAP_MMHG"
    const val PAWP_MMHG = "PAWP_MMHG"

    // PAPi (por si quieres prefill futuro)
    const val PASP_MMHG = "PASP_MMHG"
    const val PADP_MMHG = "PADP_MMHG"
    const val RAP_MMHG = "RAP_MMHG"
}
