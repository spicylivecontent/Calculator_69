package com.calculator.app.security

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** PIN is required first; missing biometrics or cancellation never grants access. */
object BiometricUnlock {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** Returns a cancellation handle that also ignores callbacks from an obsolete prompt. */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailedOrCancelled: () -> Unit
    ): (() -> Unit)? {
        if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            Toast.makeText(
                activity,
                "Aktivera biometrisk autentisering på enheten för att öppna vaulten.",
                Toast.LENGTH_LONG
            ).show()
            onFailedOrCancelled()
            return null
        }

        val session = BiometricSession(onSuccess, onFailedOrCancelled)
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    session.succeed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    session.fail()
                }

                // A mismatch is retryable; the system keeps the same prompt open.
                override fun onAuthenticationFailed() {}
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bekräfta identitet")
            .setSubtitle("Använd fingeravtryck eller ansikte för att öppna vaulten")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setNegativeButtonText("Avbryt")
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: IllegalStateException) {
            session.fail()
            return null
        }
        return {
            session.cancel()
            prompt.cancelAuthentication()
        }
    }
}
