package com.gipogo.rhctools.data.db

import com.gipogo.rhctools.data.db.dao.PatientDao
import java.time.LocalDate
import java.util.UUID
import kotlin.math.min

object PatientCodeGenerator {

    /**
     * Generates a "human" code like: GIP-2026-8F3A1C2D
     * Guarantees uniqueness by checking PatientDao and retrying.
     */
    suspend fun generateUniqueInternalCode(
        dao: PatientDao,
        prefix: String = "GIP",
        maxAttempts: Int = 12
    ): String {
        val year = LocalDate.now().year
        repeat(maxAttempts) {
            val suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .take(8)
                .uppercase()
            val code = "$prefix-$year-$suffix"

            if (!dao.existsByInternalCode(code)) return code
        }
        // Extremely unlikely fallback: longer suffix
        val suffix = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        val code = "$prefix-$year-$suffix"
        if (!dao.existsByInternalCode(code)) return code

        // If this ever happens, you have either a mocked DAO or something very wrong.
        throw IllegalStateException("Unable to generate a unique patient code after $maxAttempts attempts.")
    }
}
