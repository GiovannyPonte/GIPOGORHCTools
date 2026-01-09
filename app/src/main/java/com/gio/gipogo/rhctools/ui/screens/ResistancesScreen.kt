package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.R
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.ui.components.ResultCard
import com.gipogo.rhctools.ui.components.ScreenScaffold
import com.gipogo.rhctools.ui.viewmodel.ResistancesViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResistancesScreen(
    onBackToMenu: () -> Unit,
    vm: ResistancesViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

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
        title = stringResource(R.string.svr_screen_title),
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

            // Inputs
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.svr_section_questions), style = MaterialTheme.typography.titleSmall)

                    Field(
                        title = stringResource(R.string.svr_field_map_title),
                        shortLabel = "MAP",
                        value = state.map,
                        onValueChange = vm::setMAP,
                        unitText = "mmHg"
                    )

                    Field(
                        title = stringResource(R.string.svr_field_cvp_title),
                        shortLabel = "CVP",
                        value = state.cvp,
                        onValueChange = vm::setCVP,
                        unitText = "mmHg"
                    )

                    Field(
                        title = stringResource(R.string.svr_field_co_title),
                        shortLabel = "CO",
                        value = state.co,
                        onValueChange = vm::setCO,
                        unitText = "L/min"
                    )

                    Text(stringResource(R.string.svr_units_title), style = MaterialTheme.typography.bodyMedium)

                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS,
                            onClick = { vm.setOutputUnits(ResistancesViewModel.OutputUnits.WOOD_UNITS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(stringResource(R.string.svr_units_wood)) }

                        SegmentedButton(
                            selected = state.outputUnits == ResistancesViewModel.OutputUnits.DYNES,
                            onClick = { vm.setOutputUnits(ResistancesViewModel.OutputUnits.DYNES) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.svr_units_dynes)) }
                    }
                }
            }

            Button(
                onClick = {
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text(stringResource(R.string.common_btn_calculate)) }

            // Error traducible (con stringResource en UI: OK)
            state.error?.let { err ->
                val msg = when (err) {
                    ResistancesViewModel.ErrorCode.MISSING_INPUTS ->
                        stringResource(R.string.svr_error_missing)
                    ResistancesViewModel.ErrorCode.CO_NONPOSITIVE ->
                        stringResource(R.string.svr_error_co_nonpositive)
                }
                ResultCard(title = stringResource(R.string.common_error), body = msg)
            }

            // Resultado
            val wu = state.svrWu
            val dynes = state.svrDynes
            if (wu != null && dynes != null) {

                val valueLine = if (state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS) {
                    "${Format.d(wu, 2)} ${stringResource(R.string.svr_units_wood)}"
                } else {
                    "${Format.d(dynes, 0)} ${stringResource(R.string.svr_units_dynes)}"
                }

                val body = buildString {
                    appendLine(stringResource(R.string.svr_result_title))
                    appendLine(valueLine)
                    appendLine()
                    appendLine(stringResource(R.string.svr_siglas_title))
                    appendLine(stringResource(R.string.svr_sigla_svr))
                    appendLine(stringResource(R.string.svr_sigla_map))
                    appendLine(stringResource(R.string.svr_sigla_cvp))
                    appendLine(stringResource(R.string.svr_sigla_co))
                    appendLine(stringResource(R.string.svr_sigla_wood))
                    appendLine(stringResource(R.string.svr_sigla_dynes))
                }

                ResultCard(
                    title = stringResource(R.string.common_result),
                    body = body
                )
            }
        }
    }

    // ✅ Reporte PDF: aquí NO usamos stringResource (porque es @Composable).
    LaunchedEffect(state.svrWu, state.svrDynes) {
        val wu = state.svrWu ?: return@LaunchedEffect
        val dyn = state.svrDynes ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.SVR,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.svr_report_title),
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
