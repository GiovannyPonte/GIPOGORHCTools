package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R

/**
 * Encabezado de sección estilo HTML:
 * - Título en mayúsculas (labelSmall + semibold)
 * - Pill opcional a la derecha
 * - Botón info opcional (Opción C): uno por sección (evita conflictos con unidades)
 */
@Composable
fun GipogoSectionHeaderRow(
    title: String,
    trailingPillText: String? = null,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (trailingPillText != null) {
                GipogoPill(text = trailingPillText)
            }

            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "Info",
                        tint = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}
