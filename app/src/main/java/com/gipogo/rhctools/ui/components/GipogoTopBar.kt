package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R

/**
 * TopBar estilo "glass" (translúcida + borde inferior), reusable.
 * Para evitar depender de material-icons-extended, uso emojis/Unicode.
 */
@Composable
fun GipogoTopBar(
    title: String,
    subtitle: String? = null,

    // ✅ navegación
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,

    // ✅ botones opcionales (si NO hay back)
    leftGlyph: String? = null,
    onLeftClick: (() -> Unit)? = null,

    // ✅ acción derecha (ej: perfil)
    rightGlyph: String? = null,
    onRightClick: (() -> Unit)? = null,

    // ✅ branding
    showBrand: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val background = cs.background
    val border = cs.outline
    val text = cs.onBackground
    val muted = cs.onSurfaceVariant
    val primary = cs.primary

    val backCd = stringResource(R.string.common_back)

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

            // ---------- LEFT AREA ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when {
                    // ✅ BACK tiene prioridad. Evita duplicar con leftGlyph.
                    showBack -> {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(cs.surface)
                                .gipogoBorder(1.dp, border, dashed = false, radiusDp = 999.dp)
                                .noRippleClickable { onBack?.invoke() },
                            contentAlignment = Alignment.Center
                        ) {
                            // Unicode back: no depende de icons
                            Text(text = "‹", color = muted, fontWeight = FontWeight.Bold)
                            // accesibilidad: si tu noRippleClickable no soporta semantics,
                            // al menos no metemos texto duro en contentDescription.
                            // (si quieres, lo mejor es añadir semantics al clickable util)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Botón izquierdo opcional
                    leftGlyph != null -> {
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
                }

                // ---------- BRAND ----------
                if (showBrand) {
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
                }

                // ---------- TITLE ----------
                Box(modifier = Modifier.weight(1f)) {
                    Column {
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

            // ---------- RIGHT ----------
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
