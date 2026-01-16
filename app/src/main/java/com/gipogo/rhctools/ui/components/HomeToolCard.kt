package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HomeToolCard(
    modifier: Modifier = Modifier,
    badge: String,
    title: String,
    subtitle: String,
    accent: Color,
    onCardClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val surface = cs.surface
    val border = cs.outline
    val textLight = cs.onBackground
    val textMuted = cs.onSurfaceVariant

    val shape = RoundedCornerShape(24.dp)

    Surface(
        color = surface,
        shape = shape,
        border = BorderStroke(1.dp, border),
        shadowElevation = 8.dp,
        modifier = modifier
            .height(160.dp)
            .clip(shape)
            .clickable { onCardClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Info (arriba derecha) — click separado del card
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .noRippleClickable { onInfoClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "ⓘ", color = accent, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge circular
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Column {
                    Text(
                        text = title,
                        color = textLight,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
