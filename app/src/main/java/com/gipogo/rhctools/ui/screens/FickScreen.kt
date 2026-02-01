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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.report.CalcEntryWriters
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
import com.gipogo.rhctools.util.Format
import com.gipogo.rhctools.workshop.WorkshopMode
import com.gipogo.rhctools.workshop.WorkshopPrefillStore
import com.gipogo.rhctools.workshop.WorkshopSession
import com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FickHelpTopic { SAO2, SVO2, HB, HR }
private enum class CoMethodUi { FICK, THERMODILUTION }

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
    val context = LocalContext.current

    var submitted by rememberSaveable { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf(CoMethodUi.FICK) }

    // TD inputs (runs L/min)
    var td1 by rememberSaveable { mutableStateOf("") }
    var td2 by rememberSaveable { mutableStateOf("") }
    var td3 by rememberSaveable { mutableStateOf("") }

    // TD results
    var tdCo by rememberSaveable { mutableStateOf<Double?>(null) }
    var tdCi by rememberSaveable { mutableStateOf<Double?>(null) }
    var tdSv by rememberSaveable { mutableStateOf<Double?>(null) }
    var tdError by rememberSaveable { mutableStateOf<String?>(null) }

    var showInfoDialog by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<FickHelpTopic?>(null) }
    var scrollToResultRequested by remember { mutableStateOf(false) }

    // ---------- PREFILL desde BD (solo en modo paciente) ----------
    val workshopCtx by WorkshopSession.context.collectAsState()
    val prefill by WorkshopPrefillStore.prefill.collectAsState()

    LaunchedEffect(
        workshopCtx.mode,
        prefill,
        state.weight,
        state.height,
        state.ageGroup,
        state.weightUnit,
        state.heightUnit
    ) {
        if (workshopCtx.mode != WorkshopMode.PATIENT_STUDY) return@LaunchedEffect

        val wKg = prefill.weightKg
        if (state.weight.isBlank() && wKg != null) {
            val valueInUiUnits = when (state.weightUnit) {
                FickViewModel.WeightUnit.KG -> wKg
                FickViewModel.WeightUnit.LB -> kgToLb(wKg)
            }
            vm.setWeight(String.format("%.0f", valueInUiUnits))
        }

        val hCm = prefill.heightCm
        if (state.height.isBlank() && hCm != null) {
            val valueInUiUnits = when (state.heightUnit) {
                FickViewModel.HeightUnit.CM -> hCm
                FickViewModel.HeightUnit.IN -> cmToIn(hCm)
                FickViewModel.HeightUnit.M -> cmToM(hCm)
            }
            val formatted = when (state.heightUnit) {
                FickViewModel.HeightUnit.M -> String.format("%.2f", valueInUiUnits)
                else -> String.format("%.0f", valueInUiUnits)
            }
            vm.setHeight(formatted)
        }

        val by = prefill.birthDateMillis
        if (by != null) {
            val ageYears = ChronoUnit.YEARS.between(
                Instant.ofEpochMilli(by).atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now()
            ).toInt()

            val target = if (ageYears >= 70) FickViewModel.AgeGroup.GE70 else FickViewModel.AgeGroup.LT70
            if (state.ageGroup != target) vm.setAgeGroup(target)
        }
    }

    // ---- Parse + convert to base units ----
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

    val td1Val = NumericParsing.parseDouble(td1)
    val td2Val = NumericParsing.parseDouble(td2)
    val td3Val = NumericParsing.parseDouble(td3)

    // ✅ TD runs deben ser finitos y > 0
    val tdRuns = listOfNotNull(td1Val, td2Val, td3Val).filter { it.isFinite() && it > 0.0 }
    val tdHasError = submitted && tdRuns.isEmpty()

    // ---- Validation rules gated by submit ----
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

    val fickHasError = validation.values.any { it.severity == Severity.ERROR }
    val canCalculate = when (method) {
        CoMethodUi.FICK -> !fickHasError
        CoMethodUi.THERMODILUTION -> !tdHasError
    }

    LaunchedEffect(state.cardiacOutputLMin, state.error, tdCo, tdError) {
        val hasFickResult = (state.cardiacOutputLMin != null || state.error != null)
        val hasTdResult = (tdCo != null || tdError != null)

        if (scrollToResultRequested && (hasFickResult || hasTdResult)) {
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

    val strTdReportTitle = stringResource(R.string.td_report_title)
    val strTdNeedRun = stringResource(R.string.td_error_need_one_run)
    val strCommonError = stringResource(R.string.common_error)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoCalcTopBar(
                title = stringResource(R.string.fick_screen_title),
                onBack = onBackToMenu,
                onInfo = { showInfoDialog = true },
                onReset = {
                    vm.clear()
                    submitted = false

                    td1 = ""; td2 = ""; td3 = ""
                    tdCo = null; tdCi = null; tdSv = null; tdError = null

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
            GipogoSurfaceCard {
                CoMethodToggleCompat(
                    selected = method,
                    onSelect = {
                        method = it
                        tdCo = null; tdCi = null; tdSv = null; tdError = null
                    }
                )
            }

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

            if (method == CoMethodUi.FICK) {
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
                    unit = stringResource(R.string.common_unit_bpm),
                    onValueChange = vm::setHeartRate,
                    keyboardType = KeyboardType.Decimal,
                    onHelpClick = { helpTopic = FickHelpTopic.HR },
                    severity = hrV.severity
                )
                hrMsg?.let { GipogoFieldHint(severity = hrV.severity, text = it) }

                GipogoSurfaceCard {
                    AgeToggleCompat(selected = state.ageGroup, onSelect = vm::setAgeGroup)
                }
            }

            if (method == CoMethodUi.THERMODILUTION) {
                GipogoSectionHeaderRow(title = stringResource(R.string.td_section_title))

                GipogoSurfaceCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        GipogoSingleInputCard(
                            label = stringResource(R.string.td_run_1),
                            value = td1,
                            placeholder = stringResource(R.string.pvr_placeholder_co),
                            unit = stringResource(R.string.common_unit_lmin),
                            onValueChange = { td1 = it },
                            keyboardType = KeyboardType.Decimal,
                            onHelpClick = null,
                            severity = if (submitted && tdRuns.isEmpty()) Severity.ERROR else Severity.OK
                        )

                        GipogoSingleInputCard(
                            label = stringResource(R.string.td_run_2_optional),
                            value = td2,
                            placeholder = stringResource(R.string.pvr_placeholder_co),
                            unit = stringResource(R.string.common_unit_lmin),
                            onValueChange = { td2 = it },
                            keyboardType = KeyboardType.Decimal,
                            onHelpClick = null,
                            severity = Severity.OK
                        )

                        GipogoSingleInputCard(
                            label = stringResource(R.string.td_run_3_optional),
                            value = td3,
                            placeholder = stringResource(R.string.pvr_placeholder_co),
                            unit = stringResource(R.string.common_unit_lmin),
                            onValueChange = { td3 = it },
                            keyboardType = KeyboardType.Decimal,
                            onHelpClick = null,
                            severity = Severity.OK
                        )

                        if (submitted && tdRuns.isEmpty()) {
                            GipogoFieldHint(
                                severity = Severity.ERROR,
                                text = strTdNeedRun
                            )
                        }

                        Text(
                            text = stringResource(R.string.td_tip_weight_height_optional),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Button(
                onClick = {
                    submitted = true
                    if (!canCalculate) return@Button

                    scrollToResultRequested = true

                    when (method) {
                        CoMethodUi.FICK -> {
                            vm.calculate()
                            WorkshopRhcAutosave.setCoMethod("FICK")
                            WorkshopRhcAutosave.flushNow(context, coroutineScope)
                        }

                        CoMethodUi.THERMODILUTION -> {
                            tdError = null
                            try {
                                // BSA opcional (si hay peso+talla). Si no es válida, se pasa null (fail-clean en dominio).
                                val rawBsa = if (weightKg != null && heightCm != null) {
                                    HemodynamicsFormulas.bsaMosteller(heightCm, weightKg)
                                } else null
                                val bsa = rawBsa?.takeIf { it.isFinite() && it > 0.0 }

                                // ✅ Single source of truth: dominio calcula CO/CI/SV
                                val td = HemodynamicsFormulas.thermodilutionDerived(
                                    runs_LMin = tdRuns,
                                    bsa_m2 = bsa,
                                    hr_bpm = hrVal
                                )

                                // Gate: no permitir NaN/Inf ni CO <= 0
                                val co = td.cardiacOutputLMin.takeIf { it.isFinite() && it > 0.0 }
                                if (co == null) {
                                    tdError = strCommonError
                                    tdCo = null; tdCi = null; tdSv = null
                                    return@Button
                                }

                                val ci = td.cardiacIndexLMinM2?.takeIf { it.isFinite() && it > 0.0 }
                                val sv = td.strokeVolumeMlBeat?.takeIf { it.isFinite() && it > 0.0 }

                                tdCo = co
                                tdCi = ci
                                tdSv = sv

                                // ✅ Persistencia centralizada (C.3)
                                CalcEntryWriters.upsertThermodilutionInFick(
                                    timestampMillis = System.currentTimeMillis(),
                                    title = strTdReportTitle,
                                    tdRun1_LMin = td1Val,
                                    tdRun2_LMin = td2Val,
                                    tdRun3_LMin = td3Val,
                                    coLMin = co,
                                    bsaM2 = bsa,
                                    ciLMinM2 = ci,
                                    svMlBeat = sv
                                )

                                WorkshopRhcAutosave.setCoMethod("TD")
                                WorkshopRhcAutosave.flushNow(context, coroutineScope)
                            } catch (e: Exception) {
                                tdError = e.message ?: strCommonError
                                tdCo = null; tdCi = null; tdSv = null
                            }
                        }
                    }

                    coroutineScope.launch {
                        delay(220)
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
            ) {
                Text(stringResource(R.string.common_btn_calculate))
            }

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            // ✅ No render si CO/CI no son finitos
            val coFinite = state.cardiacOutputLMin?.takeIf { it.isFinite() }
            val ciFinite = state.cardiacIndexLMinM2?.takeIf { it.isFinite() }
            val svFinite = state.strokeVolumeMlBeat?.takeIf { it.isFinite() }

            if (state.error == null && coFinite != null && ciFinite != null) {
                val co = coFinite
                val ci = ciFinite
                val sv = svFinite

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

            if (method == CoMethodUi.THERMODILUTION) {
                tdError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                // ✅ No render si TD CO no es finito
                val co = tdCo?.takeIf { it.isFinite() }
                val ci = tdCi?.takeIf { it.isFinite() }
                val sv = tdSv?.takeIf { it.isFinite() }

                if (tdError == null && co != null) {
                    GipogoResultsHeroCard(
                        eyebrow = strTdReportTitle,
                        mainValue = format2(co),
                        mainUnit = stringResource(R.string.common_unit_lmin),
                        leftLabel = stringResource(R.string.fick_result_ci_label),
                        leftValue = ci?.let { format2(it) } ?: stringResource(R.string.common_value_na),
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

@Composable
private fun CoMethodToggleCompat(
    selected: CoMethodUi,
    onSelect: (CoMethodUi) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.co_method_label))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val leftSelected = selected == CoMethodUi.FICK
            val rightSelected = selected == CoMethodUi.THERMODILUTION

            if (leftSelected) {
                FilledTonalButton(onClick = { onSelect(CoMethodUi.FICK) }) {
                    Text(stringResource(R.string.co_method_fick))
                }
            } else {
                OutlinedButton(onClick = { onSelect(CoMethodUi.FICK) }) {
                    Text(stringResource(R.string.co_method_fick))
                }
            }

            if (rightSelected) {
                FilledTonalButton(onClick = { onSelect(CoMethodUi.THERMODILUTION) }) {
                    Text(stringResource(R.string.co_method_thermodilution))
                }
            } else {
                OutlinedButton(onClick = { onSelect(CoMethodUi.THERMODILUTION) }) {
                    Text(stringResource(R.string.co_method_thermodilution))
                }
            }
        }
    }
}

// ---------- Local converters for PREFILL (BD kg/cm -> UI units) ----------
private fun kgToLb(kg: Double): Double = kg / 0.45359237
private fun cmToIn(cm: Double): Double = cm / 2.54
private fun cmToM(cm: Double): Double = cm / 100.0

private fun format2(v: Double): String = String.format("%.2f", v)
private fun format0(v: Double): String = String.format("%.0f", v)
