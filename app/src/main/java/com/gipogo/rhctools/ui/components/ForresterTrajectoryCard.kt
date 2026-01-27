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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin


private val CardShape = RoundedCornerShape(20.dp)
private val InnerShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(999.dp)

data class ForresterTrendPoint(
    val ci: Double?,       // CI (L/min/m²) persisted
    val pcwp: Double?,     // PCWP/PAWP (mmHg) persisted
    val label: String? = null // opcional: “T1”, “T2”… si lo quieres
)

@Composable
fun ForresterTrajectoryCard(
    points: List<ForresterTrendPoint>,
    modifier: Modifier = Modifier,
    ciThreshold: Float = 2.2f,
    pcwpThreshold: Float = 18f,
    ciRange: ClosedFloatingPointRange<Float> = 0f..5f,
    pcwpRange: ClosedFloatingPointRange<Float> = 0f..40f,
) {
    val unitCi = stringResource(R.string.common_unit_lmin_m2)
    val unitMmHg = stringResource(R.string.common_unit_mmhg)

    val title = stringResource(R.string.forrester_traj_title)
    val subtitle = stringResource(R.string.forrester_traj_subtitle)
    val missing = stringResource(R.string.forrester_traj_missing)

    val xAxisTitle = stringResource(R.string.study_detail_forrester_x_axis_title, unitMmHg)
    val yAxisTitle = stringResource(R.string.study_detail_forrester_y_axis_title, unitCi)

    val nowLabel = stringResource(R.string.forrester_traj_now_label)

    // Filtrar puntos válidos (sin inventar valores)
    val valid = points.mapNotNull { p ->
        val ci = p.ci?.takeIf { it.isFinite() }?.toFloat()
        val pcwp = p.pcwp?.takeIf { it.isFinite() }?.toFloat()
        if (ci == null || pcwp == null) null else Triple(ci, pcwp, p.label)
    }

    val last = valid.lastOrNull()
    val currentProfile = last?.let { forresterProfile(it.first, it.second, ciThreshold, pcwpThreshold) }
    val currentPillText = currentProfile?.let { profileToPillText(it) }

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
            // Header row: icon + title/subtitle + current pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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

                if (!currentPillText.isNullOrBlank()) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                    ) {
                        Text(
                            text = stringResource(R.string.forrester_traj_current_pill, currentPillText),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                if (valid.isEmpty()) {
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
                    ForresterTrajectoryChartWithAxes(
                        points = valid,
                        nowLabel = nowLabel,
                        ciThreshold = ciThreshold,
                        pcwpThreshold = pcwpThreshold,
                        ciRange = ciRange,
                        pcwpRange = pcwpRange,
                        xAxisTitle = xAxisTitle,
                        yAxisTitle = yAxisTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

/* ---------------- Chart (trajectory) ---------------- */

@Composable
private fun ForresterTrajectoryChartWithAxes(
    points: List<Triple<Float, Float, String?>>, // (ci, pcwp, optionalLabel)
    nowLabel: String,
    ciThreshold: Float,
    pcwpThreshold: Float,
    ciRange: ClosedFloatingPointRange<Float>,
    pcwpRange: ClosedFloatingPointRange<Float>,
    xAxisTitle: String,
    yAxisTitle: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()

    val qWarmDry = cs.tertiaryContainer.copy(alpha = 0.22f)
    val qWarmWet = cs.primaryContainer.copy(alpha = 0.18f)
    val qColdDry = cs.secondaryContainer.copy(alpha = 0.18f)
    val qColdWet = cs.errorContainer.copy(alpha = 0.22f)

    val axisColor = cs.onSurface.copy(alpha = 0.55f)
    val gridColor = cs.onSurface.copy(alpha = 0.14f)
    val borderColor = cs.outline.copy(alpha = 0.36f)

    val pathColor = cs.onSurface.copy(alpha = 0.55f)
    val historyDotColor = cs.onSurface.copy(alpha = 0.65f)
    val nowColor = cs.primary

    val tickStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.78f), fontSize = 10.sp)
    val axisTitleStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val thresholdStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.70f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val labelStyle = TextStyle(color = cs.onSurface.copy(alpha = 0.80f), fontSize = 10.sp)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

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

        // Border
        drawRect(
            color = borderColor,
            topLeft = Offset(left, top),
            size = Size(plotW, plotH),
            style = Stroke(width = 2f)
        )

        // Threshold dashed lines
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        drawLine(axisColor, start = Offset(left, yT), end = Offset(right, yT), strokeWidth = 2f, pathEffect = dash)
        drawLine(axisColor, start = Offset(xT, top), end = Offset(xT, bottom), strokeWidth = 2f, pathEffect = dash)

        // Axes
        drawLine(axisColor, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2f)
        drawLine(axisColor, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2f)

        // Minimal ticks (clinical)
        val xTicks = listOf(0, 10, 20, 30, 40).map { it.toFloat() }
        xTicks.forEachIndexed { idx, v ->
            val x = xFromPcwp(v)
            drawLine(axisColor, start = Offset(x, bottom), end = Offset(x, bottom + 6f), strokeWidth = 2f)
            if (idx != 0 && idx != xTicks.lastIndex) {
                drawLine(gridColor, start = Offset(x, top), end = Offset(x, bottom), strokeWidth = 1.5f)
            }
            val label = v.roundToInt().toString()
            val tLayout = tm.measure(label, tickStyle)
            drawText(tm, label, Offset((x - tLayout.size.width / 2f).coerceIn(2f, w - tLayout.size.width - 2f), bottom + 8f), tickStyle)
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
            drawText(tm, label, Offset((left - 10f - tLayout.size.width).coerceAtLeast(2f), y - tLayout.size.height / 2f), tickStyle)
        }

        // Axis titles
        val xTitleX = (left + plotW / 2f) - (xTitleLayout.size.width / 2f)
        val xTitleY = h - xTitleLayout.size.height - 2f
        drawText(tm, xAxisTitle, Offset(xTitleX.coerceIn(2f, w - xTitleLayout.size.width - 2f), xTitleY.coerceAtLeast(bottom + 18f)), axisTitleStyle)
        drawText(tm, yAxisTitle, Offset(2f, 2f), axisTitleStyle)

        // Threshold labels (optional; they’re light)
        val xTh = "PCWP = ${pcwpThreshold.roundToInt()}"
        val yTh = "CI = ${"%.1f".format(ciThreshold)}"
        // IMPORTANT: No hardcoded visible text allowed → do not draw these strings.
        // We keep only the dashed lines. (If you want labels, pass them in from strings.xml.)

        // Trajectory path
        val pts = points.map { (ci, pcwp, _) -> Offset(xFromPcwp(pcwp), yFromCi(ci)) }

        if (pts.size >= 2) {
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            drawPath(path = path, color = pathColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // History points
        if (pts.size >= 2) {
            for (i in 0 until pts.lastIndex) {
                val p = pts[i]
                drawCircle(color = historyDotColor, radius = 7f, center = p)
                drawCircle(color = cs.surface, radius = 4f, center = p)
            }
        }

        // Last point emphasized + label “Now”
        val lastP = pts.last()
        drawCircle(nowColor.copy(alpha = 0.20f), radius = 20f, center = lastP)
        drawCircle(cs.surface, radius = 8f, center = lastP)
        drawCircle(nowColor, radius = 10f, center = lastP, style = Stroke(width = 4f))

        val nowLayout = tm.measure(nowLabel, labelStyle)
        val nowX = (lastP.x + 12f).coerceAtMost(right - nowLayout.size.width - 2f)
        val nowY = (lastP.y - nowLayout.size.height / 2f).coerceIn(top + 2f, bottom - nowLayout.size.height - 2f)
        drawText(tm, nowLabel, Offset(nowX, nowY), labelStyle)

        // Optional point labels if provided (T1, T2...) – caller passes localized strings if needed.
        points.forEachIndexed { idx, triple ->
            val lbl = triple.third
            if (!lbl.isNullOrBlank() && idx != points.lastIndex) {
                val p = pts[idx]
                val layout = tm.measure(lbl, labelStyle)
                val lx = (p.x + 10f).coerceAtMost(right - layout.size.width - 2f)
                val ly = (p.y - layout.size.height - 8f).coerceIn(top + 2f, bottom - layout.size.height - 2f)
                drawText(tm, lbl, Offset(lx, ly), labelStyle)
            }
        }
    }
}

/* ---------------- Profile helpers ---------------- */

private enum class ForresterProfileKey { I, II, III, IV }

private fun forresterProfile(ci: Float, pcwp: Float, ciTh: Float, pcwpTh: Float): ForresterProfileKey {
    return when {
        ci >= ciTh && pcwp <= pcwpTh -> ForresterProfileKey.I
        ci >= ciTh && pcwp > pcwpTh -> ForresterProfileKey.II
        ci < ciTh && pcwp <= pcwpTh -> ForresterProfileKey.III
        else -> ForresterProfileKey.IV
    }
}

@Composable
private fun profileToPillText(profile: ForresterProfileKey): String {
    return when (profile) {
        ForresterProfileKey.I -> stringResource(R.string.forrester_traj_profile_i)
        ForresterProfileKey.II -> stringResource(R.string.forrester_traj_profile_ii)
        ForresterProfileKey.III -> stringResource(R.string.forrester_traj_profile_iii)
        ForresterProfileKey.IV -> stringResource(R.string.forrester_traj_profile_iv)
    }
}
