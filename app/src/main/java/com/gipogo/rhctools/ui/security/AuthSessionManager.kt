package com.gipogo.rhctools.ui.security

import android.content.Context
import android.os.SystemClock

/** Short-lived authentication session tied to this process and monotonic time. */
object AuthSessionManager {
    private const val AUTH_SESSION_TIMEOUT_MS: Long = 10 * 60 * 1000L

    @Volatile
    private var lastAuthElapsedRealtime: Long = 0L

    val sessionTimeoutMs: Long
        get() = AUTH_SESSION_TIMEOUT_MS

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) = Unit

    fun markAuthenticated(now: Long = SystemClock.elapsedRealtime()) {
        lastAuthElapsedRealtime = now
    }

    fun clear() {
        lastAuthElapsedRealtime = 0L
    }

    fun lastAuthAt(): Long = lastAuthElapsedRealtime

    fun hasValidSession(now: Long = SystemClock.elapsedRealtime()): Boolean {
        val last = lastAuthElapsedRealtime
        if (last <= 0L) return false
        val elapsed = now - last
        return elapsed in 0..AUTH_SESSION_TIMEOUT_MS
    }

    fun isSessionValid(now: Long = SystemClock.elapsedRealtime()): Boolean = hasValidSession(now)
}
