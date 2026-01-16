package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top bar reutilizable para pantallas de cálculo (Fick / SVR / CPO / PAPI, etc.).
 *
 * - Fondo tipo "glass" (translúcido)
 * - Línea inferior fina
 * - Back circular a la izquierda
 * - Reset opcional (↺) y Info opcional (ⓘ) a la derecha
 */
@Composable
fun GipogoCalcTopBar(
    title: String,
    onBack: () -> Unit,
    onInfo: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme

    Surface(
        color = cs.background.copy(alpha = 0.80f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = cs.outline,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            // Bloque izquierdo: Back + Título
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircleButton(glyph = "‹", onClick = onBack)

                Text(
                    text = title,
                    modifier = Modifier.padding(start = 12.dp),
                    color = cs.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bloque derecho: Reset (opcional) + Info (opcional)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onReset != null) {
                    IconCircleButton(glyph = "↺", onClick = onReset)
                    Spacer(modifier = Modifier.width(10.dp))
                }

                if (onInfo != null) {
                    IconCircleButton(glyph = "ⓘ", onClick = onInfo)
                } else {
                    Box(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

/**
 * Botón circular reutilizable para glyphs (Unicode).
 * Usa los modifiers ya existentes: gipogoBorder + noRippleClickable.
 */
@Composable
fun IconCircleButton(
    glyph: String,
    onClick: () -> Unit,
    size: Dp = 40.dp
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(cs.surface)
            .gipogoBorder(width = 1.dp, color = cs.outline, dashed = false, radiusDp = 999.dp)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = cs.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}
