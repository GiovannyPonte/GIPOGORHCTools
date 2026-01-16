package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Card base para mantener consistencia visual en pantallas de cálculo.
 * Fondo: surface, Borde: outline, Radio: 24dp
 */
@Composable
fun GipogoSurfaceCard(
    modifier: Modifier = Modifier,
    contentPaddingDp: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .background(cs.surface, shape)
            .gipogoBorder(1.dp, cs.outline, dashed = false, radiusDp = 24.dp)
            .padding(contentPaddingDp.dp),
        content = content
    )
}
