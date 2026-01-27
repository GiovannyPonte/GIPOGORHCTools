package com.gipogo.rhctools.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.viewmodel.TrendMetric
import com.gipogo.rhctools.ui.viewmodel.TrendPointUi
import com.gipogo.rhctools.ui.viewmodel.TrendSeriesUi
import com.gipogo.rhctools.ui.viewmodel.TrendsUi
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.max

private val TrendCardShape = RoundedCornerShape(18.dp)
private val SectionPadH = 16.dp

@Composable
fun PatientTrendsDashboardBlock(
    trends: TrendsUi,
    modifier: Modifier = Modifier
) {
    fun find(metric: TrendMetric): TrendSeriesUi? =
        trends.series.firstOrNull { it.metric == metric }

    val congestion = listOfNotNull(
        find(TrendMetric.MPAP),
        find(TrendMetric.PCWP),
        find(TrendMetric.RAP),
    )

    val perfusion = listOfNotNull(
        find(TrendMetric.CI),
        find(TrendMetric.CPO),
    )

    val resistance = listOfNotNull(
        find(TrendMetric.PVR),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (congestion.isNotEmpty()) {
            TrendsSectionHeader(titleRes = R.string.trends_dash_section_congestion)
            congestion.forEachIndexed { idx, s ->
                TrendsMetricCard(series = s, showGradient = (idx == 0))
            }
        }

        if (perfusion.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TrendsSectionHeader(titleRes = R.string.trends_dash_section_perfusion)
            perfusion.forEach { s -> TrendsMetricCard(series = s) }
        }

        if (resistance.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TrendsSectionHeader(titleRes = R.string.trends_dash_section_resistance)
            resistance.forEach { s -> TrendsMetricCard(series = s) }
        }
    }
}

@Composable
private fun TrendsSectionHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionPadH, vertical = 4.dp)
    )
}

@Composable
private fun TrendsMetricCard(
    series: TrendSeriesUi,
    modifier: Modifier = Modifier,
    showGradient: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme

    val points = series.points.sortedBy { it.xMillis }
    val n = points.size
    val last = points.lastOrNull()
    val prev = if (points.size >= 2) points[points.lastIndex - 1] else null

    val decimals = metricDecimals(series.metric)
    val nf = rememberNumberFormat(decimals)

    val lastValue = last?.y
    val prevValue = prev?.y

    val valueText = lastValue?.let { nf.format(it) } ?: stringResource(R.string.common_value_na)

    val delta = if (lastValue != null && prevValue != null) (lastValue - prevValue) else null
    val hasDelta = delta != null && points.size >= 2
    val deltaAbsText = if (hasDelta) nf.format(abs(delta!!)) else ""
    val arrowUp = hasDelta && delta!! > 0.0

    val betterWhenLower = metricBetterWhenLower(series.metric)
    val isImprovement = when {
        !hasDelta -> null
        betterWhenLower -> delta!! < 0.0
        else -> delta!! > 0.0
    }

    val deltaTint = when (isImprovement) {
        true -> cs.tertiary
        false -> cs.error
        null -> cs.onSurfaceVariant
    }
    val deltaBg = deltaTint.copy(alpha = 0.12f)

    val lastDateTimeText = last?.xMillis?.let { formatDateTime(context, it) }
        ?: stringResource(R.string.common_value_na)

    val ref = metricReference(series.metric)
    val refText = ref?.let { nf.format(it) }.orEmpty()
    val refLabel = if (ref != null) metricRefLabel(series.metric, refText) else ""

    val metricName = stringResource(series.metric.labelRes)
    val unit = series.metric.unitRes?.let { stringResource(it) }.orEmpty()
    val title = listOfNotNull(metricName, unit.takeIf { it.isNotBlank() }).joinToString(separator = " ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SectionPadH),
        shape = TrendCardShape,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface
                        )

                        Spacer(Modifier.width(10.dp))

                        if (hasDelta) {
                            DeltaPill(
                                deltaText = deltaAbsText,
                                arrowUp = arrowUp,
                                tint = deltaTint,
                                background = deltaBg
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.trends_dash_points_count, n),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(R.string.trends_dash_last_label, lastDateTimeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            ) {
                SparklineWithReferenceFixedScale(
                    points = points,
                    reference = ref,
                    metric = series.metric,
                    modifier = Modifier.fillMaxSize(),
                    showGradient = showGradient
                )

                if (refLabel.isNotBlank()) {
                    Text(
                        text = refLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeltaPill(
    deltaText: String,
    arrowUp: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color
) {
    val arrow = if (arrowUp) stringResource(R.string.trends_dash_arrow_up)
    else stringResource(R.string.trends_dash_arrow_down)

    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = arrow,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = deltaText,
                color = tint,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/**
 * ✅ Fixed clinical scales per metric (honest, auditable)
 * ✅ Reference line at the true reference value
 * ✅ Discrete Y-axis tick labels (minimal guidance, low visual weight)
 */
@Composable
private fun SparklineWithReferenceFixedScale(
    points: List<TrendPointUi>,
    reference: Double?,
    metric: TrendMetric,
    modifier: Modifier = Modifier,
    showGradient: Boolean = false
) {
    if (points.isEmpty()) return

    val cs = MaterialTheme.colorScheme
    val lineColor = cs.primary
    val gridColor = cs.onSurfaceVariant.copy(alpha = 0.25f)
    val tickColor = cs.onSurfaceVariant.copy(alpha = 0.55f)

    val (yMinFixed, yMaxFixed) = metricClinicalRange(metric)
    val span = (yMaxFixed - yMinFixed).takeIf { it != 0.0 } ?: 1.0

    val yTicks = metricYAxisTicks(metric, yMinFixed, yMaxFixed)
    val tickDecimals = metricTickDecimals(metric)
    val tickFormat = remember(tickDecimals) {
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = tickDecimals
            minimumFractionDigits = 0
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val tickTextStyle = TextStyle(
        color = tickColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val topPad = h * 0.14f
        val bottomPad = h * 0.14f
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

        // Reserve a small left gutter for tick labels (based on widest label)
        val widestLabel = yTicks.maxOfOrNull { tickFormat.format(it).length } ?: 1
        val leftPad = max(18f, (widestLabel * 7f) + 10f) // simple safe estimate

        val plotW = (w - leftPad).coerceAtLeast(1f)

        fun x(i: Int): Float {
            if (points.size == 1) return leftPad + plotW * 0.5f
            return leftPad + (i.toFloat() / points.lastIndex.toFloat()) * plotW
        }

        fun y(v: Double): Float {
            val clamped = v.coerceIn(yMinFixed, yMaxFixed)
            val t = ((clamped - yMinFixed) / span).toFloat().coerceIn(0f, 1f)
            return (h - bottomPad) - (t * plotH)
        }

        // --- Discrete Y ticks: short guide mark + label (no full grid) ---
        yTicks.forEach { tv ->
            val yy = y(tv)

            // small tick mark
            drawLine(
                color = tickColor,
                start = Offset(leftPad - 10f, yy),
                end = Offset(leftPad - 2f, yy),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )

            val label = tickFormat.format(tv)
            val layout = textMeasurer.measure(label, tickTextStyle)

            // draw label left of ticks
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(
                    x = max(0f, leftPad - 14f - layout.size.width),
                    y = yy - (layout.size.height / 2f)
                ),
                style = tickTextStyle
            )
        }

        // Reference dashed line at actual reference value
        if (reference != null) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y(reference)),
                end = Offset(w, y(reference)),
                strokeWidth = 2f,
                pathEffect = dash
            )
        }

        val strokePath = Path().apply {
            moveTo(x(0), y(points[0].y))
            for (i in 1 until points.size) {
                lineTo(x(i), y(points[i].y))
            }
        }

        if (showGradient) {
            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(w, h)
                lineTo(leftPad, h)
                close()
            }
            drawPath(
                path = fillPath,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )
        }

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        for (i in points.indices) {
            drawCircle(
                color = lineColor,
                radius = 5.8f,
                center = Offset(x(i), y(points[i].y))
            )
        }

        val lx = x(points.lastIndex)
        val ly = y(points.last().y)
        drawCircle(
            color = lineColor.copy(alpha = 0.25f),
            radius = 12f,
            center = Offset(lx, ly)
        )
    }
}

private fun metricDecimals(metric: TrendMetric): Int = when (metric) {
    TrendMetric.RAP, TrendMetric.MPAP, TrendMetric.PCWP -> 0
    TrendMetric.CI, TrendMetric.PVR -> 1
    TrendMetric.CPO -> 2
}

private fun metricBetterWhenLower(metric: TrendMetric): Boolean = when (metric) {
    TrendMetric.RAP, TrendMetric.MPAP, TrendMetric.PCWP, TrendMetric.PVR -> true
    TrendMetric.CI, TrendMetric.CPO -> false
}

private fun metricReference(metric: TrendMetric): Double? = when (metric) {
    TrendMetric.MPAP -> 25.0
    TrendMetric.PCWP -> 18.0
    TrendMetric.RAP -> 8.0
    TrendMetric.CI -> 2.2
    TrendMetric.CPO -> 0.6
    TrendMetric.PVR -> 3.0
}

private fun metricClinicalRange(metric: TrendMetric): Pair<Double, Double> = when (metric) {
    TrendMetric.MPAP -> 0.0 to 60.0
    TrendMetric.PCWP -> 0.0 to 40.0
    TrendMetric.RAP -> 0.0 to 25.0
    TrendMetric.CI -> 0.0 to 5.0
    TrendMetric.CPO -> 0.0 to 1.5
    TrendMetric.PVR -> 0.0 to 10.0
}

@Composable
private fun metricRefLabel(metric: TrendMetric, refText: String): String {
    return when (metric) {
        TrendMetric.MPAP -> stringResource(R.string.trends_dash_target_le, refText)
        TrendMetric.PCWP -> stringResource(R.string.trends_dash_threshold_gt, refText)
        TrendMetric.RAP -> stringResource(R.string.trends_dash_target_le, refText)
        TrendMetric.CI -> stringResource(R.string.trends_dash_threshold_lt, refText)
        TrendMetric.CPO -> stringResource(R.string.trends_dash_threshold_lt, refText)
        TrendMetric.PVR -> stringResource(R.string.trends_dash_threshold_gt, refText)
    }
}

private fun formatDateTime(context: Context, millis: Long): String {
    val date = DateFormat.getDateFormat(context).format(java.util.Date(millis))
    val time = DateFormat.getTimeFormat(context).format(java.util.Date(millis))
    return "$date $time"
}

@Composable
private fun rememberNumberFormat(decimals: Int): NumberFormat {
    return remember(decimals) {
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = 0
        }
    }
}

private fun metricYAxisTicks(metric: TrendMetric, min: Double, max: Double): List<Double> {
    val mid = (min + max) / 2.0
    return when (metric) {
        TrendMetric.MPAP -> listOf(0.0, 25.0, 50.0).filter { it in min..max }
        TrendMetric.PCWP -> listOf(0.0, 18.0, 40.0).filter { it in min..max }
        TrendMetric.RAP -> listOf(0.0, 8.0, 20.0).filter { it in min..max }
        TrendMetric.CI -> listOf(0.0, 2.2, 5.0).filter { it in min..max }
        TrendMetric.CPO -> listOf(0.0, 0.6, 1.5).filter { it in min..max }
        TrendMetric.PVR -> listOf(0.0, 3.0, 10.0).filter { it in min..max }
    }.ifEmpty { listOf(min, mid, max) }
}

private fun metricTickDecimals(metric: TrendMetric): Int {
    return when (metric) {
        TrendMetric.CPO -> 1
        TrendMetric.CI -> 1
        else -> 0
    }
}
