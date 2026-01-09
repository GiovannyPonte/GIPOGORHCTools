package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.ui.components.ResultCard
import com.gipogo.rhctools.ui.components.ScreenScaffold
import com.gipogo.rhctools.ui.components.SectionCard
import com.gipogo.rhctools.ui.viewmodel.PapiViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore


@Composable
fun PapiScreen(
    onBackToMenu: () -> Unit,
    vm: PapiViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()
    LaunchedEffect(resetTick) { vm.clear() }

    val scrollState = rememberScrollState()

    var scrollToResultRequested by remember { mutableStateOf(false) }

    // Scroll automático cuando aparezca resultado o error
    LaunchedEffect(state.papi, state.error) {
        if (scrollToResultRequested && (state.papi != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    ScreenScaffold(
        title = "PAPi",
        onBackToMenu = onBackToMenu
    ) { _ ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            SectionCard {
                Text(
                    "Índice de pulsatilidad de arteria pulmonar (Pulmonary Artery Pulsatility Index, PAPi)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Fórmula: PAPi = (PASP − PADP) / RAP",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Inputs", style = MaterialTheme.typography.titleSmall)

                    Field(
                        title = "Presión sistólica de arteria pulmonar (Pulmonary Artery Systolic Pressure, PASP)",
                        shortLabel = "PASP",
                        value = state.pasp,
                        onValueChange = vm::setPASP,
                        unitText = "mmHg",
                        help = "Medir en trazo de arteria pulmonar con Swan-Ganz. Registrar la PASP."
                    )

                    Field(
                        title = "Presión diastólica de arteria pulmonar (Pulmonary Artery Diastolic Pressure, PADP)",
                        shortLabel = "PADP",
                        value = state.padp,
                        onValueChange = vm::setPADP,
                        unitText = "mmHg",
                        help = "Medir en trazo de arteria pulmonar con Swan-Ganz. Registrar la PADP."
                    )

                    Field(
                        title = "Presión auricular derecha / presión venosa central (Right Atrial Pressure, RAP)",
                        shortLabel = "RAP",
                        value = state.rap,
                        onValueChange = vm::setRAP,
                        unitText = "mmHg",
                        help = "Medir en aurícula derecha con Swan-Ganz (o PVC). Ideal al final de la espiración."
                    )
                }
            }

            Button(
                onClick = {
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text("Calcular") }

            state.error?.let { ResultCard(title = "Error", body = it) }

            state.papi?.let { papi ->
                val body = buildString {
                    appendLine("PAPi: ${Format.d(papi, 2)}")
                    state.note?.let { appendLine("\nNota: $it") }

                    appendLine()
                    appendLine("Siglas:")
                    appendLine("• PAPi: Pulmonary Artery Pulsatility Index (índice de pulsatilidad de AP).")
                    appendLine("• PASP: Pulmonary Artery Systolic Pressure (presión sistólica de AP).")
                    appendLine("• PADP: Pulmonary Artery Diastolic Pressure (presión diastólica de AP).")
                    appendLine("• RAP: Right Atrial Pressure (presión auricular derecha).")
                    appendLine("• PVC: Presión venosa central.")
                    appendLine("• AP/PA: Arteria pulmonar (Pulmonary Artery).")
                }

                ResultCard(title = "Resultado", body = body)
            }
        }
    }
    LaunchedEffect(state.papi) {
        val papi = state.papi ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PAPI,
                timestampMillis = System.currentTimeMillis(),
                title = "Pulmonary Artery Pulsatility Index (PAPi)",
                inputs = listOf(
                    LineItem("PASP", state.pasp, "mmHg", "PA systolic pressure"),
                    LineItem("PADP", state.padp, "mmHg", "PA diastolic pressure"),
                    LineItem("RAP", state.rap, "mmHg", "Right atrial pressure")
                ),
                outputs = listOf(
                    LineItem("PAPi", Format.d(papi, 2), null, "Pulsatility Index")
                ),
                notes = listOfNotNull(state.note)
            )
        )
    }

}

@Composable
private fun Field(
    title: String,
    shortLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String,
    help: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(shortLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = { Text(unitText) }
        )

        Text(help, style = MaterialTheme.typography.bodySmall)
        Text("Unidad: $unitText", style = MaterialTheme.typography.bodySmall)
    }
}
