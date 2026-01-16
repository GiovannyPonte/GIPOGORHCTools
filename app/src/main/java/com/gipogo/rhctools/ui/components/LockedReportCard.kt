package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LockedReportCard() {
    val cs = MaterialTheme.colorScheme
    val surface = cs.surface
    val border = cs.outline
    val textLight = cs.onBackground
    val textMuted = cs.onSurfaceVariant

    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .gipogoBorder(width = 2.dp, color = border, dashed = true, radiusDp = 24.dp)
            .padding(22.dp)
            .alpha(0.60f)
    ) {
        Text(
            text = "🔒",
            color = textMuted,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(border.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🧾", color = textMuted)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Informe PDF no disponible",
                color = textLight.copy(alpha = 0.80f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Realice al menos un cálculo para activar la generación del reporte clínico.",
                color = textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
