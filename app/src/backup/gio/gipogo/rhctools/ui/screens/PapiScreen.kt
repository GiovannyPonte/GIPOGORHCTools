package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.gipogo.rhctools.ui.components.SectionCard
import com.gipogo.rhctools.ui.viewmodel.PapiViewModel
import com.gipogo.rhctools.util.Format
import kotlinx.coroutines.delay

@Composable
fun PapiScreen(
    onBackToMenu: () -> Unit,
    vm: PapiViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val resetTick by com.gipogo.rhctools.reset.AppResetBus.tick.collectAsState()
    LaunchedEffect(resetTick) { vm.clear() }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var scrollToResultRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state.papi, state.error) {
        if (scrollToResultRequested && (state.papi != null || state.error != null)) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
            scrollToResultRequested = false
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.papi_screen_title),
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
                Text(stringResource(R.string.papi_intro_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.papi_intro_body), style = MaterialTheme.typography.bodyMedium)
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.papi_inputs_title), style = MaterialTheme.typography.titleSmall)

                    Field(
                        title = stringResource(R.string.papi_field_pasp_title),
                        shortLabel = "PASP",
                        value = state.pasp,
                        onValueChange = vm::setPASP,
                        unitText = "mmHg",
                        help = stringResource(R.string.papi_help_pasp)
                    )

                    Field(
                        title = stringResource(R.string.papi_field_padp_title),
                        shortLabel = "PADP",
                        value = state.padp,
                        onValueChange = vm::setPADP,
                        unitText = "mmHg",
                        help = stringResource(R.string.papi_help_padp)
                    )

                    Field(
                        title = stringResource(R.string.papi_field_rap_title),
                        shortLabel = "RAP",
                        value = state.rap,
                        onValueChange = vm::setRAP,
                        unitText = "mmHg",
                        help = stringResource(R.string.papi_help_rap)
                    )
                }
            }

            Button(
                onClick = {
                    scrollToResultRequested = true
                    vm.calculate()
                }
            ) { Text(stringResource(R.string.common_btn_calculate)) }

            state.error?.let { err ->
                val msg = when (err) {
                    PapiViewModel.ErrorCode.MISSING_INPUTS -> stringResource(R.string.papi_error_missing)
                    PapiViewModel.ErrorCode.RAP_NONPOSITIVE -> stringResource(R.string.papi_error_rap_nonpositive)
                    PapiViewModel.ErrorCode.PASP_LT_PADP -> stringResource(R.string.papi_error_pasp_lt_padp)
                }
                ResultCard(title = stringResource(R.string.common_error), body = msg)
            }

            state.papi?.let { papi ->
                val noteText = when (state.note) {
                    PapiViewModel.NoteCode.HIGH_RISK -> stringResource(R.string.papi_note_high_risk)
                    PapiViewModel.NoteCode.LOWER_RISK -> stringResource(R.string.papi_note_lower_risk)
                    null -> null
                }

                val body = buildString {
                    appendLine(stringResource(R.string.papi_result_line, Format.d(papi, 2)))
                    noteText?.let { appendLine(); appendLine(stringResource(R.string.papi_note_label, it)) }

                    appendLine()
                    appendLine(stringResource(R.string.papi_siglas_title))
                    appendLine(stringResource(R.string.papi_sigla_papi))
                    appendLine(stringResource(R.string.papi_sigla_pasp))
                    appendLine(stringResource(R.string.papi_sigla_padp))
                    appendLine(stringResource(R.string.papi_sigla_rap))
                    appendLine(stringResource(R.string.papi_sigla_pvc))
                    appendLine(stringResource(R.string.papi_sigla_pa))
                }

                ResultCard(title = stringResource(R.string.papi_result_title), body = body)
            }
        }
    }

    // Reporte PDF: en LaunchedEffect NO se usa stringResource; se usa context.getString
    LaunchedEffect(state.papi, state.note) {
        val papi = state.papi ?: return@LaunchedEffect

        val noteStr = when (state.note) {
            PapiViewModel.NoteCode.HIGH_RISK -> context.getString(R.string.papi_note_high_risk)
            PapiViewModel.NoteCode.LOWER_RISK -> context.getString(R.string.papi_note_lower_risk)
            null -> null
        }

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PAPI,
                timestampMillis = System.currentTimeMillis(),
                title = context.getString(R.string.papi_report_title),
                inputs = listOf(
                    LineItem("PASP", state.pasp, "mmHg", "PA systolic pressure"),
                    LineItem("PADP", state.padp, "mmHg", "PA diastolic pressure"),
                    LineItem("RAP", state.rap, "mmHg", "Right atrial pressure")
                ),
                outputs = listOf(
                    LineItem("PAPi", Format.d(papi, 2), null, "Pulsatility Index")
                ),
                notes = listOfNotNull(noteStr)
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
    unitText: String,
    help: String
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

        Text(help, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.common_unit_label, unitText), style = MaterialTheme.typography.bodySmall)
    }
}
