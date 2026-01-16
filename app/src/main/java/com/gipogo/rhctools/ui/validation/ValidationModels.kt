package com.gipogo.rhctools.ui.validation

import androidx.annotation.StringRes

enum class Severity { OK, WARNING, ERROR }

data class ValidationResult(
    val severity: Severity = Severity.OK,
    @StringRes val messageResId: Int? = null,
    val messageArgs: List<Any> = emptyList()
) {
    companion object {
        val OK = ValidationResult(Severity.OK, null)
        fun warning(@StringRes resId: Int, vararg args: Any) =
            ValidationResult(Severity.WARNING, resId, args.toList())

        fun error(@StringRes resId: Int, vararg args: Any) =
            ValidationResult(Severity.ERROR, resId, args.toList())
    }
}
