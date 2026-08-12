package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp

/**
 * TopBar estilo "glass" (translúcida + borde inferior), reusable.
 * Para evitar depender de material-icons-extended, uso emojis/Unicode.
 */
@Composable
fun GipogoTopBar(
    title: String,
    subtitle: String? = null,
    leftGlyph: String? = null,
    onLeftClick: (() -> Unit)? = null,
    rightGlyph: String? = null,
    onRightClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val background = cs.background
    val border = cs.outline
    val text = cs.onBackground
    val muted = cs.onSurfaceVariant
    val primary = cs.primary

    Surface(
        color = background.copy(alpha = 0.80f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = border,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (leftGlyph != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(cs.surface)
                            .gipogoBorder(1.dp, border, dashed = false, radiusDp = 999.dp)
                            .noRippleClickable { onLeftClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = leftGlyph, color = muted, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Bloque "logo" + título/subtítulo (para Home se ve como el HTML)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "▮▮▮", color = primary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    // Title + subtitle
                    androidx.compose.foundation.layout.Column {
                        Text(
                            text = title,
                            color = text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                color = muted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (rightGlyph != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.surface)
                        .gipogoBorder(1.dp, border, dashed = false, radiusDp = 999.dp)
                        .noRippleClickable { onRightClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = rightGlyph, color = muted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
