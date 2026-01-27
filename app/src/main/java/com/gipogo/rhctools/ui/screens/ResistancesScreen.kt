package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
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
import com.gipogo.rhctools.ui.validation.NumericParsing
import com.gipogo.rhctools.ui.validation.NumericValidators
import com.gipogo.rhctools.ui.validation.Severity
import com.gipogo.rhctools.ui.validation.SvrField
import com.gipogo.rhctools.ui.validation.SvrValidation
import com.gipogo.rhctools.ui.viewmodel.ResistancesViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay
import com.gipogo.rhctools.ui.interpretation.SvrInterpretation
import com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave
import androidx.compose.runtime.rememberCoroutineScope


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResistancesScreen(
    onBackToMenu: () -> Unit,
    onNextCalc: () -> Unit,
    onPrevCalc: () -> Unit,
    vm: ResistancesViewModel
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    val entries by ReportStore.entries.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()

    var submitted by rememberSaveable { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var scrollToResultRequested by remember { mutableStateOf(false) }

    var didPrefillShared by rememberSaveable { mutableStateOf(false) }

    // Reset global -> permitir que el prefill vuelva a correr cuando cambie resetTick
    LaunchedEffect(resetTick) { didPrefillShared = false }

    // ✅ Prefill shared inputs (CO, MAP, CVP) from ReportStore
    LaunchedEffect(entries, state.map, state.cvp, state.co, didPrefillShared) {
        if (didPrefillShared) return@LaunchedEffect

        val needMap = state.map.isBlank()
        val needCvp = state.cvp.isBlank()
        val needCo = state.co.isBlank()

        if (!needMap && !needCvp && !needCo) {
            didPrefillShared = true
            return@LaunchedEffect
        }

        if (needMap) {
            val mapFromStore = ReportStore.latestValueDoubleByKey(SharedKeys.MAP_MMHG)
            if (mapFromStore != null) {
                val ok = NumericValidators.validateValue(mapFromStore, SvrValidation.mapRule).severity != Severity.ERROR
                if (ok) vm.setMAP(Format.d(mapFromStore, 0))
            }
        }

        if (needCvp) {
            val cvpFromStore = ReportStore.latestValueDoubleByKey(SharedKeys.CVP_MMHG)
            if (cvpFromStore != null) {
                val ok = NumericValidators.validateValue(cvpFromStore, SvrValidation.cvpRule).severity != Severity.ERROR
                if (ok) vm.setCVP(Format.d(cvpFromStore, 0))
            }
        }

        if (needCo) {
            val coFromStore = ReportStore.latestValueDoubleByKey(SharedKeys.CO_LMIN)
            if (coFromStore != null) {
                val ok = NumericValidators.validateValue(coFromStore, SvrValidation.coRule).severity != Severity.ERROR
                if (ok) vm.setCO(Format.d(coFromStore, 2))
            }
        }

        didPrefillShared = true
    }

    // Parse + validate inputs
    val mapVal = NumericParsing.parseDouble(state.map)
    val cvpVal = NumericParsing.parseDouble(state.cvp)
    val coVal = NumericParsing.parseDouble(state.co)

    val mapRule = SvrValidation.mapRule.copy(required = submitted)
    val cvpRule = SvrValidation.cvpRule.copy(required = submitted)
    val coRule = SvrValidation.coRule.copy(required = submitted)

    val validation = remember(mapVal, cvpVal, coVal, submitted) {
        mapOf(
            SvrField.MAP to NumericValidators.validateValue(mapVal, mapRule),
            SvrField.CVP to NumericValidators.validateValue(cvpVal, cvpRule),
            SvrField.CO to NumericValidators.validateValue(coVal, coRule)
        )
    }

    val mapV = validation[SvrField.MAP]!!
    val cvpV = validation[SvrField.CVP]!!
    val coV = validation[SvrField.CO]!!

    val hasError = validation.values.any { it.severity == Severity.ERROR }
    val canCalculate = !hasError

    val mapMsg =
        if ((submitted || state.map.isNotBlank()) && mapV.severity != Severity.OK && mapV.messageResId != null)
            stringResource(mapV.messageResId)
        else null

    val cvpMsg =
        if ((submitted || state.cvp.isNotBlank()) && cvpV.severity != Severity.OK && cvpV.messageResId != null)
            stringResource(cvpV.messageResId)
        else null

    val coMsg =
        if ((submitted || state.co.isNotBlank()) && coV.severity != Severity.OK && coV.messageResId != null)
            stringResource(coV.messageResId)
        else null

    // Scroll a resultados/error
    LaunchedEffect(state.svrWu, state.svrDynes, state.error) {
        if (scrollToResultRequested && (state.svrWu != null || state.svrDynes != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    Scaffold(
        topBar = {
            GipogoCalcTopBar(
                title = stringResource(R.string.svr_screen_title),
                onBack = onBackToMenu,
                onInfo = { showInfoDialog = true },
                onReset = {
                    vm.clear()
                    submitted = false
                    didPrefillShared = false
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

            GipogoSectionHeaderRow(title = stringResource(R.string.svr_section_questions))

            GipogoSingleInputCard(
                label = stringResource(R.string.svr_field_map_title),
                value = state.map,
                placeholder = "0–200",
                unit = "mmHg",
                onValueChange = vm::setMAP,
                keyboardType = KeyboardType.Decimal,
                severity = mapV.severity
            )
            mapMsg?.let { GipogoFieldHint(severity = mapV.severity, text = it) }

            GipogoSingleInputCard(
                label = stringResource(R.string.svr_field_cvp_title),
                value = state.cvp,
                placeholder = "0–50",
                unit = "mmHg",
                onValueChange = vm::setCVP,
                keyboardType = KeyboardType.Decimal,
                severity = cvpV.severity
            )
            cvpMsg?.let { GipogoFieldHint(severity = cvpV.severity, text = it) }

            GipogoSingleInputCard(
                label = stringResource(R.string.svr_field_co_title),
                value = state.co,
                placeholder = "1.0–10.0",
                unit = "L/min",
                onValueChange = vm::setCO,
                keyboardType = KeyboardType.Decimal,
                severity = coV.severity
            )
            coMsg?.let { GipogoFieldHint(severity = coV.severity, text = it) }

            GipogoSectionHeaderRow(title = stringResource(R.string.svr_units_title))

            GipogoSurfaceCard {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                    submitted = true
                    if (!canCalculate) return@Button
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text(stringResource(R.string.common_btn_calculate)) }

            if (submitted && !canCalculate) {
                Text(
                    text = stringResource(R.string.svr_msg_review_required_fields),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val wu = state.svrWu
            val dyn = state.svrDynes

            if (wu != null && dyn != null && state.error == null) {
                val mainValue: String
                val mainUnit: String
                if (state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS) {
                    mainValue = Format.d(wu, 2)
                    mainUnit = stringResource(R.string.svr_units_wood)
                } else {
                    mainValue = Format.d(dyn, 0)
                    mainUnit = stringResource(R.string.svr_units_dynes)
                }

                val usingWu = state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS
                val gaugeValue = if (usingWu) wu else dyn
                val gaugeSpec = if (usingWu) SvrInterpretation.specWu else SvrInterpretation.specDynes

                GipogoResultsHeroCard(
                    eyebrow = stringResource(R.string.svr_result_title),
                    mainValue = mainValue,
                    mainUnit = mainUnit,

                    leftLabel = stringResource(R.string.svr_units_wood),
                    leftValue = Format.d(wu, 2),
                    leftUnit = "WU",

                    rightLabel = stringResource(R.string.svr_units_dynes),
                    rightValue = Format.d(dyn, 0),
                    rightUnit = "dyn·s·cm⁻⁵",

                    interpretationContent = {
                        InterpretationGaugeCardGeneric(
                            value = gaugeValue,
                            spec = gaugeSpec
                        )
                    }
                )
            }

            CalcNavigatorBar(onPrev = onPrevCalc, onNext = onNextCalc)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.svr_info_title)) },
            text = { Text(stringResource(R.string.svr_info_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }

    // Guardar en ReportStore
    LaunchedEffect(state.svrWu, state.svrDynes) {
        val wu = state.svrWu ?: return@LaunchedEffect
        val dyn = state.svrDynes ?: return@LaunchedEffect

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.SVR,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.svr_report_title),
                inputs = listOf(
                    LineItem(
                        key = SharedKeys.MAP_MMHG,
                        label = "MAP",
                        value = state.map,
                        unit = "mmHg",
                        detail = "Mean Arterial Pressure"
                    ),
                    LineItem(
                        key = SharedKeys.CVP_MMHG,
                        label = "CVP",
                        value = state.cvp,
                        unit = "mmHg",
                        detail = "Central Venous Pressure"
                    ),
                    LineItem(
                        key = SharedKeys.CO_LMIN,
                        label = "CO",
                        value = state.co,
                        unit = "L/min",
                        detail = "Cardiac Output"
                    )
                ),
                outputs = listOf(
                    LineItem(
                        key = SharedKeys.SVR_WOOD,
                        label = "SVR",
                        value = Format.d(wu, 2),
                        unit = "WU",
                        detail = "Wood Units"
                    ),
                    LineItem(
                        key = SharedKeys.SVR_DYN,
                        label = "SVR",
                        value = Format.d(dyn, 0),
                        unit = "dyn·s·cm⁻⁵",
                        detail = "CGS units"
                    ),
                    LineItem(
                        key = SharedKeys.SVR_UNITS,
                        label = "SVR units",
                        value = if (state.outputUnits == ResistancesViewModel.OutputUnits.WOOD_UNITS) "WOOD" else "DYN",
                        unit = null,
                        detail = null
                    )
                )

            )
        )
        WorkshopRhcAutosave.flushNow(context, coroutineScope)

    }
}
