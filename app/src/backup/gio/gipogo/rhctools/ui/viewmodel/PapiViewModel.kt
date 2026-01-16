package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PapiViewModel : ViewModel() {

    enum class ErrorCode { MISSING_INPUTS, RAP_NONPOSITIVE, PASP_LT_PADP }
    enum class NoteCode { HIGH_RISK, LOWER_RISK }

    data class State(
        val pasp: String = "",  // mmHg
        val padp: String = "",  // mmHg
        val rap: String = "",   // mmHg

        val papi: Double? = null,
        val note: NoteCode? = null,
        val error: ErrorCode? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setPASP(v: String) = _state.update { it.copy(pasp = v) }
    fun setPADP(v: String) = _state.update { it.copy(padp = v) }
    fun setRAP(v: String) = _state.update { it.copy(rap = v) }

    fun clear() = _state.update { State() }

    fun calculate() {
        val pasp = Parse.toDoubleOrNull(_state.value.pasp)
        val padp = Parse.toDoubleOrNull(_state.value.padp)
        val rap = Parse.toDoubleOrNull(_state.value.rap)

        if (pasp == null || padp == null || rap == null) {
            _state.update { it.copy(error = ErrorCode.MISSING_INPUTS, papi = null, note = null) }
            return
        }
        if (rap <= 0) {
            _state.update { it.copy(error = ErrorCode.RAP_NONPOSITIVE, papi = null, note = null) }
            return
        }
        if (pasp < padp) {
            _state.update { it.copy(error = ErrorCode.PASP_LT_PADP, papi = null, note = null) }
            return
        }

        val res = HemodynamicsFormulas.papi(pasp, padp, rap)

        val noteCode = if (res.papi < 0.9) NoteCode.HIGH_RISK else NoteCode.LOWER_RISK

        _state.update { it.copy(papi = res.papi, note = noteCode, error = null) }
    }
}
