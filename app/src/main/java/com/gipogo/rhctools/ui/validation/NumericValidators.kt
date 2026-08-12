package com.gipogo.rhctools.ui.validation

import androidx.annotation.StringRes
import com.gipogo.rhctools.R

data class NumericRule(
    val required: Boolean = true,
    val hardMin: Double? = null,
    val hardMax: Double? = null,
    val warnLow: Double? = null,
    val warnHigh: Double? = null,

    @StringRes val requiredMsg: Int = R.string.val_required,
    @StringRes val invalidNumberMsg: Int = R.string.val_invalid_number,
    @StringRes val hardMsg: Int = R.string.val_out_of_hard_range,
    @StringRes val warnLowMsg: Int = R.string.val_very_low_double_check,
    @StringRes val warnHighMsg: Int = R.string.val_very_high_double_check
)

object NumericValidators {

    fun validate(text: String, rule: NumericRule): ValidationResult {
        val raw = text.trim()

        if (raw.isEmpty()) {
            return if (rule.required) ValidationResult.error(rule.requiredMsg) else ValidationResult.OK
        }

        val v = NumericParsing.parseDouble(raw) ?: return ValidationResult.error(rule.invalidNumberMsg)
        if (!v.isFinite()) return ValidationResult.error(rule.invalidNumberMsg)

        if (rule.hardMin != null && v < rule.hardMin) return ValidationResult.error(rule.hardMsg)
        if (rule.hardMax != null && v > rule.hardMax) return ValidationResult.error(rule.hardMsg)

        if (rule.warnLow != null && v < rule.warnLow) return ValidationResult.warning(rule.warnLowMsg)
        if (rule.warnHigh != null && v > rule.warnHigh) return ValidationResult.warning(rule.warnHighMsg)

        return ValidationResult.OK
    }

    fun validateValue(value: Double?, rule: NumericRule): ValidationResult {
        if (value == null) {
            return if (rule.required) ValidationResult.error(rule.requiredMsg) else ValidationResult.OK
        }
        if (!value.isFinite()) return ValidationResult.error(rule.invalidNumberMsg)

        if (rule.hardMin != null && value < rule.hardMin) return ValidationResult.error(rule.hardMsg)
        if (rule.hardMax != null && value > rule.hardMax) return ValidationResult.error(rule.hardMsg)

        if (rule.warnLow != null && value < rule.warnLow) return ValidationResult.warning(rule.warnLowMsg)
        if (rule.warnHigh != null && value > rule.warnHigh) return ValidationResult.warning(rule.warnHighMsg)

        return ValidationResult.OK
    }
}
