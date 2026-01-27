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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.yield

/**
 * ✅ No muestra el menú “Pegar / Seleccionar todo / Autocompletar”
 * (pero deja cursor/selección funcionales)
 */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) = Unit
}

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

    // ✅ Selección visible + handle visible (profesional)
    val selectionColors = remember(cs.primary) {
        TextSelectionColors(
            handleColor = cs.primary,
            backgroundColor = cs.primary.copy(alpha = 0.25f)
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
    var tfv by remember { mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length))) }
    var wasFocused by remember { mutableStateOf(false) }

    // ✅ “Select-all” diferido (para que el tap no lo pise)
    var pendingSelectAll by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSelectAll) {
        if (pendingSelectAll) {
            // Espera 1 tick para ganar a la colocación de cursor por tap
            yield()
            if (tfv.text.isNotBlank()) {
                tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
            }
            pendingSelectAll = false
        }
    }

    // Mantener sincronía si el value externo cambia
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
        CompositionLocalProvider(
            LocalTextToolbar provides NoTextToolbar,
            LocalTextSelectionColors provides selectionColors
        ) {
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

                        // ✅ Al ganar foco: select-all (diferido)
                        if (focused && !wasFocused && tfv.text.isNotBlank()) {
                            pendingSelectAll = true
                        }

                        // ✅ Al perder foco: normaliza y deja cursor al final
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

        // ✅ Unidad al extremo derecho del Row
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
