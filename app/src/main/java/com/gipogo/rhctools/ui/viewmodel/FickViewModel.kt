package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.util.Format
import com.gipogo.rhctools.util.Parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

class FickViewModel : ViewModel() {

    enum class WeightUnit { KG, LB }
    enum class AgeGroup { LT70, GE70 }
    enum class HeightUnit { CM, IN, M }
    enum class HbUnit { G_DL, G_L }

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
        val hbUnit: HbUnit = HbUnit.G_DL,

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

    fun toggleHbUnit() {
        val current = state.value.hb
        val unit = state.value.hbUnit
        val parsed = current.toDoubleOrNull()

        val nextUnit = if (unit == HbUnit.G_DL) HbUnit.G_L else HbUnit.G_DL

        val converted = if (parsed == null) {
            current
        } else {
            val nextValue = if (unit == HbUnit.G_DL) parsed * 10.0 else parsed / 10.0
            Format.d(nextValue, 1)
        }

        _state.update { it.copy(hbUnit = nextUnit, hb = converted) }
    }

    fun toggleHeightUnit() {
        val current = state.value.height
        val currentUnit = state.value.heightUnit

        val parsed = current.toDoubleOrNull()
        val nextUnit = when (currentUnit) {
            HeightUnit.CM -> HeightUnit.IN
            HeightUnit.IN -> HeightUnit.M
            HeightUnit.M -> HeightUnit.CM
        }

        val converted = if (parsed == null) {
            current
        } else {
            val cm = toCm(parsed, currentUnit)
            val nextValue = when (nextUnit) {
                HeightUnit.CM -> cm
                HeightUnit.IN -> cmToIn(cm)
                HeightUnit.M -> cm / 100.0
            }
            if (nextUnit == HeightUnit.M) Format.d(nextValue, 2) else Format.d(nextValue, 1)
        }

        _state.update { it.copy(heightUnit = nextUnit, height = converted) }
    }

    private fun fail(msg: String) {
        _state.update {
            it.copy(
                error = msg,

                cardiacOutputLMin = null,
                cardiacIndexLMinM2 = null,
                strokeVolumeMlBeat = null,

                bsa = null,
                vo2UsedMlMin = null,
                vo2FactorUsedMlMinM2 = null,
                caO2_mlDl = null,
                cvO2_mlDl = null,
                avDiff_mlDl = null
            )
        }
    }

    // -------- conversions --------
    private fun kgToLb(kg: Double) = kg / 0.45359237
    private fun lbToKg(lb: Double) = lb * 0.45359237
    private fun cmToIn(cm: Double) = cm / 2.54
    private fun inToCm(`in`: Double) = `in` * 2.54

    private fun toKg(value: Double, unit: WeightUnit) =
        if (unit == WeightUnit.KG) value else lbToKg(value)

    private fun toCm(value: Double, unit: HeightUnit) =
        when (unit) {
            HeightUnit.CM -> value
            HeightUnit.IN -> inToCm(value)
            HeightUnit.M -> value * 100.0
        }



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

    fun calculate() {
        val wRaw = Parse.toDoubleOrNull(_state.value.weight)
        val hRaw = Parse.toDoubleOrNull(_state.value.height)
        val sa = Parse.toDoubleOrNull(_state.value.saO2)
        val sv = Parse.toDoubleOrNull(_state.value.svO2)
        val hbRaw = Parse.toDoubleOrNull(_state.value.hb)

        if (wRaw == null || hRaw == null || sa == null || sv == null || hbRaw == null) {
            fail("Faltan datos: peso, talla, SaO₂, SvO₂ y Hb.")
            return
        }

        // Validaciones fisiológicas mínimas (A.1.2)
        if (sa !in 0.0..100.0) {
            fail("SaO₂ debe estar entre 0 y 100%.")
            return
        }
        if (sv !in 0.0..100.0) {
            fail("SvO₂ debe estar entre 0 y 100%.")
            return
        }

        val wKg = toKg(wRaw, _state.value.weightUnit)
        val hCm = toCm(hRaw, _state.value.heightUnit)

        if (wKg <= 0.0 || hCm <= 0.0) {
            fail("Peso y talla deben ser > 0.")
            return
        }

        // Hb: convertir a g/dL si el usuario está en g/L
        val hb_gDl = when (_state.value.hbUnit) {
            HbUnit.G_DL -> hbRaw
            HbUnit.G_L -> hbRaw / 10.0
        }
        if (hb_gDl <= 0.0) {
            fail("Hb debe ser > 0.")
            return
        }

        val paO2 = Parse.toDoubleOrNull(_state.value.paO2)
        val pvO2 = Parse.toDoubleOrNull(_state.value.pvO2)

        if (_state.value.includeDissolved) {
            if (paO2 != null && paO2 < 0.0) {
                fail("PaO₂ no puede ser negativa.")
                return
            }
            if (pvO2 != null && pvO2 < 0.0) {
                fail("PvO₂ no puede ser negativa.")
                return
            }
        }

        val bsa = HemodynamicsFormulas.bsaMosteller(hCm, wKg)

        val ca = HemodynamicsFormulas.oxygenContentMlPerDl(
            hb_gDl = hb_gDl,
            sat_percent = sa,
            po2_mmHg = paO2,
            includeDissolved = _state.value.includeDissolved
        )

        val cv = HemodynamicsFormulas.oxygenContentMlPerDl(
            hb_gDl = hb_gDl,
            sat_percent = sv,
            po2_mmHg = pvO2,
            includeDissolved = _state.value.includeDissolved
        )

        val avDiff = ca - cv
        if (avDiff <= 0.0 || !avDiff.isFinite()) {
            fail("CaO₂ − CvO₂ ≤ 0. Revisa saturaciones/Hb.")
            return
        }

        val factor = vo2FactorByAge(_state.value.ageGroup)

        val vo2Used = if (_state.value.useMeasuredVo2) {
            val measured = Parse.toDoubleOrNull(_state.value.vo2Measured)
            if (measured == null) {
                fail("VO₂ medido no es un número válido.")
                return
            }
            if (measured <= 0.0) {
                fail("VO₂ medido debe ser > 0.")
                return
            }
            measured
        } else {
            val est = HemodynamicsFormulas.estimatedVo2MlMin(bsa_m2 = bsa, factor_mlMinM2 = factor)
            if (!est.isFinite() || est <= 0.0) {
                fail("VO₂ estimado no es válido.")
                return
            }
            est
        }

        val co = vo2Used / (avDiff * 10.0)
        if (!co.isFinite() || co <= 0.0) {
            fail("No se puede calcular CO: datos no fisiológicos o inválidos.")
            return
        }
        val ci = co / bsa

        val hr = Parse.toDoubleOrNull(_state.value.heartRate)
        val svMlBeat = if (hr != null && hr > 0.0) (co * 1000.0) / hr else null

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
