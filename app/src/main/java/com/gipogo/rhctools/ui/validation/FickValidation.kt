package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R

enum class FickField { WEIGHT, HEIGHT, SAO2, SVO2, HB, HR }

object FickValidation {

    val weightRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 300.0,
        warnLow = 30.0,
        warnHigh = 250.0,
        hardMsg = R.string.val_too_high_change_to_proceed
    )

    val heightRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 250.0,
        warnLow = 120.0,
        warnHigh = 213.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    val sao2Rule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 100.0,
        warnLow = 80.0,
        hardMsg = R.string.val_sao2_bounds
    )

    val svo2Rule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 100.0,
        warnLow = 30.0,
        warnHigh = 90.0,
        hardMsg = R.string.val_svo2_bounds
    )

    val hbRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 25.0,
        warnLow = 5.0,
        warnHigh = 20.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    val hrRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 300.0,
        warnLow = 30.0,
        warnHigh = 180.0,
        hardMsg = R.string.val_out_of_hard_range
    )
}
