package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PvrViewModel : ViewModel() {

    enum class OutputUnits { WOOD_UNITS, DYNES }

    enum class ErrorCode {
        MISSING_INPUTS,
        CO_NONPOSITIVE,
        GRADIENT_NONPOSITIVE
    }

    data class State(
        // Inputs
        val mpap: String = "",
        val pawp: String = "",
        val co: String = "", // Qp ~ CO (sin shunt)

        // Units
        val outputUnits: OutputUnits = OutputUnits.WOOD_UNITS,

        // Outputs (PVR)
        val pvrWu: Double? = null,
        val pvrDynes: Double? = null,

        // Outputs (TPR)
        val tprWu: Double? = null,
        val tprDynes: Double? = null,

        // Error (code, not text)
        val error: ErrorCode? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setMPAP(v: String) = _state.update { it.copy(mpap = v) }
    fun setPAWP(v: String) = _state.update { it.copy(pawp = v) }
    fun setCO(v: String) = _state.update { it.copy(co = v) }

    fun setOutputUnits(v: OutputUnits) = _state.update { it.copy(outputUnits = v) }

    fun clear() = _state.update { State() }

    fun calculate() {
        _state.update { it.copy(error = null) }

        val mpap = Parse.toDoubleOrNull(_state.value.mpap)
        val pawp = Parse.toDoubleOrNull(_state.value.pawp)
        val co = Parse.toDoubleOrNull(_state.value.co)

        if (mpap == null || pawp == null || co == null) {
            _state.update {
                it.copy(
                    pvrWu = null, pvrDynes = null,
                    tprWu = null, tprDynes = null,
                    error = ErrorCode.MISSING_INPUTS
                )
            }
            return
        }

        if (co <= 0.0) {
            _state.update {
                it.copy(
                    pvrWu = null, pvrDynes = null,
                    tprWu = null, tprDynes = null,
                    error = ErrorCode.CO_NONPOSITIVE
                )
            }
            return
        }

        // ✅ TPR siempre se puede calcular con mPAP y CO
        val tprWu = mpap / co
        val tprDynes = tprWu * 80.0

        val gradient = mpap - pawp
        if (gradient <= 0.0) {
            // PVR no aplica si el gradiente es <= 0, pero TPR sí es útil
            _state.update {
                it.copy(
                    pvrWu = null, pvrDynes = null,
                    tprWu = tprWu, tprDynes = tprDynes,
                    error = ErrorCode.GRADIENT_NONPOSITIVE
                )
            }
            return
        }

        // ✅ PVR (WU) = (mPAP - PAWP) / CO
        val pvrWu = gradient / co
        val pvrDynes = pvrWu * 80.0

        _state.update {
            it.copy(
                pvrWu = pvrWu,
                pvrDynes = pvrDynes,
                tprWu = tprWu,
                tprDynes = tprDynes,
                error = null
            )
        }
    }
}
