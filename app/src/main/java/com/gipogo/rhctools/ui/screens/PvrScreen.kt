package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gipogo.rhctools.R
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.ui.components.CalcNavigatorBar
import com.gipogo.rhctools.ui.components.GipogoCalcTopBar
import com.gipogo.rhctools.ui.components.GipogoFieldHint
import com.gipogo.rhctools.ui.components.GipogoResultsHeroCard
import com.gipogo.rhctools.ui.components.GipogoSectionHeaderRow
import com.gipogo.rhctools.ui.components.GipogoSingleInputCard
import com.gipogo.rhctools.ui.components.GipogoSurfaceCard
import com.gipogo.rhctools.ui.components.InterpretationGaugeCardGeneric
import com.gipogo.rhctools.ui.components.calcSwipeNavigation
import com.gipogo.rhctools.ui.interpretation.PvrInterpretation
import com.gipogo.rhctools.ui.validation.NumericParsing
import com.gipogo.rhctools.ui.validation.NumericValidators
import com.gipogo.rhctools.ui.validation.PvrField
import com.gipogo.rhctools.ui.validation.PvrValidation
import com.gipogo.rhctools.ui.validation.Severity
import com.gipogo.rhctools.ui.viewmodel.PvrViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay

private enum class PvrHelpTopic { MPAP, PAWP, CO, UNITS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PvrScreen(
    onBackToMenu: () -> Unit,
    onNextCalc: () -> Unit,
    onPrevCalc: () -> Unit,
    vm: PvrViewModel
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val entries by ReportStore.entries.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()

    var submitted by rememberSaveable { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<PvrHelpTopic?>(null) }
    var scrollToResultRequested by remember { mutableStateOf(false) }

    // ✅ NUEVO: Prefill shared (solo 1 vez)
    var didPrefillShared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resetTick) { didPrefillShared = false }

    // ✅ NUEVO: marca si la mPAP fue estimada desde PASP/PADP
    var mpapEstimated by remember { mutableStateOf(false) }

    // ✅ PASO 5.5: Prefill mPAP / PAWP / CO desde ReportStore usando KEYS
    LaunchedEffect(entries, state.mpap, state.pawp, state.co, didPrefillShared) {
        if (didPrefillShared) return@LaunchedEffect

        val needMpap = state.mpap.isBlank()
        val needPawp = state.pawp.isBlank()
        val needCo = state.co.isBlank()

        if (!needMpap && !needPawp && !needCo) {
            didPrefillShared = true
            return@LaunchedEffect
        }

        // 1) mPAP directa si existe
        if (needMpap) {
            val mpapFromStore = ReportStore.latestValueDoubleByKey(SharedKeys.MPAP_MMHG)
            if (mpapFromStore != null) {
                val ok = NumericValidators.validateValue(mpapFromStore, PvrValidation.mpapRule).severity != Severity.ERROR
                if (ok) {
                    vm.setMPAP(Format.d(mpapFromStore, 0))
                    mpapEstimated = false
                }
            } else {
                // 1b) ✅ NUEVO: estimar mPAP desde PASP/PADP si existen
                val pasp = ReportStore.latestValueDoubleByKey(SharedKeys.PASP_MMHG)
                val padp = ReportStore.latestValueDoubleByKey(SharedKeys.PADP_MMHG)
                if (pasp != null && padp != null) {
                    val mpapEst = HemodynamicsFormulas.meanPulmonaryArteryPressureFromSystolicDiastolic(
                        pasp_mmHg = pasp,
                        padp_mmHg = padp
                    )
                    val ok = NumericValidators.validateValue(mpapEst, PvrValidation.mpapRule).severity != Severity.ERROR
                    if (ok) {
                        vm.setMPAP(Format.d(mpapEst, 0))
                        mpapEstimated = true
                    }
                }
            }
        }

        // 2) PAWP
        if (needPawp) {
            val pawpFromStore = ReportStore.latestValueDoubleByKey(SharedKeys.PAWP_MMHG)
            if (pawpFromStore != null) {
                val ok = NumericValidators.validateValue(pawpFromStore, PvrValidation.pawpRule).severity != Severity.ERROR
                if (ok) vm.setPAWP(Format.d(pawpFromStore, 0))
            }
        }

        // 3) CO
        if (needCo) {
            val coFromStore = ReportStore.latestValueDoubleByKey(SharedKeys.CO_LMIN)
            if (coFromStore != null) {
                val ok = NumericValidators.validateValue(coFromStore, PvrValidation.coRule).severity != Severity.ERROR
                if (ok) vm.setCO(Format.d(coFromStore, 2))
            }
        }

        didPrefillShared = true
    }

    // Parse inputs
    val mpapVal = NumericParsing.parseDouble(state.mpap)
    val pawpVal = NumericParsing.parseDouble(state.pawp)
    val coVal = NumericParsing.parseDouble(state.co)

    // Rules gated by submit
    val mpapRule = PvrValidation.mpapRule.copy(required = submitted)
    val pawpRule = PvrValidation.pawpRule.copy(required = submitted)
    val coRule = PvrValidation.coRule.copy(required = submitted)

    val validation = remember(mpapVal, pawpVal, coVal, submitted) {
        mapOf(
            PvrField.MPAP to NumericValidators.validateValue(mpapVal, mpapRule),
            PvrField.PAWP to NumericValidators.validateValue(pawpVal, pawpRule),
            PvrField.CO to NumericValidators.validateValue(coVal, coRule)
        )
    }

    val mpapV = validation[PvrField.MPAP]!!
    val pawpV = validation[PvrField.PAWP]!!
    val coV = validation[PvrField.CO]!!

    val hasError = validation.values.any { it.severity == Severity.ERROR }
    val canCalculate = !hasError

    val mpapMsg: String? =
        if ((submitted || state.mpap.isNotBlank()) && mpapV.severity != Severity.OK && mpapV.messageResId != null)
            stringResource(mpapV.messageResId)
        else null

    val pawpMsg: String? =
        if ((submitted || state.pawp.isNotBlank()) && pawpV.severity != Severity.OK && pawpV.messageResId != null)
            stringResource(pawpV.messageResId)
        else null

    val coMsg: String? =
        if ((submitted || state.co.isNotBlank()) && coV.severity != Severity.OK && coV.messageResId != null)
            stringResource(coV.messageResId)
        else null

    // Scroll a resultados/error
    LaunchedEffect(state.pvrWu, state.pvrDynes, state.tprWu, state.tprDynes, state.error) {
        if (scrollToResultRequested && (state.pvrWu != null || state.tprWu != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoCalcTopBar(
                title = stringResource(R.string.pvr_screen_title),
                onBack = onBackToMenu,
                onInfo = { showInfoDialog = true },
                onReset = {
                    vm.clear()
                    submitted = false
                    didPrefillShared = false
                    mpapEstimated = false
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
            GipogoSectionHeaderRow(title = stringResource(R.string.pvr_section_variables))

            GipogoSingleInputCard(
                label = stringResource(R.string.pvr_field_mpap_title),
                value = state.mpap,
                placeholder = stringResource(R.string.pvr_placeholder_mpap),
                unit = stringResource(R.string.common_unit_mmhg),
                onValueChange = {
                    mpapEstimated = false // si el usuario escribe, deja de ser estimada
                    vm.setMPAP(it)
                },
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = PvrHelpTopic.MPAP },
                severity = mpapV.severity
            )
            mpapMsg?.let { GipogoFieldHint(severity = mpapV.severity, text = it) }

            // ✅ NUEVO: etiqueta informativa (no rompe nada)
            if (mpapEstimated) {
                Text(
                    text = "mPAP estimada a partir de PASP/PADP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            GipogoSingleInputCard(
                label = stringResource(R.string.pvr_field_pawp_title),
                value = state.pawp,
                placeholder = stringResource(R.string.pvr_placeholder_pawp),
                unit = stringResource(R.string.common_unit_mmhg),
                onValueChange = vm::setPAWP,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = PvrHelpTopic.PAWP },
                severity = pawpV.severity
            )
            pawpMsg?.let { GipogoFieldHint(severity = pawpV.severity, text = it) }

            GipogoSingleInputCard(
                label = stringResource(R.string.pvr_field_co_title),
                value = state.co,
                placeholder = stringResource(R.string.pvr_placeholder_co),
                unit = stringResource(R.string.common_unit_lmin),
                onValueChange = vm::setCO,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = PvrHelpTopic.CO },
                severity = coV.severity
            )
            coMsg?.let { GipogoFieldHint(severity = coV.severity, text = it) }

            GipogoSectionHeaderRow(title = stringResource(R.string.pvr_units_title))

            GipogoSurfaceCard {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.outputUnits == PvrViewModel.OutputUnits.WOOD_UNITS,
                        onClick = { vm.setOutputUnits(PvrViewModel.OutputUnits.WOOD_UNITS) },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.common_unit_wu)) }

                    SegmentedButton(
                        selected = state.outputUnits == PvrViewModel.OutputUnits.DYNES,
                        onClick = { vm.setOutputUnits(PvrViewModel.OutputUnits.DYNES) },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.common_unit_dynes)) }
                }
            }

            Button(
                onClick = {
                    submitted = true
                    if (!canCalculate) return@Button
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text(stringResource(R.string.common_btn_calculate)) }

            state.error?.let { code ->
                val msg = when (code) {
                    PvrViewModel.ErrorCode.MISSING_INPUTS -> stringResource(R.string.pvr_error_missing)
                    PvrViewModel.ErrorCode.CO_NONPOSITIVE -> stringResource(R.string.pvr_error_co_nonpositive)
                    PvrViewModel.ErrorCode.GRADIENT_NONPOSITIVE -> stringResource(R.string.pvr_error_gradient_nonpositive)
                }
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }

            val pvrWu = state.pvrWu
            val pvrDyn = state.pvrDynes
            val tprWu = state.tprWu
            val tprDyn = state.tprDynes

            if (state.error == null && pvrWu != null && pvrDyn != null) {
                val mainValue: String
                val mainUnit: String

                if (state.outputUnits == PvrViewModel.OutputUnits.WOOD_UNITS) {
                    mainValue = Format.d(pvrWu, 2)
                    mainUnit = stringResource(R.string.common_unit_wu)
                } else {
                    mainValue = Format.d(pvrDyn, 0)
                    mainUnit = stringResource(R.string.common_unit_dynes)
                }

                val usingWu = state.outputUnits == PvrViewModel.OutputUnits.WOOD_UNITS
                val gaugeValue = if (usingWu) pvrWu else pvrDyn
                val gaugeSpec = if (usingWu) PvrInterpretation.specWu else PvrInterpretation.specDynes

                GipogoResultsHeroCard(
                    eyebrow = stringResource(R.string.pvr_result_title),
                    mainValue = mainValue,
                    mainUnit = mainUnit,

                    leftLabel = stringResource(R.string.pvr_hero_left_label),
                    leftValue = Format.d(pvrWu, 2),
                    leftUnit = stringResource(R.string.common_unit_wu),

                    rightLabel = stringResource(R.string.pvr_hero_right_label),
                    rightValue = Format.d(pvrDyn, 0),
                    rightUnit = stringResource(R.string.common_unit_dynes),

                    interpretationContent = {
                        InterpretationGaugeCardGeneric(
                            value = gaugeValue,
                            spec = gaugeSpec
                        )
                    }
                )
            }

            if (tprWu != null && tprDyn != null) {
                val mainValue: String
                val mainUnit: String

                if (state.outputUnits == PvrViewModel.OutputUnits.WOOD_UNITS) {
                    mainValue = Format.d(tprWu, 2)
                    mainUnit = stringResource(R.string.common_unit_wu)
                } else {
                    mainValue = Format.d(tprDyn, 0)
                    mainUnit = stringResource(R.string.common_unit_dynes)
                }

                GipogoResultsHeroCard(
                    eyebrow = stringResource(R.string.tpr_result_title),
                    mainValue = mainValue,
                    mainUnit = mainUnit,
                    leftLabel = stringResource(R.string.pvr_hero_left_label),
                    leftValue = Format.d(tprWu, 2),
                    leftUnit = stringResource(R.string.common_unit_wu),
                    rightLabel = stringResource(R.string.pvr_hero_right_label),
                    rightValue = Format.d(tprDyn, 0),
                    rightUnit = stringResource(R.string.common_unit_dynes)
                )
            }

            CalcNavigatorBar(onPrev = onPrevCalc, onNext = onNextCalc)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ✅ Guardar en ReportStore si hay al menos un resultado (PVR o TPR)
    LaunchedEffect(state.pvrWu, state.pvrDynes, state.tprWu, state.tprDynes) {
        val pvrWu = state.pvrWu
        val pvrDyn = state.pvrDynes
        val tprWu = state.tprWu
        val tprDyn = state.tprDynes

        if (pvrWu == null && tprWu == null) return@LaunchedEffect

        val outputs = mutableListOf<LineItem>()

        if (pvrWu != null && pvrDyn != null) {
            outputs += LineItem(label = "PVR", value = Format.d(pvrWu, 2), unit = "WU", detail = "Pulmonary Vascular Resistance (Wood Units)")
            outputs += LineItem(label = "PVR", value = Format.d(pvrDyn, 0), unit = "dyn·s·cm⁻⁵", detail = "Pulmonary Vascular Resistance (CGS)")
        }

        if (tprWu != null && tprDyn != null) {
            outputs += LineItem(label = "TPR", value = Format.d(tprWu, 2), unit = "WU", detail = "Total Pulmonary Resistance (Wood Units)")
            outputs += LineItem(label = "TPR", value = Format.d(tprDyn, 0), unit = "dyn·s·cm⁻⁵", detail = "Total Pulmonary Resistance (CGS)")
        }

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PVR,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.pvr_report_title),
                inputs = listOf(
                    LineItem(key = SharedKeys.MPAP_MMHG, label = "mPAP", value = state.mpap, unit = "mmHg", detail = "Mean Pulmonary Artery Pressure"),
                    LineItem(key = SharedKeys.PAWP_MMHG, label = "PAWP", value = state.pawp, unit = "mmHg", detail = "Pulmonary Artery Wedge Pressure"),
                    LineItem(key = SharedKeys.CO_LMIN, label = "CO", value = state.co, unit = "L/min", detail = "Cardiac Output (Qp in absence of shunt)")
                ),
                outputs = outputs,
                notes = emptyList()
            )
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.pvr_info_title)) },
            text = { Text(stringResource(R.string.pvr_info_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }

    helpTopic?.let { topic ->
        val (titleRes, bodyRes) = when (topic) {
            PvrHelpTopic.MPAP -> R.string.pvr_help_mpap_title to R.string.pvr_help_mpap_body
            PvrHelpTopic.PAWP -> R.string.pvr_help_pawp_title to R.string.pvr_help_pawp_body
            PvrHelpTopic.CO -> R.string.pvr_help_co_title to R.string.pvr_help_co_body
            PvrHelpTopic.UNITS -> R.string.pvr_help_units_title to R.string.pvr_help_units_body
        }

        AlertDialog(
            onDismissRequest = { helpTopic = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { helpTopic = null }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }
}
