package com.gipogo.rhctools.ui.security

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.gipogo.rhctools.R

private const val TAG = "BIOGATE_777"

object BiometricGate {

    private const val TAG = "BiometricGate"

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

        Log.d(
            TAG,
            "availability(): canAuthenticate(AUTHENTICATORS=$AUTHENTICATORS) returned=$code " +
                    "[sdk=${Build.VERSION.SDK_INT}, device=${Build.MODEL}, brand=${Build.BRAND}, " +
                    "fingerprint=${Build.FINGERPRINT}]"
        )

        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "availability(): BIOMETRIC_SUCCESS")
                AuthResult.Success
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.w(TAG, "availability(): BIOMETRIC_ERROR_NO_HARDWARE")
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.NO_HARDWARE,
                    messageRes = R.string.auth_unavailable_no_hardware
                )
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.w(TAG, "availability(): BIOMETRIC_ERROR_HW_UNAVAILABLE")
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.HW_UNAVAILABLE,
                    messageRes = R.string.auth_unavailable_hw_unavailable
                )
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w(TAG, "availability(): BIOMETRIC_ERROR_NONE_ENROLLED")
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.NONE_ENROLLED,
                    messageRes = R.string.auth_unavailable_none_enrolled
                )
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.w(TAG, "availability(): BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED")
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNSUPPORTED,
                    messageRes = R.string.auth_unavailable_security_update_required
                )
            }

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.w(TAG, "availability(): BIOMETRIC_ERROR_UNSUPPORTED")
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNSUPPORTED,
                    messageRes = R.string.auth_unavailable_unsupported
                )
            }

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                // Importante: en emuladores/OEM esto aparece aunque el prompt funcione.
                Log.w(TAG, "availability(): BIOMETRIC_STATUS_UNKNOWN -> fail-safe allow prompt")
                AuthResult.NotAvailable(
                    reason = NotAvailableReason.UNKNOWN,
                    messageRes = R.string.auth_unavailable_unknown
                )
            }

            else -> {
                // Códigos no mapeados (OEM/emulador). No confiamos en esto para bloquear.
                Log.w(TAG, "availability(): unrecognized canAuthenticate() code=$code -> treat as UNKNOWN")
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
        Log.e(TAG, "AUTH CALLED!!!")

        Log.d(TAG, "authenticate(): called")

        // Pre-check: SOLO bloquea si es claramente imposible.
        // UNKNOWN -> NO bloquea (deja que el prompt decida).
        val avail = availability(activity)
        if (avail is AuthResult.NotAvailable && avail.reason != NotAvailableReason.UNKNOWN) {
            Log.w(TAG, "authenticate(): blocked by availability reason=${avail.reason}")
            onResult(avail)
            return
        } else if (avail is AuthResult.NotAvailable && avail.reason == NotAvailableReason.UNKNOWN) {
            Log.w(TAG, "authenticate(): availability UNKNOWN -> continuing to prompt")
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(TAG, "prompt: onAuthenticationSucceeded()")
                onResult(AuthResult.Success)
            }

            override fun onAuthenticationFailed() {
                // Huella no válida; prompt sigue abierto.
                Log.d(TAG, "prompt: onAuthenticationFailed() (non-fatal, prompt continues)")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e(TAG, "prompt: onAuthenticationError(code=$errorCode, msg=$errString)")

                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_TIMEOUT -> {
                        Log.i(TAG, "prompt: treated as CANCELED")
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
        Log.d(TAG, "prompt: authenticate() starting (allowedAuthenticators=$AUTHENTICATORS)")
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
