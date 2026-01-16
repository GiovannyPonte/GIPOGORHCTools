package com.gipogo.rhctools.util

import java.util.Locale
import kotlin.math.round

object Format {
    fun d(value: Double, decimals: Int = 2): String {
        val factor = Math.pow(10.0, decimals.toDouble())
        val rounded = round(value * factor) / factor
        return String.format(Locale.US, "%.${decimals}f", rounded)
    }
}

