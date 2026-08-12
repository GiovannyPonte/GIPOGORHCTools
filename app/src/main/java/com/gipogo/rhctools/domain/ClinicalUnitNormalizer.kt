package com.gipogo.rhctools.domain

/**
 * Boundary between user-facing units and the clinical calculation domain.
 *
 * Every calculation and database write must use these canonical units:
 * kg, cm, g/dL, mmHg, L/min, m², mL/min, bpm, percent, W and Wood units.
 * Presentation code may convert canonical values back to any supported unit.
 */
object ClinicalUnitNormalizer {
    enum class WeightUnit { KG, LB }
    enum class HeightUnit { CM, M, IN }
    enum class HemoglobinUnit { G_DL, G_L }
    enum class PressureUnit { MMHG, KPA }
    enum class CardiacOutputUnit { L_MIN, L_SEC }
    enum class ResistanceUnit { WOOD, DYN_S_CM5 }

    private const val LB_TO_KG = 0.45359237
    private const val IN_TO_CM = 2.54
    private const val KPA_TO_MMHG = 7.50062
    private const val DYN_PER_WOOD = 80.0

    fun weightToKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value * LB_TO_KG
    }

    fun weightFromKg(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg / LB_TO_KG
    }

    fun heightToCm(value: Double, unit: HeightUnit): Double = when (unit) {
        HeightUnit.CM -> value
        HeightUnit.M -> value * 100.0
        HeightUnit.IN -> value * IN_TO_CM
    }

    fun heightFromCm(cm: Double, unit: HeightUnit): Double = when (unit) {
        HeightUnit.CM -> cm
        HeightUnit.M -> cm / 100.0
        HeightUnit.IN -> cm / IN_TO_CM
    }

    fun hemoglobinToGdl(value: Double, unit: HemoglobinUnit): Double = when (unit) {
        HemoglobinUnit.G_DL -> value
        HemoglobinUnit.G_L -> value / 10.0
    }

    fun hemoglobinFromGdl(gdl: Double, unit: HemoglobinUnit): Double = when (unit) {
        HemoglobinUnit.G_DL -> gdl
        HemoglobinUnit.G_L -> gdl * 10.0
    }

    fun pressureToMmHg(value: Double, unit: PressureUnit): Double = when (unit) {
        PressureUnit.MMHG -> value
        PressureUnit.KPA -> value * KPA_TO_MMHG
    }

    fun pressureFromMmHg(mmHg: Double, unit: PressureUnit): Double = when (unit) {
        PressureUnit.MMHG -> mmHg
        PressureUnit.KPA -> mmHg / KPA_TO_MMHG
    }

    fun cardiacOutputToLMin(value: Double, unit: CardiacOutputUnit): Double = when (unit) {
        CardiacOutputUnit.L_MIN -> value
        CardiacOutputUnit.L_SEC -> value * 60.0
    }

    fun cardiacOutputFromLMin(lMin: Double, unit: CardiacOutputUnit): Double = when (unit) {
        CardiacOutputUnit.L_MIN -> lMin
        CardiacOutputUnit.L_SEC -> lMin / 60.0
    }

    fun resistanceToWood(value: Double, unit: ResistanceUnit): Double = when (unit) {
        ResistanceUnit.WOOD -> value
        ResistanceUnit.DYN_S_CM5 -> value / DYN_PER_WOOD
    }

    fun resistanceFromWood(wood: Double, unit: ResistanceUnit): Double = when (unit) {
        ResistanceUnit.WOOD -> wood
        ResistanceUnit.DYN_S_CM5 -> wood * DYN_PER_WOOD
    }
}
