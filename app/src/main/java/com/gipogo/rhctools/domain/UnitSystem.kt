package com.gipogo.rhctools.domain

import androidx.annotation.StringRes
import com.gipogo.rhctools.R
import java.util.Locale
import kotlin.math.abs

enum class UnitSystem(
    @StringRes val labelRes: Int,
    @StringRes val weightUnitRes: Int,
    @StringRes val heightUnitRes: Int
) {
    Metric(R.string.units_metric_kg_cm, R.string.unit_kg, R.string.unit_cm),
    Imperial(R.string.units_imperial_lb_in, R.string.unit_lb, R.string.unit_in);

    fun weightToKg(input: String): Double? {
        val v = input.toClinicalDoubleOrNull() ?: return null
        return when (this) {
            Metric -> v
            Imperial -> ClinicalUnitNormalizer.weightToKg(v, ClinicalUnitNormalizer.WeightUnit.LB)
        }
    }

    fun heightToCm(input: String): Double? {
        val v = input.toClinicalDoubleOrNull() ?: return null
        return when (this) {
            Metric -> v
            Imperial -> ClinicalUnitNormalizer.heightToCm(v, ClinicalUnitNormalizer.HeightUnit.IN)
        }
    }

    fun kgToWeightString(kg: Double): String =
        when (this) {
            Metric -> format2(kg)
            Imperial -> format2(ClinicalUnitNormalizer.weightFromKg(kg, ClinicalUnitNormalizer.WeightUnit.LB))
        }

    fun cmToHeightString(cm: Double): String =
        when (this) {
            Metric -> format2(cm)
            Imperial -> format2(ClinicalUnitNormalizer.heightFromCm(cm, ClinicalUnitNormalizer.HeightUnit.IN))
        }

    // ✅ NUEVO: para persistencia (kg/cm) en texto “smart”
    fun weightKgTextOrBlank(input: String): String {
        val t = input.trim()
        if (t.isBlank()) return ""
        val kg = weightToKg(t) ?: return ""
        return formatSmart(kg)
    }

    fun heightCmTextOrBlank(input: String): String {
        val t = input.trim()
        if (t.isBlank()) return ""
        val cm = heightToCm(t) ?: return ""
        return formatSmart(cm)
    }

    companion object {
        fun fromStored(value: String?): UnitSystem =
            if (value == Imperial.name) Imperial else Metric
    }
}

// helper local
private fun format2(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

/** Accepts the decimal separators exposed by Android numeric keyboards. */
fun String.toClinicalDoubleOrNull(): Double? {
    val normalized = trim().replace(',', '.')
    if (normalized.isBlank() || normalized.count { it == '.' } > 1) return null
    return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
}

// ✅ NUEVO: 72.0 -> "72"; 163.0 -> "163"; 1.75 -> "1.75"
private fun formatSmart(v: Double): String {
    val rounded = v.toLong().toDouble()
    return if (abs(v - rounded) < 1e-9) {
        rounded.toLong().toString()
    } else {
        // evita notación científica rara en algunos casos
        v.toString()
    }
}
