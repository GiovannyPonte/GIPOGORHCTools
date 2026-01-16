package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R

enum class PvrField { MPAP, PAWP, CO }

object PvrValidation {

    // mPAP (Mean Pulmonary Artery Pressure) mmHg
    val mpapRule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 120.0,
        warnLow = 10.0,
        warnHigh = 50.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // PAWP (Pulmonary Artery Wedge Pressure) mmHg
    val pawpRule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 50.0,
        warnLow = 5.0,
        warnHigh = 25.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // CO / Qp (Cardiac Output / Pulmonary Flow) L/min
    val coRule = NumericRule(
        required = true,
        hardMin = 0.0001,
        hardMax = 25.0,
        warnLow = 2.0,
        warnHigh = 12.0,
        hardMsg = R.string.val_out_of_hard_range
    )
}

