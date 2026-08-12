package com.gipogo.rhctools.ui.interpretation

import androidx.compose.ui.graphics.Color
import com.gipogo.rhctools.R

/**
 * PAPi (Pulmonary Artery Pulsatility Index) = (PASP - PADP) / RAP.
 * Riesgo es unilateral: PAPi bajo = peor.
 *
 * Umbrales:
 * - < 1.0  : alto riesgo (rojo)
 * - 1.0–1.5: intermedio (amarillo)
 * - > 1.5  : adecuado (verde)
 */
object PapiInterpretation {

    private const val MIN_SCALE = 0.0
    private const val MAX_SCALE = 4.0

    private const val T1 = 1.0
    private const val T2 = 1.5

    private val alertRed = Color(0xFFE74C3C)
    private val warnAmber = Color(0xFFF1C40F)
    private val normalGreen = Color(0xFF2ECC71)

    val spec: InterpretationSpec = InterpretationSpec(
        minScale = MIN_SCALE,
        maxScale = MAX_SCALE,
        t1 = T1,
        t2 = T2,

        titleRes = R.string.interp_papi_title,
        unitRes = R.string.common_unit_none, // unidad vacía/none

        normalLabelRes = R.string.interp_high_risk,        // izquierda
        borderlineLabelRes = R.string.interp_intermediate, // centro
        elevatedLabelRes = R.string.interp_preserved,      // derecha

        normalRangeRes = R.string.interp_papi_high_risk_range,
        borderlineRangeRes = R.string.interp_papi_intermediate_range,
        elevatedRangeRes = R.string.interp_papi_preserved_range,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = alertRed,
            midColor = warnAmber,
            rightColor = normalGreen,

            pillLeftAccent = alertRed,
            pillMidAccent = warnAmber,
            pillRightAccent = normalGreen
        )
    )
}
