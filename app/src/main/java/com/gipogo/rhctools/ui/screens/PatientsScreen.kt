package com.gipogo.rhctools.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.ui.viewmodel.PatientsDatePreset
import com.gipogo.rhctools.ui.viewmodel.PatientsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val CardShape = RoundedCornerShape(22.dp)
private val AvatarShape = CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onOpenPatient: ((String) -> Unit)? = null,
    onEditPatient: ((String) -> Unit)? = null,
) {
    val vm: PatientsViewModel = viewModel()
    val ui by vm.state.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.common_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingDeleteId
                        pendingDeleteId = null
                        if (id != null) vm.deletePatient(id)
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.patients_title_app),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() }, enabled = onBack != null) {
                        Icon(Icons.Outlined.Menu, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleSearchActive() }) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    }
                    IconButton(onClick = { onAdd?.invoke() }, enabled = onAdd != null) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAdd?.invoke() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            FiltersRowSingleLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding)
                    .padding(top = 10.dp, bottom = 6.dp),
                last7DaysSelected = (ui.datePreset == PatientsDatePreset.LAST_7_DAYS),
                selectedTagKeys = ui.selectedTagKeys,
                onToggleLast7Days = { vm.toggleLast7Days() },
                onToggleTag = { vm.toggleTag(it) }
            )

            if (ui.searchActive) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = { vm.setQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding)
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.patients_search_hint)) },
                    trailingIcon = {
                        if (ui.query.isNotBlank()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = null)
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScreenPadding),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.items, key = { it.patient.id }) { row ->
                    PatientCard(
                        p = row.patient,
                        lastStudyAtMillis = row.lastStudyAtMillis,
                        onClick = { onOpenPatient?.invoke(row.patient.id) },
                        onEdit = { onEditPatient?.invoke(row.patient.id) },
                        onDelete = { pendingDeleteId = row.patient.id }
                    )
                }

                if (ui.items.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.patients_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .alpha(0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersRowSingleLine(
    modifier: Modifier = Modifier,
    last7DaysSelected: Boolean,
    selectedTagKeys: Set<String>,
    onToggleLast7Days: () -> Unit,
    onToggleTag: (String) -> Unit
) {
    val scroll = rememberScrollState()

    // ✅ SOLO estos 6 tags canónicos
    val tags: List<Pair<String, Int>> = listOf(
        "SHOCK" to R.string.patients_chip_shock,
        "HF" to R.string.patients_chip_hf,
        "PREOP" to R.string.patients_chip_preop,
        "POST_OP" to R.string.patients_chip_postop,
        "PAH_EVAL" to R.string.patients_tag_pah_eval,
        "FOLLOW_UP" to R.string.patients_tag_follow_up
    )

    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = last7DaysSelected,
            onClick = onToggleLast7Days,
            label = {
                Text(
                    text = stringResource(R.string.patients_chip_last_7d),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                )
            }
        )

        tags.forEach { (key, labelRes) ->
            FilterChip(
                selected = selectedTagKeys.contains(key),
                onClick = { onToggleTag(key) },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            )
        }
    }
}

@Composable
private fun PatientCard(
    p: PatientEntity,
    lastStudyAtMillis: Long?,
    onClick: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val title = p.displayName?.takeIf { it.isNotBlank() }
        ?: p.notes?.takeIf { it.isNotBlank() }
        ?: p.internalCode

    val maleShort = stringResource(R.string.common_sex_m_short)
    val femaleShort = stringResource(R.string.common_sex_f_short)

    val ageSex = remember(p.birthDateMillis, p.sex, maleShort, femaleShort) {
        formatAgeSex(p.birthDateMillis, p.sex, maleShort, femaleShort)
    }

    val lastStudyLabel = remember(lastStudyAtMillis) { lastStudyAtMillis?.toString() }
        ?: stringResource(R.string.common_value_na)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = AvatarShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = true)
                        )

                        if (ageSex.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = { /* no-op */ },
                                label = { Text(text = ageSex, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(999.dp)
                            )
                        }
                    }

                    Text(
                        text = p.internalCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = stringResource(R.string.patients_last_study, lastStudyLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit?.invoke()
                            },
                            enabled = onEdit != null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete?.invoke()
                            },
                            enabled = onDelete != null
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { onClick?.invoke() },
                    enabled = onClick != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.patients_view_details),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatAgeSex(
    birthDateMillis: Long?,
    sex: String?,
    maleShort: String,
    femaleShort: String
): String {
    val sexLabel = sex
        ?.trim()
        ?.uppercase()
        ?.let {
            when (it) {
                "M", "MALE" -> maleShort
                "F", "FEMALE" -> femaleShort
                else -> null
            }
        }

    val ageYears: Int? = birthDateMillis?.let { ms ->
        try {
            val dob = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now(ZoneId.systemDefault())
            Period.between(dob, today).years.coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }

    return when {
        ageYears != null && sexLabel != null -> "${ageYears}${sexLabel}"
        ageYears != null -> ageYears.toString()
        sexLabel != null -> sexLabel
        else -> ""
    }
}
