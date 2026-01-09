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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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


@Composable
fun FickScreen(
    onBackToMenu: () -> Unit,
    vm: FickViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()
    LaunchedEffect(resetTick) { vm.clear() }

    val scrollState = rememberScrollState()

    // Para desplazar el scroll al resultado después de calcular
    var scrollToResultRequested by remember { mutableStateOf(false) }

    // Cuando aparezca resultado o error, bajamos al final
    LaunchedEffect(state.cardiacOutputLMin, state.error) {
        if (scrollToResultRequested && (state.cardiacOutputLMin != null || state.error != null)) {
            // pequeña espera para que Compose recalculé el layout y maxValue sea correcto
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    ScreenScaffold(
        title = "Cardiac Output (Fick)",
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
                Text("Cardiac Output (Fick’s Formula)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Interfaz tipo MDCalc: peso, talla, SaO₂, SvO₂, Hb, HR y edad. " +
                            "VO₂ estimado se ajusta automáticamente por edad; opción avanzada para VO₂ medido.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Peso / talla con toggle que convierte el valor (como MDCalc)
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Weight / Height", style = MaterialTheme.typography.titleSmall)

                    UnitToggleField(
                        title = "Peso (Weight)",
                        value = state.weight,
                        onValueChange = vm::setWeight,
                        unitText = if (state.weightUnit == FickViewModel.WeightUnit.KG) "kg" else "lb",
                        onToggleUnit = vm::toggleWeightUnit
                    )

                    UnitToggleField(
                        title = "Talla (Height)",
                        value = state.height,
                        onValueChange = vm::setHeight,
                        unitText = if (state.heightUnit == FickViewModel.HeightUnit.CM) "cm" else "in",
                        onToggleUnit = vm::toggleHeightUnit
                    )

                    state.bsa?.let {
                        Text("BSA (Mosteller): ${Format.d(it, 2)} m²", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Variables requeridas
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Variables requeridas", style = MaterialTheme.typography.titleSmall)

                    SimpleField(
                        title = "SaO₂ (arterial) – ideal en gasometría/co-oximetría",
                        value = state.saO2,
                        onValueChange = vm::setSaO2,
                        unit = "%"
                    )

                    SimpleField(
                        title = "SvO₂ (mixta) – muestra de arteria pulmonar (PA) con Swan-Ganz",
                        value = state.svO2,
                        onValueChange = vm::setSvO2,
                        unit = "%"
                    )

                    SimpleField(
                        title = "Hemoglobina (Hemoglobin, Hb) – biometría/lab",
                        value = state.hb,
                        onValueChange = vm::setHb,
                        unit = "g/dL"
                    )

                    SimpleField(
                        title = "Frecuencia cardiaca (Heart rate, HR)",
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
                            "VO₂ estimado automático según edad: ${Format.d(factor, 0)} mL/min/m²",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Opciones avanzadas
            SectionCard {
                TextButton(onClick = { vm.setShowAdvanced(!state.showAdvanced) }) {
                    Text(if (state.showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
                }

                if (state.showAdvanced) {
                    RowCheck(
                        label = "Usar VO₂ medido (calorimetría indirecta)",
                        checked = state.useMeasuredVo2,
                        onCheckedChange = vm::setUseMeasuredVo2
                    )
                    if (state.useMeasuredVo2) {
                        SimpleField(
                            title = "VO₂ medido (Measured VO₂)",
                            value = state.vo2Measured,
                            onValueChange = vm::setVo2Measured,
                            unit = "mL/min"
                        )
                    }

                    RowCheck(
                        label = "Incluir componente disuelto (PaO₂/PvO₂)",
                        checked = state.includeDissolved,
                        onCheckedChange = vm::setIncludeDissolved
                    )
                    if (state.includeDissolved) {
                        SimpleField("PaO₂ (arterial)", state.paO2, vm::setPaO2, "mmHg")
                        SimpleField("PvO₂ (venosa)", state.pvO2, vm::setPvO2, "mmHg")
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

            // Resultado + glosario debajo (como pediste)
            state.cardiacOutputLMin?.let { co ->
                val body = buildString {
                    appendLine("CO: ${Format.d(co, 2)} L/min")
                    state.cardiacIndexLMinM2?.let { appendLine("CI: ${Format.d(it, 2)} L/min/m²") }
                    state.strokeVolumeMlBeat?.let { appendLine("SV: ${Format.d(it, 1)} mL/beat") }
                    state.vo2UsedMlMin?.let { appendLine("VO₂ usado: ${Format.d(it, 0)} mL/min") }
                    state.bsa?.let { appendLine("BSA: ${Format.d(it, 2)} m²") }
                    state.caO2_mlDl?.let { appendLine("CaO₂: ${Format.d(it, 2)} mL/dL") }
                    state.cvO2_mlDl?.let { appendLine("CvO₂: ${Format.d(it, 2)} mL/dL") }
                    state.avDiff_mlDl?.let { appendLine("A–V diff: ${Format.d(it, 2)} mL/dL") }
                    state.vo2FactorUsedMlMinM2?.let { appendLine("VO₂ factor usado: ${Format.d(it, 0)} mL/min/m²") }



                    appendLine()
                    appendLine("Siglas:")
                    appendLine("• CO: Cardiac Output (gasto cardíaco).")
                    appendLine("• CI: Cardiac Index (índice cardíaco = CO/BSA).")
                    appendLine("• SV: Stroke Volume (volumen sistólico = CO/HR).")
                    appendLine("• VO₂: Oxygen consumption (consumo de oxígeno).")
                    appendLine("• BSA: Body Surface Area (superficie corporal).")
                    appendLine("• CaO₂: Arterial oxygen content (contenido arterial de O₂).")
                    appendLine("• CvO₂: Venous oxygen content (contenido venoso de O₂).")
                    appendLine("• A–V diff: diferencia arterio-venosa de O₂.")
                }

                ResultCard(title = "Resultado", body = body)
            }
        }
    }
    LaunchedEffect(state.cardiacOutputLMin) {
        val co = state.cardiacOutputLMin ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.FICK,
                timestampMillis = System.currentTimeMillis(),
                title = "Cardiac Output (Fick)",
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
                    state.vo2FactorUsedMlMinM2?.let { "VO₂ factor: ${Format.d(it,0)} mL/min/m² (auto by age)" }
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
        Text("Edad (Age)", style = MaterialTheme.typography.bodyMedium)

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = selected == FickViewModel.AgeGroup.LT70,
                onClick = { onSelect(FickViewModel.AgeGroup.LT70) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("< 70 años")
            }

            SegmentedButton(
                selected = selected == FickViewModel.AgeGroup.GE70,
                onClick = { onSelect(FickViewModel.AgeGroup.GE70) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("≥ 70 años")
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
