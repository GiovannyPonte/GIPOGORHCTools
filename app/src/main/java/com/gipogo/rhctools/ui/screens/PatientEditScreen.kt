package com.gipogo.rhctools.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gipogo.rhctools.R
import com.gipogo.rhctools.domain.UnitSystem
import com.gipogo.rhctools.ui.components.GipogoFieldHint
import com.gipogo.rhctools.ui.components.GipogoSectionHeaderRow
import com.gipogo.rhctools.ui.components.GipogoSplitInputCard
import com.gipogo.rhctools.ui.components.GipogoSurfaceCard
import com.gipogo.rhctools.ui.security.AuthSessionManager
import com.gipogo.rhctools.ui.security.BiometricGate
import com.gipogo.rhctools.ui.validation.Severity
import com.gipogo.rhctools.ui.viewmodel.PatientEditorUiState
import com.gipogo.rhctools.ui.viewmodel.PatientEditorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.launch

/**
 * Wrapper REAL:
 * - Autenticación biométrica / sesión
 * - ViewModel (Room/Repo)
 * - startNew/startEdit
 * - Guardar (vm.save)
 *
 * La UI real está en PatientEditContent(...) para que el Preview sea estable y no duplique código.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientEditScreen(
    modifier: Modifier = Modifier,
    isEdit: Boolean,
    patientId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit,
    requireAuth: Boolean = true,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val vm: PatientEditorViewModel = viewModel()
    val ui by vm.state.collectAsState()

    // ---------- FORM VALIDATION ----------
    var submitted by remember { mutableStateOf(false) }

    fun nameIsValid(): Boolean = ui.displayName.trim().length >= 2

    fun codeIsValid(): Boolean {
        val t = ui.code.trim()
        if (t.isBlank()) return false
        if (ui.codeTaken) return false
        return true
    }

    // Normaliza coma decimal a punto para parse
    fun normalizeNumericForParse(raw: String): String = raw.trim().replace(',', '.')

    // ---------- AUTH STATE ----------
    var isAuthed by remember { mutableStateOf(!requireAuth) }
    var authInFlight by remember { mutableStateOf(false) }

    fun showSnack(@StringRes resId: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(resId)) }
    }

    fun requestAuth() {
        if (!requireAuth) {
            isAuthed = true
            return
        }
        if (authInFlight) return
        if (activity == null) {
            showSnack(R.string.auth_unavailable_unknown)
            onBack()
            return
        }

        authInFlight = true
        BiometricGate.authenticate(
            activity = activity,
            titleRes = R.string.auth_prompt_patients_title,
            subtitleRes = R.string.auth_prompt_patients_subtitle,
            onResult = { result ->
                authInFlight = false
                when (result) {
                    is BiometricGate.AuthResult.Success -> {
                        AuthSessionManager.markAuthenticated()
                        isAuthed = true
                    }
                    is BiometricGate.AuthResult.Canceled -> { /* keep locked */ }
                    is BiometricGate.AuthResult.NotAvailable -> {
                        showSnack(result.messageRes); onBack()
                    }
                    is BiometricGate.AuthResult.Error -> {
                        showSnack(result.messageRes)
                    }
                }
            }
        )
    }

    // Autenticación automática al entrar
    LaunchedEffect(requireAuth) {
        if (!requireAuth) {
            isAuthed = true
            return@LaunchedEffect
        }
        if (AuthSessionManager.isSessionValid()) {
            isAuthed = true
            return@LaunchedEffect
        }
        requestAuth()
    }

    // Inicializa VM solo cuando está autenticado
    LaunchedEffect(isAuthed, isEdit, patientId) {
        if (!isAuthed) return@LaunchedEffect
        submitted = false

        if (isEdit) {
            val id = patientId
            if (id.isNullOrBlank()) {
                showSnack(R.string.auth_unavailable_unknown)
                onBack()
                return@LaunchedEffect
            }
            vm.startEdit(id)
        } else {
            vm.startNew()
        }
    }

    // ---------- Units (estado visible de UI) ----------
    val defaultUnitSystem = rememberDefaultUnitSystem()
    var unitSystem by rememberSaveable { mutableStateOf(defaultUnitSystem) }

    var weightDisplay by rememberSaveable(ui.patientId) { mutableStateOf("") }
    var heightDisplay by rememberSaveable(ui.patientId) { mutableStateOf("") }

    // Inicializar campos visibles desde lo que haya en VM (kg/cm)
    var didInitPhysical by rememberSaveable(ui.patientId) { mutableStateOf(false) }

    fun normNum(s: String): String = normalizeNumericForParse(s)

    // Rangos “permitidos” (en kg / cm, independientemente del UnitSystem mostrado)
    val WEIGHT_MIN_KG = 20.0
    val WEIGHT_MAX_KG = 300.0
    val HEIGHT_MIN_CM = 120.0
    val HEIGHT_MAX_CM = 230.0

    fun weightIsValidNow(): Boolean {
        val t = normNum(weightDisplay)
        if (t.isBlank()) return false
        val kg = unitSystem.weightToKg(t) ?: return false
        return kg in 20.0..300.0
    }

    fun heightIsValidNow(): Boolean {
        val t = normNum(heightDisplay)
        if (t.isBlank()) return false
        val cm = unitSystem.heightToCm(t) ?: return false
        return cm in 120.0..230.0
    }


    fun canSaveNow(): Boolean =
        nameIsValid() &&
                codeIsValid() &&
                weightIsValidNow() &&
                heightIsValidNow() &&
                !ui.saving &&
                !ui.loading

    LaunchedEffect(
        ui.patientId,
        ui.weightKgText,
        ui.heightCmText,
        unitSystem,
        ui.loading
    ) {
        if (ui.loading) return@LaunchedEffect
        if (didInitPhysical) return@LaunchedEffect

        val kg = ui.weightKgText.trim().toDoubleOrNull()
        val cm = ui.heightCmText.trim().toDoubleOrNull()

        if (kg != null) weightDisplay = unitSystem.kgToWeightString(kg)
        if (cm != null) heightDisplay = unitSystem.cmToHeightString(cm)

        didInitPhysical = true
    }

    fun onWeightDisplayChanged(newValue: String) {
        weightDisplay = newValue
        // ✅ importante: esta extensión ya normaliza coma/punto (ver abajo)
        vm.setWeightKgText(unitSystem.weightKgTextOrBlank(newValue))
    }

    fun onHeightDisplayChanged(newValue: String) {
        heightDisplay = newValue
        vm.setHeightCmText(unitSystem.heightCmTextOrBlank(newValue))
    }

    val bsaValue = remember(weightDisplay, heightDisplay, unitSystem) {
        val kg = unitSystem.weightToKg(normNum(weightDisplay))
        val cm = unitSystem.heightToCm(normNum(heightDisplay))
        if (kg != null && cm != null) calculateBsaKgCm(kg, cm) else null
    }

    fun attemptSave() {
        submitted = true
        if (!isAuthed) {
            requestAuth()
            return
        }

        // sincroniza a VM (kg/cm) antes de guardar
        onWeightDisplayChanged(weightDisplay)
        onHeightDisplayChanged(heightDisplay)

        if (!canSaveNow()) {
            showSnack(R.string.patient_edit_fix_errors)
            return
        }

        vm.save(onSuccess = onSave)
    }

    PatientEditContent(
        modifier = modifier,
        isEdit = isEdit,
        ui = ui,
        submitted = submitted,

        snackbarHostState = snackbarHostState,

        isAuthed = isAuthed,
        authInFlight = authInFlight,
        requireAuth = requireAuth,
        onRequestAuth = { requestAuth() },

        onBack = onBack,
        onSaveClick = { attemptSave() },

        // VM setters
        onCodeChange = vm::setCode,
        onRegenerateCode = vm::regenerateCode,
        onNameChange = vm::setDisplayName,
        onBirthDateMillisPick = vm::setBirthDateMillis,
        onSexChange = vm::setSex,
        onNotesChange = vm::setNotes,

        onToggleTag = vm::toggleTag,

        unitSystem = unitSystem,
        onUnitSystemChange = { newSys ->
            val old = unitSystem
            weightDisplay = convertWeightString(weightDisplay, old, newSys)
            heightDisplay = convertHeightString(heightDisplay, old, newSys)
            unitSystem = newSys
            onWeightDisplayChanged(weightDisplay)
            onHeightDisplayChanged(heightDisplay)
        },
        weightDisplay = weightDisplay,
        onWeightDisplayChange = { onWeightDisplayChanged(it) },
        heightDisplay = heightDisplay,
        onHeightDisplayChange = { onHeightDisplayChanged(it) },
        bsaValue = bsaValue,
    )
}

/**
 * UI pura (sin viewModel, sin biometría directa). Se usa tanto en runtime como en Preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientEditContent(
    modifier: Modifier = Modifier,
    isEdit: Boolean,
    ui: PatientEditorUiState,
    submitted: Boolean,

    snackbarHostState: SnackbarHostState,

    isAuthed: Boolean,
    authInFlight: Boolean,
    requireAuth: Boolean,
    onRequestAuth: () -> Unit,

    onBack: () -> Unit,
    onSaveClick: () -> Unit,

    onCodeChange: (String) -> Unit,
    onRegenerateCode: () -> Unit,
    onNameChange: (String) -> Unit,
    onBirthDateMillisPick: (Long?) -> Unit,
    onSexChange: (String?) -> Unit,
    onNotesChange: (String) -> Unit,

    onToggleTag: (String) -> Unit,

    unitSystem: UnitSystem,
    onUnitSystemChange: (UnitSystem) -> Unit,
    weightDisplay: String,
    onWeightDisplayChange: (String) -> Unit,
    heightDisplay: String,
    onHeightDisplayChange: (String) -> Unit,
    bsaValue: Double?,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val minDob = today.minusYears(120)

    val dob: LocalDate? = ui.birthDateMillis?.let { ms ->
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }

    val dobInFuture = dob?.isAfter(today) == true
    val dobTooOld = dob?.isBefore(minDob) == true
    val dobInvalid = dobInFuture || dobTooOld

    val ageYears: Int? = if (dob != null && !dobInvalid) {
        java.time.Period.between(dob, today).years.coerceAtLeast(0)
    } else null


    fun nameIsValid(): Boolean = ui.displayName.trim().length >= 2
    fun codeIsValid(): Boolean = ui.code.trim().isNotBlank() && !ui.codeTaken

    fun normNum(s: String): String = s.trim().replace(',', '.')

    // Rangos permitidos (kg / cm)
    val WEIGHT_MIN_KG = 20.0
    val WEIGHT_MAX_KG = 300.0
    val HEIGHT_MIN_CM = 120.0
    val HEIGHT_MAX_CM = 230.0

    // ✅ Aquí SÍ bloqueamos por rango, porque el usuario pidió que GUARDAR se desactive fuera de rango.
    fun weightIsValidNow(): Boolean {
        val t = normNum(weightDisplay)
        if (t.isBlank()) return false
        val kg = unitSystem.weightToKg(t) ?: return false
        return kg in WEIGHT_MIN_KG..WEIGHT_MAX_KG
    }

    fun heightIsValidNow(): Boolean {
        val t = normNum(heightDisplay)
        if (t.isBlank()) return false
        val cm = unitSystem.heightToCm(t) ?: return false
        return cm in HEIGHT_MIN_CM..HEIGHT_MAX_CM
    }

    val isSaveEnabled =
        !ui.saving &&
                !ui.loading &&
                codeIsValid() &&
                nameIsValid() &&
                weightIsValidNow() &&
                heightIsValidNow() &&
                !dobInvalid


    // DOB picker
    var showDobPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun showDobSnack(@StringRes resId: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(resId)) }
    }


    if (showDobPicker) {
        DatePickerDialog(
            onDismissRequest = { showDobPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            val today = LocalDate.now(ZoneId.systemDefault())
                            val minDob = today.minusYears(120)

                            when {
                                picked.isAfter(today) -> {
                                    showDobSnack(R.string.patient_edit_err_dob_future)
                                    return@TextButton
                                }
                                picked.isBefore(minDob) -> {
                                    showDobSnack(R.string.patient_edit_err_dob_range)
                                    return@TextButton
                                }
                                else -> onBirthDateMillisPick(millis)
                            }
                        }
                        showDobPicker = false
                    }

                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDobPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // Tag help
    var showTagHelp by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showTagHelp) {
        ModalBottomSheet(
            onDismissRequest = { showTagHelp = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.tag_help_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = stringResource(R.string.tag_help_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                TagKey.entries.forEach { tag ->
                    Text(
                        text = stringResource(tag.labelRes),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = stringResource(tag.helpRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isEdit) R.string.patient_edit_title_edit
                            else R.string.patient_edit_title_create
                        ),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSaveClick,
                        enabled = isSaveEnabled
                    ) {
                        Icon(imageVector = Icons.Outlined.Save, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->

        // ---------- LOCKED STATE ----------
        if (!isAuthed) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GipogoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.auth_locked_patients_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(R.string.auth_locked_patients_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onRequestAuth() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authInFlight && requireAuth
                    ) {
                        Text(stringResource(R.string.auth_locked_patients_cta))
                    }
                }
            }
            return@Scaffold
        }

        // ---------- MAIN CONTENT ----------
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ==========================
            // Identity / Core
            // ==========================
            GipogoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                GipogoSectionHeaderRow(title = stringResource(R.string.patient_edit_section_identity))
                Spacer(Modifier.height(10.dp))

                val codeError = (submitted && ui.code.trim().isBlank()) || ui.codeTaken

                OutlinedTextField(
                    value = ui.code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.patient_edit_field_code)) },
                    supportingText = {
                        when {
                            ui.codeTaken -> Text(stringResource(R.string.patient_edit_code_taken))
                            submitted && ui.code.trim().isBlank() -> Text(stringResource(R.string.patient_edit_err_code_required))
                            else -> Text(stringResource(R.string.patient_edit_field_code_hint))
                        }
                    },
                    isError = codeError,
                    trailingIcon = {
                        IconButton(onClick = onRegenerateCode, enabled = !ui.loading) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                val nameError = submitted && !nameIsValid()

                OutlinedTextField(
                    value = ui.displayName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.patient_edit_field_name)) },
                    placeholder = { Text(stringResource(R.string.patient_edit_field_name_hint)) },
                    supportingText = {
                        if (nameError) Text(stringResource(R.string.patient_edit_err_name_required))
                        else Text(stringResource(R.string.patient_edit_name_support))
                    },
                    isError = nameError,
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = ui.birthDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
                    }.orEmpty(),
                    onValueChange = { /* readOnly */ },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.patient_edit_field_dob)) },
                    placeholder = { Text(stringResource(R.string.patient_edit_field_dob_hint)) },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDobPicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        }
                    },
                    isError = dobInvalid,
                    supportingText = {
                        when {
                            dobInFuture -> Text(stringResource(R.string.patient_edit_err_dob_future))
                            dobTooOld -> Text(stringResource(R.string.patient_edit_err_dob_range))
                            ageYears != null -> Text(stringResource(R.string.patient_edit_age_computed, ageYears))
                            else -> Text(stringResource(R.string.patient_edit_field_dob_support))
                        }
                    }
                )


                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.patient_edit_field_sex),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(8.dp))

                val sexSelected = remember(ui.sex) {
                    when (ui.sex?.trim()?.uppercase()) {
                        "M", "MALE" -> Sex.Male
                        "F", "FEMALE" -> Sex.Female
                        else -> Sex.NotSpecified
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Sex.entries.forEachIndexed { index, s ->
                        SegmentedButton(
                            selected = sexSelected == s,
                            onClick = {
                                val sexValue = when (s) {
                                    Sex.Male -> "M"
                                    Sex.Female -> "F"
                                    Sex.NotSpecified -> null
                                }
                                onSexChange(sexValue)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size)
                        ) { Text(stringResource(s.labelRes)) }
                    }
                }
            }

            // ==========================
            // Physical (peso/talla)
            // ==========================
            GipogoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.patient_edit_section_physical),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.widthIn(min = 200.dp)
                        ) {
                            UnitSystem.entries.forEachIndexed { index, sys ->
                                SegmentedButton(
                                    selected = unitSystem == sys,
                                    onClick = { onUnitSystemChange(sys) },
                                    shape = SegmentedButtonDefaults.itemShape(index, UnitSystem.entries.size),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(sys.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    val weightTrim = normNum(weightDisplay)
                    val heightTrim = normNum(heightDisplay)

                    val weightKg = unitSystem.weightToKg(weightTrim)
                    val heightCm = unitSystem.heightToCm(heightTrim)

                    val weightOutOfRange = weightKg != null && (weightKg !in WEIGHT_MIN_KG..WEIGHT_MAX_KG)
                    val heightOutOfRange = heightCm != null && (heightCm !in HEIGHT_MIN_CM..HEIGHT_MAX_CM)

                    // Como ahora rango BLOQUEA guardar → lo tratamos como ERROR (rojo).
                    val weightSeverity: Severity? = when {
                        weightTrim.isBlank() -> if (submitted) Severity.ERROR else null
                        weightKg == null -> Severity.ERROR
                        weightOutOfRange -> Severity.ERROR
                        else -> Severity.OK
                    }

                    val heightSeverity: Severity? = when {
                        heightTrim.isBlank() -> if (submitted) Severity.ERROR else null
                        heightCm == null -> Severity.ERROR
                        heightOutOfRange -> Severity.ERROR
                        else -> Severity.OK
                    }

                    val physicalOverallSeverity: Severity? = when {
                        weightSeverity == Severity.ERROR || heightSeverity == Severity.ERROR -> Severity.ERROR
                        weightSeverity == Severity.OK && heightSeverity == Severity.OK -> Severity.OK
                        else -> null
                    }

                    val weightPlaceholder = stringResource(
                        if (unitSystem == UnitSystem.Metric) R.string.patient_edit_placeholder_weight_metric
                        else R.string.patient_edit_placeholder_weight_imperial
                    )
                    val heightPlaceholder = stringResource(
                        if (unitSystem == UnitSystem.Metric) R.string.patient_edit_placeholder_height_metric
                        else R.string.patient_edit_placeholder_height_imperial
                    )

                    GipogoSplitInputCard(
                        leftLabel = stringResource(R.string.patient_edit_field_weight),
                        leftValue = weightDisplay,
                        leftPlaceholder = weightPlaceholder,
                        leftUnit = stringResource(unitSystem.weightUnitRes),
                        onLeftChange = onWeightDisplayChange,
                        leftSeverity = weightSeverity,

                        rightLabel = stringResource(R.string.patient_edit_field_height),
                        rightValue = heightDisplay,
                        rightPlaceholder = heightPlaceholder,
                        rightUnit = stringResource(unitSystem.heightUnitRes),
                        onRightChange = onHeightDisplayChange,
                        rightSeverity = heightSeverity,

                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        overallSeverity = physicalOverallSeverity
                    )

                    val showPhysicalHint = submitted || weightTrim.isNotBlank() || heightTrim.isNotBlank()

                    val physicalHint: Pair<Severity, Int>? = when {
                        // REQUIRED: solo al intentar guardar
                        submitted && weightTrim.isBlank() && heightTrim.isBlank() ->
                            Severity.ERROR to R.string.patient_edit_err_physical_required
                        submitted && weightTrim.isBlank() ->
                            Severity.ERROR to R.string.patient_edit_err_weight_required
                        submitted && heightTrim.isBlank() ->
                            Severity.ERROR to R.string.patient_edit_err_height_required

                        // INVALID: si escribió algo y no parsea
                        weightTrim.isNotBlank() && weightKg == null ->
                            Severity.ERROR to R.string.patient_edit_err_weight_invalid
                        heightTrim.isNotBlank() && heightCm == null ->
                            Severity.ERROR to R.string.patient_edit_err_height_invalid

                        // OUT OF RANGE: ahora es ERROR porque bloquea guardar
                        weightOutOfRange && heightOutOfRange ->
                            Severity.ERROR to R.string.patient_edit_warn_physical_range
                        weightOutOfRange ->
                            Severity.ERROR to R.string.patient_edit_warn_weight_range
                        heightOutOfRange ->
                            Severity.ERROR to R.string.patient_edit_warn_height_range

                        else -> null
                    }

                    if (showPhysicalHint && physicalHint != null) {
                        val (sev, msgRes) = physicalHint
                        GipogoFieldHint(
                            severity = sev,
                            text = stringResource(msgRes)
                        )
                    }

                    if (bsaValue != null) {
                        val unitM2 = stringResource(R.string.common_unit_m2)
                        val bsaText = "${format2(bsaValue)} $unitM2"
                        Text(
                            text = stringResource(R.string.patient_edit_bsa_computed, bsaText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ==========================
            // Tags
            // ==========================
            GipogoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                GipogoSectionHeaderRow(
                    title = stringResource(R.string.patient_edit_field_tags),
                    onInfoClick = { showTagHelp = true }
                )

                Spacer(Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TagKey.entries.forEach { tag ->
                        val selected = ui.tagKeys.contains(tag.key)
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleTag(tag.key) },
                            label = { Text(stringResource(tag.labelRes)) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.patient_edit_tags_support),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ==========================
            // Notes (opcional)
            // ==========================
            GipogoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                GipogoSectionHeaderRow(title = stringResource(R.string.patient_edit_field_notes))
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = ui.notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    label = { Text(stringResource(R.string.patient_edit_field_notes)) },
                    placeholder = { Text(stringResource(R.string.patient_edit_field_notes_hint)) }
                )
            }

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = isSaveEnabled
            ) {
                Text(stringResource(R.string.common_save))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ---------- Sex ---------- */
private enum class Sex(@StringRes val labelRes: Int) {
    Male(R.string.sex_male),
    Female(R.string.sex_female),
    NotSpecified(R.string.sex_not_specified)
}

/* ---------- Locale default units ---------- */
@Composable
private fun rememberDefaultUnitSystem(): UnitSystem {
    val config = LocalConfiguration.current
    val locale = config.locales[0]
    val country = locale.country.uppercase()
    return remember(country) {
        if (country == "US") UnitSystem.Imperial else UnitSystem.Metric
    }
}

/* ---------- Tags (canonical keys for DB) ---------- */
private enum class TagKey(val key: String, @StringRes val labelRes: Int, @StringRes val helpRes: Int) {
    FOLLOW_UP("FOLLOW_UP", R.string.tag_followup, R.string.tag_help_followup),
    PREOP("PREOP", R.string.tag_preop, R.string.tag_help_preop),
    POST_OP("POST_OP", R.string.tag_postop, R.string.tag_help_postop),
    PAH_EVAL("PAH_EVAL", R.string.tag_pah, R.string.tag_help_pah),
    HF("HF", R.string.tag_hf, R.string.tag_help_hf),
    SHOCK("SHOCK", R.string.tag_shock, R.string.tag_help_shock),
}

/* ---------- Helpers ---------- */
private fun calculateBsaKgCm(kg: Double, cm: Double): Double {
    return 0.007184 * kg.pow(0.425) * cm.pow(0.725)
}

private fun format2(value: Double): String = String.format("%.2f", value)

private fun formatSmart(v: Double): String {
    val rounded = v.toLong().toDouble()
    return if (abs(v - rounded) < 1e-9) rounded.toLong().toString() else v.toString()
}

// ✅ IMPORTANTE: convierte aun si el display usa coma decimal
private fun convertWeightString(value: String, old: UnitSystem, new: UnitSystem): String {
    val v = value.trim().replace(',', '.').toDoubleOrNull() ?: return value
    val kg = when (old) {
        UnitSystem.Metric -> v
        UnitSystem.Imperial -> v * 0.45359237
    }
    return when (new) {
        UnitSystem.Metric -> String.format("%.2f", kg)
        UnitSystem.Imperial -> String.format("%.2f", kg / 0.45359237)
    }
}

// ✅ IMPORTANTE: convierte aun si el display usa coma decimal
private fun convertHeightString(value: String, old: UnitSystem, new: UnitSystem): String {
    val v = value.trim().replace(',', '.').toDoubleOrNull() ?: return value
    val cm = when (old) {
        UnitSystem.Metric -> v
        UnitSystem.Imperial -> v * 2.54
    }
    return when (new) {
        UnitSystem.Metric -> String.format("%.2f", cm)
        UnitSystem.Imperial -> String.format("%.2f", cm / 2.54)
    }
}

/**
 * Extensiones: garantizan compilación aunque UnitSystem del dominio no tenga estos helpers como miembros.
 * ✅ Normalizan coma->punto para no romper parse al escribir 95,00.
 */
private fun UnitSystem.weightKgTextOrBlank(display: String): String {
    val t = display.trim()
    if (t.isBlank()) return ""
    val normalized = t.replace(',', '.')
    val kg = this.weightToKg(normalized) ?: return ""
    return formatSmart(kg)
}

private fun UnitSystem.heightCmTextOrBlank(display: String): String {
    val t = display.trim()
    if (t.isBlank()) return ""
    val normalized = t.replace(',', '.')
    val cm = this.heightToCm(normalized) ?: return ""
    return formatSmart(cm)
}

private fun UnitSystem.kgToWeightString(kg: Double): String {
    return when (this) {
        UnitSystem.Metric -> format2(kg)
        UnitSystem.Imperial -> format2(kg / 0.45359237)
    }
}

private fun UnitSystem.cmToHeightString(cm: Double): String {
    return when (this) {
        UnitSystem.Metric -> format2(cm)
        UnitSystem.Imperial -> format2(cm / 2.54)
    }
}

/* ---------- Preview ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun Preview_PatientEditScreen() {
    val snackbar = remember { SnackbarHostState() }

    val fakeUi = PatientEditorUiState(
        loading = false,
        saving = false,
        patientId = "preview",
        code = "GIP-2026-926A9DA8",
        displayName = "Mariela Ruiz Dominguez",
        sex = "F",
        birthDateMillis = 0L,
        notes = "Preview note",
        weightKgText = "54",
        heightCmText = "160",
        tagKeys = setOf("PAH_EVAL", "FOLLOW_UP"),
        codeTaken = false,
        error = null
    )

    var unitSystem by remember { mutableStateOf(UnitSystem.Metric) }
    var weightDisplay by remember { mutableStateOf("54") }
    var heightDisplay by remember { mutableStateOf("160") }

    fun normNum(s: String): String = s.trim().replace(',', '.')

    val bsa = remember(weightDisplay, heightDisplay, unitSystem) {
        val kg = unitSystem.weightToKg(normNum(weightDisplay))
        val cm = unitSystem.heightToCm(normNum(heightDisplay))
        if (kg != null && cm != null) calculateBsaKgCm(kg, cm) else null
    }

    MaterialTheme {
        PatientEditContent(
            isEdit = true,
            ui = fakeUi,
            submitted = false,

            snackbarHostState = snackbar,

            isAuthed = true,
            authInFlight = false,
            requireAuth = false,
            onRequestAuth = {},

            onBack = {},
            onSaveClick = {},

            onCodeChange = {},
            onRegenerateCode = {},
            onNameChange = {},
            onBirthDateMillisPick = {},
            onSexChange = {},
            onNotesChange = {},

            onToggleTag = {},

            unitSystem = unitSystem,
            onUnitSystemChange = { unitSystem = it },
            weightDisplay = weightDisplay,
            onWeightDisplayChange = { weightDisplay = it },
            heightDisplay = heightDisplay,
            onHeightDisplayChange = { heightDisplay = it },
            bsaValue = bsa
        )
    }
}
