package com.gipogo.rhctools.ui.interpretation

import androidx.compose.ui.graphics.Color
import com.gipogo.rhctools.R

/**
 * Interpretación SOLO para Resistencia Vascular Pulmonar (PVR).
 * TPR queda excluida deliberadamente.
 */
object PvrInterpretation {

    private val normalGreen = Color(0xFF2ECC71)
    private val warnAmber = Color(0xFFF1C40F)
    private val alertRed  = Color(0xFFE74C3C)

    // ---------- Wood Units ----------
    private const val MIN_WU = 0.0
    private const val MAX_WU = 10.0
    private const val T1_WU = 2.0
    private const val T2_WU = 3.0

    // ---------- Dynes ----------
    private const val MIN_DYN = MIN_WU * 80.0
    private const val MAX_DYN = MAX_WU * 80.0
    private const val T1_DYN = T1_WU * 80.0
    private const val T2_DYN = T2_WU * 80.0

    val specWu: InterpretationSpec = InterpretationSpec(
        minScale = MIN_WU,
        maxScale = MAX_WU,
        t1 = T1_WU,
        t2 = T2_WU,
        titleRes = R.string.interp_pvr_title_wu,
        unitRes = R.string.common_unit_wu_short,

        normalLabelRes = R.string.interp_pvr_normal,
        borderlineLabelRes = R.string.interp_pvr_borderline,
        elevatedLabelRes = R.string.interp_pvr_elevated,

        normalRangeRes = R.string.interp_pvr_normal_range_wu,
        borderlineRangeRes = R.string.interp_pvr_borderline_range_wu,
        elevatedRangeRes = R.string.interp_pvr_elevated_range_wu,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = normalGreen,
            midColor = warnAmber,
            rightColor = alertRed
        )
    )

    val specDynes: InterpretationSpec = InterpretationSpec(
        minScale = MIN_DYN,
        maxScale = MAX_DYN,
        t1 = T1_DYN,
        t2 = T2_DYN,
        titleRes = R.string.interp_pvr_title_dynes,
        unitRes = R.string.common_unit_dynes,

        normalLabelRes = R.string.interp_pvr_normal,
        borderlineLabelRes = R.string.interp_pvr_borderline,
        elevatedLabelRes = R.string.interp_pvr_elevated,

        normalRangeRes = R.string.interp_pvr_normal_range_dynes,
        borderlineRangeRes = R.string.interp_pvr_borderline_range_dynes,
        elevatedRangeRes = R.string.interp_pvr_elevated_range_dynes,

        resultLineFormatRes = R.string.interp_result_line,

        palette = GaugePalette(
            leftColor = normalGreen,
            midColor = warnAmber,
            rightColor = alertRed
        )
    )
}

