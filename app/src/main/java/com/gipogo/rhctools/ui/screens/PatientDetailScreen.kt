package com.gipogo.rhctools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.dao.TagDao
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import androidx.compose.runtime.collectAsState
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.ui.viewmodel.InlineMetricUi
import com.gipogo.rhctools.ui.viewmodel.LatestStudySummaryUi
import com.gipogo.rhctools.ui.viewmodel.PatientDetailEvent
import com.gipogo.rhctools.ui.viewmodel.PatientDetailUiState
import com.gipogo.rhctools.ui.viewmodel.PatientDetailViewModel
import com.gipogo.rhctools.ui.viewmodel.StudyItemUi
import com.gipogo.rhctools.ui.viewmodel.SummaryRowUi
import com.gipogo.rhctools.ui.viewmodel.TrendMetric
import com.gipogo.rhctools.ui.viewmodel.TrendPointUi
import com.gipogo.rhctools.ui.viewmodel.TrendSeriesUi
import com.gipogo.rhctools.ui.viewmodel.TrendsClinicalOpinionUi
import com.gipogo.rhctools.ui.viewmodel.TrendsUi
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.tooling.preview.Preview
import com.gipogo.rhctools.ui.components.PatientTrendsDashboardBlock

import com.gipogo.rhctools.ui.components.ForresterTrajectoryCard
import com.gipogo.rhctools.ui.components.ForresterTrendPoint


private val HeaderShape = RoundedCornerShape(22.dp)
private val CardShape = RoundedCornerShape(20.dp)

private enum class PatientTab(@StringRes val titleRes: Int) {
    Overview(R.string.patient_tab_overview),
    Studies(R.string.patient_tab_studies),
    Trends(R.string.patient_tab_trends),
    Reports(R.string.patient_tab_reports),
}



/**
 * ROUTE (usa Room directamente, explícito y auditable).
 *
 * - Sin edición de paciente (no hay callbacks de editar).
 * - Maneja errores con uiState + snackbar.
 */


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailRoute(
    patientId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNewStudy: (patientId: String) -> Unit,
    onOpenStudy: (patientId: String, studyId: String) -> Unit,
    onExportLatestPdf: (patientId: String) -> Unit,
    onExportStudyPdf: (patientId: String, studyId: String) -> Unit,
    onExportLongitudinalPdf: (patientId: String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // ✅ DB aquí (Route) y DAOs al ViewModel (NavGraph no toca Room)
    val appCtx = context.applicationContext
    val db = remember(appCtx) { com.gipogo.rhctools.data.db.DbProvider.get(appCtx) }
    val patientDao = remember(db) { db.patientDao() }
    val rhcStudyDao = remember(db) { db.rhcStudyDao() }

    val studyDao = remember(db) { db.studyDao() }

    val vm: PatientDetailViewModel = viewModel(
        key = patientId,
        factory = PatientDetailViewModel.Factory(
            patientId = patientId,
            patientDao = patientDao,
            rhcStudyDao = rhcStudyDao,
            studyDao = studyDao
        )

    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ✅ Eventos no fatales (snackbar). Sin helpers raros.
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is PatientDetailEvent.Snackbar -> {
                    val base = context.getString(ev.messageRes)
                    val dbg = ev.debugMessage?.takeIf { it.isNotBlank() }
                    val msg = if (dbg == null) base else "$base $dbg"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            }
        }
    }

    PatientDetailScreen(
        modifier = modifier,
        patientId = patientId,
        uiStateFlow = vm.uiState,
        onBack = onBack,
        onRetry = { vm.retry() },
        onNewStudy = onNewStudy,
        onOpenStudy = onOpenStudy,
        onExportLatestPdf = onExportLatestPdf,
        onExportStudyPdf = onExportStudyPdf,
        onExportLongitudinalPdf = onExportLongitudinalPdf,
        onDeleteStudy = { sid -> vm.deleteStudy(sid) },
        snackbarHostState = snackbarHostState
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientDetailScreen(
    patientId: String,
    uiStateFlow: kotlinx.coroutines.flow.Flow<PatientDetailUiState>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNewStudy: (String) -> Unit,
    onOpenStudy: (String, String) -> Unit,
    onExportLatestPdf: (String) -> Unit,
    onExportStudyPdf: (String, String) -> Unit,
    onExportLongitudinalPdf: (String) -> Unit,
    onDeleteStudy: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    // If you already use collectAsStateWithLifecycle in the project, feel free to swap.
    val uiState by uiStateFlow.collectAsState(initial = PatientDetailUiState.Loading)

    var tab by rememberSaveable { mutableStateOf(PatientTab.Overview) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()


    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val title = (uiState as? PatientDetailUiState.Content)?.topBarTitle ?: patientId
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // New Study is the primary creation action: explicit & visible.
            androidx.compose.material3.FloatingActionButton(
                onClick = { onNewStudy(patientId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.patient_action_new_study)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (uiState) {
                is PatientDetailUiState.Loading -> {
                    LoadingBlock()
                }

                is PatientDetailUiState.Error -> {
                    val err = uiState as PatientDetailUiState.Error
                    ErrorBlock(
                        messageRes = err.messageRes,
                        debug = err.debugMessage,
                        onRetry = onRetry
                    )
                }

                is PatientDetailUiState.Content -> {
                    val content = uiState as PatientDetailUiState.Content

                    PatientHeaderCard(
                        patientId = content.patientId,
                        secondaryLine = content.headerSecondaryLine,
                        lastUpdateMillis = content.lastUpdateMillis,
                        onCopyId = { copied ->
                            val msgRes = if (copied) R.string.patient_msg_copied else R.string.patient_msg_copy_failed
                            scope.launch { snackbarHostState.showSnackbar(context.getString(msgRes)) }
                        },

                        onOpenLastStudy = {
                            val lastId = content.latestSummary?.studyId ?: content.studies.firstOrNull()?.studyId
                            if (lastId != null) onOpenStudy(patientId, lastId)
                        },
                        onExportLatestPdf = {
                            // Latest export: still in Patient context (OK).
                            onExportLatestPdf(patientId)
                        },
                        hasAnyStudy = content.studies.isNotEmpty()
                    )

                    Spacer(Modifier.height(10.dp))

                    PatientTabSelector(selected = tab, onSelected = { tab = it })

                    Spacer(Modifier.height(10.dp))

                    when (tab) {
                        PatientTab.Overview -> OverviewTab(
                            latestSummary = content.latestSummary,
                            studies = content.studies,
                            onOpenStudy = { sid -> onOpenStudy(patientId, sid) }
                        )

                        PatientTab.Studies -> StudiesTab(
                            studies = content.studies,
                            onOpenStudy = { sid -> onOpenStudy(patientId, sid) },
                            onDeleteStudy = onDeleteStudy
                        )


                        PatientTab.Trends -> TrendsTab(
                            trends = content.trends
                        )

                        PatientTab.Reports -> ReportsTab(
                            studies = content.studies,
                            latestStudyId = content.latestSummary?.studyId ?: content.studies.firstOrNull()?.studyId,
                            onExportLatest = { onExportLatestPdf(patientId) },
                            onExportSelectedStudy = { sid -> onExportStudyPdf(patientId, sid) },
                            onExportLongitudinal = { onExportLongitudinalPdf(patientId) }
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------- */
/* UI Blocks                     */
/* ----------------------------- */

@Composable
private fun LoadingBlock() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBlock(
    @StringRes messageRes: Int,
    debug: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.common_error),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!debug.isNullOrBlank()) {
                    Text(
                        text = debug,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.85f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onRetry) {
                        Text(androidx.compose.ui.res.stringResource(R.string.common_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientHeaderCard(
    patientId: String,
    secondaryLine: String?,
    lastUpdateMillis: Long?,
    onCopyId: (Boolean) -> Unit,
    onOpenLastStudy: () -> Unit,
    onExportLatestPdf: () -> Unit,
    hasAnyStudy: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = HeaderShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patientId,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )


                    if (!secondaryLine.isNullOrBlank()) {
                        Text(
                            text = secondaryLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val lastUpdateText = lastUpdateMillis?.let { formatRelativeDateTime(context, it) }
                    val line = lastUpdateText ?: androidx.compose.ui.res.stringResource(R.string.common_value_na)
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.patient_last_update, line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val ok = copyToClipboard(context, label = context.getString(R.string.patient_action_copy_id), value = patientId)
                        onCopyId(ok)
                    }
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.patient_action_copy_id)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenLastStudy,
                    modifier = Modifier.weight(1f),
                    enabled = hasAnyStudy,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.patient_action_open_last_study))
                }

                OutlinedButton(
                    onClick = onExportLatestPdf,
                    modifier = Modifier.weight(1f),
                    enabled = hasAnyStudy,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.patient_action_export_pdf))
                }
            }
        }
    }
}

@Composable
private fun PatientTabSelector(
    selected: PatientTab,
    onSelected: (PatientTab) -> Unit
) {
    val tabs = remember { PatientTab.entries }
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(tab.titleRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ----------------------------- */
/* Tab: Overview                 */
/* ----------------------------- */

@Composable
private fun OverviewTab(
    latestSummary: LatestStudySummaryUi?,
    studies: List<StudyItemUi>,
    onOpenStudy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (studies.isEmpty()) {
            item {
                EmptyStudiesCard()
            }
            return@LazyColumn
        }

        item {
            Surface(
                shape = CardShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.patient_overview_latest_summary),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (latestSummary == null || !latestSummary.hasAnyValue) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.patient_overview_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        latestSummary.rows.forEach { row ->
                            SummaryRow(row = row, onPrimaryContainer = true)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timeline, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.patient_overview_recent_studies),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    studies.take(3).forEach { s ->
                        StudyMiniRow(item = s, onClick = { onOpenStudy(s.studyId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStudiesCard() {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.patient_empty_studies_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.patient_empty_studies_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryRow(row: SummaryRowUi, onPrimaryContainer: Boolean) {
    val label = androidx.compose.ui.res.stringResource(row.labelRes)
    val valueText = formatNullableNumber(row.value, row.decimals)
    val unit = row.unitRes?.let { androidx.compose.ui.res.stringResource(it) }

    val color = if (onPrimaryContainer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = color)
        Text(
            text = listOfNotNull(valueText, unit).joinToString(separator = " "),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

@Composable
private fun StudyMiniRow(item: StudyItemUi, onClick: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = androidx.compose.ui.res.stringResource(item.titleRes),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = formatRelativeDateTime(ctx, item.startedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ----------------------------- */
/* Tab: Studies                  */
/* ----------------------------- */

@Composable
private fun StudiesTab(
    studies: List<StudyItemUi>,
    onOpenStudy: (String) -> Unit,
    onDeleteStudy: (String) -> Unit
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    if (studies.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmptyStudiesCard()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(studies) { s ->
            Surface(
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStudy(s.studyId) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(s.titleRes),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        IconButton(onClick = { pendingDeleteId = s.studyId }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = androidx.compose.ui.res.stringResource(R.string.patient_action_delete_study)
                            )
                        }
                    }

                    Text(
                        text = formatRelativeDateTime(ctx, s.startedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (s.inlineMetrics.isNotEmpty()) {
                        InlineMetricsRow(metrics = s.inlineMetrics)
                    }
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.patient_delete_study_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.patient_delete_study_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val sid = pendingDeleteId
                    pendingDeleteId = null
                    if (sid != null) onDeleteStudy(sid)
                }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.common_cancel))
                }
            }
        )
    }
}


@Composable
private fun InlineMetricsRow(metrics: List<InlineMetricUi>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.forEach { m ->
            val label = androidx.compose.ui.res.stringResource(m.labelRes)
            val valueText = formatNullableNumber(m.value, m.decimals)
            val unit = m.unitRes?.let { androidx.compose.ui.res.stringResource(it) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = listOfNotNull(valueText, unit).joinToString(" "),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

/* ----------------------------- */
/* Tab: Trends                   */
/* ----------------------------- */


@Composable
private fun TrendsTab(trends: TrendsUi?) {
    if (trends == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.patient_trends_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.patient_trends_not_enough_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    // ✅ Trajectory points built ONLY from TrendsUi (Room -> VM -> TrendsUi)
    val ciSeries = trends.series.firstOrNull { it.metric == TrendMetric.CI }
    val pcwpSeries = trends.series.firstOrNull { it.metric == TrendMetric.PCWP }

    val trajectoryPoints: List<ForresterTrendPoint> = remember(ciSeries, pcwpSeries) {
        if (ciSeries == null || pcwpSeries == null) {
            emptyList()
        } else {
            val ciByTime = ciSeries.points.associateBy({ it.xMillis }, { it.y })
            val pwByTime = pcwpSeries.points.associateBy({ it.xMillis }, { it.y })

            val commonTimes = (ciByTime.keys intersect pwByTime.keys).toList().sorted()

            commonTimes.map { t ->
                ForresterTrendPoint(
                    ci = ciByTime[t],
                    pcwp = pwByTime[t],
                    label = null // si luego quieres T1/T2, lo añadimos con strings.
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ✅ Forrester trajectory (first -> last, last highlighted)
        item {
            Spacer(Modifier.height(6.dp))
            ForresterTrajectoryCard(
                points = trajectoryPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // ✅ New dashboard trends (the cards you already integrated)
        item {
            Spacer(Modifier.height(6.dp))
            PatientTrendsDashboardBlock(
                trends = trends,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ✅ Keep clinical opinion
        item {
            Spacer(Modifier.height(8.dp))
            ClinicalOpinionCard(opinion = trends.opinion)
            Spacer(Modifier.height(16.dp))
        }
    }
}


@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(
        text = androidx.compose.ui.res.stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun TrendChartCard(series: TrendSeriesUi) {
    val metricName = androidx.compose.ui.res.stringResource(series.metric.labelRes)
    val unit = series.metric.unitRes?.let { androidx.compose.ui.res.stringResource(it) }

    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = listOfNotNull(metricName, unit).joinToString(separator = " · "),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            TrendLineChart(points = series.points)

            val last = series.points.lastOrNull()?.y
            val lastText = formatNullableNumber(last, decimals = guessDecimals(series.metric))
            Text(
                text = lastText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClinicalOpinionCard(opinion: TrendsClinicalOpinionUi) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.patient_trends_clinical_opinion_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            // Direction lines (structured, localizable)
            opinion.directions.forEach { md ->
                val metric = androidx.compose.ui.res.stringResource(md.metric.labelRes)
                val dir = androidx.compose.ui.res.stringResource(md.direction.labelRes)
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.patient_trends_metric_trend_line, metric, dir),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Divider(modifier = Modifier.alpha(0.4f))

            // Pattern insights (qualitative)
            opinion.insights.forEach { insight ->
                Text(
                    text = "• " + androidx.compose.ui.res.stringResource(insight.textRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ----------------------------- */
/* Tab: Reports                  */
/* ----------------------------- */

@Composable
private fun ReportsTab(
    studies: List<StudyItemUi>,
    latestStudyId: String?,
    onExportLatest: () -> Unit,
    onExportSelectedStudy: (String) -> Unit,
    onExportLongitudinal: () -> Unit
) {
    var showSelectSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.patient_reports_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                if (studies.isEmpty() || latestStudyId == null) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.patient_reports_no_studies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FilledTonalButton(
                        onClick = onExportLatest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.patient_reports_latest_pdf))
                    }

                    OutlinedButton(
                        onClick = { showSelectSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.patient_reports_select_study_pdf))
                    }

                    OutlinedButton(
                        onClick = onExportLongitudinal,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.patient_reports_longitudinal_pdf))
                    }

                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.patient_reports_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.9f)
                    )
                }
            }
        }
    }

    if (showSelectSheet) {
        ReportStudyPickerSheet(
            studies = studies,
            onDismiss = { showSelectSheet = false },
            onSelect = { sid ->
                showSelectSheet = false
                onExportSelectedStudy(sid)
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportStudyPickerSheet(
    studies: List<StudyItemUi>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.patient_reports_select_study_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.patient_reports_select_study_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

            studies.forEach { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(s.studyId) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(s.titleRes),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = formatRelativeDateTime(ctx, s.startedAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text(androidx.compose.ui.res.stringResource(R.string.common_cancel))
                }
            }
        }
    }
}

/* ----------------------------- */
/* Charts (Canvas line chart)    */
/* ----------------------------- */

@Composable
private fun TrendLineChart(points: List<TrendPointUi>) {
    val safe = points.sortedBy { it.xMillis }
    val yMin = safe.minOfOrNull { it.y } ?: 0.0
    val yMax = safe.maxOfOrNull { it.y } ?: 0.0

    // ✅ Captura colores en contexto composable
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    val lineStroke = 5f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(vertical = 2.dp)
    ) {
        val w = size.width
        val h = size.height

        // Background faint baseline
        drawLine(
            color = onSurfaceVariant.copy(alpha = 0.25f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 2f
        )

        if (safe.size < 2) return@Canvas

        val xStep = w / (safe.size - 1).toFloat().coerceAtLeast(1f)
        val ySpan = (yMax - yMin).takeIf { it != 0.0 } ?: 1.0

        fun yToPx(y: Double): Float {
            val norm = ((y - yMin) / ySpan).toFloat()
            return h - (norm * h)
        }

        // Draw polyline
        for (i in 0 until safe.size - 1) {
            val p1 = safe[i]
            val p2 = safe[i + 1]
            val x1 = i * xStep
            val x2 = (i + 1) * xStep
            val y1 = yToPx(p1.y)
            val y2 = yToPx(p2.y)

            drawLine(
                color = primary,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
        }

        // Draw points
        safe.forEachIndexed { i, p ->
            val cx = i * xStep
            val cy = yToPx(p.y)
            drawCircle(
                color = primary,
                radius = 7f,
                center = Offset(cx, cy)
            )
        }

        // light padding overlay
        drawRect(
            color = surface.copy(alpha = 0.0f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h)
        )
    }
}

/* ----------------------------- */
/* Helpers                       */
/* ----------------------------- */

private fun copyToClipboard(context: Context, label: String, value: String): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        true
    }.getOrDefault(false)
}

private fun formatNullableNumber(value: Double?, decimals: Int): String {
    if (value == null) return "—"
    val nf = java.text.NumberFormat.getNumberInstance()
    nf.maximumFractionDigits = decimals
    nf.minimumFractionDigits = 0
    return nf.format(value)
}

private fun guessDecimals(metric: TrendMetric): Int {
    return when (metric) {
        TrendMetric.RAP, TrendMetric.MPAP, TrendMetric.PCWP -> 0
        TrendMetric.CI, TrendMetric.PVR -> 1
        TrendMetric.CPO -> 2
    }
}

/**
 * Relative datetime formatting without hard-coded UI text.
 * Uses your strings:
 * - common_today_time, common_yesterday_time
 */
private fun formatRelativeDateTime(context: Context, millis: Long): String {
    val calNow = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = millis }

    val sameYear = calNow.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    val nowDay = calNow.get(Calendar.DAY_OF_YEAR)
    val day = cal.get(Calendar.DAY_OF_YEAR)

    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    return when {
        sameYear && day == nowDay -> context.getString(R.string.common_today_time, time)
        sameYear && day == (nowDay - 1) -> context.getString(R.string.common_yesterday_time, time)
        else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
    }
}

@Preview(name = "PatientDetailScreen", showBackground = true)
@Composable
private fun Preview_PatientDetailScreen() {
    MaterialTheme {
        PatientDetailScreen(
            patientId = "PT-0001",
            uiStateFlow = kotlinx.coroutines.flow.flowOf(PatientDetailUiState.Loading),
            onBack = {},
            onRetry = {},
            onNewStudy = {},
            onOpenStudy = { _, _ -> },
            onExportLatestPdf = {},
            onExportStudyPdf = { _, _ -> },
            onExportLongitudinalPdf = {},
            onDeleteStudy = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
