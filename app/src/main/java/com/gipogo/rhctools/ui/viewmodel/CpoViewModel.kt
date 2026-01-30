package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CpoViewModel : ViewModel() {

    enum class MapUnit { MMHG, KPA }
    enum class CoUnit { L_MIN, L_SEC }

    data class Result(
        val cpoWatts: Double,
        val cpiWattsPerM2: Double?
    )

    data class State(
        val map: String = "",
        val mapUnit: MapUnit = MapUnit.MMHG,

        val co: String = "",
        val coUnit: CoUnit = CoUnit.L_MIN,

        val bsa: String = "",

        val result: Result? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setMAP(v: String) = _state.update { it.copy(map = v) }
    fun setCO(v: String) = _state.update { it.copy(co = v) }
    fun setBSA(v: String) = _state.update { it.copy(bsa = v) }

    fun toggleMapUnit() = _state.update {
        it.copy(mapUnit = if (it.mapUnit == MapUnit.MMHG) MapUnit.KPA else MapUnit.MMHG)
    }

    fun toggleCoUnit() = _state.update {
        it.copy(coUnit = if (it.coUnit == CoUnit.L_MIN) CoUnit.L_SEC else CoUnit.L_MIN)
    }

    fun clear() = _state.update { State() }

    private fun fail(msg: String) {
        _state.update { it.copy(result = null, error = msg) }
    }

    fun calculate() {
        val mapRaw = Parse.toDoubleOrNull(_state.value.map)
        val coRaw = Parse.toDoubleOrNull(_state.value.co)
        val bsaRaw = Parse.toDoubleOrNull(_state.value.bsa)

        if (mapRaw == null || coRaw == null) {
            fail("MAP y CO son obligatorios.")
            return
        }

        val mapMmHg = when (_state.value.mapUnit) {
            MapUnit.MMHG -> mapRaw
            // A.6.2: 1 kPa = 7.50062 mmHg

            MapUnit.KPA -> mapRaw * 7.50062
        }

        val coLMin = when (_state.value.coUnit) {
            CoUnit.L_MIN -> coRaw
            CoUnit.L_SEC -> coRaw * 60.0
        }

        if (mapMmHg <= 0.0 || coLMin <= 0.0) {
            fail("MAP y CO deben ser > 0.")
            return
        }

        val cpoRes = HemodynamicsFormulas.cardiacPowerOutput(
            map_mmHg = mapMmHg,
            cardiacOutput_LMin = coLMin,
            bsa_m2 = bsaRaw
        )

        val cpo = cpoRes.cpoWatts
        if (!cpo.isFinite()) {
            fail("No se pudo calcular CPO.")
            return
        }

        val cpi = cpoRes.cpiWattsPerM2?.takeIf { it.isFinite() }

        _state.update {
            it.copy(
                result = Result(cpoWatts = cpo, cpiWattsPerM2 = cpi),
                error = null
            )
        }
    }
}
