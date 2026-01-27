package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gipogo.rhctools.data.db.dao.StudyWithRhcData
import com.gipogo.rhctools.data.studies.StudiesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed class StudyDetailUiState {
    data object Loading : StudyDetailUiState()

    data class Content(
        val patientId: String,
        val studyId: String,
        val studyWithRhc: StudyWithRhcData?
    ) : StudyDetailUiState()
}

class StudyDetailViewModel(
    private val patientId: String,
    private val studyId: String,
    private val repo: StudiesRepository
) : ViewModel() {

    val uiState: StateFlow<StudyDetailUiState> =
        repo.observeStudyWithRhcData(patientId = patientId, studyId = studyId)
            .map { sw ->
                StudyDetailUiState.Content(
                    patientId = patientId,
                    studyId = studyId,
                    studyWithRhc = sw
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StudyDetailUiState.Loading
            )

    class Factory(
        private val patientId: String,
        private val studyId: String,
        private val repo: StudiesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudyDetailViewModel(patientId, studyId, repo) as T
        }
    }
}

