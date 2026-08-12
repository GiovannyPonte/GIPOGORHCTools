package com.gipogo.rhctools.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalHemodynamicsTest {
    @Test fun alternateInputUnitsYieldIdenticalDerivedSnapshot() {
        val a = CanonicalHemodynamics.derive(5.0, 2.0, 80.0, 8.0, 45.0, 20.0, 30.0, 12.0)
        val b = CanonicalHemodynamics.derive(
            ClinicalUnitNormalizer.cardiacOutputToLMin(5.0 / 60.0, ClinicalUnitNormalizer.CardiacOutputUnit.L_SEC),
            2.0,
            ClinicalUnitNormalizer.pressureToMmHg(80.0 / 7.50062, ClinicalUnitNormalizer.PressureUnit.KPA),
            ClinicalUnitNormalizer.pressureToMmHg(8.0 / 7.50062, ClinicalUnitNormalizer.PressureUnit.KPA),
            45.0, 20.0, 30.0, 12.0
        )
        assertEquals(a, b)
    }

    @Test fun changingSelectedCardiacOutputRecalculatesEveryDependentValue() {
        val old = CanonicalHemodynamics.derive(4.0, 2.0, 80.0, 8.0, 45.0, 20.0, 30.0, 12.0)
        val new = CanonicalHemodynamics.derive(6.0, 2.0, 80.0, 8.0, 45.0, 20.0, 30.0, 12.0)
        assertNotEquals(old.cardiacIndexLMinM2, new.cardiacIndexLMinM2)
        assertNotEquals(old.svrWood, new.svrWood)
        assertNotEquals(old.pvrWood, new.pvrWood)
        assertNotEquals(old.cardiacPowerW, new.cardiacPowerW)
        assertEquals(old.papi, new.papi)
    }

    @Test fun physiologicallyInvalidRelationshipsAreNotPersistableOutputs() {
        val invalid = CanonicalHemodynamics.derive(5.0, 2.0, 5.0, 8.0, 15.0, 20.0, 10.0, 12.0)
        assertEquals(null, invalid.svrWood)
        assertEquals(null, invalid.pvrWood)
        assertEquals(null, invalid.papi)
    }
}
