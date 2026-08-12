package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.interpretation.InterpretationSpec
import com.gipogo.rhctools.ui.interpretation.RiskBand
import kotlin.math.max
import kotlin.math.min

@Composable
fun InterpretationGaugeCardGeneric(
    value: Double,
    spec: InterpretationSpec,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary

    val trackBg = cs.surface.copy(alpha = 0.55f)
    val textDim = cs.onSurface.copy(alpha = 0.70f)

    val progress = normalize(value, spec.minScale, spec.maxScale)
    val unitText = stringResource(spec.unitRes)

    val band = bandFromValue(value, spec.t1, spec.t2)

    // Etiqueta de categoría (usa labels del spec)
    val bandLabel = when (band) {
        RiskBand.NORMAL -> stringResource(spec.borderlineLabelRes)     // centro
        RiskBand.BORDERLINE -> stringResource(spec.normalLabelRes)     // izq
        RiskBand.ELEVATED -> stringResource(spec.elevatedLabelRes)     // der
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(spec.titleRes).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = cs.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Text(
            text = stringResource(R.string.interp_result),
            style = MaterialTheme.typography.labelMedium,
            color = primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        spec.resultLineFormatRes?.let { fmt ->
            Text(
                text = stringResource(fmt, bandLabel),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }

        GaugeBarContinuous(
            progress01 = progress,
            backgroundColor = trackBg,
            thumbColor = primary,
            minScale = spec.minScale,
            maxScale = spec.maxScale,
            t1 = spec.t1,
            t2 = spec.t2,
            leftColor = spec.palette.leftColor,
            midColor = spec.palette.midColor,
            rightColor = spec.palette.rightColor
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(spec.normalLabelRes).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = textDim,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Text(
                text = stringResource(spec.borderlineLabelRes).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = textDim,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(spec.elevatedLabelRes).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = textDim,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RangePill(
                modifier = Modifier.weight(1f),
                topText = stringResource(spec.normalRangeRes),
                bottomText = unitText,
                accent = spec.palette.pillLeftAccent,
                isActive = band == RiskBand.BORDERLINE
            )
            RangePill(
                modifier = Modifier.weight(1f),
                topText = stringResource(spec.borderlineRangeRes),
                bottomText = unitText,
                accent = spec.palette.pillMidAccent,
                isActive = band == RiskBand.NORMAL
            )
            RangePill(
                modifier = Modifier.weight(1f),
                topText = stringResource(spec.elevatedRangeRes),
                bottomText = unitText,
                accent = spec.palette.pillRightAccent,
                isActive = band == RiskBand.ELEVATED
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun GaugeBarContinuous(
    progress01: Float,
    backgroundColor: Color,
    thumbColor: Color,
    minScale: Double,
    maxScale: Double,
    t1: Double,
    t2: Double,
    leftColor: Color,
    midColor: Color,
    rightColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val trackHeight = 10.dp.toPx()
        val radius = trackHeight / 2f
        val yTop = (size.height - trackHeight) / 2f

        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(0f, yTop),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        fun xOf(v: Double): Float {
            if (maxScale <= minScale) return 0f
            val p = ((v - minScale) / (maxScale - minScale)).toFloat().coerceIn(0f, 1f)
            return size.width * p
        }

        val x1 = xOf(t1)
        val x2 = xOf(t2)

        // Segmento izquierdo
        if (x1 > 0f) {
            clipRect(left = 0f, top = yTop, right = x1, bottom = yTop + trackHeight) {
                drawRoundRect(
                    color = leftColor,
                    topLeft = Offset(0f, yTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }

        // Segmento medio
        if (x2 > x1) {
            clipRect(left = x1, top = yTop, right = x2, bottom = yTop + trackHeight) {
                drawRoundRect(
                    color = midColor,
                    topLeft = Offset(0f, yTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }

        // Segmento derecho
        if (size.width > x2) {
            clipRect(left = x2, top = yTop, right = size.width, bottom = yTop + trackHeight) {
                drawRoundRect(
                    color = rightColor,
                    topLeft = Offset(0f, yTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }

        val x = (size.width * progress01).coerceIn(0f, size.width)
        val cy = yTop + trackHeight / 2f
        val r = 7.dp.toPx()

        drawCircle(Color.White.copy(alpha = 0.25f), radius = r * 1.8f, center = Offset(x, cy))
        drawCircle(thumbColor, radius = r, center = Offset(x, cy))
    }
}

@Composable
private fun RangePill(
    modifier: Modifier = Modifier,
    topText: String,
    bottomText: String,
    accent: Color,
    isActive: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val baseBg = cs.surface.copy(alpha = 0.35f)
    val activeBg = cs.surface.copy(alpha = 0.55f)
    val bottom = cs.onSurface.copy(alpha = 0.65f)
    val border = if (isActive) accent.copy(alpha = 0.75f) else cs.onSurface.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isActive) activeBg else baseBg)
            .gipogoBorder(1.dp, border, dashed = false, radiusDp = 18.dp)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = topText,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                textAlign = TextAlign.Center,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = bottomText,
                style = MaterialTheme.typography.labelSmall,
                color = bottom,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun bandFromValue(value: Double, t1: Double, t2: Double): RiskBand = when {
    value < t1 -> RiskBand.BORDERLINE
    value < t2 -> RiskBand.NORMAL
    else -> RiskBand.ELEVATED
}

private fun normalize(value: Double, minValue: Double, maxValue: Double): Float {
    if (maxValue <= minValue) return 0f
    val clamped = min(max(value, minValue), maxValue)
    return ((clamped - minValue) / (maxValue - minValue)).toFloat()
}
