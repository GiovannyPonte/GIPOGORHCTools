package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
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
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.ui.components.ResultCard
import com.gipogo.rhctools.ui.components.ScreenScaffold
import com.gipogo.rhctools.ui.components.SectionCard
import com.gipogo.rhctools.ui.viewmodel.CpoViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay

@Composable
fun CpoScreen(
    onBackToMenu: () -> Unit,
    vm: CpoViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    // ✅ Reset global SIN vm.clear()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()
    LaunchedEffect(resetTick) {
        // limpiar valores
        vm.setMAP("")
        vm.setCO("")
        vm.setBSA("")

        // volver unidades a default
        if (state.mapUnit != CpoViewModel.MapUnit.MMHG) vm.toggleMapUnit()
        if (state.coUnit != CpoViewModel.CoUnit.L_MIN) vm.toggleCoUnit()
    }

    val scrollState = rememberScrollState()
    var scrollToResultRequested by remember { mutableStateOf(false) }

    // Scroll automático al resultado/error después de calcular
    LaunchedEffect(state.result, state.error) {
        if (scrollToResultRequested && (state.result != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    // Guardar resultado para Reporte PDF
    LaunchedEffect(state.result) {
        val r = state.result ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.CPO,
                timestampMillis = System.currentTimeMillis(),
                title = "Cardiac Power Output (CPO/CPI)",
                inputs = listOf(
                    LineItem("MAP", state.map, if (state.mapUnit == CpoViewModel.MapUnit.KPA) "kPa" else "mmHg", "Mean Arterial Pressure"),
                    LineItem("CO", state.co, if (state.coUnit == CpoViewModel.CoUnit.L_SEC) "L/s" else "L/min", "Cardiac Output"),
                    LineItem("BSA", state.bsa, "m²", "Body Surface Area (optional)")
                ),
                outputs = listOfNotNull(
                    LineItem("CPO", Format.d(r.cpoWatts, 2), "W", "Cardiac Power Output"),
                    r.cpiWattsPerM2?.let { LineItem("CPI", Format.d(it, 2), "W/m²", "Cardiac Power Index") }
                )
            )
        )
    }

    ScreenScaffold(
        title = "Potencia cardíaca",
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
                    "Potencia cardíaca (Cardiac Power Output, CPO) e índice (Cardiac Power Index, CPI)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "CPO = (MAP × CO) / 451.  CPI = (MAP × CI) / 451 si hay BSA.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Variables", style = MaterialTheme.typography.titleSmall)

                    UnitToggleField(
                        title = "Presión arterial media (Mean Arterial Pressure, MAP)",
                        value = state.map,
                        onValueChange = vm::setMAP,
                        unitText = if (state.mapUnit == CpoViewModel.MapUnit.MMHG) "mmHg" else "kPa",
                        onToggleUnit = vm::toggleMapUnit,
                        help = "Preferible: línea arterial invasiva. Alternativa: MAP ≈ (PAS + 2×PAD) / 3."
                    )

                    UnitToggleField(
                        title = "Gasto cardíaco (Cardiac Output, CO)",
                        value = state.co,
                        onValueChange = vm::setCO,
                        unitText = if (state.coUnit == CpoViewModel.CoUnit.L_MIN) "L/min" else "L/s",
                        onToggleUnit = vm::toggleCoUnit,
                        help = "Tomar del cateterismo derecho: termodilución (promedio) o Fick."
                    )

                    SimpleField(
                        title = "Superficie corporal (Body Surface Area, BSA) (opcional)",
                        value = state.bsa,
                        onValueChange = vm::setBSA,
                        unit = "m²",
                        help = "Necesaria solo si quieres CPI (CPO indexado a BSA)."
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

            state.result?.let { r ->
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MetricTilesRow(
                            leftLabel = "CPO",
                            leftValue = Format.d(r.cpoWatts, 2),
                            leftUnit = "W",
                            rightLabel = "CPI",
                            rightValue = r.cpiWattsPerM2?.let { Format.d(it, 2) } ?: "—",
                            rightUnit = "W/m²"
                        )
                    }
                }

                val body = buildString {
                    appendLine("CPO: ${Format.d(r.cpoWatts, 2)} W")
                    r.cpiWattsPerM2?.let { appendLine("CPI: ${Format.d(it, 2)} W/m²") }
                    appendLine()
                    appendLine("Siglas:")
                    appendLine("• MAP: Mean Arterial Pressure (presión arterial media).")
                    appendLine("• CO: Cardiac Output (gasto cardíaco).")
                    appendLine("• CI: Cardiac Index (índice cardíaco = CO/BSA).")
                    appendLine("• BSA: Body Surface Area (superficie corporal).")
                    appendLine("• CPO: Cardiac Power Output (potencia cardíaca).")
                    appendLine("• CPI: Cardiac Power Index (CPO indexado a BSA).")
                }

                ResultCard(title = "Resultado", body = body)
            }
        }
    }
}

@Composable
private fun MetricTilesRow(
    leftLabel: String,
    leftValue: String,
    leftUnit: String,
    rightLabel: String,
    rightValue: String,
    rightUnit: String
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val half = maxWidth / 2

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .width(half)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MetricTile(label = leftLabel, value = leftValue, unit = leftUnit)
            }

            Column(
                modifier = Modifier
                    .width(half)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MetricTile(label = rightLabel, value = rightValue, unit = rightUnit)
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, unit: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(unit, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UnitToggleField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String,
    onToggleUnit: () -> Unit,
    help: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(unitText) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = {
                TextButton(onClick = onToggleUnit) { Text(unitText) }
            }
        )

        Text(help, style = MaterialTheme.typography.bodySmall)
        Text("Unidad: $unitText", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SimpleField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    help: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Text(help, style = MaterialTheme.typography.bodySmall)
        Text("Unidad: $unit", style = MaterialTheme.typography.bodySmall)
    }
}
