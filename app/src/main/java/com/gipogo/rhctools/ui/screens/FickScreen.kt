package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.CalcNavigatorBar
import com.gipogo.rhctools.ui.components.GipogoCalcTopBar
import com.gipogo.rhctools.ui.components.GipogoFieldHint
import com.gipogo.rhctools.ui.components.GipogoResultsHeroCard
import com.gipogo.rhctools.ui.components.GipogoSectionHeaderRow
import com.gipogo.rhctools.ui.components.GipogoSingleInputCard
import com.gipogo.rhctools.ui.components.GipogoSplitInputCard
import com.gipogo.rhctools.ui.components.GipogoSurfaceCard
import com.gipogo.rhctools.ui.components.InterpretationGaugeCardGeneric
import com.gipogo.rhctools.ui.components.calcSwipeNavigation
import com.gipogo.rhctools.ui.interpretation.FickCoInterpretation
import com.gipogo.rhctools.ui.validation.FickField
import com.gipogo.rhctools.ui.validation.FickValidation
import com.gipogo.rhctools.ui.validation.NumericParsing
import com.gipogo.rhctools.ui.validation.NumericValidators
import com.gipogo.rhctools.ui.validation.Severity
import com.gipogo.rhctools.ui.validation.UnitConverters
import com.gipogo.rhctools.ui.viewmodel.FickViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FickHelpTopic { SAO2, SVO2, HB, HR }

@Composable
fun FickScreen(
    onBackToMenu: () -> Unit,
    onNextCalculation: (() -> Unit)? = null,
    onNextCalc: () -> Unit,
    onPrevCalc: () -> Unit,
    vm: FickViewModel
) {
    val state by vm.state.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var submitted by rememberSaveable { mutableStateOf(false) }

    var showInfoDialog by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<FickHelpTopic?>(null) }

    var scrollToResultRequested by remember { mutableStateOf(false) }

    // ---- parse + convert to base units ----
    val weightParsed = NumericParsing.parseDouble(state.weight)
    val weightKg = when (state.weightUnit) {
        FickViewModel.WeightUnit.KG -> weightParsed
        FickViewModel.WeightUnit.LB -> weightParsed?.let { UnitConverters.lbToKg(it) }
    }

    val heightParsed = NumericParsing.parseDouble(state.height)
    val heightCm = when (state.heightUnit) {
        FickViewModel.HeightUnit.CM -> heightParsed
        FickViewModel.HeightUnit.IN -> heightParsed?.let { UnitConverters.inToCm(it) }
        FickViewModel.HeightUnit.M -> heightParsed?.let { UnitConverters.mToCm(it) }
    }

    val hbParsed = NumericParsing.parseDouble(state.hb)
    val hbGdL = when (state.hbUnit) {
        FickViewModel.HbUnit.G_DL -> hbParsed
        FickViewModel.HbUnit.G_L -> hbParsed?.let { UnitConverters.gLToGdL(it) }
    }

    val sao2Val = NumericParsing.parseDouble(state.saO2)
    val svo2Val = NumericParsing.parseDouble(state.svO2)
    val hrVal = NumericParsing.parseDouble(state.heartRate)

    // ---- rules gated by submit ----
    val weightRule = FickValidation.weightRule.copy(required = submitted)
    val heightRule = FickValidation.heightRule.copy(required = submitted)
    val sao2Rule = FickValidation.sao2Rule.copy(required = submitted)
    val svo2Rule = FickValidation.svo2Rule.copy(required = submitted)
    val hbRule = FickValidation.hbRule.copy(required = submitted)
    val hrRule = FickValidation.hrRule.copy(required = submitted)

    val validation = remember(weightKg, heightCm, hbGdL, sao2Val, svo2Val, hrVal, submitted) {
        mapOf(
            FickField.WEIGHT to NumericValidators.validateValue(weightKg, weightRule),
            FickField.HEIGHT to NumericValidators.validateValue(heightCm, heightRule),
            FickField.SAO2 to NumericValidators.validateValue(sao2Val, sao2Rule),
            FickField.SVO2 to NumericValidators.validateValue(svo2Val, svo2Rule),
            FickField.HB to NumericValidators.validateValue(hbGdL, hbRule),
            FickField.HR to NumericValidators.validateValue(hrVal, hrRule)
        )
    }

    val hasError = validation.values.any { it.severity == Severity.ERROR }
    val canCalculate = !hasError

    // Scroll al final cuando aparece resultado/error
    LaunchedEffect(state.cardiacOutputLMin, state.error) {
        if (scrollToResultRequested && (state.cardiacOutputLMin != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    val weightV = validation[FickField.WEIGHT]!!
    val heightV = validation[FickField.HEIGHT]!!
    val sao2V = validation[FickField.SAO2]!!
    val svo2V = validation[FickField.SVO2]!!
    val hbV = validation[FickField.HB]!!
    val hrV = validation[FickField.HR]!!

    val patientSeverity: Severity? = when {
        weightV.severity == Severity.ERROR || heightV.severity == Severity.ERROR -> Severity.ERROR
        weightV.severity == Severity.WARNING || heightV.severity == Severity.WARNING -> Severity.WARNING
        else -> null
    }

    val oxygenSeverity: Severity? = when {
        sao2V.severity == Severity.ERROR || svo2V.severity == Severity.ERROR -> Severity.ERROR
        sao2V.severity == Severity.WARNING || svo2V.severity == Severity.WARNING -> Severity.WARNING
        else -> null
    }

    val patientMsg: String? = run {
        val show = submitted || state.weight.isNotBlank() || state.height.isNotBlank()
        if (!show) null else {
            val candidate =
                listOf(weightV, heightV).firstOrNull { it.severity == Severity.ERROR && it.messageResId != null }
                    ?: listOf(weightV, heightV).firstOrNull { it.severity == Severity.WARNING && it.messageResId != null }
            candidate?.messageResId?.let { stringResource(it) }
        }
    }

    val oxygenMsg: String? = run {
        val show = submitted || state.saO2.isNotBlank() || state.svO2.isNotBlank()
        if (!show) null else {
            val candidate =
                listOf(sao2V, svo2V).firstOrNull { it.severity == Severity.ERROR && it.messageResId != null }
                    ?: listOf(sao2V, svo2V).firstOrNull { it.severity == Severity.WARNING && it.messageResId != null }
            candidate?.messageResId?.let { stringResource(it) }
        }
    }

    val hbMsg: String? =
        if ((submitted || state.hb.isNotBlank()) && hbV.severity != Severity.OK && hbV.messageResId != null)
            stringResource(hbV.messageResId)
        else null

    val hrMsg: String? =
        if ((submitted || state.heartRate.isNotBlank()) && hrV.severity != Severity.OK && hrV.messageResId != null)
            stringResource(hrV.messageResId)
        else null

    val heightUnitText = when (state.heightUnit) {
        FickViewModel.HeightUnit.CM -> "cm"
        FickViewModel.HeightUnit.IN -> "in"
        FickViewModel.HeightUnit.M -> "m"
    }

    val hbUnitText = when (state.hbUnit) {
        FickViewModel.HbUnit.G_DL -> "g/dL"
        FickViewModel.HbUnit.G_L -> "g/L"
    }

    val sao2Placeholder = "95–100"
    val svo2Placeholder = "60–80"
    val hbPlaceholder = "12–17"
    val hrPlaceholder = "60–100"

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoCalcTopBar(
                title = stringResource(R.string.fick_screen_title),
                onBack = onBackToMenu,
                onInfo = { showInfoDialog = true },

                // ✅ RESET DIRECTO (sin AlertDialog)
                onReset = {
                    vm.clear()
                    submitted = false
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .calcSwipeNavigation(onPrev = onPrevCalc, onNext = onNextCalc)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GipogoSectionHeaderRow(title = stringResource(R.string.fick_section_patient_data))

            GipogoSplitInputCard(
                leftLabel = stringResource(R.string.fick_field_weight),
                leftValue = state.weight,
                leftPlaceholder = "1–150",
                leftUnit = if (state.weightUnit == FickViewModel.WeightUnit.KG) "kg" else "lb",
                onLeftChange = vm::setWeight,
                onLeftUnitClick = vm::toggleWeightUnit,
                onLeftHelpClick = null,
                leftSeverity = weightV.severity,

                rightLabel = stringResource(R.string.fick_field_height),
                rightValue = state.height,
                rightPlaceholder = "152–213",
                rightUnit = heightUnitText,
                onRightChange = vm::setHeight,
                onRightUnitClick = vm::toggleHeightUnit,
                onRightHelpClick = null,
                rightSeverity = heightV.severity,

                keyboardType = KeyboardType.Decimal,
                overallSeverity = patientSeverity
            )
            patientMsg?.let { GipogoFieldHint(severity = patientSeverity ?: Severity.OK, text = it) }

            GipogoSectionHeaderRow(title = stringResource(R.string.fick_section_oxygenation))

            GipogoSplitInputCard(
                leftLabel = stringResource(R.string.fick_label_sao2_short),
                leftValue = state.saO2,
                leftPlaceholder = sao2Placeholder,
                leftUnit = "%",
                onLeftChange = vm::setSaO2,
                onLeftHelpClick = { helpTopic = FickHelpTopic.SAO2 },
                leftSeverity = sao2V.severity,

                rightLabel = stringResource(R.string.fick_label_svo2_short),
                rightValue = state.svO2,
                rightPlaceholder = svo2Placeholder,
                rightUnit = "%",
                onRightChange = vm::setSvO2,
                onRightHelpClick = { helpTopic = FickHelpTopic.SVO2 },
                rightSeverity = svo2V.severity,

                keyboardType = KeyboardType.Decimal,
                overallSeverity = oxygenSeverity
            )
            oxygenMsg?.let { GipogoFieldHint(severity = oxygenSeverity ?: Severity.OK, text = it) }

            GipogoSectionHeaderRow(title = stringResource(R.string.fick_section_labs))

            GipogoSingleInputCard(
                label = stringResource(R.string.fick_label_hb_short),
                value = state.hb,
                placeholder = hbPlaceholder,
                unit = hbUnitText,
                onValueChange = vm::setHb,
                keyboardType = KeyboardType.Decimal,
                onUnitClick = vm::toggleHbUnit,
                onHelpClick = { helpTopic = FickHelpTopic.HB },
                severity = hbV.severity
            )
            hbMsg?.let { GipogoFieldHint(severity = hbV.severity, text = it) }

            GipogoSectionHeaderRow(title = stringResource(R.string.fick_section_heart_rate))

            GipogoSingleInputCard(
                label = stringResource(R.string.fick_label_hr_short),
                value = state.heartRate,
                placeholder = hrPlaceholder,
                unit = "bpm",
                onValueChange = vm::setHeartRate,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = FickHelpTopic.HR },
                severity = hrV.severity
            )
            hrMsg?.let { GipogoFieldHint(severity = hrV.severity, text = it) }

            GipogoSurfaceCard {
                AgeToggleCompat(selected = state.ageGroup, onSelect = vm::setAgeGroup)
            }

            Button(
                onClick = {
                    submitted = true
                    if (!canCalculate) return@Button
                    scrollToResultRequested = true
                    vm.calculate()
                    coroutineScope.launch {
                        delay(220)
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
            ) { Text(stringResource(R.string.common_btn_calculate)) }

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            // Resultados + gauge (usando CO)
            if (state.error == null && state.cardiacOutputLMin != null && state.cardiacIndexLMinM2 != null) {
                val co = state.cardiacOutputLMin!!
                val ci = state.cardiacIndexLMinM2!!
                val sv = state.strokeVolumeMlBeat

                GipogoResultsHeroCard(
                    eyebrow = stringResource(R.string.fick_result_eyebrow_co),
                    mainValue = format2(co),
                    mainUnit = stringResource(R.string.common_unit_lmin),
                    leftLabel = stringResource(R.string.fick_result_ci_label),
                    leftValue = format2(ci),
                    leftUnit = stringResource(R.string.common_unit_lmin_m2),
                    rightLabel = stringResource(R.string.fick_result_sv_label),
                    rightValue = sv?.let { format0(it) } ?: stringResource(R.string.common_value_na),
                    rightUnit = stringResource(R.string.common_unit_ml),
                    interpretationContent = {
                        InterpretationGaugeCardGeneric(
                            value = co,
                            spec = FickCoInterpretation.spec
                        )
                    }
                )
            }

            if (onNextCalculation != null) {
                OutlinedButton(onClick = onNextCalculation) {
                    Text(stringResource(R.string.fick_next_calc_svr))
                }
            }

            CalcNavigatorBar(onPrev = onPrevCalc, onNext = onNextCalc)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.fick_intro_title)) },
            text = { Text(stringResource(R.string.fick_intro_body)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }

    helpTopic?.let { topic ->
        val (titleRes, bodyRes) = when (topic) {
            FickHelpTopic.SAO2 -> R.string.fick_help_sao2_title to R.string.fick_help_sao2_body
            FickHelpTopic.SVO2 -> R.string.fick_help_svo2_title to R.string.fick_help_svo2_body
            FickHelpTopic.HB -> R.string.fick_help_hb_title to R.string.fick_help_hb_body
            FickHelpTopic.HR -> R.string.fick_help_hr_title to R.string.fick_help_hr_body
        }

        AlertDialog(
            onDismissRequest = { helpTopic = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = { helpTopic = null }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }
}

@Composable
private fun AgeToggleCompat(
    selected: FickViewModel.AgeGroup,
    onSelect: (FickViewModel.AgeGroup) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fick_age_title))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val leftSelected = selected == FickViewModel.AgeGroup.LT70
            val rightSelected = selected == FickViewModel.AgeGroup.GE70

            if (leftSelected) {
                FilledTonalButton(onClick = { onSelect(FickViewModel.AgeGroup.LT70) }) {
                    Text(stringResource(R.string.fick_age_lt70))
                }
            } else {
                OutlinedButton(onClick = { onSelect(FickViewModel.AgeGroup.LT70) }) {
                    Text(stringResource(R.string.fick_age_lt70))
                }
            }

            if (rightSelected) {
                FilledTonalButton(onClick = { onSelect(FickViewModel.AgeGroup.GE70) }) {
                    Text(stringResource(R.string.fick_age_ge70))
                }
            } else {
                OutlinedButton(onClick = { onSelect(FickViewModel.AgeGroup.GE70) }) {
                    Text(stringResource(R.string.fick_age_ge70))
                }
            }
        }
    }
}

private fun format2(v: Double): String = String.format("%.2f", v)
private fun format0(v: Double): String = String.format("%.0f", v)
