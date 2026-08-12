package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R

enum class CpoField { MAP, CO, BSA }

object CpoValidation {

    // MAP validado en unidades base: mmHg
    val mapRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 200.0,
        warnLow = 50.0,
        warnHigh = 140.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // CO validado en unidades base: L/min (debe ser > 0)
    val coRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 25.0,
        warnLow = 2.0,
        warnHigh = 12.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // BSA opcional (solo afecta CPI)
    val bsaRule = NumericRule(
        required = false,
        hardMin = 0.5,
        hardMax = 3.0,
        warnLow = 1.2,
        warnHigh = 2.5,
        hardMsg = R.string.val_out_of_hard_range
    )
}
