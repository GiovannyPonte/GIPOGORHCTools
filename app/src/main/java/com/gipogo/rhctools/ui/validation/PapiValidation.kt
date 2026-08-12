package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R

enum class PapiField { PASP, PADP, RAP }

object PapiValidation {

    // PASP (Pulmonary Artery Systolic Pressure) mmHg
    val paspRule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 140.0,
        warnLow = 10.0,
        warnHigh = 80.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // PADP (Pulmonary Artery Diastolic Pressure) mmHg
    val padpRule = NumericRule(
        required = true,
        hardMin = 0.0,
        hardMax = 80.0,
        warnLow = 5.0,
        warnHigh = 40.0,
        hardMsg = R.string.val_out_of_hard_range
    )

    // RAP (Right Atrial Pressure) mmHg
    val rapRule = NumericRule(
        required = true,
        hardMin = 0.0001, // para evitar 0 (denominador)
        hardMax = 40.0,
        warnLow = 1.0,
        warnHigh = 25.0,
        hardMsg = R.string.val_out_of_hard_range
    )
}
