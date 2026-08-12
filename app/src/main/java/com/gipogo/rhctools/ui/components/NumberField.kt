package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun NumberField(
    // NUEVO: título arriba del campo (ideal para nombres largos)
    titleAbove: String? = null,

    // Label corto dentro del TextField (ej. "CO", "MAP", "BSA")
    label: String,

    value: String,
    onValueChange: (String) -> Unit,

    // Compatibilidad hacia atrás:
    suffix: String? = null,

    // Nuevo nombre:
    unit: String? = null,

    supportingText: String? = null,

    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val finalUnit = unit ?: suffix

    Column(modifier = modifier) {
        if (!titleAbove.isNullOrBlank()) {
            Text(titleAbove)
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = { onValueChange(it) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
                Column {
                    if (!supportingText.isNullOrBlank()) Text(supportingText)
                    if (!finalUnit.isNullOrBlank()) Text("Unidad: $finalUnit")
                }
            }
        )
    }
}
