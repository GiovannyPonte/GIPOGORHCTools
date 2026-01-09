package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PapiViewModel : ViewModel() {

    data class State(
        val pasp: String = "",  // mmHg
        val padp: String = "",  // mmHg
        val rap: String = "",   // mmHg

        val papi: Double? = null,
        val note: String? = null,
        val error: String? = null
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
            _state.update { it.copy(error = "PASP, PADP y RAP son obligatorios.", papi = null, note = null) }
            return
        }
        if (rap <= 0) {
            _state.update { it.copy(error = "RAP debe ser > 0.", papi = null, note = null) }
            return
        }
        if (pasp < padp) {
            _state.update { it.copy(error = "PASP no puede ser menor que PADP. Revisa los valores.", papi = null, note = null) }
            return
        }

        val res = HemodynamicsFormulas.papi(pasp, padp, rap)

        val note = if (res.papi < 0.9) {
            "PAPi < 0.9 sugiere mayor riesgo de disfunción del ventrículo derecho (según herramienta de referencia)."
        } else {
            "PAPi ≥ 0.9: sin criterio de alto riesgo por este umbral (interpretar en contexto clínico)."
        }

        _state.update { it.copy(papi = res.papi, note = note, error = null) }
    }
}
