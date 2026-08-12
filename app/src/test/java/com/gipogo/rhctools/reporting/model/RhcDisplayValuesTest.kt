package com.gipogo.rhctools.reporting.model

import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RhcDisplayValuesTest {

    @Test
    fun displaySelectedCo_prefersSelectedFieldsAndMethod() {
        val entity = baseEntity(
            cardiacOutputLMin = 4.8,
            cardiacIndexLMinM2 = 2.1,
            cardiacOutputSelectedLMin = 5.6,
            cardiacIndexSelectedLMinM2 = 2.7,
            coSelectedMethod = "TD"
        )

        val display = entity.displaySelectedCo()

        assertEquals(5.6, display.cardiacOutputLMin)
        assertEquals(2.7, display.cardiacIndexLMinM2)
        assertEquals(R.string.co_method_thermodilution, display.methodLabelRes)
    }

    @Test
    fun displaySelectedCo_fallsBackToLegacyValues() {
        val entity = baseEntity(
            cardiacOutputLMin = 4.2,
            cardiacIndexLMinM2 = 2.0,
            coMethod = "FICK"
        )

        val display = entity.displaySelectedCo()

        assertEquals(4.2, display.cardiacOutputLMin)
        assertEquals(2.0, display.cardiacIndexLMinM2)
        assertEquals(R.string.co_method_fick, display.methodLabelRes)
    }

    @Test
    fun displayPvr_usesPersistedDynUnits() {
        val entity = baseEntity(
            pvrWood = 3.4,
            pvrDyn = 272.0,
            pvrUnits = "DYN"
        )

        val display = entity.displayPvr()

        assertEquals(272.0, display.value)
        assertEquals(R.string.common_unit_dynes, display.unitRes)
    }

    @Test
    fun displayPvr_defaultsToWoodUnits() {
        val entity = baseEntity(
            pvrWood = 2.8,
            pvrDyn = 224.0,
            pvrUnits = null
        )

        val display = entity.displayPvr()

        assertEquals(2.8, display.value)
        assertEquals(R.string.common_unit_wu_short, display.unitRes)
    }

    @Test
    fun displaySelectedCo_handlesMissingMethod() {
        val entity = baseEntity(
            cardiacOutputSelectedLMin = 5.0,
            cardiacIndexSelectedLMinM2 = 2.5
        )

        val display = entity.displaySelectedCo()

        assertEquals(5.0, display.cardiacOutputLMin)
        assertEquals(2.5, display.cardiacIndexLMinM2)
        assertNull(display.methodLabelRes)
    }

    private fun baseEntity(
        cardiacOutputLMin: Double? = null,
        cardiacIndexLMinM2: Double? = null,
        cardiacOutputSelectedLMin: Double? = null,
        cardiacIndexSelectedLMinM2: Double? = null,
        coSelectedMethod: String? = null,
        coMethod: String? = null,
        pvrWood: Double? = null,
        pvrDyn: Double? = null,
        pvrUnits: String? = null
    ): RhcStudyDataEntity {
        return RhcStudyDataEntity(
            id = "rhc-1",
            studyId = "study-1",
            cardiacOutputLMin = cardiacOutputLMin,
            cardiacIndexLMinM2 = cardiacIndexLMinM2,
            cardiacOutputSelectedLMin = cardiacOutputSelectedLMin,
            cardiacIndexSelectedLMinM2 = cardiacIndexSelectedLMinM2,
            coSelectedMethod = coSelectedMethod,
            coMethod = coMethod,
            pvrWood = pvrWood,
            pvrDyn = pvrDyn,
            pvrUnits = pvrUnits,
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }
}
