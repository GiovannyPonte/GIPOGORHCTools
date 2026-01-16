package com.gipogo.rhctools.ui.components

import com.gipogo.rhctools.R

enum class FickField { WEIGHT, HEIGHT, SAO2, SVO2, HB, HR }

object FickFieldValidation {

    private fun parse(text: String): Double? = text.trim().toDoubleOrNull()

    fun validateWeightKg(text: String): FieldAlert {
        val v = parse(text) ?: return FieldAlert.error(R.string.val_invalid_number)
        if (!v.isFinite() || v <= 0.0) return FieldAlert.error(R.string.val_must_be_gt_zero)
        if (v > 300.0) return FieldAlert.error(R.string.val_too_high_change_to_proceed)
        if (v < 30.0) return FieldAlert.warning(R.string.val_very_low_double_check)
        if (v > 250.0) return FieldAlert.warning(R.string.val_very_high_double_check)
        return FieldAlert.NONE
    }

    fun validateHeightCm(text: String): FieldAlert {
        val v = parse(text) ?: return FieldAlert.error(R.string.val_invalid_number)
        if (!v.isFinite() || v <= 0.0) return FieldAlert.error(R.string.val_must_be_gt_zero)
        if (v > 250.0) return FieldAlert.error(R.string.val_out_of_hard_range)
        if (v < 120.0) return FieldAlert.warning(R.string.val_very_low_double_check)
        if (v > 213.0) return FieldAlert.warning(R.string.val_very_high_double_check)
        return FieldAlert.NONE
    }

    fun validateSaO2(text: String): FieldAlert {
        val v = parse(text) ?: return FieldAlert.error(R.string.val_invalid_number)
        if (!v.isFinite()) return FieldAlert.error(R.string.val_invalid_number)
        if (v < 0.0 || v > 100.0) return FieldAlert.error(R.string.val_sao2_bounds)
        if (v < 80.0) return FieldAlert.warning(R.string.val_very_low_double_check)
        return FieldAlert.NONE
    }

    fun validateSvO2(text: String): FieldAlert {
        val v = parse(text) ?: return FieldAlert.error(R.string.val_invalid_number)
        if (!v.isFinite()) return FieldAlert.error(R.string.val_invalid_number)
        if (v < 0.0 || v > 100.0) return FieldAlert.error(R.string.val_svo2_bounds)
        if (v < 30.0) return FieldAlert.warning(R.string.val_very_low_double_check)
        if (v > 90.0) return FieldAlert.warning(R.string.val_very_high_double_check)
        return FieldAlert.NONE
    }

    fun validateHbGdl(text: String): FieldAlert {
        val v = parse(text) ?: return FieldAlert.error(R.string.val_invalid_number)
        if (!v.isFinite() || v <= 0.0) return FieldAlert.error(R.string.val_must_be_gt_zero)
        if (v > 25.0) return FieldAlert.error(R.string.val_out_of_hard_range)
        if (v < 5.0) return FieldAlert.warning(R.string.val_very_low_double_check)
        if (v > 20.0) return FieldAlert.warning(R.string.val_very_high_double_check)
        return FieldAlert.NONE
    }

    fun validateHr(text: String): FieldAlert {
        val v = parse(text) ?: return FieldAlert.error(R.string.val_invalid_number)
        if (!v.isFinite() || v <= 0.0) return FieldAlert.error(R.string.val_must_be_gt_zero)
        if (v > 300.0) return FieldAlert.error(R.string.val_out_of_hard_range)
        if (v < 30.0) return FieldAlert.warning(R.string.val_very_low_double_check)
        if (v > 180.0) return FieldAlert.warning(R.string.val_very_high_double_check)
        return FieldAlert.NONE
    }
}
