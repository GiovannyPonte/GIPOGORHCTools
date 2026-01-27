package com.gipogo.rhctools.reporting.compose

import android.content.Context
import com.gipogo.rhctools.R
import java.text.DateFormat
import java.util.Date

object ReportFormat {

    fun formatDateTime(context: Context, millis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(millis))
    }

    fun formatDateOnly(context: Context, millis: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date(millis))
    }

    fun formatTimeOnly(context: Context, millis: Long): String {
        return DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(Date(millis))
    }

    fun na(context: Context): String = context.getString(R.string.common_value_na)
}

