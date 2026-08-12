package com.gipogo.rhctools.ui.security

import android.content.Context
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.gipogo.rhctools.R

object BiometricGate {

    sealed class AuthResult {
        data object Success : AuthResult()
        data object Canceled : AuthResult()

        data class NotAvailable(
            val reason: NotAvailableReason,
            @StringRes val messageRes: Int
        ) : AuthResult()

        data class Error(
            val code: Int,
            @StringRes val messageRes: Int
        ) : AuthResult()
    }

    enum class NotAvailableReason {
        NO_HARDWARE,
        HW_UNAVAILABLE,
        NONE_ENROLLED,
        UNSUPPORTED,
        UNKNOWN
    }

    // No const: evita problemas raros y funciona bien en todos los setups
    private val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): AuthResult {
        val bm = BiometricManager.from(context)
        val code = bm.canAuthenticate(AUTHENTICATORS)

        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthResult.Success

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.NO_HARDWARE,
                    messageRes = R.string.auth_unavailable_no_hardware
                )
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.HW_UNAVAILABLE,
                    messageRes = R.string.auth_unavailable_hw_unavailable
                )
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.NONE_ENROLLED,
                    messageRes = R.string.auth_unavailable_none_enrolled
                )
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNSUPPORTED,
                    messageRes = R.string.auth_unavailable_security_update_required
                )
            }

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNSUPPORTED,
                    messageRes = R.string.auth_unavailable_unsupported
                )
            }

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                // Importante: en emuladores/OEM esto aparece aunque el prompt funcione.
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNKNOWN,
                    messageRes = R.string.auth_unavailable_unknown
                )
            }

            else -> {
                // Códigos no mapeados (OEM/emulador). No confiamos en esto para bloquear.
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNKNOWN,
                    messageRes = R.string.auth_unavailable_unknown
                )
            }
        }
    }

    fun authenticate(

        activity: FragmentActivity,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int? = null,
        @StringRes descriptionRes: Int? = null,
        onResult: (AuthResult) -> Unit
    ) {
        // Pre-check: SOLO bloquea si es claramente imposible.
        // UNKNOWN -> NO bloquea (deja que el prompt decida).
        val avail = availability(activity)
        if (avail is AuthResult.NotAvailable && avail.reason != NotAvailableReason.UNKNOWN) {
            onResult(avail)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(AuthResult.Success)
            }

            override fun onAuthenticationFailed() {
                // Huella no válida; prompt sigue abierto.
                // Intento no válido; BiometricPrompt permanece abierto.
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_TIMEOUT -> {
                        onResult(AuthResult.Canceled)
                        return
                    }
                }

                val msgRes = mapErrorToMessageRes(errorCode)
                onResult(AuthResult.Error(code = errorCode, messageRes = msgRes))
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(titleRes))
            .setAllowedAuthenticators(AUTHENTICATORS)

        subtitleRes?.let { builder.setSubtitle(activity.getString(it)) }
        descriptionRes?.let { builder.setDescription(activity.getString(it)) }

        // IMPORTANTE: con DEVICE_CREDENTIAL NO usar setNegativeButtonText()
        prompt.authenticate(builder.build())
    }

    @StringRes
    private fun mapErrorToMessageRes(errorCode: Int): Int {
        return when (errorCode) {
            BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                R.string.auth_error_hw_unavailable

            BiometricPrompt.ERROR_LOCKOUT ->
                R.string.auth_error_lockout

            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                R.string.auth_error_lockout_permanent

            BiometricPrompt.ERROR_NO_BIOMETRICS ->
                R.string.auth_error_no_biometrics

            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                R.string.auth_error_no_device_credential

            BiometricPrompt.ERROR_NO_SPACE ->
                R.string.auth_error_no_space

            BiometricPrompt.ERROR_UNABLE_TO_PROCESS ->
                R.string.auth_error_unable_to_process

            BiometricPrompt.ERROR_VENDOR ->
                R.string.auth_error_vendor

            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                R.string.auth_error_security_update_required

            else ->
                R.string.auth_error_generic
        }
    }
}
