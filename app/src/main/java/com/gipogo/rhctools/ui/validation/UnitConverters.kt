package com.gipogo.rhctools.ui.validation

object UnitConverters {

    fun lbToKg(lb: Double): Double = lb * 0.45359237
    fun kgToLb(kg: Double): Double = kg / 0.45359237

    fun inToCm(inches: Double): Double = inches * 2.54
    fun mToCm(meters: Double): Double = meters * 100.0

    fun gLToGdL(gL: Double): Double = gL / 10.0
}

