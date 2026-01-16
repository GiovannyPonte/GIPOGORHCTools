package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Card de resultados estilo "hero"
 *
 * NUEVO:
 * - interpretationContent: slot opcional para barras de interpretación
 *   (PVR, SVR, etc.)
 */
@Composable
fun GipogoResultsHeroCard(
    eyebrow: String,
    mainValue: String,
    mainUnit: String,
    leftLabel: String,
    leftValue: String,
    leftUnit: String,
    rightLabel: String,
    rightValue: String,
    rightUnit: String,
    interpretationContent: (@Composable () -> Unit)? = null // ✅ NUEVO
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.primary.copy(alpha = 0.10f), shape)
            .gipogoBorder(
                width = 1.dp,
                color = cs.primary.copy(alpha = 0.25f),
                dashed = false,
                radiusDp = 24.dp
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ───── Eyebrow ─────
            Text(
                text = eyebrow.uppercase(),
                color = cs.primary.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // ───── Valor principal ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = mainValue,
                    color = cs.onBackground,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = " $mainUnit",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
            }

            // ───── Grid inferior ─────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .gipogoBorder(
                        width = 1.dp,
                        color = cs.primary.copy(alpha = 0.10f),
                        dashed = false,
                        radiusDp = 18.dp
                    )
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricBlock(
                    label = leftLabel,
                    value = leftValue,
                    unit = leftUnit,
                    modifier = Modifier.weight(1f)
                )
                MetricBlock(
                    label = rightLabel,
                    value = rightValue,
                    unit = rightUnit,
                    modifier = Modifier.weight(1f)
                )
            }

            // ─────────────────────────────
            // ✅ NUEVO: Interpretación
            // ─────────────────────────────
            interpretationContent?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    it()
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = modifier.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = cs.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " $unit",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
    }
}
