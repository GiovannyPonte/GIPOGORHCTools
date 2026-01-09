package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.ui.components.ResultCard
import com.gipogo.rhctools.ui.components.ScreenScaffold
import com.gipogo.rhctools.ui.components.SectionCard
import com.gipogo.rhctools.ui.viewmodel.FickViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import androidx.compose.ui.platform.LocalContext
import com.gipogo.rhctools.R


@Composable
fun FickScreen(
    onBackToMenu: () -> Unit,
    vm: FickViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()
    LaunchedEffect(resetTick) { vm.clear() }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Para desplazar el scroll al resultado después de calcular
    var scrollToResultRequested by remember { mutableStateOf(false) }

    // Cuando aparezca resultado o error, bajamos al final
    LaunchedEffect(state.cardiacOutputLMin, state.error) {
        if (scrollToResultRequested && (state.cardiacOutputLMin != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.fick_screen_title),
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
                    stringResource(R.string.fick_intro_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.fick_intro_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Peso / talla con toggle que convierte el valor (como MDCalc)
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.fick_card_weight_height), style = MaterialTheme.typography.titleSmall)

                    UnitToggleField(
                        title = stringResource(R.string.fick_field_weight),
                        value = state.weight,
                        onValueChange = vm::setWeight,
                        unitText = if (state.weightUnit == FickViewModel.WeightUnit.KG) "kg" else "lb",
                        onToggleUnit = vm::toggleWeightUnit
                    )

                    UnitToggleField(
                        title = stringResource(R.string.fick_field_height),
                        value = state.height,
                        onValueChange = vm::setHeight,
                        unitText = if (state.heightUnit == FickViewModel.HeightUnit.CM) "cm" else "in",
                        onToggleUnit = vm::toggleHeightUnit
                    )

                    state.bsa?.let {
                        val bsaText = Format.d(it, 2)
                        Text(
                            stringResource(R.string.fick_bsa_mosteller, bsaText),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Variables requeridas
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.fick_required_vars_title), style = MaterialTheme.typography.titleSmall)

                    SimpleField(
                        title = stringResource(R.string.fick_field_sao2),
                        value = state.saO2,
                        onValueChange = vm::setSaO2,
                        unit = "%"
                    )

                    SimpleField(
                        title = stringResource(R.string.fick_field_svo2),
                        value = state.svO2,
                        onValueChange = vm::setSvO2,
                        unit = "%"
                    )

                    SimpleField(
                        title = stringResource(R.string.fick_field_hb),
                        value = state.hb,
                        onValueChange = vm::setHb,
                        unit = "g/dL"
                    )

                    SimpleField(
                        title = stringResource(R.string.fick_field_hr),
                        value = state.heartRate,
                        onValueChange = vm::setHeartRate,
                        unit = "bpm"
                    )

                    AgeToggle(
                        selected = state.ageGroup,
                        onSelect = vm::setAgeGroup
                    )

                    // Mostramos el ajuste “tipo MDCalc” sin permitir edición
                    state.vo2FactorUsedMlMinM2?.let { factor ->
                        Text(
                            stringResource(R.string.fick_vo2_auto, Format.d(factor, 0)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Opciones avanzadas
            SectionCard {
                TextButton(onClick = { vm.setShowAdvanced(!state.showAdvanced) }) {
                    Text(
                        if (state.showAdvanced)
                            stringResource(R.string.fick_advanced_hide)
                        else
                            stringResource(R.string.fick_advanced_show)
                    )
                }

                if (state.showAdvanced) {
                    RowCheck(
                        label = stringResource(R.string.fick_use_measured_vo2),
                        checked = state.useMeasuredVo2,
                        onCheckedChange = vm::setUseMeasuredVo2
                    )
                    if (state.useMeasuredVo2) {
                        SimpleField(
                            title = stringResource(R.string.fick_measured_vo2),
                            value = state.vo2Measured,
                            onValueChange = vm::setVo2Measured,
                            unit = "mL/min"
                        )
                    }

                    RowCheck(
                        label = stringResource(R.string.fick_include_dissolved),
                        checked = state.includeDissolved,
                        onCheckedChange = vm::setIncludeDissolved
                    )
                    if (state.includeDissolved) {
                        SimpleField(stringResource(R.string.fick_pao2), state.paO2, vm::setPaO2, "mmHg")
                        SimpleField(stringResource(R.string.fick_pvo2), state.pvO2, vm::setPvO2, "mmHg")
                    }
                }
            }

            Button(
                onClick = {
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text(stringResource(R.string.common_btn_calculate)) }

            state.error?.let { ResultCard(title = stringResource(R.string.common_error), body = it) }

            // Resultado + glosario debajo
            state.cardiacOutputLMin?.let { co ->
                val body = buildString {
                    appendLine(stringResource(R.string.fick_result_co, Format.d(co, 2)))
                    state.cardiacIndexLMinM2?.let { appendLine(stringResource(R.string.fick_result_ci, Format.d(it, 2))) }
                    state.strokeVolumeMlBeat?.let { appendLine(stringResource(R.string.fick_result_sv, Format.d(it, 1))) }
                    state.vo2UsedMlMin?.let { appendLine(stringResource(R.string.fick_result_vo2_used, Format.d(it, 0))) }
                    state.bsa?.let { appendLine(stringResource(R.string.fick_result_bsa, Format.d(it, 2))) }
                    state.caO2_mlDl?.let { appendLine(stringResource(R.string.fick_result_cao2, Format.d(it, 2))) }
                    state.cvO2_mlDl?.let { appendLine(stringResource(R.string.fick_result_cvo2, Format.d(it, 2))) }
                    state.avDiff_mlDl?.let { appendLine(stringResource(R.string.fick_result_avdiff, Format.d(it, 2))) }
                    state.vo2FactorUsedMlMinM2?.let { appendLine(stringResource(R.string.fick_result_vo2_factor, Format.d(it, 0))) }

                    appendLine()
                    appendLine(stringResource(R.string.fick_abbrev_title))
                    appendLine(stringResource(R.string.fick_abbrev_co))
                    appendLine(stringResource(R.string.fick_abbrev_ci))
                    appendLine(stringResource(R.string.fick_abbrev_sv))
                    appendLine(stringResource(R.string.fick_abbrev_vo2))
                    appendLine(stringResource(R.string.fick_abbrev_sc))
                    appendLine(stringResource(R.string.fick_abbrev_cao2))
                    appendLine(stringResource(R.string.fick_abbrev_cvo2))
                    appendLine(stringResource(R.string.fick_abbrev_avdiff))
                }


                ResultCard(title = stringResource(R.string.common_result), body = body)
            }
        }
    }

    LaunchedEffect(state.cardiacOutputLMin) {
        val co = state.cardiacOutputLMin ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.FICK,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.fick_report_title),
                inputs = listOf(
                    LineItem("Weight", state.weight, null, "Peso"),
                    LineItem("Height", state.height, null, "Talla"),
                    LineItem("SaO₂", state.saO2, "%", "Arterial O₂ saturation"),
                    LineItem("SvO₂", state.svO2, "%", "Mixed venous O₂ saturation (PA)"),
                    LineItem("Hb", state.hb, "g/dL", "Hemoglobin"),
                    LineItem("HR", state.heartRate, "bpm", "Heart rate"),
                    LineItem("Age group", if (state.ageGroup.name == "GE70") "≥70" else "<70", null, "Grupo de edad")
                ),
                outputs = listOf(
                    LineItem("CO", Format.d(co, 2), "L/min", "Cardiac Output"),
                    state.cardiacIndexLMinM2?.let { LineItem("CI", Format.d(it, 2), "L/min/m²", "Cardiac Index") },
                    state.strokeVolumeMlBeat?.let { LineItem("SV", Format.d(it, 1), "mL/beat", "Stroke Volume") },
                    state.vo2UsedMlMin?.let { LineItem("VO₂ used", Format.d(it, 0), "mL/min", "O₂ consumption used") }
                ).filterNotNull(),
                notes = listOfNotNull(
                    state.vo2FactorUsedMlMinM2?.let { "VO₂ factor: ${Format.d(it, 0)} mL/min/m² (auto by age)" }
                )
            )
        )
    }
}

@Composable
private fun UnitToggleField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String,
    onToggleUnit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(unitText.uppercase()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = {
                TextButton(onClick = onToggleUnit) { Text(unitText) }
            }
        )
    }
}

@Composable
private fun SimpleField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeToggle(
    selected: FickViewModel.AgeGroup,
    onSelect: (FickViewModel.AgeGroup) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fick_age_title), style = MaterialTheme.typography.bodyMedium)

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = selected == FickViewModel.AgeGroup.LT70,
                onClick = { onSelect(FickViewModel.AgeGroup.LT70) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.fick_age_lt70))
            }

            SegmentedButton(
                selected = selected == FickViewModel.AgeGroup.GE70,
                onClick = { onSelect(FickViewModel.AgeGroup.GE70) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.fick_age_ge70))
            }
        }
    }
}

@Composable
private fun RowCheck(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}
