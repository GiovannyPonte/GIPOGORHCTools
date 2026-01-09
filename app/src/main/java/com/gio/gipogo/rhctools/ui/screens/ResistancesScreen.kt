package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import com.gipogo.rhctools.ui.viewmodel.ResistancesViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResistancesScreen(
    onBackToMenu: () -> Unit,
    vm: ResistancesViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()
    LaunchedEffect(resetTick) { vm.clear() }

    val scrollState = rememberScrollState()

    var scrollToResultRequested by remember { mutableStateOf(false) }

    // Scroll automático al resultado / error
    LaunchedEffect(state.svrWu, state.svrDynes, state.error) {
        if (scrollToResultRequested && (state.svrWu != null || state.svrDynes != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    ScreenScaffold(
        title = "Systemic Vascular Resistance",
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

            // Inputs exactamente como la web
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Questions", style = MaterialTheme.typography.titleSmall)

                    Field(
                        title = "Mean Arterial Pressure (MAP)",
                        shortLabel = "MAP",
                        value = state.map,
                        onValueChange = vm::setMAP,
                        unitText = "mmHg"
                    )

                    Field(
                        title = "Central Venous Pressure (CVP)",
                        shortLabel = "CVP",
                        value = state.cvp,
                        onValueChange = vm::setCVP,
                        unitText = "mmHg"
                    )

                    Field(
                        title = "Cardiac Output (CO)",
                        shortLabel = "CO",
                        value = state.co,
                        onValueChange = vm::setCO,
                        unitText = "L/min"
                    )

                    Text("Units", style = MaterialTheme.typography.bodyMedium)

                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS,
                            onClick = { vm.setOutputUnits(ResistancesViewModel.OutputUnits.WOOD_UNITS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Wood Units") }

                        SegmentedButton(
                            selected = state.outputUnits == ResistancesViewModel.OutputUnits.DYNES,
                            onClick = { vm.setOutputUnits(ResistancesViewModel.OutputUnits.DYNES) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("dyn·s·cm⁻⁵") }
                    }
                }
            }

            Button(
                onClick = {
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text("Calcular") }

            state.error?.let { ResultCard(title = "Error", body = it) }

            // Resultado como panel
            val wu = state.svrWu
            val dynes = state.svrDynes
            if (wu != null && dynes != null) {
                val valueLine = if (state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS) {
                    "${Format.d(wu, 2)} Wood Units"
                } else {
                    "${Format.d(dynes, 0)} dyn·s·cm⁻⁵"
                }

                ResultCard(
                    title = "Results",
                    body = "Systemic Vascular Resistance\n$valueLine\n\n" +
                            "Siglas:\n" +
                            "• SVR: Systemic Vascular Resistance.\n" +
                            "• MAP: Mean Arterial Pressure (presión arterial media).\n" +
                            "• CVP: Central Venous Pressure (presión venosa central).\n" +
                            "• CO: Cardiac Output (gasto cardíaco).\n" +
                            "• Wood Units: unidades de resistencia.\n" +
                            "• dyn·s·cm⁻⁵: unidades en sistema CGS."
                )
            }
        }
    }
    LaunchedEffect(state.svrWu, state.svrDynes) {
        val wu = state.svrWu ?: return@LaunchedEffect
        val dyn = state.svrDynes ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.SVR,
                timestampMillis = System.currentTimeMillis(),
                title = "Systemic Vascular Resistance (SVR)",
                inputs = listOf(
                    LineItem("MAP", state.map, "mmHg", "Mean Arterial Pressure"),
                    LineItem("CVP", state.cvp, "mmHg", "Central Venous Pressure"),
                    LineItem("CO", state.co, "L/min", "Cardiac Output")
                ),
                outputs = listOf(
                    LineItem("SVR", Format.d(wu, 2), "WU", "Wood Units"),
                    LineItem("SVR", Format.d(dyn, 0), "dyn·s·cm⁻⁵", "CGS units")
                )
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
    unitText: String
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
    }
}
