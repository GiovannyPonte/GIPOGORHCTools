package com.gipogo.rhctools.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.patients.PatientsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PatientRowUi(
    val patient: com.gipogo.rhctools.data.db.entities.PatientEntity,
    val lastStudyAtMillis: Long?
)

enum class PatientsDatePreset {
    NONE,
    LAST_7_DAYS
}

data class PatientsUiState(
    val searchActive: Boolean = false,
    val query: String = "",
    val selectedTagKeys: Set<String> = emptySet(),
    val datePreset: PatientsDatePreset = PatientsDatePreset.NONE,
    val items: List<PatientRowUi> = emptyList()
)

class PatientsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PatientsRepository.get(app)

    private val searchActiveFlow = MutableStateFlow(false)
    private val queryFlow = MutableStateFlow("")
    private val selectedTagKeysFlow = MutableStateFlow<Set<String>>(emptySet())
    private val datePresetFlow = MutableStateFlow(PatientsDatePreset.NONE)

    private fun fromMillisForPreset(preset: PatientsDatePreset, nowMillis: Long): Long? =
        when (preset) {
            PatientsDatePreset.NONE -> null
            PatientsDatePreset.LAST_7_DAYS -> nowMillis - (7L * 24L * 60L * 60L * 1000L)
        }

    private val filteredRowsFlow: Flow<List<PatientDao.PatientWithLastStudyRow>> =
        combine(
            queryFlow.debounce(250).map { it.trim() }.distinctUntilChanged(),
            selectedTagKeysFlow,
            datePresetFlow
        ) { q, tagKeys, preset ->
            Triple(q, tagKeys, preset)
        }.flatMapLatest { (q, tagKeys, preset) ->
            val now = System.currentTimeMillis()
            val from = fromMillisForPreset(preset, now)
            repo.observePatientsFiltered(
                query = q.takeIf { it.isNotBlank() },
                tagKeys = tagKeys,
                fromMillis = from
            )
        }

    val state: StateFlow<PatientsUiState> =
        combine(
            searchActiveFlow,
            queryFlow,
            selectedTagKeysFlow,
            datePresetFlow,
            filteredRowsFlow
        ) { searchActive, query, tagKeys, datePreset, rows ->
            PatientsUiState(
                searchActive = searchActive,
                query = query,
                selectedTagKeys = tagKeys,
                datePreset = datePreset,
                items = rows.map { r -> PatientRowUi(r.patient, r.lastStudyAtMillis) }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientsUiState())

    fun toggleSearchActive() {
        searchActiveFlow.value = !searchActiveFlow.value
    }

    fun setQuery(q: String) {
        queryFlow.value = q
    }

    fun toggleLast7Days() {
        datePresetFlow.value =
            if (datePresetFlow.value == PatientsDatePreset.LAST_7_DAYS) PatientsDatePreset.NONE
            else PatientsDatePreset.LAST_7_DAYS
    }

    fun toggleTag(tagKey: String) {
        val cur = selectedTagKeysFlow.value
        selectedTagKeysFlow.value = if (cur.contains(tagKey)) cur - tagKey else cur + tagKey
    }

    fun clearTags() {
        selectedTagKeysFlow.value = emptySet()
    }

    fun deletePatient(id: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            when (repo.deletePatient(id)) {
                is DataResult.Success -> onDone?.invoke()
                is DataResult.Failure -> Unit
            }
        }
    }
}
