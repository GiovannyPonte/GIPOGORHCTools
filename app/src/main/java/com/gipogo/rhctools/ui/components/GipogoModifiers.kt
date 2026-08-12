package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null
) { onClick() }

/**
 * Reusable: borde sólido o punteado, para cards tipo "dashed".
 */
fun Modifier.gipogoBorder(
    width: Dp,
    color: Color,
    dashed: Boolean,
    radiusDp: Dp
): Modifier = this.then(
    Modifier.drawBehind {
        val strokeWidthPx = width.toPx()
        val r = radiusDp.toPx()

        val rect = Rect(
            left = strokeWidthPx / 2,
            top = strokeWidthPx / 2,
            right = size.width - strokeWidthPx / 2,
            bottom = size.height - strokeWidthPx / 2
        )

        val pathEffect = if (dashed) {
            PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
        } else null

        drawRoundRect(
            color = color,
            topLeft = Offset(rect.left, rect.top),
            size = rect.size,
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = strokeWidthPx, pathEffect = pathEffect)
        )
    }
)
