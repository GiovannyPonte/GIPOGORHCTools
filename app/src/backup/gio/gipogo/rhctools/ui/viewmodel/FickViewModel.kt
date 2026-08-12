package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

class FickViewModel : ViewModel() {

    enum class WeightUnit { KG, LB }
    enum class HeightUnit { CM, IN }
    enum class AgeGroup { LT70, GE70 }

    // Técnica tipo MDCalc: VO2 estimado por edad (observación por tus resultados)
    // <70 -> 125 mL/min/m²
    // ≥70 -> 112 mL/min/m²
    private fun vo2FactorByAge(age: AgeGroup): Double =
        when (age) {
            AgeGroup.LT70 -> 125.0
            AgeGroup.GE70 -> 112.0
        }

    data class State(
        val weight: String = "",
        val weightUnit: WeightUnit = WeightUnit.KG,

        val height: String = "",
        val heightUnit: HeightUnit = HeightUnit.CM,

        val saO2: String = "",
        val svO2: String = "",
        val hb: String = "",

        val heartRate: String = "",
        val ageGroup: AgeGroup = AgeGroup.LT70,

        // Advanced
        val showAdvanced: Boolean = false,
        val includeDissolved: Boolean = false,
        val paO2: String = "",
        val pvO2: String = "",

        val useMeasuredVo2: Boolean = false,
        val vo2Measured: String = "", // mL/min

        // Derived + output
        val bsa: Double? = null,
        val vo2UsedMlMin: Double? = null,
        val vo2FactorUsedMlMinM2: Double? = null,

        val cardiacOutputLMin: Double? = null,
        val cardiacIndexLMinM2: Double? = null,
        val strokeVolumeMlBeat: Double? = null,

        val caO2_mlDl: Double? = null,
        val cvO2_mlDl: Double? = null,
        val avDiff_mlDl: Double? = null,

        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    // -------- setters --------
    fun setWeight(v: String) = _state.update { it.copy(weight = v) }
    fun setHeight(v: String) = _state.update { it.copy(height = v) }

    fun setSaO2(v: String) = _state.update { it.copy(saO2 = v) }
    fun setSvO2(v: String) = _state.update { it.copy(svO2 = v) }
    fun setHb(v: String) = _state.update { it.copy(hb = v) }

    fun setHeartRate(v: String) = _state.update { it.copy(heartRate = v) }
    fun setAgeGroup(v: AgeGroup) = _state.update { it.copy(ageGroup = v) }

    fun setShowAdvanced(v: Boolean) = _state.update { it.copy(showAdvanced = v) }
    fun setIncludeDissolved(v: Boolean) = _state.update { it.copy(includeDissolved = v) }
    fun setPaO2(v: String) = _state.update { it.copy(paO2 = v) }
    fun setPvO2(v: String) = _state.update { it.copy(pvO2 = v) }

    fun setUseMeasuredVo2(v: Boolean) = _state.update { it.copy(useMeasuredVo2 = v) }
    fun setVo2Measured(v: String) = _state.update { it.copy(vo2Measured = v) }

    fun clear() = _state.update { State() }

    // -------- conversions --------
    private fun kgToLb(kg: Double) = kg / 0.45359237
    private fun lbToKg(lb: Double) = lb * 0.45359237
    private fun cmToIn(cm: Double) = cm / 2.54
    private fun inToCm(`in`: Double) = `in` * 2.54

    private fun toKg(value: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) value else lbToKg(value)
    private fun toCm(value: Double, unit: HeightUnit) = if (unit == HeightUnit.CM) value else inToCm(value)

    private fun bsaMosteller(heightCm: Double, weightKg: Double): Double =
        sqrt((heightCm * weightKg) / 3600.0)

    // Importante: al cambiar unidad, convertir también el número (como MDCalc)
    fun toggleWeightUnit() = _state.update { s ->
        val current = Parse.toDoubleOrNull(s.weight)
        if (current == null) {
            s.copy(weightUnit = if (s.weightUnit == WeightUnit.KG) WeightUnit.LB else WeightUnit.KG)
        } else {
            val newUnit = if (s.weightUnit == WeightUnit.KG) WeightUnit.LB else WeightUnit.KG
            val newValue = if (newUnit == WeightUnit.LB) kgToLb(current) else lbToKg(current)
            s.copy(weightUnit = newUnit, weight = "%.1f".format(newValue))
        }
    }

    fun toggleHeightUnit() = _state.update { s ->
        val current = Parse.toDoubleOrNull(s.height)
        if (current == null) {
            s.copy(heightUnit = if (s.heightUnit == HeightUnit.CM) HeightUnit.IN else HeightUnit.CM)
        } else {
            val newUnit = if (s.heightUnit == HeightUnit.CM) HeightUnit.IN else HeightUnit.CM
            val newValue = if (newUnit == HeightUnit.IN) cmToIn(current) else inToCm(current)
            s.copy(heightUnit = newUnit, height = "%.1f".format(newValue))
        }
    }

    fun calculate() {
        val wRaw = Parse.toDoubleOrNull(_state.value.weight)
        val hRaw = Parse.toDoubleOrNull(_state.value.height)
        val sa = Parse.toDoubleOrNull(_state.value.saO2)
        val sv = Parse.toDoubleOrNull(_state.value.svO2)
        val hb = Parse.toDoubleOrNull(_state.value.hb)

        if (wRaw == null || hRaw == null || sa == null || sv == null || hb == null) {
            _state.update { it.copy(error = "Faltan datos: peso, talla, SaO₂, SvO₂ y Hb.", cardiacOutputLMin = null) }
            return
        }

        val wKg = toKg(wRaw, _state.value.weightUnit)
        val hCm = toCm(hRaw, _state.value.heightUnit)

        if (wKg <= 0 || hCm <= 0) {
            _state.update { it.copy(error = "Peso y talla deben ser > 0.", cardiacOutputLMin = null) }
            return
        }

        val bsa = bsaMosteller(hCm, wKg)

        val paO2 = Parse.toDoubleOrNull(_state.value.paO2)
        val pvO2 = Parse.toDoubleOrNull(_state.value.pvO2)

        val ca = HemodynamicsFormulas.oxygenContentMlPerDl(
            hb_gDl = hb,
            sat_percent = sa,
            po2_mmHg = paO2,
            includeDissolved = _state.value.includeDissolved
        )

        val cv = HemodynamicsFormulas.oxygenContentMlPerDl(
            hb_gDl = hb,
            sat_percent = sv,
            po2_mmHg = pvO2,
            includeDissolved = _state.value.includeDissolved
        )

        val avDiff = ca - cv
        if (avDiff <= 0) {
            _state.update { it.copy(error = "CaO₂ − CvO₂ ≤ 0. Revisa saturaciones/Hb.", cardiacOutputLMin = null) }
            return
        }

        val factor = vo2FactorByAge(_state.value.ageGroup)

        val vo2Used = if (_state.value.useMeasuredVo2) {
            val measured = Parse.toDoubleOrNull(_state.value.vo2Measured)
            if (measured == null || measured <= 0) {
                _state.update { it.copy(error = "VO₂ medido debe ser > 0.", cardiacOutputLMin = null) }
                return
            }
            measured
        } else {
            HemodynamicsFormulas.estimatedVo2MlMin(bsa_m2 = bsa, factor_mlMinM2 = factor)
        }

        // CO = VO2 / (AVdiff * 10)
        val co = vo2Used / (avDiff * 10.0)
        val ci = co / bsa

        val hr = Parse.toDoubleOrNull(_state.value.heartRate)
        val svMlBeat = if (hr != null && hr > 0) (co * 1000.0) / hr else null

        _state.update {
            it.copy(
                bsa = bsa,
                vo2UsedMlMin = vo2Used,
                vo2FactorUsedMlMinM2 = if (_state.value.useMeasuredVo2) null else factor,

                cardiacOutputLMin = co,
                cardiacIndexLMinM2 = ci,
                strokeVolumeMlBeat = svMlBeat,

                caO2_mlDl = ca,
                cvO2_mlDl = cv,
                avDiff_mlDl = avDiff,
                error = null
            )
        }
    }
}
