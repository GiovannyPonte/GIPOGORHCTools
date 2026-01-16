package com.gipogo.rhctools.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
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

    // -------- conversions --------
    private fun kgToLb(kg: Double) = kg / 0.45359237
    private fun lbToKg(lb: Double) = lb * 0.45359237
    private fun cmToIn(cm: Double) = cm / 2.54
    private fun inToCm(`in`: Double) = `in` * 2.54

    private fun toKg(value: Double, unit: WeightUnit) =
        if (unit == WeightUnit.KG) value else lbToKg(value)

    private fun toCm(value: Double, unit: HeightUnit): Double = when (unit) {
        HeightUnit.CM -> value
        HeightUnit.IN -> inToCm(value)
        HeightUnit.M  -> value * 100.0
    }

    private fun bsaMosteller(heightCm: Double, weightKg: Double): Double =
        sqrt((heightCm * weightKg) / 3600.0)

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
            HeightUnit.M  -> HeightUnit.CM
        }

        val converted = if (parsed == null) {
            current
        } else {
            val cm = toCm(parsed, currentUnit)
            val nextValue = when (nextUnit) {
                HeightUnit.CM -> cm
                HeightUnit.IN -> cmToIn(cm)
                HeightUnit.M  -> cm / 100.0
            }
            if (nextUnit == HeightUnit.M) Format.d(nextValue, 2) else Format.d(nextValue, 1)
        }

        _state.update { it.copy(heightUnit = nextUnit, height = converted) }
    }

    fun calculate() {
        // Limpia error previo
        _state.update { it.copy(error = null) }

        val wRaw = Parse.toDoubleOrNull(_state.value.weight)
        val hRaw = Parse.toDoubleOrNull(_state.value.height)
        val sa = Parse.toDoubleOrNull(_state.value.saO2)
        val sv = Parse.toDoubleOrNull(_state.value.svO2)
        val hbRaw = Parse.toDoubleOrNull(_state.value.hb)

        if (wRaw == null || hRaw == null || sa == null || sv == null || hbRaw == null) {
            _state.update {
                it.copy(
                    error = "Faltan datos: peso, talla, SaO₂, SvO₂ y Hb.",
                    cardiacOutputLMin = null,
                    cardiacIndexLMinM2 = null,
                    strokeVolumeMlBeat = null
                )
            }
            return
        }

        // Hb siempre en g/dL para fórmula
        val hb_gDl = when (_state.value.hbUnit) {
            HbUnit.G_DL -> hbRaw
            HbUnit.G_L  -> hbRaw / 10.0
        }

        val wKg = toKg(wRaw, _state.value.weightUnit)
        val hCm = toCm(hRaw, _state.value.heightUnit)

        if (wKg <= 0 || hCm <= 0) {
            _state.update {
                it.copy(
                    error = "Peso y talla deben ser > 0.",
                    cardiacOutputLMin = null,
                    cardiacIndexLMinM2 = null,
                    strokeVolumeMlBeat = null
                )
            }
            return
        }

        val bsa = bsaMosteller(hCm, wKg)

        val paO2 = Parse.toDoubleOrNull(_state.value.paO2)
        val pvO2 = Parse.toDoubleOrNull(_state.value.pvO2)

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
        if (avDiff <= 0) {
            _state.update {
                it.copy(
                    error = "CaO₂ − CvO₂ ≤ 0. Revisa saturaciones/Hb.",
                    cardiacOutputLMin = null,
                    cardiacIndexLMinM2 = null,
                    strokeVolumeMlBeat = null
                )
            }
            return
        }

        val factor = vo2FactorByAge(_state.value.ageGroup)

        val vo2Used = if (_state.value.useMeasuredVo2) {
            val measured = Parse.toDoubleOrNull(_state.value.vo2Measured)
            if (measured == null || measured <= 0) {
                _state.update {
                    it.copy(
                        error = "VO₂ medido debe ser > 0.",
                        cardiacOutputLMin = null,
                        cardiacIndexLMinM2 = null,
                        strokeVolumeMlBeat = null
                    )
                }
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

        // ✅ Actualiza UI state (éxito)
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

        // ✅ Guardar en ReportStore SOLO si fue exitoso (aquí siempre lo es)
        val now = System.currentTimeMillis()

        val weightUnitText = if (_state.value.weightUnit == WeightUnit.KG) "kg" else "lb"
        val heightUnitText = when (_state.value.heightUnit) {
            HeightUnit.CM -> "cm"
            HeightUnit.IN -> "in"
            HeightUnit.M  -> "m"
        }
        val hbUnitText = when (_state.value.hbUnit) {
            HbUnit.G_DL -> "g/dL"
            HbUnit.G_L  -> "g/L"
        }

        val ageText = if (_state.value.ageGroup == AgeGroup.LT70) "< 70" else "≥ 70"

        val inputs = buildList {
            add(LineItem(label = "Peso", value = Format.d(wRaw, 1), unit = weightUnitText))
            add(LineItem(label = "Altura", value = Format.d(hRaw, 1), unit = heightUnitText))
            add(LineItem(label = "SaO₂", value = Format.d(sa, 0), unit = "%"))
            add(LineItem(label = "SvO₂", value = Format.d(sv, 0), unit = "%"))
            add(LineItem(label = "Hemoglobina", value = Format.d(hbRaw, 1), unit = hbUnitText))
            if (!state.value.heartRate.isBlank()) {
                hr?.let { add(LineItem(label = "Frecuencia cardíaca", value = Format.d(it, 0), unit = "bpm")) }
            }
            add(LineItem(label = "Edad", value = ageText, unit = "años"))
            add(
                LineItem(
                    key = SharedKeys.BSA_M2,
                    label = "BSA",
                    value = Format.d(bsa, 2),
                    unit = "m²",
                    detail = "Body Surface Area"
                )
            )

            add(
                if (_state.value.useMeasuredVo2) {
                    LineItem(label = "VO₂", value = Format.d(vo2Used, 0), unit = "mL/min", detail = "Medido")
                } else {
                    LineItem(label = "VO₂", value = Format.d(vo2Used, 0), unit = "mL/min", detail = "Estimado")
                }
            )
        }

        val outputs = buildList {
            add(
                LineItem(
                    key = SharedKeys.CO_LMIN,
                    label = "CO",
                    value = Format.d(co, 2),
                    unit = "L/min",
                    detail = "Cardiac Output"
                )
            )

            add(LineItem(label = "CI", value = Format.d(ci, 2), unit = "L/min/m²", detail = "Cardiac Index"))
            svMlBeat?.let { add(LineItem(label = "SV", value = Format.d(it, 0), unit = "mL", detail = "Stroke Volume")) }

            add(LineItem(label = "CaO₂", value = Format.d(ca, 2), unit = "mL/dL"))
            add(LineItem(label = "CvO₂", value = Format.d(cv, 2), unit = "mL/dL"))
            add(LineItem(label = "ΔA-V O₂", value = Format.d(avDiff, 2), unit = "mL/dL", detail = "CaO₂ − CvO₂"))
        }

        val entry = CalcEntry(
            type = CalcType.FICK,
            timestampMillis = now,
            title = "Fick: Gasto Cardíaco",
            inputs = inputs,
            outputs = outputs,
            notes = emptyList()
        )

        ReportStore.upsert(entry)
    }
}
