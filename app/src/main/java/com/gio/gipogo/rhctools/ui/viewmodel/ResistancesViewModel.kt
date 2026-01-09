package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class ResistancesViewModel : ViewModel() {

    enum class OutputUnits { WOOD_UNITS, DYNES }

    data class State(
        // Inputs (exactly as QxMD)
        val map: String = "",        // mmHg
        val cvp: String = "",        // mmHg
        val co: String = "",         // L/min
        val outputUnits: OutputUnits = OutputUnits.WOOD_UNITS,

        // Outputs
        val svrWu: Double? = null,
        val svrDynes: Double? = null,

        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setMAP(v: String) = _state.update { it.copy(map = v) }
    fun setCVP(v: String) = _state.update { it.copy(cvp = v) }
    fun setCO(v: String) = _state.update { it.copy(co = v) }

    fun setOutputUnits(v: OutputUnits) = _state.update { it.copy(outputUnits = v) }

    fun clear() = _state.update { State() }

    fun calculate() {
        val map = Parse.toDoubleOrNull(_state.value.map)
        val cvp = Parse.toDoubleOrNull(_state.value.cvp)
        val co = Parse.toDoubleOrNull(_state.value.co)

        if (map == null || cvp == null || co == null) {
            _state.update { it.copy(error = "MAP, CVP y CO son obligatorios.", svrWu = null, svrDynes = null) }
            return
        }
        if (co <= 0) {
            _state.update { it.copy(error = "CO debe ser > 0.", svrWu = null, svrDynes = null) }
            return
        }

        // SVR (Wood Units) = (MAP - CVP) / CO
        val wu = (map - cvp) / co
        val dynes = wu * 80.0

        _state.update {
            it.copy(
                svrWu = wu,
                svrDynes = dynes,
                error = null
            )
        }
    }
}
