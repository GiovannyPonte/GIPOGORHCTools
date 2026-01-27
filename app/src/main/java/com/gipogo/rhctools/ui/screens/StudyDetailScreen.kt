package com.gipogo.rhctools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.studies.StudiesRepository
import com.gipogo.rhctools.ui.components.ForresterStudyCard
import com.gipogo.rhctools.ui.viewmodel.StudyDetailUiState
import com.gipogo.rhctools.ui.viewmodel.StudyDetailViewModel
import java.text.DateFormat
import java.util.Date
import com.gipogo.rhctools.ui.components.ForresterStudyCard



private val ScreenPad = 16.dp
private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyDetailRoute(
    patientId: String,
    patientName: String?, // nombre real para el subtitle (sin mostrar códigos)
    studyId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onExportStudyPdf: (patientId: String, studyId: String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember(context.applicationContext) { StudiesRepository.get(context.applicationContext) }

    val vm: StudyDetailViewModel = viewModel(
        key = "$patientId:$studyId",
        factory = StudyDetailViewModel.Factory(
            patientId = patientId,
            studyId = studyId,
            repo = repo
        )
    )

    StudyDetailScreen(
        patientId = patientId,
        patientName = patientName,
        studyId = studyId,
        uiState = vm.uiState,
        modifier = modifier,
        onBack = onBack,
        onExportStudyPdf = onExportStudyPdf
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyDetailScreen(
    patientId: String,
    patientName: String?,
    studyId: String,
    uiState: kotlinx.coroutines.flow.StateFlow<StudyDetailUiState>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onExportStudyPdf: (patientId: String, studyId: String) -> Unit,
) {
    val state by uiState.collectAsState()

    val subtitleName = patientName?.trim().takeIf { !it.isNullOrBlank() } ?: patientId
    val sw: StudyWithRhcData? = (state as? StudyDetailUiState.Content)?.studyWithRhc

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.study_detail_title_rhc),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.study_detail_subtitle_patient, subtitleName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { /* menú futuro */ }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = contentColorFor(MaterialTheme.colorScheme.background)
                )
            )
        }
    ) { padding ->

        val startedAt = sw?.study?.startedAtMillis
        val startedAtText = startedAt?.let { formatDateTime(it) }
            ?: stringResource(R.string.common_value_na)

        val inputs = buildInputsFromDb(sw?.rhc)
        val outputs = buildOutputsFromDb(sw?.rhc)

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ScreenPad,
                end = ScreenPad,
                top = 12.dp,
                bottom = 22.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StudyMetaCard(
                    dateTimeText = startedAtText,
                    studyId = studyId,
                    onCopyStudyId = {
                        copyToClipboard(
                            context = context,
                            label = context.getString(R.string.patient_action_copy_id),
                            value = studyId
                        )
                    }
                )
            }

            item {
                SectionTitle(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.study_detail_section_inputs),
                    subtitle = stringResource(R.string.study_detail_section_inputs_subtitle)
                )
            }

            item { InputsCard(inputs = inputs) }

            item {
                SectionTitle(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.study_detail_section_outputs),
                    subtitle = stringResource(R.string.study_detail_section_outputs_subtitle)
                )
            }

            item { OutputsCard(outputs = outputs) }

            item { ClinicalNoteCard() }

            // ✅ NUEVO: Tarjeta Forrester (valores persistidos del estudio)
            item {
                ForresterStudyCard(
                    rhc = sw?.rhc,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ActionsCard(
                    onExportStudyPdf = { onExportStudyPdf(patientId, studyId) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

/* ------------------------- Header / Meta ------------------------- */

@Composable
private fun StudyMetaCard(
    dateTimeText: String,
    studyId: String,
    onCopyStudyId: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateTimeText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = stringResource(R.string.study_detail_study_id, studyId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(onClick = onCopyStudyId) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                }
            }
        }
    }
}

/* ------------------------- Section Titles ------------------------- */

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------- Inputs / Outputs Cards ------------------------- */

@Composable
private fun InputsCard(inputs: List<RowUi>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            inputs.forEachIndexed { idx, row ->
                InputOutputRow(
                    label = stringResource(row.labelRes),
                    valueText = row.valueText,
                    unitText = row.unitRes?.let { stringResource(it) }.orEmpty()
                )
                if (idx != inputs.lastIndex) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                }
            }
        }
    }
}

@Composable
private fun OutputsCard(outputs: List<RowUi>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            outputs.forEachIndexed { idx, row ->
                InputOutputRow(
                    label = stringResource(row.labelRes),
                    valueText = row.valueText,
                    unitText = row.unitRes?.let { stringResource(it) }.orEmpty()
                )
                if (idx != outputs.lastIndex) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                }
            }
        }
    }
}

@Composable
private fun InputOutputRow(
    label: String,
    valueText: String,
    unitText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unitText.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ------------------------- Clinical Note ------------------------- */

@Composable
private fun ClinicalNoteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.study_detail_section_note),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                text = stringResource(R.string.study_detail_note_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.9f)
            )
        }
    }
}

/* ------------------------- Actions ------------------------- */

@Composable
private fun ActionsCard(
    onExportStudyPdf: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.study_detail_section_actions),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Button(
                onClick = onExportStudyPdf,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.study_action_export_pdf))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.study_action_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ------------------------- Mapping DB -> UI rows ------------------------- */

private data class RowUi(
    @StringRes val labelRes: Int,
    val valueText: String,
    @StringRes val unitRes: Int?
)

@Composable
private fun buildInputsFromDb(rhc: RhcStudyDataEntity?): List<RowUi> {
    val na = stringResource(R.string.common_value_na)

    fun v0(x: Double?): String = x?.let { formatNumber(it, 0) } ?: na
    fun v1(x: Double?): String = x?.let { formatNumber(it, 1) } ?: na

    return listOf(
        RowUi(R.string.study_input_ra, v0(rhc?.rapMmHg), R.string.common_unit_mmhg),
        RowUi(R.string.pvr_help_mpap_title, v0(rhc?.mpapMmHg), R.string.common_unit_mmhg),
        RowUi(R.string.study_input_pcwp, v0(rhc?.pawpMmHg), R.string.common_unit_mmhg),

        RowUi(R.string.study_input_co, v1(rhc?.cardiacOutputLMin), R.string.common_unit_lmin),
        RowUi(R.string.study_input_ci, v1(rhc?.cardiacIndexLMinM2), R.string.common_unit_lmin_m2),

        RowUi(R.string.study_input_hb, v1(rhc?.hemoglobinGdl), R.string.unit_g_dl),
        RowUi(R.string.study_input_sao2, v0(rhc?.saO2Percent), R.string.unit_percent),
        RowUi(R.string.study_input_svo2, v0(rhc?.svO2Percent), R.string.unit_percent),

        RowUi(R.string.study_input_weight, v1(rhc?.weightKg), R.string.unit_kg),
        RowUi(R.string.study_input_height, v0(rhc?.heightCm), R.string.unit_cm),
    )
}

@Composable
private fun buildOutputsFromDb(rhc: RhcStudyDataEntity?): List<RowUi> {
    val na = stringResource(R.string.common_value_na)

    fun v1(x: Double?): String = x?.let { formatNumber(it, 1) } ?: na
    fun v2(x: Double?): String = x?.let { formatNumber(it, 2) } ?: na
    fun v0(x: Double?): String = x?.let { formatNumber(it, 0) } ?: na

    val rows = mutableListOf<RowUi>()

    rows += RowUi(R.string.study_out_svr, v1(rhc?.svrWood), R.string.common_unit_wu_short)
    rows += RowUi(R.string.study_out_svr, v0(rhc?.svrDyn), R.string.common_unit_dynes)

    rows += RowUi(R.string.study_out_pvr, v1(rhc?.pvrWood), R.string.common_unit_wu_short)
    rows += RowUi(R.string.study_out_pvr, v0(rhc?.pvrDyn), R.string.common_unit_dynes)

    rows += RowUi(R.string.study_out_cpo, v2(rhc?.cardiacPowerW), R.string.common_unit_w)
    rows += RowUi(R.string.study_out_papi, v2(rhc?.papi), R.string.common_unit_none)

    return rows.distinctBy { Triple(it.labelRes, it.valueText, it.unitRes) }
}

/* ------------------------- Helpers ------------------------- */

private fun copyToClipboard(context: Context, label: String, value: String): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        true
    }.getOrDefault(false)
}

private fun formatDateTime(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}

private fun formatNumber(value: Double, decimals: Int): String {
    val nf = java.text.NumberFormat.getNumberInstance()
    nf.maximumFractionDigits = decimals
    nf.minimumFractionDigits = 0
    return nf.format(value)
}

@Preview(name = "StudyDetailScreen", showBackground = true)
@Composable
private fun Preview_StudyDetailScreen() {
    MaterialTheme {
        StudyDetailRoute(
            patientId = "PT-0001",
            patientName = "Ramon Ruiz",
            studyId = "S-0001",
            onBack = {},
            onExportStudyPdf = { _, _ -> }
        )
    }
}
