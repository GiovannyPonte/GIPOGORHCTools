package com.gipogo.rhctools.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.gipogo.rhctools.R

private enum class StudyMethod(@StringRes val labelRes: Int) {
    Fick(R.string.study_method_fick),
    TD(R.string.study_method_td)
}

private enum class StudyContext(@StringRes val labelRes: Int) {
    Baseline(R.string.study_context_baseline),
    FollowUp(R.string.study_context_followup),
    PostOp(R.string.study_context_postop),
    ShockReassess(R.string.study_context_shock_reassess)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyEditScreen(
    patientId: String,
    modifier: Modifier = Modifier,
    isEdit: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    // UI-only state
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var method by remember { mutableStateOf(StudyMethod.Fick) }
    var context by remember { mutableStateOf(StudyContext.Baseline) }

    var rap by remember { mutableStateOf("") }
    var mpap by remember { mutableStateOf("") }
    var pcwp by remember { mutableStateOf("") }
    var co by remember { mutableStateOf("") }
    var hb by remember { mutableStateOf("") }
    var sao2 by remember { mutableStateOf("") }
    var svo2 by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(if (isEdit) R.string.study_edit_title_edit else R.string.study_edit_title_create),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.study_edit_subtitle_patient, patientId),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.study_edit_field_title)) },
                placeholder = { Text(stringResource(R.string.study_edit_field_title_hint)) },
                singleLine = true
            )

            // Method
            SectionHeader(textRes = R.string.study_edit_section_method)
            SegmentedEnumRow(
                items = StudyMethod.entries,
                selected = method,
                labelRes = { it.labelRes },
                onSelected = { method = it }
            )

            // Context
            SectionHeader(textRes = R.string.study_edit_section_context)
            SegmentedEnumRow(
                items = StudyContext.entries,
                selected = context,
                labelRes = { it.labelRes },
                onSelected = { context = it }
            )

            // Inputs
            SectionHeader(textRes = R.string.study_edit_section_inputs)

            UnitTextField(
                value = rap,
                onValueChange = { rap = it },
                labelRes = R.string.study_input_rap,
                unitRes = R.string.unit_mmhg
            )

            UnitTextField(
                value = mpap,
                onValueChange = { mpap = it },
                labelRes = R.string.study_input_mpap,
                unitRes = R.string.unit_mmhg
            )

            UnitTextField(
                value = pcwp,
                onValueChange = { pcwp = it },
                labelRes = R.string.study_input_pcwp,
                unitRes = R.string.unit_mmhg
            )

            UnitTextField(
                value = co,
                onValueChange = { co = it },
                labelRes = R.string.study_input_co,
                unitRes = R.string.unit_l_min
            )

            UnitTextField(
                value = hb,
                onValueChange = { hb = it },
                labelRes = R.string.study_input_hb,
                unitRes = R.string.unit_g_dl
            )

            UnitTextField(
                value = sao2,
                onValueChange = { sao2 = it },
                labelRes = R.string.study_input_sao2,
                unitRes = R.string.unit_percent
            )

            UnitTextField(
                value = svo2,
                onValueChange = { svo2 = it },
                labelRes = R.string.study_input_svo2,
                unitRes = R.string.unit_percent
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                label = { Text(stringResource(R.string.study_edit_field_notes)) },
                placeholder = { Text(stringResource(R.string.study_edit_field_notes_hint)) }
            )

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_save))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun <T> SegmentedEnumRow(
    items: List<T>,
    selected: T,
    @StringRes labelRes: (T) -> Int,
    onSelected: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                selected = (item == selected),
                onClick = { onSelected(item) },
                shape = SegmentedButtonDefaults.itemShape(index, items.size)
            ) {
                Text(stringResource(labelRes(item)))
            }
        }
    }
}

@Composable
private fun UnitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    @StringRes unitRes: Int
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        trailingIcon = {
            Text(
                text = stringResource(unitRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true
    )
}
