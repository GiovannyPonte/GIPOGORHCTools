package com.gipogo.rhctools.ui.security

import android.content.Context

object AuthSessionManager {

    // Ajusta esto a tu gusto (ej: 10 min = 10*60*1000)
    private const val AUTH_SESSION_TIMEOUT_MS: Long = 10 * 60 * 1000L

    private const val PREFS = "auth_session_prefs"
    private const val KEY_LAST_AUTH_AT = "last_auth_at"

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun markAuthenticated(now: Long = System.currentTimeMillis()) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTH_AT, now)
            .apply()
    }

    fun clear() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_AUTH_AT)
            .apply()
    }

    fun lastAuthAt(): Long {
        val ctx = appContext ?: return 0L
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTH_AT, 0L)
    }

    fun hasValidSession(now: Long = System.currentTimeMillis()): Boolean {
        val last = lastAuthAt()
        if (last <= 0L) return false
        return (now - last) <= AUTH_SESSION_TIMEOUT_MS
    }

    fun isSessionValid(now: Long = System.currentTimeMillis()): Boolean = hasValidSession(now)
}
