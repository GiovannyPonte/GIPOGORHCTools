package com.gipogo.rhctools.ui.components

import androidx.annotation.StringRes

enum class AlertLevel { NONE, WARNING, ERROR }

data class FieldAlert(
    val level: AlertLevel = AlertLevel.NONE,
    @StringRes val messageResId: Int? = null,
    val messageArgs: List<Any> = emptyList()
) {
    companion object {
        val NONE = FieldAlert(AlertLevel.NONE, null)
        fun warning(@StringRes resId: Int, vararg args: Any) =
            FieldAlert(AlertLevel.WARNING, resId, args.toList())

        fun error(@StringRes resId: Int, vararg args: Any) =
            FieldAlert(AlertLevel.ERROR, resId, args.toList())
    }
}
