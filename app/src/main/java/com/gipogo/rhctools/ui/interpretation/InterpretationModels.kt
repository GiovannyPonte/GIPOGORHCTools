package com.gipogo.rhctools.ui.interpretation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

enum class RiskBand { NORMAL, BORDERLINE, ELEVATED }

/**
 * Paleta configurable por cálculo:
 * - leftColor: segmento izquierdo
 * - midColor: segmento central (normal)
 * - rightColor: segmento derecho
 * - acentos de pills (por defecto igual a los segmentos)
 */
data class GaugePalette(
    val leftColor: Color,
    val midColor: Color,
    val rightColor: Color,
    val pillLeftAccent: Color = leftColor,
    val pillMidAccent: Color = midColor,
    val pillRightAccent: Color = rightColor
)

data class InterpretationSpec(
    val minScale: Double,
    val maxScale: Double,
    val t1: Double,
    val t2: Double,
    @StringRes val titleRes: Int,
    @StringRes val unitRes: Int,
    @StringRes val normalLabelRes: Int,
    @StringRes val borderlineLabelRes: Int,
    @StringRes val elevatedLabelRes: Int,
    @StringRes val normalRangeRes: Int,
    @StringRes val borderlineRangeRes: Int,
    @StringRes val elevatedRangeRes: Int,
    @StringRes val resultLineFormatRes: Int? = null,
    val palette: GaugePalette
)
