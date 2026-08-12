package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.FieldAlert

enum class PatientField { CODE, DOB, WEIGHT, HEIGHT }

object PatientValidation {

    fun validateCode(code: String): FieldAlert {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return FieldAlert.error(R.string.val_required)
        // patrón sugerido (flexible): GIP-2026-XXXX
        if (trimmed.length < 6) return FieldAlert.error(R.string.val_invalid_format)
        return FieldAlert.NONE
    }

    fun validateDob(selected: Boolean): FieldAlert {
        // DOB recomendado (warning si falta), NO error duro
        return if (!selected) FieldAlert.warning(R.string.val_recommended) else FieldAlert.NONE
    }

    fun validateWeightKg(weightKg: Double?): FieldAlert {
        if (weightKg == null) return FieldAlert.NONE // opcional
        if (weightKg <= 0.0) return FieldAlert.error(R.string.val_out_of_hard_range)
        if (weightKg < 30.0 || weightKg > 250.0) return FieldAlert.warning(R.string.val_out_of_typical_range)
        return FieldAlert.NONE
    }

    fun validateHeightCm(heightCm: Double?): FieldAlert {
        if (heightCm == null) return FieldAlert.NONE // opcional
        if (heightCm <= 0.0) return FieldAlert.error(R.string.val_out_of_hard_range)
        if (heightCm < 120.0 || heightCm > 220.0) return FieldAlert.warning(R.string.val_out_of_typical_range)
        return FieldAlert.NONE
    }
}
