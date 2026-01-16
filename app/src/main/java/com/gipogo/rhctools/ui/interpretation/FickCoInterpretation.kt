package com.gipogo.rhctools.ui.interpretation

import androidx.compose.ui.graphics.Color
import com.gipogo.rhctools.R

/**
 * Interpretación basada en Cardiac Output (CO).
 * Normal verde, extremos amarillos.
 */
object FickCoInterpretation {

    private const val MIN_SCALE = 0.0
    private const val MAX_SCALE = 10.0

    private const val T1 = 4.0
    private const val T2 = 8.0

    private val normalGreen = Color(0xFF2ECC71)
    private val warnAmber = Color(0xFFF1C40F)

    val spec: InterpretationSpec = InterpretationSpec(
        minScale = MIN_SCALE,
        maxScale = MAX_SCALE,
        t1 = T1,
        t2 = T2,
        titleRes = R.string.interp_fick_co_title,
        unitRes = R.string.common_unit_lmin,

        normalLabelRes = R.string.interp_low_output,
        borderlineLabelRes = R.string.interp_normal_flow,
        elevatedLabelRes = R.string.interp_high_output,

        normalRangeRes = R.string.interp_fick_co_low_range,
        borderlineRangeRes = R.string.interp_fick_co_normal_range,
        elevatedRangeRes = R.string.interp_fick_co_high_range,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = warnAmber,
            midColor = normalGreen,
            rightColor = warnAmber
        )
    )
}
