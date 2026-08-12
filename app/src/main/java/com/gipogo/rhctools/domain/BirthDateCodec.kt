package com.gipogo.rhctools.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Persists date-only clinical values as UTC midnight.
 *
 * A date of birth is not an instant in the phone's time zone. Keeping every
 * conversion in UTC prevents it moving to the previous day in western zones.
 */
object BirthDateCodec {
    fun fromStorageMillis(value: Long): LocalDate =
        Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()

    fun toStorageMillis(value: LocalDate): Long =
        value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun normalizeStorageMillis(value: Long): Long =
        toStorageMillis(fromStorageMillis(value))
}
