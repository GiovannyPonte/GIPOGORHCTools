package com.gipogo.rhctools.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ClinicalUnitNormalizerTest {
    private fun close(expected: Double, actual: Double) = assertEquals(expected, actual, 1e-6)

    @Test fun equivalentInputsProduceIdenticalCanonicalValues() {
        close(70.0, ClinicalUnitNormalizer.weightToKg(70.0 / 0.45359237, ClinicalUnitNormalizer.WeightUnit.LB))
        close(175.0, ClinicalUnitNormalizer.heightToCm(1.75, ClinicalUnitNormalizer.HeightUnit.M))
        close(175.0, ClinicalUnitNormalizer.heightToCm(175.0 / 2.54, ClinicalUnitNormalizer.HeightUnit.IN))
        close(14.0, ClinicalUnitNormalizer.hemoglobinToGdl(140.0, ClinicalUnitNormalizer.HemoglobinUnit.G_L))
        close(100.0, ClinicalUnitNormalizer.pressureToMmHg(100.0 / 7.50062, ClinicalUnitNormalizer.PressureUnit.KPA))
        close(6.0, ClinicalUnitNormalizer.cardiacOutputToLMin(0.1, ClinicalUnitNormalizer.CardiacOutputUnit.L_SEC))
        close(3.0, ClinicalUnitNormalizer.resistanceToWood(240.0, ClinicalUnitNormalizer.ResistanceUnit.DYN_S_CM5))
    }

    @Test fun canonicalValuesRoundTripThroughEveryDisplayUnit() {
        close(70.0, ClinicalUnitNormalizer.weightToKg(ClinicalUnitNormalizer.weightFromKg(70.0, ClinicalUnitNormalizer.WeightUnit.LB), ClinicalUnitNormalizer.WeightUnit.LB))
        close(175.0, ClinicalUnitNormalizer.heightToCm(ClinicalUnitNormalizer.heightFromCm(175.0, ClinicalUnitNormalizer.HeightUnit.IN), ClinicalUnitNormalizer.HeightUnit.IN))
        close(14.0, ClinicalUnitNormalizer.hemoglobinToGdl(ClinicalUnitNormalizer.hemoglobinFromGdl(14.0, ClinicalUnitNormalizer.HemoglobinUnit.G_L), ClinicalUnitNormalizer.HemoglobinUnit.G_L))
        close(85.0, ClinicalUnitNormalizer.pressureToMmHg(ClinicalUnitNormalizer.pressureFromMmHg(85.0, ClinicalUnitNormalizer.PressureUnit.KPA), ClinicalUnitNormalizer.PressureUnit.KPA))
        close(5.2, ClinicalUnitNormalizer.cardiacOutputToLMin(ClinicalUnitNormalizer.cardiacOutputFromLMin(5.2, ClinicalUnitNormalizer.CardiacOutputUnit.L_SEC), ClinicalUnitNormalizer.CardiacOutputUnit.L_SEC))
    }

    @Test fun equivalentUnitsProduceIdenticalClinicalCalculation() {
        val metricBsa = HemodynamicsFormulas.bsaMosteller(175.0, 70.0)
        val alternateBsa = HemodynamicsFormulas.bsaMosteller(
            ClinicalUnitNormalizer.heightToCm(1.75, ClinicalUnitNormalizer.HeightUnit.M),
            ClinicalUnitNormalizer.weightToKg(70.0 / 0.45359237, ClinicalUnitNormalizer.WeightUnit.LB)
        )
        close(metricBsa, alternateBsa)
        val mmHg = ClinicalUnitNormalizer.pressureToMmHg(80.0 / 7.50062, ClinicalUnitNormalizer.PressureUnit.KPA)
        val lMin = ClinicalUnitNormalizer.cardiacOutputToLMin(5.0 / 60.0, ClinicalUnitNormalizer.CardiacOutputUnit.L_SEC)
        close(
            HemodynamicsFormulas.cardiacPowerOutput(80.0, 5.0, metricBsa).cpoWatts,
            HemodynamicsFormulas.cardiacPowerOutput(mmHg, lMin, alternateBsa).cpoWatts
        )
    }
}
