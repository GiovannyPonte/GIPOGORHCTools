package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun GipogoBorderlessNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    unitText: String,
    keyboardType: KeyboardType,
    onUnitClick: (() -> Unit)? = null,
    showUnitInField: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }

    // ✅ Cursor claro (fondo oscuro)
    val cursorBrush = remember(cs.primary) { SolidColor(cs.primary) }

    // ✅ Oculta la “gota” (handle) cuando hay selección
    val selectionColors = remember(cs.primary) {
        TextSelectionColors(
            handleColor = Color.Transparent,                 // <- esto elimina la gota
            backgroundColor = cs.primary.copy(alpha = 0.25f) // selección suave
        )
    }

    // Valor (más grande)
    val valueStyle: TextStyle = MaterialTheme.typography.headlineSmall.copy(
        color = cs.onBackground
    )

    // Placeholder (mismo tamaño que unidad)
    val placeholderStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(
        color = cs.onSurfaceVariant
    )

    // Unidad
    val unitStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(
        color = cs.onSurfaceVariant
    )

    // ---------- Estado interno para selección ----------
    var tfv by remember { mutableStateOf(TextFieldValue(text = value)) }
    var wasFocused by remember { mutableStateOf(false) }

    // Mantener sincronía si el value externo cambia (p.ej. clear/reset, toggles, etc.)
    LaunchedEffect(value) {
        if (value != tfv.text) {
            tfv = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    // Normalización profesional al perder foco (sin cambiar al escribir)
    fun normalizeOnBlur(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return s

        // Coma decimal -> punto
        s = s.replace(',', '.')

        // Si termina en ".", quítalo (ej: "12." -> "12")
        if (s.endsWith(".")) {
            s = s.dropLast(1)
        }

        // Eliminar ceros a la izquierda sin romper "0.x"
        if (s.length > 1) {
            val neg = s.startsWith("-")
            val body = if (neg) s.drop(1) else s
            val normalizedBody = when {
                body.startsWith("0.") -> body
                body.contains('.') -> body.trimStart('0').let { if (it.startsWith(".")) "0$it" else it }
                else -> body.trimStart('0').ifEmpty { "0" }
            }
            s = if (neg) "-$normalizedBody" else normalizedBody
        }

        return s
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = tfv,
                onValueChange = { next ->
                    tfv = next
                    onValueChange(next.text)
                },
                singleLine = true,
                textStyle = valueStyle,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                interactionSource = interaction,
                cursorBrush = cursorBrush,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
                    .onFocusChanged { fs ->
                        val focused = fs.isFocused

                        // ✅ Al ganar foco: seleccionar todo si hay valor
                        if (focused && !wasFocused && tfv.text.isNotBlank()) {
                            tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
                        }

                        // ✅ Al perder foco: normaliza (si cambia, propágalo)
                        if (!focused && wasFocused) {
                            val normalized = normalizeOnBlur(tfv.text)
                            if (normalized != tfv.text) {
                                tfv = TextFieldValue(
                                    text = normalized,
                                    selection = TextRange(normalized.length)
                                )
                                onValueChange(normalized)
                            } else {
                                tfv = tfv.copy(selection = TextRange(tfv.text.length))
                            }
                        }

                        wasFocused = focused
                    },
                decorationBox = { innerTextField ->
                    Box {
                        innerTextField()

                        if (tfv.text.isBlank()) {
                            Text(
                                text = placeholder,
                                style = placeholderStyle,
                                modifier = Modifier.alpha(0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        }

        // ✅ Unidad pegada al extremo derecho del Row
        if (showUnitInField && unitText.isNotBlank()) {
            if (onUnitClick != null) {
                TextButton(
                    onClick = onUnitClick,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = unitText,
                        style = unitStyle,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = unitText,
                    style = unitStyle,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 10.dp),
                    maxLines = 1
                )
            }
        }
    }
}
