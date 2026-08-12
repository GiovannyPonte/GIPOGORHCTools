package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(20.dp)
private val InnerShape = RoundedCornerShape(16.dp)
private val NoteShape = RoundedCornerShape(14.dp)

@Composable
fun ForresterStudyCard(
    rhc: RhcStudyDataEntity?,
    modifier: Modifier = Modifier,
    // Thresholds (standard Forrester)
    ciThreshold: Float = 2.2f,
    pcwpThreshold: Float = 18f,
    // Clinically typical display ranges
    ciRange: ClosedFloatingPointRange<Float> = 0f..5f,
    pcwpRange: ClosedFloatingPointRange<Float> = 0f..40f,
) {
    // Source of truth: persisted study data (Room via Repository -> VM -> uiState)
    val ciDb: Double? = rhc?.cardiacIndexLMinM2
    val pcwpDb: Double? = rhc?.pawpMmHg

    val ci: Float? = ciDb.toFiniteFloatOrNull()
    val pcwp: Float? = pcwpDb.toFiniteFloatOrNull()

    val na = stringResource(R.string.common_value_na)

    val ciText = ciDb?.let { formatNumber(it, 1) } ?: na
    val pcwpText = pcwpDb?.let { formatNumber(it, 0) } ?: na

    val unitCi = stringResource(R.string.common_unit_lmin_m2)
    val unitMmHg = stringResource(R.string.common_unit_mmhg)

    // No hardcoded text: all visible strings from strings.xml
    val title = stringResource(R.string.study_detail_section_forrester)
    val subtitle = stringResource(R.string.study_detail_section_forrester_subtitle)
    val missing = stringResource(R.string.study_detail_forrester_missing_values)

    val xAxisTitle = stringResource(R.string.study_detail_forrester_x_axis_title, unitMmHg)
    val yAxisTitle = stringResource(R.string.study_detail_forrester_y_axis_title, unitCi)

    val xThresholdLabel = stringResource(R.string.study_detail_forrester_pcwp_threshold_label, pcwpThreshold.toInt())
    val yThresholdLabel = stringResource(R.string.study_detail_forrester_ci_threshold_label, ciThreshold)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Chart container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = InnerShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                if (ci == null || pcwp == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = missing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ForresterChartWithAxes(
                        ci = ci,
                        pcwp = pcwp,
                        ciThreshold = ciThreshold,
                        pcwpThreshold = pcwpThreshold,
                        ciRange = ciRange,
                        pcwpRange = pcwpRange,
                        xAxisTitle = xAxisTitle,
                        yAxisTitle = yAxisTitle,
                        xThresholdLabel = xThresholdLabel,
                        yThresholdLabel = yThresholdLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }

            // ✅ Clinical note (only when we have both values)
            if (ci != null && pcwp != null) {
                val profile = forresterProfile(ci, pcwp, ciThreshold, pcwpThreshold)
                ForresterClinicalNote(
                    profile = profile,
                    ciThreshold = ciThreshold,
                    pcwpThreshold = pcwpThreshold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

            // Values (from DB, no recalculation)
            MetricRow(
                label = stringResource(R.string.study_input_ci),
                valueText = ciText,
                unitText = unitCi
            )
            MetricRow(
                label = stringResource(R.string.study_input_pcwp),
                valueText = pcwpText,
                unitText = unitMmHg
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    valueText: String,
    unitText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = unitText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Forrester chart (CI vs PCWP) with:
 * - clinically appropriate ticks: PCWP 0-40 by 10; CI 0-5 by 1
 * - axis titles and threshold labels (strings.xml)
 * - safe insets so X-axis title never clips
 * - colors derived from MaterialTheme
 */
@Composable
private fun ForresterChartWithAxes(
    ci: Float,
    pcwp: Float,
    ciThreshold: Float,
    pcwpThreshold: Float,
    ciRange: ClosedFloatingPointRange<Float>,
    pcwpRange: ClosedFloatingPointRange<Float>,
    xAxisTitle: String,
    yAxisTitle: String,
    xThresholdLabel: String,
    yThresholdLabel: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()

    // Crisp quadrant fills derived from theme (no hex hardcode)
    val qWarmDry = cs.tertiaryContainer.copy(alpha = 0.32f)
    val qWarmWet = cs.primaryContainer.copy(alpha = 0.28f)
    val qColdDry = cs.secondaryContainer.copy(alpha = 0.28f)
    val qColdWet = cs.errorContainer.copy(alpha = 0.32f)

    val axisColor = cs.onSurface.copy(alpha = 0.55f)
    val gridColor = cs.onSurface.copy(alpha = 0.14f)
    val borderColor = cs.outline.copy(alpha = 0.36f)

    val pointColor = cs.primary

    val tickStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.78f), fontSize = 10.sp)
    val axisTitleStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val thresholdStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.80f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Measure text to compute safe insets (prevents clipping of X title)
        val xTitleLayout = tm.measure(xAxisTitle, axisTitleStyle)
        val yTitleLayout = tm.measure(yAxisTitle, axisTitleStyle)
        val maxYTickLayout = tm.measure(ciRange.endInclusive.roundToInt().toString(), tickStyle)

        val leftInset = max(12f, maxYTickLayout.size.width.toFloat() + yTitleLayout.size.height.toFloat() + 16f)
        val rightInset = 12f
        val topInset = 12f
        val bottomInset = max(12f, xTitleLayout.size.height.toFloat() + 22f)

        val left = leftInset
        val top = topInset
        val right = w - rightInset
        val bottom = h - bottomInset

        val plotW = max(1f, right - left)
        val plotH = max(1f, bottom - top)

        fun xFromPcwp(v: Float): Float {
            val start = pcwpRange.start
            val end = pcwpRange.endInclusive
            val denom = (end - start).takeIf { it != 0f } ?: 1f
            val t = ((v - start) / denom).coerceIn(0f, 1f)
            return left + t * plotW
        }

        fun yFromCi(v: Float): Float {
            val start = ciRange.start
            val end = ciRange.endInclusive
            val denom = (end - start).takeIf { it != 0f } ?: 1f
            val t = ((v - start) / denom).coerceIn(0f, 1f)
            return bottom - t * plotH
        }

        val xT = xFromPcwp(pcwpThreshold)
        val yT = yFromCi(ciThreshold)

        // Quadrants
        drawRect(qWarmDry, topLeft = Offset(left, top), size = Size(xT - left, yT - top))
        drawRect(qWarmWet, topLeft = Offset(xT, top), size = Size(right - xT, yT - top))
        drawRect(qColdDry, topLeft = Offset(left, yT), size = Size(xT - left, bottom - yT))
        drawRect(qColdWet, topLeft = Offset(xT, yT), size = Size(right - xT, bottom - yT))

        // Plot border
        drawRect(
            color = borderColor,
            topLeft = Offset(left, top),
            size = Size(plotW, plotH),
            style = Stroke(width = 2f)
        )

        // Threshold dashed lines
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        drawLine(
            color = axisColor,
            start = Offset(left, yT),
            end = Offset(right, yT),
            strokeWidth = 2f,
            pathEffect = dash
        )
        drawLine(
            color = axisColor,
            start = Offset(xT, top),
            end = Offset(xT, bottom),
            strokeWidth = 2f,
            pathEffect = dash
        )

        // Axes (solid)
        drawLine(axisColor, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2f)
        drawLine(axisColor, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2f)

        // Clinical ticks
        val xTicks = listOf(0, 10, 20, 30, 40).map { it.toFloat() }
        xTicks.forEachIndexed { idx, v ->
            val x = xFromPcwp(v)

            drawLine(axisColor, start = Offset(x, bottom), end = Offset(x, bottom + 6f), strokeWidth = 2f)

            if (idx != 0 && idx != xTicks.lastIndex) {
                drawLine(gridColor, start = Offset(x, top), end = Offset(x, bottom), strokeWidth = 1.5f)
            }

            val label = v.roundToInt().toString()
            val tLayout = tm.measure(label, tickStyle)
            drawText(
                textMeasurer = tm,
                text = label,
                topLeft = Offset(
                    (x - tLayout.size.width / 2f).coerceIn(2f, w - tLayout.size.width - 2f),
                    bottom + 8f
                ),
                style = tickStyle
            )
        }

        val yTicks = listOf(0, 1, 2, 3, 4, 5).map { it.toFloat() }
        yTicks.forEachIndexed { idx, v ->
            val y = yFromCi(v)

            drawLine(axisColor, start = Offset(left - 6f, y), end = Offset(left, y), strokeWidth = 2f)

            if (idx != 0 && idx != yTicks.lastIndex) {
                drawLine(gridColor, start = Offset(left, y), end = Offset(right, y), strokeWidth = 1.5f)
            }

            val label = v.roundToInt().toString()
            val tLayout = tm.measure(label, tickStyle)
            drawText(
                textMeasurer = tm,
                text = label,
                topLeft = Offset(
                    (left - 10f - tLayout.size.width).coerceAtLeast(2f),
                    (y - tLayout.size.height / 2f).coerceIn(2f, h - bottomInset - tLayout.size.height - 2f)
                ),
                style = tickStyle
            )
        }

        // Axis titles (safe placement; no clipping)
        val xTitleX = (left + plotW / 2f) - (xTitleLayout.size.width / 2f)
        val xTitleY = h - xTitleLayout.size.height - 2f
        drawText(
            textMeasurer = tm,
            text = xAxisTitle,
            topLeft = Offset(
                xTitleX.coerceIn(2f, w - xTitleLayout.size.width - 2f),
                xTitleY.coerceAtLeast(bottom + 18f)
            ),
            style = axisTitleStyle
        )

        drawText(
            textMeasurer = tm,
            text = yAxisTitle,
            topLeft = Offset(2f, 2f),
            style = axisTitleStyle
        )

        // Threshold labels
        val xThLayout = tm.measure(xThresholdLabel, thresholdStyle)
        drawText(
            textMeasurer = tm,
            text = xThresholdLabel,
            topLeft = Offset(
                (xT + 6f).coerceIn(left + 2f, right - xThLayout.size.width - 2f),
                (top + 2f).coerceIn(2f, bottom - xThLayout.size.height - 2f)
            ),
            style = thresholdStyle
        )

        val yThLayout = tm.measure(yThresholdLabel, thresholdStyle)
        drawText(
            textMeasurer = tm,
            text = yThresholdLabel,
            topLeft = Offset(
                (left + 6f).coerceIn(left + 2f, right - yThLayout.size.width - 2f),
                (yT - yThLayout.size.height - 6f).coerceIn(top + 2f, bottom - yThLayout.size.height - 2f)
            ),
            style = thresholdStyle
        )

        // Patient point (more legible)
        val p = Offset(xFromPcwp(pcwp), yFromCi(ci))
        drawCircle(pointColor.copy(alpha = 0.22f), radius = 24f, center = p)
        drawCircle(cs.surface, radius = 9f, center = p)
        drawCircle(pointColor, radius = 11f, center = p, style = Stroke(width = 4f))
    }
}

/* ---------------------- Clinical note ---------------------- */

private enum class ForresterProfileKeys { I, II, III, IV }

private fun forresterProfile(
    ci: Float,
    pcwp: Float,
    ciThreshold: Float,
    pcwpThreshold: Float
): ForresterProfileKeys {
    return when {
        ci >= ciThreshold && pcwp <= pcwpThreshold -> ForresterProfileKeys.I
        ci >= ciThreshold && pcwp > pcwpThreshold -> ForresterProfileKeys.II
        ci < ciThreshold && pcwp <= pcwpThreshold -> ForresterProfileKeys.III
        else -> ForresterProfileKeys.IV
    }
}

@Composable
private fun ForresterClinicalNote(
    profile: ForresterProfileKeys,
    ciThreshold: Float,
    pcwpThreshold: Float,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val border = cs.outline.copy(alpha = 0.18f)

    // Titles (no hardcoded text)
    val title = stringResource(R.string.study_detail_forrester_note_title)
    val considerationsTitle = stringResource(R.string.study_detail_forrester_note_considerations_title)
    val measurementTitle = stringResource(R.string.study_detail_forrester_note_measurement_title)
    val measurementText = stringResource(R.string.study_detail_forrester_note_measurement_text)

    // Profile header
    val profileLabel = when (profile) {
        ForresterProfileKeys.I -> stringResource(R.string.study_detail_forrester_profile_i_title)
        ForresterProfileKeys.II -> stringResource(R.string.study_detail_forrester_profile_ii_title)
        ForresterProfileKeys.III -> stringResource(R.string.study_detail_forrester_profile_iii_title)
        ForresterProfileKeys.IV -> stringResource(R.string.study_detail_forrester_profile_iv_title)
    }

    // Summary / meaning (already uses thresholds; no hardcoded text)
    val meaning = when (profile) {
        ForresterProfileKeys.I -> stringResource(R.string.study_detail_forrester_profile_i_meaning, ciThreshold, pcwpThreshold)
        ForresterProfileKeys.II -> stringResource(R.string.study_detail_forrester_profile_ii_meaning, ciThreshold, pcwpThreshold)
        ForresterProfileKeys.III -> stringResource(R.string.study_detail_forrester_profile_iii_meaning, ciThreshold, pcwpThreshold)
        ForresterProfileKeys.IV -> stringResource(R.string.study_detail_forrester_profile_iv_meaning, ciThreshold, pcwpThreshold)
    }

    // Expanded considerations (new strings)
    val considerations = when (profile) {
        ForresterProfileKeys.I -> stringResource(R.string.study_detail_forrester_profile_i_considerations)
        ForresterProfileKeys.II -> stringResource(R.string.study_detail_forrester_profile_ii_considerations)
        ForresterProfileKeys.III -> stringResource(R.string.study_detail_forrester_profile_iii_considerations)
        ForresterProfileKeys.IV -> stringResource(R.string.study_detail_forrester_profile_iv_considerations)
    }

    Card(
        modifier = modifier,
        shape = NoteShape,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.22f)),
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )

            Text(
                text = profileLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface
            )

            Text(
                text = meaning,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

            Divider(color = cs.outline.copy(alpha = 0.14f))

            Text(
                text = considerationsTitle,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )
            Text(
                text = considerations,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

            Divider(color = cs.outline.copy(alpha = 0.14f))

            Text(
                text = measurementTitle,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )
            Text(
                text = measurementText,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}


/* ---------------------- Helpers ---------------------- */

private fun Double?.toFiniteFloatOrNull(): Float? {
    val v = this ?: return null
    if (!v.isFinite()) return null
    return v.toFloat()
}

private fun formatNumber(value: Double, decimals: Int): String {
    val nf = NumberFormat.getNumberInstance()
    nf.maximumFractionDigits = decimals
    nf.minimumFractionDigits = 0
    return nf.format(value)
}
