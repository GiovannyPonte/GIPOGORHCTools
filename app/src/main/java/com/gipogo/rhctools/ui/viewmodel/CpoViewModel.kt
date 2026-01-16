package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.CpoResult
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CpoViewModel : ViewModel() {

    enum class MapUnit { MMHG, KPA }
    enum class CoUnit { L_MIN, L_SEC }

    data class State(
        val map: String = "",
        val mapUnit: MapUnit = MapUnit.MMHG,

        val co: String = "",
        val coUnit: CoUnit = CoUnit.L_MIN,

        val bsa: String = "",

        val result: CpoResult? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setMAP(v: String) = _state.update { it.copy(map = v) }
    fun setCO(v: String) = _state.update { it.copy(co = v) }
    fun setBSA(v: String) = _state.update { it.copy(bsa = v) }

    private fun mmHgToKpa(mmHg: Double) = mmHg * 0.133322
    private fun kpaToMmHg(kPa: Double) = kPa * 7.50062

    private fun lMinToLSec(lMin: Double) = lMin / 60.0
    private fun lSecToLMin(lSec: Double) = lSec * 60.0

    fun toggleMapUnit() = _state.update { s ->
        val current = Parse.toDoubleOrNull(s.map)
        val newUnit = if (s.mapUnit == MapUnit.MMHG) MapUnit.KPA else MapUnit.MMHG

        if (current == null) {
            s.copy(mapUnit = newUnit)
        } else {
            val newValue = if (newUnit == MapUnit.KPA) mmHgToKpa(current) else kpaToMmHg(current)
            s.copy(mapUnit = newUnit, map = "%.1f".format(newValue))
        }
    }

    fun toggleCoUnit() = _state.update { s ->
        val current = Parse.toDoubleOrNull(s.co)
        val newUnit = if (s.coUnit == CoUnit.L_MIN) CoUnit.L_SEC else CoUnit.L_MIN

        if (current == null) {
            s.copy(coUnit = newUnit)
        } else {
            val newValue = if (newUnit == CoUnit.L_SEC) lMinToLSec(current) else lSecToLMin(current)
            s.copy(coUnit = newUnit, co = "%.2f".format(newValue))
        }
    }

    fun calculate() {
        val mapRaw = Parse.toDoubleOrNull(_state.value.map)
        val coRaw = Parse.toDoubleOrNull(_state.value.co)
        val bsa = Parse.toDoubleOrNull(_state.value.bsa)

        if (mapRaw == null || coRaw == null) {
            _state.update { it.copy(error = "MAP y CO son obligatorios.", result = null) }
            return
        }

        val mapMmHg = if (_state.value.mapUnit == MapUnit.MMHG) mapRaw else kpaToMmHg(mapRaw)
        val coLMin = if (_state.value.coUnit == CoUnit.L_MIN) coRaw else lSecToLMin(coRaw)

        if (coLMin <= 0) {
            _state.update { it.copy(error = "CO debe ser > 0.", result = null) }
            return
        }

        val res = HemodynamicsFormulas.cardiacPowerOutput(mapMmHg, coLMin, bsa)
        _state.update { it.copy(result = res, error = null) }
    }

    /**
     * ✅ Reset TOTAL:
     * - borra inputs
     * - reinicia unidades
     * - borra result + error
     */
    fun clear() = _state.update {
        it.copy(
            map = "",
            mapUnit = MapUnit.MMHG,
            co = "",
            coUnit = CoUnit.L_MIN,
            bsa = "",
            result = null,
            error = null
        )
    }
}
