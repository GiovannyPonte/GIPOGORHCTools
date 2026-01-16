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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.reset.AppResetBus
import com.gipogo.rhctools.ui.components.CalcNavigatorBar
import com.gipogo.rhctools.ui.components.GipogoCalcTopBar
import com.gipogo.rhctools.ui.components.GipogoFieldHint
import com.gipogo.rhctools.ui.components.GipogoResultsHeroCard
import com.gipogo.rhctools.ui.components.GipogoSectionHeaderRow
import com.gipogo.rhctools.ui.components.GipogoSingleInputCard
import com.gipogo.rhctools.ui.components.InterpretationGaugeCardGeneric
import com.gipogo.rhctools.ui.components.calcSwipeNavigation
import com.gipogo.rhctools.ui.interpretation.CpoInterpretation
import com.gipogo.rhctools.ui.validation.CpoField
import com.gipogo.rhctools.ui.validation.CpoValidation
import com.gipogo.rhctools.ui.validation.NumericParsing
import com.gipogo.rhctools.ui.validation.NumericValidators
import com.gipogo.rhctools.ui.validation.Severity
import com.gipogo.rhctools.ui.viewmodel.CpoViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CpoHelpTopic { MAP, CO, BSA, GLOBAL }

@Composable
fun CpoScreen(
    onBackToMenu: () -> Unit,
    onNextCalc: () -> Unit,
    onPrevCalc: () -> Unit,
    vm: CpoViewModel
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val entries by ReportStore.entries.collectAsState()
    val resetTick by AppResetBus.tick.collectAsState()

    var submitted by rememberSaveable { mutableStateOf(false) }
    var scrollToResultRequested by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<CpoHelpTopic?>(null) }

    // ✅ Prefill shared (solo una vez, no pisa usuario)
    var didPrefillShared by rememberSaveable { mutableStateOf(false) }

    // ✅ Reset local único y definitivo (borra resultados)
    fun resetLocal() {
        vm.clear()
        submitted = false
        didPrefillShared = false
        coroutineScope.launch { scrollState.animateScrollTo(0) }
    }

    // ✅ Reset global via AppResetBus (sin reset al entrar)
    var lastResetToken by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(resetTick) {
        val token = resetTick.toString()

        if (lastResetToken == null) {
            lastResetToken = token
            return@LaunchedEffect
        }

        if (lastResetToken != token) {
            lastResetToken = token
            resetLocal()
        }
    }

    // ✅ PREFILL por KEYS: MAP (mmHg), CO (L/min), BSA (m²)
    LaunchedEffect(
        entries,
        state.map, state.co, state.bsa,
        state.mapUnit, state.coUnit,
        didPrefillShared
    ) {
        if (didPrefillShared) return@LaunchedEffect

        val needMap = state.map.isBlank()
        val needCo = state.co.isBlank()
        val needBsa = state.bsa.isBlank()

        if (!needMap && !needCo && !needBsa) {
            didPrefillShared = true
            return@LaunchedEffect
        }

        // MAP canónico guardado en mmHg
        if (needMap) {
            val mapMmHg = ReportStore.latestValueDoubleByKey(SharedKeys.MAP_MMHG)
            if (mapMmHg != null) {
                val ok = NumericValidators.validateValue(mapMmHg, CpoValidation.mapRule).severity != Severity.ERROR
                if (ok) {
                    val mapForUi = when (state.mapUnit) {
                        CpoViewModel.MapUnit.MMHG -> mapMmHg
                        CpoViewModel.MapUnit.KPA -> mapMmHg * 0.133322
                    }
                    vm.setMAP(
                        if (state.mapUnit == CpoViewModel.MapUnit.MMHG) Format.d(mapForUi, 0)
                        else Format.d(mapForUi, 1)
                    )
                }
            }
        }

        // CO canónico guardado en L/min
        if (needCo) {
            val coLMin = ReportStore.latestValueDoubleByKey(SharedKeys.CO_LMIN)
            if (coLMin != null) {
                val ok = NumericValidators.validateValue(coLMin, CpoValidation.coRule).severity != Severity.ERROR
                if (ok) {
                    val coForUi = when (state.coUnit) {
                        CpoViewModel.CoUnit.L_MIN -> coLMin
                        CpoViewModel.CoUnit.L_SEC -> coLMin / 60.0
                    }
                    vm.setCO(Format.d(coForUi, 2))
                }
            }
        }

        // BSA canónica guardada en m²
        if (needBsa) {
            val bsa = ReportStore.latestValueDoubleByKey(SharedKeys.BSA_M2)
            if (bsa != null) {
                val ok = NumericValidators.validateValue(bsa, CpoValidation.bsaRule).severity != Severity.ERROR
                if (ok) vm.setBSA(Format.d(bsa, 2))
            }
        }

        didPrefillShared = true
    }

    val mapRaw = NumericParsing.parseDouble(state.map)
    val coRaw = NumericParsing.parseDouble(state.co)
    val bsaRaw = NumericParsing.parseDouble(state.bsa)

    val mapMmHg = when (state.mapUnit) {
        CpoViewModel.MapUnit.MMHG -> mapRaw
        CpoViewModel.MapUnit.KPA -> mapRaw?.let { it * 7.50062 }
    }
    val coLMin = when (state.coUnit) {
        CpoViewModel.CoUnit.L_MIN -> coRaw
        CpoViewModel.CoUnit.L_SEC -> coRaw?.let { it * 60.0 }
    }

    val mapRule = CpoValidation.mapRule.copy(required = submitted)
    val coRule = CpoValidation.coRule.copy(required = submitted)
    val bsaRule = CpoValidation.bsaRule.copy(required = false)

    val validation = remember(mapMmHg, coLMin, bsaRaw, submitted) {
        mapOf(
            CpoField.MAP to NumericValidators.validateValue(mapMmHg, mapRule),
            CpoField.CO to NumericValidators.validateValue(coLMin, coRule),
            CpoField.BSA to NumericValidators.validateValue(bsaRaw, bsaRule)
        )
    }

    val mapV = validation[CpoField.MAP]!!
    val coV = validation[CpoField.CO]!!
    val bsaV = validation[CpoField.BSA]!!

    val hasError = validation.values.any { it.severity == Severity.ERROR }
    val canCalculate = !hasError

    val mapMsg =
        if ((submitted || state.map.isNotBlank()) && mapV.severity != Severity.OK && mapV.messageResId != null)
            stringResource(mapV.messageResId)
        else null

    val coMsg =
        if ((submitted || state.co.isNotBlank()) && coV.severity != Severity.OK && coV.messageResId != null)
            stringResource(coV.messageResId)
        else null

    val bsaMsg =
        if (state.bsa.isNotBlank() && bsaV.severity != Severity.OK && bsaV.messageResId != null)
            stringResource(bsaV.messageResId)
        else null

    LaunchedEffect(state.result, state.error) {
        if (scrollToResultRequested && (state.result != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoCalcTopBar(
                title = stringResource(R.string.cpo_screen_title),
                onBack = onBackToMenu,
                onInfo = { helpTopic = CpoHelpTopic.GLOBAL },
                onReset = { resetLocal() }
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
            GipogoSectionHeaderRow(title = stringResource(R.string.cpo_section_variables))

            GipogoSingleInputCard(
                label = stringResource(R.string.cpo_field_map_title),
                value = state.map,
                placeholder = if (state.mapUnit == CpoViewModel.MapUnit.MMHG) "50–140" else "6.7–18.7",
                unit = if (state.mapUnit == CpoViewModel.MapUnit.MMHG) "mmHg" else "kPa",
                onValueChange = vm::setMAP,
                keyboardType = KeyboardType.Decimal,
                onUnitClick = vm::toggleMapUnit,
                onHelpClick = { helpTopic = CpoHelpTopic.MAP },
                severity = mapV.severity
            )
            mapMsg?.let { GipogoFieldHint(severity = mapV.severity, text = it) }

            GipogoSingleInputCard(
                label = stringResource(R.string.cpo_field_co_title),
                value = state.co,
                placeholder = if (state.coUnit == CpoViewModel.CoUnit.L_MIN) "2.0–12.0" else "0.03–0.20",
                unit = if (state.coUnit == CpoViewModel.CoUnit.L_MIN) "L/min" else "L/s",
                onValueChange = vm::setCO,
                keyboardType = KeyboardType.Decimal,
                onUnitClick = vm::toggleCoUnit,
                onHelpClick = { helpTopic = CpoHelpTopic.CO },
                severity = coV.severity
            )
            coMsg?.let { GipogoFieldHint(severity = coV.severity, text = it) }

            GipogoSectionHeaderRow(title = stringResource(R.string.cpo_section_optional))

            GipogoSingleInputCard(
                label = stringResource(R.string.cpo_field_bsa_title),
                value = state.bsa,
                placeholder = "1.2–2.8",
                unit = stringResource(R.string.common_unit_m2),
                onValueChange = vm::setBSA,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = CpoHelpTopic.BSA },
                severity = bsaV.severity
            )
            bsaMsg?.let { GipogoFieldHint(severity = bsaV.severity, text = it) }

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
                    text = stringResource(R.string.cpo_error_review_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.result?.let { r ->
                GipogoResultsHeroCard(
                    eyebrow = stringResource(R.string.cpo_result_eyebrow),
                    mainValue = Format.d(r.cpoWatts, 2),
                    mainUnit = stringResource(R.string.common_unit_w),
                    leftLabel = stringResource(R.string.cpo_metric_cpi),
                    leftValue = r.cpiWattsPerM2?.let { Format.d(it, 2) } ?: stringResource(R.string.common_value_na),
                    leftUnit = stringResource(R.string.common_unit_w_m2),
                    rightLabel = stringResource(R.string.common_label_bsa),
                    rightValue = bsaRaw?.let { Format.d(it, 2) } ?: stringResource(R.string.common_value_na),
                    rightUnit = stringResource(R.string.common_unit_m2),
                    interpretationContent = {
                        InterpretationGaugeCardGeneric(
                            value = r.cpoWatts,
                            spec = CpoInterpretation.spec
                        )
                    }
                )
            }

            CalcNavigatorBar(onPrev = onPrevCalc, onNext = onNextCalc)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ✅ Persistir CPO: publica MAP/CO/BSA con KEYS para que SVR/PVR/CPO se autollenan
    LaunchedEffect(state.result) {
        val r = state.result ?: return@LaunchedEffect

        val mapRaw2 = NumericParsing.parseDouble(state.map) ?: return@LaunchedEffect
        val coRaw2 = NumericParsing.parseDouble(state.co) ?: return@LaunchedEffect
        val bsaRaw2 = NumericParsing.parseDouble(state.bsa)

        val mapMmHg2 = if (state.mapUnit == CpoViewModel.MapUnit.MMHG) mapRaw2 else mapRaw2 * 7.50062
        val coLMin2 = if (state.coUnit == CpoViewModel.CoUnit.L_MIN) coRaw2 else coRaw2 * 60.0

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.CPO,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.cpo_screen_title),
                inputs = listOf(
                    LineItem(key = SharedKeys.MAP_MMHG, label = "MAP", value = Format.d(mapMmHg2, 0), unit = "mmHg", detail = "Mean Arterial Pressure"),
                    LineItem(key = SharedKeys.CO_LMIN, label = "CO", value = Format.d(coLMin2, 2), unit = "L/min", detail = "Cardiac Output"),
                    LineItem(key = SharedKeys.BSA_M2, label = "BSA", value = bsaRaw2?.let { Format.d(it, 2) } ?: "", unit = "m²", detail = "Body Surface Area")
                ),
                outputs = listOf(
                    LineItem(label = "CPO", value = Format.d(r.cpoWatts, 2), unit = "W", detail = "Cardiac Power Output"),
                    LineItem(label = "CPI", value = r.cpiWattsPerM2?.let { Format.d(it, 2) } ?: "", unit = "W/m²", detail = "Cardiac Power Index")
                )
            )
        )
    }

    helpTopic?.let { topic ->
        when (topic) {
            CpoHelpTopic.GLOBAL -> {
                AlertDialog(
                    onDismissRequest = { helpTopic = null },
                    title = { Text(stringResource(R.string.cpo_intro_title)) },
                    text = { Text(stringResource(R.string.cpo_intro_body)) },
                    confirmButton = {
                        TextButton(onClick = { helpTopic = null }) {
                            Text(stringResource(R.string.home_dialog_close))
                        }
                    }
                )
            }
            CpoHelpTopic.MAP -> {
                AlertDialog(
                    onDismissRequest = { helpTopic = null },
                    title = { Text(stringResource(R.string.cpo_field_map_title)) },
                    text = { Text(stringResource(R.string.cpo_field_map_help)) },
                    confirmButton = {
                        TextButton(onClick = { helpTopic = null }) {
                            Text(stringResource(R.string.home_dialog_close))
                        }
                    }
                )
            }
            CpoHelpTopic.CO -> {
                AlertDialog(
                    onDismissRequest = { helpTopic = null },
                    title = { Text(stringResource(R.string.cpo_field_co_title)) },
                    text = { Text(stringResource(R.string.cpo_field_co_help)) },
                    confirmButton = {
                        TextButton(onClick = { helpTopic = null }) {
                            Text(stringResource(R.string.home_dialog_close))
                        }
                    }
                )
            }
            CpoHelpTopic.BSA -> {
                AlertDialog(
                    onDismissRequest = { helpTopic = null },
                    title = { Text(stringResource(R.string.cpo_field_bsa_title)) },
                    text = { Text(stringResource(R.string.cpo_field_bsa_help)) },
                    confirmButton = {
                        TextButton(onClick = { helpTopic = null }) {
                            Text(stringResource(R.string.home_dialog_close))
                        }
                    }
                )
            }
        }
    }
}
