package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.ui.validation.Severity

@Composable
fun GipogoSingleInputCard(
    label: String,
    value: String,
    placeholder: String,
    unit: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onUnitClick: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
    severity: Severity? = null
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)

    val borderColor = when (severity) {
        Severity.ERROR -> cs.error
        Severity.WARNING -> cs.tertiary
        else -> cs.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface, shape)
            .gipogoBorder(1.dp, borderColor, dashed = false, radiusDp = 22.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ✅ Label + Info al lado (evita solapamiento con unidad)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (onHelpClick != null) {
                IconCircleButton(
                    onClick = onHelpClick,
                    size = 28.dp,
                    glyph = "ⓘ"
                )
            }
        }

        // ✅ Valor + unidad dentro del field (unidad al extremo derecho)
        GipogoBorderlessNumberField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            unitText = unit,
            keyboardType = keyboardType,
            onUnitClick = onUnitClick,
            showUnitInField = true
        )
    }
}
