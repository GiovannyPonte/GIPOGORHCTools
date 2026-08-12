package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.domain.ClinicalUnitNormalizer

object UnitConverters {

    fun lbToKg(lb: Double): Double = ClinicalUnitNormalizer.weightToKg(lb, ClinicalUnitNormalizer.WeightUnit.LB)
    fun kgToLb(kg: Double): Double = ClinicalUnitNormalizer.weightFromKg(kg, ClinicalUnitNormalizer.WeightUnit.LB)

    fun inToCm(inches: Double): Double = ClinicalUnitNormalizer.heightToCm(inches, ClinicalUnitNormalizer.HeightUnit.IN)
    fun mToCm(meters: Double): Double = ClinicalUnitNormalizer.heightToCm(meters, ClinicalUnitNormalizer.HeightUnit.M)

    fun gLToGdL(gL: Double): Double = ClinicalUnitNormalizer.hemoglobinToGdl(gL, ClinicalUnitNormalizer.HemoglobinUnit.G_L)
}
