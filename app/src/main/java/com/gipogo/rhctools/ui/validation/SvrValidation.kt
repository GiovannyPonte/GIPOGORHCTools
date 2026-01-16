package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R

enum class SvrField { MAP, CVP, CO }

object SvrValidation {

    // MAP (Mean Arterial Pressure) mmHg
    val mapRule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 200.0,
        warnLow = 50.0,
        warnHigh = 140.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // CVP (Central Venous Pressure) mmHg
    val cvpRule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 50.0,
        warnLow = 2.0,
        warnHigh = 20.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // CO (Cardiac Output) L/min  -> debe ser > 0
    val coRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 25.0,
        warnLow = 2.0,
        warnHigh = 12.0,
        hardMsg = R.string.val_out_of_hard_range
    )
}
