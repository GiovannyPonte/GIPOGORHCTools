package com.gipogo.rhctools.ui.validation

import java.text.DecimalFormatSymbols
import java.util.Locale

object NumericParsing {

    /**
     * Acepta:
     * - "176.4"
     * - "176,4"
     * - "  176,4  "
     * - "1,234.5" (si el usuario mete separador de miles, lo limpiamos con heurística)
     *
     * Estrategia:
     * 1) Trim
     * 2) Si contiene tanto ',' como '.', asumimos que el último que aparece es decimal
     *    y eliminamos el otro como separador de miles.
     * 3) Si contiene solo ',', lo convertimos a '.'
     */
    fun parseDouble(text: String): Double? {
        val s0 = text.trim()
        if (s0.isEmpty()) return null

        var s = s0

        val hasComma = s.contains(',')
        val hasDot = s.contains('.')

        if (hasComma && hasDot) {
            val lastComma = s.lastIndexOf(',')
            val lastDot = s.lastIndexOf('.')
            val decimalIsComma = lastComma > lastDot

            s = if (decimalIsComma) {
                // coma decimal, punto miles
                s.replace(".", "").replace(',', '.')
            } else {
                // punto decimal, coma miles
                s.replace(",", "")
            }
        } else if (hasComma && !hasDot) {
            // coma decimal simple
            s = s.replace(',', '.')
        }

        return s.toDoubleOrNull()
    }
}

