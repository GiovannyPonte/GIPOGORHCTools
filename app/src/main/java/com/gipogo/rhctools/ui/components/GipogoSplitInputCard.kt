package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
fun GipogoSplitInputCard(
    leftLabel: String,
    leftValue: String,
    leftPlaceholder: String,
    leftUnit: String,
    onLeftChange: (String) -> Unit,
    onLeftUnitClick: (() -> Unit)? = null,
    onLeftHelpClick: (() -> Unit)? = null,
    leftSeverity: Severity? = null,

    rightLabel: String,
    rightValue: String,
    rightPlaceholder: String,
    rightUnit: String,
    onRightChange: (String) -> Unit,
    onRightUnitClick: (() -> Unit)? = null,
    onRightHelpClick: (() -> Unit)? = null,
    rightSeverity: Severity? = null,

    keyboardType: KeyboardType = KeyboardType.Decimal,
    overallSeverity: Severity? = null
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)

    val borderColor = when (overallSeverity) {
        Severity.ERROR -> cs.error
        Severity.WARNING -> cs.tertiary
        else -> cs.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface, shape)
            .gipogoBorder(1.dp, borderColor, dashed = false, radiusDp = 22.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SplitField(
            label = leftLabel,
            value = leftValue,
            placeholder = leftPlaceholder,
            unit = leftUnit,
            onValueChange = onLeftChange,
            onUnitClick = onLeftUnitClick,
            onHelpClick = onLeftHelpClick,
            keyboardType = keyboardType,
            severity = leftSeverity,
            modifier = Modifier.weight(1f)
        )

        Row(
            modifier = Modifier
                .width(1.dp)
                .height(30.dp)
                .background(cs.outline.copy(alpha = 0.9f))
        ) {}

        SplitField(
            label = rightLabel,
            value = rightValue,
            placeholder = rightPlaceholder,
            unit = rightUnit,
            onValueChange = onRightChange,
            onUnitClick = onRightUnitClick,
            onHelpClick = onRightHelpClick,
            keyboardType = keyboardType,
            severity = rightSeverity,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SplitField(
    label: String,
    value: String,
    placeholder: String,
    unit: String,
    onValueChange: (String) -> Unit,
    onUnitClick: (() -> Unit)?,
    onHelpClick: (() -> Unit)?,
    keyboardType: KeyboardType,
    severity: Severity? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    val fieldBorderColor = when (severity) {
        Severity.ERROR -> cs.error
        Severity.WARNING -> cs.tertiary
        else -> cs.outline
    }

    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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

        // ✅ borde por mitad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .gipogoBorder(1.dp, fieldBorderColor, dashed = false, radiusDp = 16.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
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
}
