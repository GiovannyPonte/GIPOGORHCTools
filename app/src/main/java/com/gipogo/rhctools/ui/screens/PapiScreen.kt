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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.gipogo.rhctools.ui.components.InterpretationGaugeCardGeneric
import com.gipogo.rhctools.ui.components.calcSwipeNavigation
import com.gipogo.rhctools.ui.interpretation.PapiInterpretation
import com.gipogo.rhctools.ui.validation.NumericParsing
import com.gipogo.rhctools.ui.validation.NumericValidators
import com.gipogo.rhctools.ui.validation.PapiField
import com.gipogo.rhctools.ui.validation.PapiValidation
import com.gipogo.rhctools.ui.validation.Severity
import com.gipogo.rhctools.ui.viewmodel.PapiViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PapiHelpTopic { PASP, PADP, RAP, GLOBAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapiScreen(
    onBackToMenu: () -> Unit,
    onNextCalc: () -> Unit,
    onPrevCalc: () -> Unit,
    vm: PapiViewModel
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // ✅ IMPORTANTE: hacerlo reactivo para que al venir de SVR aparezca el botón
    val entries by ReportStore.entries.collectAsState()

    // CVP disponible (mmHg) si SVR lo guardó con SharedKeys.CVP_MMHG
    val cvpFromStore = remember(entries) {
        ReportStore.latestValueDoubleByKey(SharedKeys.CVP_MMHG)
    }

    var submitted by rememberSaveable { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var helpTopic by remember { mutableStateOf<PapiHelpTopic?>(null) }
    var scrollToResultRequested by remember { mutableStateOf(false) }

    val paspVal = NumericParsing.parseDouble(state.pasp)
    val padpVal = NumericParsing.parseDouble(state.padp)
    val rapVal = NumericParsing.parseDouble(state.rap)

    val paspRule = PapiValidation.paspRule.copy(required = submitted)
    val padpRule = PapiValidation.padpRule.copy(required = submitted)
    val rapRule = PapiValidation.rapRule.copy(required = submitted)

    val validation = remember(paspVal, padpVal, rapVal, submitted) {
        mapOf(
            PapiField.PASP to NumericValidators.validateValue(paspVal, paspRule),
            PapiField.PADP to NumericValidators.validateValue(padpVal, padpRule),
            PapiField.RAP to NumericValidators.validateValue(rapVal, rapRule)
        )
    }

    val paspV = validation[PapiField.PASP]!!
    val padpV = validation[PapiField.PADP]!!
    val rapV = validation[PapiField.RAP]!!

    val hasFieldError = validation.values.any { it.severity == Severity.ERROR }
    val canCalculate = !hasFieldError

    LaunchedEffect(state.papi, state.error) {
        if (scrollToResultRequested && (state.papi != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GipogoCalcTopBar(
                title = stringResource(R.string.papi_screen_title),
                onBack = onBackToMenu,
                onInfo = { showInfoDialog = true },
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
            GipogoSectionHeaderRow(title = stringResource(R.string.papi_section_variables))

            GipogoSingleInputCard(
                label = stringResource(R.string.papi_field_pasp_title),
                value = state.pasp,
                placeholder = stringResource(R.string.papi_placeholder_pasp),
                unit = stringResource(R.string.common_unit_mmhg),
                onValueChange = vm::setPASP,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = PapiHelpTopic.PASP },
                severity = paspV.severity
            )

            if ((submitted || state.pasp.isNotBlank()) && paspV.severity != Severity.OK && paspV.messageResId != null) {
                GipogoFieldHint(severity = paspV.severity, text = stringResource(paspV.messageResId))
            }

            GipogoSingleInputCard(
                label = stringResource(R.string.papi_field_padp_title),
                value = state.padp,
                placeholder = stringResource(R.string.papi_placeholder_padp),
                unit = stringResource(R.string.common_unit_mmhg),
                onValueChange = vm::setPADP,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = PapiHelpTopic.PADP },
                severity = padpV.severity
            )

            if ((submitted || state.padp.isNotBlank()) && padpV.severity != Severity.OK && padpV.messageResId != null) {
                GipogoFieldHint(severity = padpV.severity, text = stringResource(padpV.messageResId))
            }

            GipogoSingleInputCard(
                label = stringResource(R.string.papi_field_rap_title),
                value = state.rap,
                placeholder = stringResource(R.string.papi_placeholder_rap),
                unit = stringResource(R.string.common_unit_mmhg),
                onValueChange = vm::setRAP,
                keyboardType = KeyboardType.Decimal,
                onHelpClick = { helpTopic = PapiHelpTopic.RAP },
                severity = rapV.severity
            )

            if ((submitted || state.rap.isNotBlank()) && rapV.severity != Severity.OK && rapV.messageResId != null) {
                GipogoFieldHint(severity = rapV.severity, text = stringResource(rapV.messageResId))
            }

            // ✅ NUEVO: botón explícito para usar CVP como RAP (NO automático)
            if (state.rap.isBlank() && cvpFromStore != null) {
                Button(
                    onClick = {
                        val rap = HemodynamicsFormulas.rapFromCvp(cvpFromStore)
                        vm.setRAP(Format.d(rap, 0))
                    }
                ) {
                    Text("Usar CVP (${Format.d(cvpFromStore, 0)} mmHg) como RAP")
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

            state.papi?.let { papi ->
                GipogoResultsHeroCard(
                    eyebrow = stringResource(R.string.common_result),
                    mainValue = Format.d(papi, 2),
                    mainUnit = stringResource(R.string.common_unit_none),

                    leftLabel = stringResource(R.string.papi_hero_left_label),
                    leftValue = state.papp?.let { Format.d(it, 1) } ?: stringResource(R.string.common_value_na),
                    leftUnit = stringResource(R.string.common_unit_mmhg),

                    rightLabel = stringResource(R.string.papi_hero_right_label),
                    rightValue = state.rap.ifBlank { stringResource(R.string.common_value_na) },
                    rightUnit = stringResource(R.string.common_unit_mmhg),

                    interpretationContent = {
                        InterpretationGaugeCardGeneric(
                            value = papi,
                            spec = PapiInterpretation.spec
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
            title = { Text(stringResource(R.string.papi_info_title)) },
            text = { Text(stringResource(R.string.papi_info_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.home_dialog_close))
                }
            }
        )
    }

    helpTopic?.let { topic ->
        val (titleRes, bodyRes) = when (topic) {
            PapiHelpTopic.PASP -> R.string.papi_help_pasp_title to R.string.papi_help_pasp_body
            PapiHelpTopic.PADP -> R.string.papi_help_padp_title to R.string.papi_help_padp_body
            PapiHelpTopic.RAP -> R.string.papi_help_rap_title to R.string.papi_help_rap_body
            PapiHelpTopic.GLOBAL -> R.string.papi_info_title to R.string.papi_info_body
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

    // ✅ Persistir en ReportStore — CAMBIO MÍNIMO: añadir keys a inputs (ya estaba)
    LaunchedEffect(state.papi, state.papp) {
        val papi = state.papi ?: return@LaunchedEffect

        val outputs = mutableListOf<LineItem>()
        outputs += LineItem(
            label = "PAPi",
            value = Format.d(papi, 2),
            unit = "",
            detail = "Pulmonary Artery Pulsatility Index"
        )
        state.papp?.let { papp ->
            outputs += LineItem(
                label = "PAPP",
                value = Format.d(papp, 1),
                unit = "mmHg",
                detail = "Pulmonary artery pulse pressure"
            )
        }

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PAPI,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.papi_report_title),
                inputs = listOf(
                    LineItem(
                        key = SharedKeys.PASP_MMHG,
                        label = "PASP",
                        value = state.pasp,
                        unit = "mmHg",
                        detail = "PA systolic pressure"
                    ),
                    LineItem(
                        key = SharedKeys.PADP_MMHG,
                        label = "PADP",
                        value = state.padp,
                        unit = "mmHg",
                        detail = "PA diastolic pressure"
                    ),
                    LineItem(
                        key = SharedKeys.RAP_MMHG,
                        label = "RAP",
                        value = state.rap,
                        unit = "mmHg",
                        detail = "Right atrial pressure"
                    )
                ),
                outputs = outputs
            )
        )
    }
}
