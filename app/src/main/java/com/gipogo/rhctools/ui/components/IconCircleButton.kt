package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Botón circular "info" estilo HomeToolCard:
 * círculo + texto "ⓘ" (no drawable).
 */
@Composable
fun IconCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    glyph: String = "ⓘ"
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // igual idea de HomeToolCard: fondo suave sobre surface
            .background(cs.primary.copy(alpha = 0.12f))
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = cs.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
