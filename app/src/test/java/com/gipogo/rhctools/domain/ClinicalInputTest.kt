package com.gipogo.rhctools.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class ClinicalInputTest {
    @Test
    fun birthDateRoundTripIsIndependentFromPhoneTimeZone() {
        val previous = TimeZone.getDefault()
        try {
            val expected = LocalDate.of(1990, 1, 15)
            val stored = BirthDateCodec.toStorageMillis(expected)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Cancun"))
            assertEquals(expected, BirthDateCodec.fromStorageMillis(stored))

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertEquals(expected, BirthDateCodec.fromStorageMillis(stored))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun unitSystemAcceptsCommaAndPointWithoutDroppingMeasurements() {
        assertEquals(72.5, UnitSystem.Metric.weightToKg("72,5")!!, 0.000001)
        assertEquals(72.5, UnitSystem.Metric.weightToKg("72.5")!!, 0.000001)
        assertEquals("72.5", UnitSystem.Metric.weightKgTextOrBlank("72,5"))
        assertEquals("180.5", UnitSystem.Metric.heightCmTextOrBlank("180,5"))
    }

    @Test
    fun malformedOrNonFiniteClinicalNumbersAreRejected() {
        assertNull("1,2,3".toClinicalDoubleOrNull())
        assertNull("NaN".toClinicalDoubleOrNull())
        assertNull("Infinity".toClinicalDoubleOrNull())
    }
}
