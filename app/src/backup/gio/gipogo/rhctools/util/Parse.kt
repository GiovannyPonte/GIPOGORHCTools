package com.gipogo.rhctools.util

object Parse {
    fun toDoubleOrNull(text: String): Double? {
        val clean = text.trim().replace(",", ".")
        if (clean.isBlank()) return null
        return clean.toDoubleOrNull()
    }
}

