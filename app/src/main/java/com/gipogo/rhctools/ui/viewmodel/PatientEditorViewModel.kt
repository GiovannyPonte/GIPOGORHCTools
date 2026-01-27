package com.gipogo.rhctools.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gipogo.rhctools.core.result.DataError
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.patients.PatientsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PatientEditorUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val patientId: String? = null,

    val code: String = "",
    val displayName: String = "",
    val sex: String? = null,                 // "M" / "F" / null

    // ✅ DOB completo (epoch millis)
    val birthDateMillis: Long? = null,

    val notes: String = "",

    val weightKgText: String = "",
    val heightCmText: String = "",

    // ✅ tags persistidos (keys)
    val tagKeys: Set<String> = emptySet(),

    val codeTaken: Boolean = false,
    val error: DataError? = null
)

class PatientEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PatientsRepository.get(app)

    private val _state = MutableStateFlow(PatientEditorUiState())
    val state: StateFlow<PatientEditorUiState> = _state.asStateFlow()

    fun startNew() {
        _state.value = PatientEditorUiState(loading = true)
        viewModelScope.launch {
            when (val r = repo.generateUniqueCode()) {
                is DataResult.Success -> _state.update { it.copy(loading = false, code = r.value) }
                is DataResult.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun startEdit(patientId: String) {
        _state.update { it.copy(loading = true, patientId = patientId) }
        viewModelScope.launch {
            when (val r = repo.getPatientWithTags(patientId)) {
                is DataResult.Success -> {
                    val pw = r.value
                    val p = pw.patient
                    val tags = pw.tags.map { it.key }.toSet()

                    _state.value = PatientEditorUiState(
                        loading = false,
                        saving = false,
                        patientId = p.id,
                        code = p.internalCode,
                        displayName = p.displayName.orEmpty(),
                        sex = p.sex,
                        birthDateMillis = p.birthDateMillis,
                        notes = p.notes.orEmpty(),
                        weightKgText = p.weightKg?.let(::formatSmartDouble).orEmpty(),
                        heightCmText = p.heightCm?.let(::formatSmartDouble).orEmpty(),
                        tagKeys = tags,
                        codeTaken = false,
                        error = null
                    )
                }
                is DataResult.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun setCode(v: String) = _state.update { it.copy(code = v, codeTaken = false) }
    fun setDisplayName(v: String) = _state.update { it.copy(displayName = v) }
    fun setSex(v: String?) = _state.update { it.copy(sex = v) }

    // ✅ DOB completo
    fun setBirthDateMillis(v: Long?) = _state.update { it.copy(birthDateMillis = v) }

    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setWeightKgText(v: String) = _state.update { it.copy(weightKgText = v) }
    fun setHeightCmText(v: String) = _state.update { it.copy(heightCmText = v) }

    fun toggleTag(key: String) {
        val k = key.trim()
        if (k.isBlank()) return
        _state.update { s ->
            val set = s.tagKeys.toMutableSet()
            if (set.contains(k)) set.remove(k) else set.add(k)
            s.copy(tagKeys = set)
        }
    }

    fun regenerateCode() {
        _state.update { it.copy(loading = true, error = null, codeTaken = false) }
        viewModelScope.launch {
            when (val r = repo.generateUniqueCode()) {
                is DataResult.Success -> _state.update { it.copy(loading = false, code = r.value) }
                is DataResult.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        val s = _state.value
        val code = s.code.trim().uppercase()
        val name = s.displayName.trim()

        if (code.isBlank()) {
            _state.update { it.copy(error = DataError.Validation("internalCode", "Empty")) }
            return
        }
        if (name.length < 2) {
            _state.update { it.copy(error = DataError.Validation("displayName", "TooShort")) }
            return
        }

        val weightKg = s.weightKgText.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val heightCm = s.heightCmText.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()

        Log.d(
            "PAT_SAVE",
            "patientId=${s.patientId} dobMillis=${s.birthDateMillis} w=$weightKg h=$heightCm tags=${s.tagKeys}"
        )

        if (s.weightKgText.isNotBlank() && weightKg == null) {
            _state.update { it.copy(error = DataError.Validation("weightKg", "InvalidNumber")) }
            return
        }
        if (s.heightCmText.isNotBlank() && heightCm == null) {
            _state.update { it.copy(error = DataError.Validation("heightCm", "InvalidNumber")) }
            return
        }
        if (weightKg != null && (weightKg <= 0.0 || weightKg > 500.0)) {
            _state.update { it.copy(error = DataError.Validation("weightKg", "OutOfRange")) }
            return
        }
        if (heightCm != null && (heightCm <= 0.0 || heightCm > 300.0)) {
            _state.update { it.copy(error = DataError.Validation("heightCm", "OutOfRange")) }
            return
        }

        _state.update { it.copy(saving = true, error = null, codeTaken = false) }

        viewModelScope.launch {
            val result: DataResult<String> =
                if (s.patientId == null) {
                    repo.createPatient(
                        internalCode = code,
                        displayName = name,
                        sex = s.sex,
                        birthDateMillis = s.birthDateMillis,
                        notes = s.notes.takeIf { it.isNotBlank() },
                        weightKg = weightKg,
                        heightCm = heightCm,
                        tagKeys = s.tagKeys.toList()
                    )
                } else {
                    repo.updatePatient(
                        id = s.patientId!!,
                        internalCode = code,
                        displayName = name,
                        sex = s.sex,
                        birthDateMillis = s.birthDateMillis,
                        notes = s.notes.takeIf { it.isNotBlank() },
                        weightKg = weightKg,
                        heightCm = heightCm,
                        tagKeys = s.tagKeys.toList()
                    ).let { r ->
                        when (r) {
                            is DataResult.Success -> DataResult.Success(s.patientId!!)
                            is DataResult.Failure -> DataResult.Failure(r.error)
                        }
                    }
                }

            when (result) {
                is DataResult.Success -> {
                    _state.update { it.copy(saving = false) }
                    onSuccess()
                }
                is DataResult.Failure -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            error = result.error,
                            codeTaken = (result.error is DataError.DuplicateCode)
                        )
                    }
                }
            }
        }
    }

    private fun formatSmartDouble(v: Double): String {
        val rounded = v.toLong().toDouble()
        return if (abs(v - rounded) < 1e-9) rounded.toLong().toString() else v.toString()
    }
}
