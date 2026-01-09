package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    var acceptedCheckbox by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Aviso legal y descargo de responsabilidad",
            style = MaterialTheme.typography.titleLarge
        )

        ElevatedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Uso educativo e instructivo",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Esta aplicación tiene fines exclusivamente educativos e instructivos. " +
                            "Los cálculos y recomendaciones presentados son herramientas de apoyo y pueden " +
                            "depender de la calidad de los datos ingresados, condiciones clínicas y supuestos. " +
                            "No sustituyen la valoración médica integral, guías clínicas, protocolos institucionales " +
                            "ni el juicio clínico.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    "Responsabilidad del usuario",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "El usuario (profesional de la salud) es el único responsable de:\n" +
                            "• Verificar los datos capturados (unidades, origen de las mediciones, tiempos).\n" +
                            "• Interpretar los resultados en el contexto clínico del paciente.\n" +
                            "• Tomar decisiones diagnósticas y terapéuticas basadas en su criterio profesional.\n" +
                            "• Cumplir con normativas locales y políticas de su institución.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    "Limitación de responsabilidad",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "El desarrollador no asume responsabilidad por daños directos o indirectos derivados del uso " +
                            "de esta aplicación. Se recomienda contrastar cualquier resultado con fuentes confiables " +
                            "y, cuando aplique, con métodos de referencia.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    "Privacidad",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Evita ingresar información identificable del paciente si no es necesaria. " +
                            "Si decides hacerlo, eres responsable de su manejo conforme a la legislación aplicable.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Checkbox de aceptación explícita
        ElevatedCard {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = acceptedCheckbox,
                    onCheckedChange = { acceptedCheckbox = it }
                )
                Text(
                    "He leído y acepto los términos. Entiendo que el uso y la interpretación de la información " +
                            "es mi responsabilidad profesional.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Acciones
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onDecline
            ) {
                Text("No acepto")
            }

            Button(
                modifier = Modifier.weight(1f),
                enabled = acceptedCheckbox,
                onClick = onAccept
            ) {
                Text("Acepto")
            }
        }
    }
}
