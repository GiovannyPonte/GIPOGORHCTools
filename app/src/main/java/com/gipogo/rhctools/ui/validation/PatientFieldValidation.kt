package com.gipogo.rhctools.ui.validation

import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.FieldAlert

object PatientFieldValidation {

    fun validateDisplayName(value: String): FieldAlert {
        val v = value.trim()
        return when {
            v.isEmpty() -> FieldAlert.NONE
            v.length == 1 -> FieldAlert.warning(R.string.val_name_too_short)
            v.length > 60 -> FieldAlert.error(R.string.val_name_too_long)
            else -> FieldAlert.NONE
        }
    }

    fun validateInternalCode(value: String): FieldAlert {
        return if (value.trim().isEmpty()) FieldAlert.error(R.string.val_required) else FieldAlert.NONE
    }
}
