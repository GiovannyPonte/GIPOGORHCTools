package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    var acceptedCheckbox by remember { mutableStateOf(false) }

    // Altura aproximada de la zona fija inferior (checkbox+botones + padding)
    // Esto evita que el final del texto quede tapado.
    val bottomSafePadding = 120.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // CONTENIDO SCROLLEABLE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = bottomSafePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Para que en tablets / pantallas anchas no se vea como “líneas interminables”
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.disclaimer_title),
                    style = MaterialTheme.typography.titleLarge
                )

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Permite seleccionar/copiar el texto del disclaimer si el usuario lo necesita.
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = stringResource(R.string.disclaimer_section_edu_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.disclaimer_section_edu_body),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    )
                                )

                                Text(
                                    text = stringResource(R.string.disclaimer_section_user_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.disclaimer_section_user_body),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
                                    )
                                )

                                Text(
                                    text = stringResource(R.string.disclaimer_section_limit_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.disclaimer_section_limit_body),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
                                    )
                                )

                                Text(
                                    text = stringResource(R.string.disclaimer_section_privacy_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.disclaimer_section_privacy_body),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        // BARRA INFERIOR FIJA (siempre accesible)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptedCheckbox,
                            onCheckedChange = { acceptedCheckbox = it }
                        )
                        Text(
                            text = stringResource(R.string.disclaimer_checkbox_text),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDecline
                    ) { Text(stringResource(R.string.disclaimer_btn_decline)) }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = acceptedCheckbox,
                        onClick = onAccept
                    ) { Text(stringResource(R.string.disclaimer_btn_accept)) }
                }
            }
        }
    }
}
