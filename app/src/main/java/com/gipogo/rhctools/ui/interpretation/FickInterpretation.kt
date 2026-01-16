package com.gipogo.rhctools.ui.interpretation

import androidx.compose.ui.graphics.Color
import com.gipogo.rhctools.R

/**
 * Interpretación basada en Cardiac Index (CI).
 * (Aunque uses CO en UI, este debe compilar si existe.)
 */
object FickInterpretation {

    private const val MIN_SCALE = 0.0
    private const val MAX_SCALE = 6.0

    private const val T1 = 2.2
    private const val T2 = 4.0

    private val normalGreen = Color(0xFF2ECC71)
    private val warnAmber = Color(0xFFF1C40F)

    val spec: InterpretationSpec = InterpretationSpec(
        minScale = MIN_SCALE,
        maxScale = MAX_SCALE,
        t1 = T1,
        t2 = T2,
        titleRes = R.string.interp_title,
        unitRes = R.string.common_unit_lmin_m2,

        normalLabelRes = R.string.interp_normal,
        borderlineLabelRes = R.string.interp_borderline,
        elevatedLabelRes = R.string.interp_elevated,

        normalRangeRes = R.string.interp_fick_ci_normal_range,
        borderlineRangeRes = R.string.interp_fick_ci_borderline_range,
        elevatedRangeRes = R.string.interp_fick_ci_elevated_range,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = warnAmber,
            midColor = normalGreen,
            rightColor = warnAmber
        )
    )
}
