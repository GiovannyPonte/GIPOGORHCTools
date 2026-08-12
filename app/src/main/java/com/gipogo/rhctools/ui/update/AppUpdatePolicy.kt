package com.gipogo.rhctools.ui.update

internal enum class AppUpdateMode { FLEXIBLE, IMMEDIATE }

/** Pure policy kept independent from Google Play so every decision is testable. */
internal object AppUpdatePolicy {
    const val CRITICAL_PRIORITY = 4

    fun selectMode(
        updateAvailable: Boolean,
        flexibleAllowed: Boolean,
        immediateAllowed: Boolean,
        priority: Int,
    ): AppUpdateMode? {
        if (!updateAvailable) return null
        val isCritical = priority >= CRITICAL_PRIORITY
        return when {
            isCritical && immediateAllowed -> AppUpdateMode.IMMEDIATE
            flexibleAllowed -> AppUpdateMode.FLEXIBLE
            immediateAllowed -> AppUpdateMode.IMMEDIATE
            else -> null
        }
    }

    fun isDailyCheckDue(lastCheckEpochDay: Long?, todayEpochDay: Long): Boolean =
        lastCheckEpochDay != todayEpochDay
}
