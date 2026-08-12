package com.gipogo.rhctools.ui.interpretation

import androidx.compose.ui.graphics.Color
import com.gipogo.rhctools.R

/**
 * Interpretación de Resistencia Vascular Sistémica (RVS/SVR).
 * Normal verde, baja amarilla, alta roja.
 */
object SvrInterpretation {

    private val normalGreen = Color(0xFF2ECC71)
    private val warnAmber = Color(0xFFF1C40F)
    private val alertRed = Color(0xFFE74C3C)

    private const val MIN_DYN = 400.0
    private const val MAX_DYN = 2000.0
    private const val T1_DYN = 800.0
    private const val T2_DYN = 1200.0

    private const val MIN_WU = MIN_DYN / 80.0
    private const val MAX_WU = MAX_DYN / 80.0
    private const val T1_WU = T1_DYN / 80.0
    private const val T2_WU = T2_DYN / 80.0

    val specDynes: InterpretationSpec = InterpretationSpec(
        minScale = MIN_DYN,
        maxScale = MAX_DYN,
        t1 = T1_DYN,
        t2 = T2_DYN,
        titleRes = R.string.interp_svr_title_dynes,
        unitRes = R.string.common_unit_dynes,

        normalLabelRes = R.string.interp_low_svr,
        borderlineLabelRes = R.string.interp_normal_svr,
        elevatedLabelRes = R.string.interp_high_svr,

        normalRangeRes = R.string.interp_svr_low_range_dynes,
        borderlineRangeRes = R.string.interp_svr_normal_range_dynes,
        elevatedRangeRes = R.string.interp_svr_high_range_dynes,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = warnAmber,
            midColor = normalGreen,
            rightColor = alertRed
        )
    )

    val specWu: InterpretationSpec = InterpretationSpec(
        minScale = MIN_WU,
        maxScale = MAX_WU,
        t1 = T1_WU,
        t2 = T2_WU,
        titleRes = R.string.interp_svr_title_wu,
        unitRes = R.string.common_unit_wu_short,

        normalLabelRes = R.string.interp_low_svr,
        borderlineLabelRes = R.string.interp_normal_svr,
        elevatedLabelRes = R.string.interp_high_svr,

        normalRangeRes = R.string.interp_svr_low_range_wu,
        borderlineRangeRes = R.string.interp_svr_normal_range_wu,
        elevatedRangeRes = R.string.interp_svr_high_range_wu,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = warnAmber,
            midColor = normalGreen,
            rightColor = alertRed
        )
    )
}
