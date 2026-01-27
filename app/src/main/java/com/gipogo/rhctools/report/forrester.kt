package com.gipogo.rhctools.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ----------------------------
// Data model
// ----------------------------
data class ForresterData(
    val cardiacIndexLMinM2: Float,            // Índice cardiaco (L/min/m²)
    val wedgePressureMmHg: Float              // Presión capilar pulmonar enclavada (mmHg)
)

// ----------------------------
// Public screen-like composable
// ----------------------------
@Composable
fun ForresterChartCard(
    data: ForresterData,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "Clasificación de Forrester",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(12.dp))

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                ForresterCanvas(
                    data = data,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(12.dp))

            ForresterDiagnosisCard(data = data)
        }
    }
}

// ----------------------------
// Canvas chart
// ----------------------------
@OptIn(ExperimentalTextApi::class)
@Composable
private fun ForresterCanvas(
    data: ForresterData,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Rangos “de trabajo” (puedes ajustarlos si quieres)
    val maxCi = 5.0f
    val maxPcwp = 40f

    // Umbrales Forrester
    val ciThreshold = 2.2f
    val pcwpThreshold = 18f

    val padding = 28.dp

    Canvas(
        modifier = modifier.padding(padding)
    ) {
        val w = size.width
        val h = size.height

        fun xFromPcwp(value: Float): Float {
            val v = value.coerceIn(0f, maxPcwp)
            return (v / maxPcwp) * w
        }

        fun yFromCi(value: Float): Float {
            val v = value.coerceIn(0f, maxCi)
            // CI crece hacia arriba, Canvas crece hacia abajo => invertir
            return h - (v / maxCi) * h
        }

        val xT = xFromPcwp(pcwpThreshold)
        val yT = yFromCi(ciThreshold)

        // ----------------------------
        // Quadrants (I/II/III/IV)
        // ----------------------------
        val qAlpha = 0.10f

        // I
        drawRect(
            color = Color(0xFF2E7D32).copy(alpha = qAlpha),
            topLeft = Offset(0f, 0f),
            size = Size(xT, yT)
        )
        // II
        drawRect(
            color = Color(0xFF0277BD).copy(alpha = qAlpha),
            topLeft = Offset(xT, 0f),
            size = Size(w - xT, yT)
        )
        // III
        drawRect(
            color = Color(0xFFF9A825).copy(alpha = qAlpha),
            topLeft = Offset(0f, yT),
            size = Size(xT, h - yT)
        )
        // IV
        drawRect(
            color = Color(0xFFC62828).copy(alpha = qAlpha),
            topLeft = Offset(xT, yT),
            size = Size(w - xT, h - yT)
        )

        // ----------------------------
        // Grid border
        // ----------------------------
        drawRect(
            color = Color.White.copy(alpha = 0.20f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            style = Stroke(width = 2f)
        )

        // ----------------------------
        // Threshold dashed lines
        // ----------------------------
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        val lineColor = Color.White.copy(alpha = 0.35f)

        // CI = 2.2 (horizontal)
        drawLine(
            color = lineColor,
            start = Offset(0f, yT),
            end = Offset(w, yT),
            strokeWidth = 2f,
            pathEffect = dash
        )

        // PCWP = 18 (vertical)
        drawLine(
            color = lineColor,
            start = Offset(xT, 0f),
            end = Offset(xT, h),
            strokeWidth = 2f,
            pathEffect = dash
        )

        // ----------------------------
        // Axis labels (inside the canvas bounds)
        // ----------------------------
        val axisLabelStyle = TextStyle(
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 11.sp
        )

        // OJO: antes estaba en h + 8f (se recortaba). Ahora queda visible.
        drawText(
            textMeasurer = textMeasurer,
            text = "PCWP (mmHg)",
            topLeft = Offset(w * 0.33f, (h - 16f).coerceAtLeast(0f)),
            style = axisLabelStyle
        )

        // OJO: antes estaba en (-24f, -24f) (se recortaba). Ahora queda visible.
        drawText(
            textMeasurer = textMeasurer,
            text = "CI (L/min/m²)",
            topLeft = Offset(8f, 8f),
            style = axisLabelStyle
        )

        // ----------------------------
        // Quadrant labels
        // ----------------------------
        val qStyle = TextStyle(
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 10.sp
        )

        drawText(textMeasurer, "I  Warm & Dry", Offset(8f, 24f), qStyle)
        drawText(textMeasurer, "II Warm & Wet", Offset(xT + 8f, 24f), qStyle)
        drawText(textMeasurer, "III Cold & Dry", Offset(8f, yT + 8f), qStyle)
        drawText(textMeasurer, "IV Cold & Wet", Offset(xT + 8f, yT + 8f), qStyle)

        // ----------------------------
        // Patient point
        // ----------------------------
        val px = xFromPcwp(data.wedgePressureMmHg)
        val py = yFromCi(data.cardiacIndexLMinM2)
        val p = Offset(px, py)

        // Halo
        drawCircle(
            color = Color(0xFF03A9F4).copy(alpha = 0.25f),
            radius = 22f,
            center = p
        )

        // White core
        drawCircle(
            color = Color.White,
            radius = 8f,
            center = p
        )

        // Stroke ring
        drawCircle(
            color = Color(0xFF03A9F4),
            radius = 10f,
            center = p,
            style = Stroke(width = 3.5f)
        )

        // Value label near point
        val valueStyle = TextStyle(
            color = Color.White,
            fontSize = 11.sp
        )
        val label = "CI ${data.cardiacIndexLMinM2.pretty1()}  |  PCWP ${data.wedgePressureMmHg.pretty0()}"

        // Mantener el label dentro del canvas, sin “números mágicos” tan agresivos.
        val approxLabelWidth = 180f
        val lx = (px + 12f).coerceIn(8f, (w - approxLabelWidth).coerceAtLeast(8f))
        val ly = (py - 18f).coerceIn(8f, (h - 20f).coerceAtLeast(8f))

        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(lx, ly),
            style = valueStyle
        )
    }
}

// ----------------------------
// Diagnosis card
// ----------------------------
@Composable
private fun ForresterDiagnosisCard(data: ForresterData) {
    val ci = data.cardiacIndexLMinM2
    val pcwp = data.wedgePressureMmHg

    val profile = when {
        ci >= 2.2f && pcwp <= 18f -> "Perfil I: Normal (Warm & Dry)"
        ci >= 2.2f && pcwp > 18f -> "Perfil II: Congestión (Warm & Wet)"
        ci < 2.2f && pcwp <= 18f -> "Perfil III: Hipoperfusión sin congestión (Cold & Dry)"
        else -> "Perfil IV: Choque con congestión (Cold & Wet)"
    }

    val statusColor = when {
        ci < 2.2f && pcwp > 18f -> MaterialTheme.colorScheme.error
        ci < 2.2f -> Color(0xFFF9A825)
        pcwp > 18f -> Color(0xFF0277BD)
        else -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Estado actual",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = profile,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            Spacer(Modifier.height(8.dp))

            val suggestion = when {
                ci >= 2.2f && pcwp <= 18f -> "Perfil hemodinámico estable. Revalorar en contexto clínico."
                ci >= 2.2f && pcwp > 18f -> "Congestión predominante. Considerar estrategia de descongestión según escenario."
                ci < 2.2f && pcwp <= 18f -> "Hipoperfusión sin congestión. Considerar optimización de precarga/inotropía según causa."
                else -> "Choque con congestión. Manejo urgente guiado por perfusión, presión y congestión."
            }

            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ----------------------------
// Small helpers
// ----------------------------
private fun Float.pretty0(): String = this.roundToInt().toString()
private fun Float.pretty1(): String = ((this * 10f).roundToInt() / 10f).toString()

// ----------------------------
// Preview
// ----------------------------
@Preview(showBackground = true, widthDp = 420)
@Composable
fun ForresterChartCardPreview() {
    MaterialTheme {
        ForresterChartCard(
            data = ForresterData(
                cardiacIndexLMinM2 = 1.8f,
                wedgePressureMmHg = 24f
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
