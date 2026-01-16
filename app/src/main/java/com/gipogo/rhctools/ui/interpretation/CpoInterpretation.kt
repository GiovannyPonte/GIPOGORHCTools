package com.gipogo.rhctools.ui.interpretation

import androidx.compose.ui.graphics.Color
import com.gipogo.rhctools.R

/**
 * Interpretación basada en Cardiac Power Output (CPO) en Watts (W).
 *
 * Bandas (orientativas):
 * - Low: < 0.6 W
 * - Intermediate: 0.6–0.8 W
 * - Adequate: > 0.8 W
 *
 * Paleta pro:
 * Rojo (bajo) -> Amarillo (intermedio) -> Verde (adecuado)
 */
object CpoInterpretation {

    private const val MIN_SCALE = 0.0
    private const val MAX_SCALE = 2.0

    private const val T1 = 0.6
    private const val T2 = 0.8

    private val alertRed = Color(0xFFE74C3C)
    private val warnAmber = Color(0xFFF1C40F)
    private val normalGreen = Color(0xFF2ECC71)

    val spec: InterpretationSpec = InterpretationSpec(
        minScale = MIN_SCALE,
        maxScale = MAX_SCALE,
        t1 = T1,
        t2 = T2,

        titleRes = R.string.interp_cpo_title,
        unitRes = R.string.common_unit_w,

        // Labels (izq/centro/der)
        normalLabelRes = R.string.interp_low_cpo,
        borderlineLabelRes = R.string.interp_intermediate_cpo,
        elevatedLabelRes = R.string.interp_adequate_cpo,

        // Rangos
        normalRangeRes = R.string.interp_cpo_low_range,
        borderlineRangeRes = R.string.interp_cpo_intermediate_range,
        elevatedRangeRes = R.string.interp_cpo_adequate_range,

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
