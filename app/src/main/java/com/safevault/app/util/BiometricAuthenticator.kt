package com.safevault.app.util

import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Result of an availability check for the vault's unlock mechanism. */
enum class BiometricAvailability {
    /** Biometrics or device credential can be used right now. */
    AVAILABLE,

    /** Hardware exists but nothing is enrolled (no fingerprint/PIN set). */
    NONE_ENROLLED,

    /** No suitable authentication hardware on this device. */
    UNAVAILABLE,
}

/**
 * Thin, testable wrapper around [BiometricPrompt].
 *
 * Uses class-3 (STRONG) biometrics and allows the device credential
 * (PIN/pattern/password) as a fallback, satisfying the "biometric OR device
 * credential" unlock requirement without a custom negative button.
 */
class BiometricAuthenticator(
    private val activity: FragmentActivity,
) {
    private val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun availability(): BiometricAvailability =
        when (BiometricManager.from(activity).canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (message: String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancellations are expected and not surfaced as errors.
                    val userCancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!userCancelled) onError(errString.toString())
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            // No negative button is allowed when DEVICE_CREDENTIAL is enabled.
            .build()

        prompt.authenticate(info)
    }

    companion object {
        /**
         * Intent action helper for sending users to enroll a credential when
         * nothing is enrolled. Exposed as a launcher contract at the call site.
         */
        fun launchEnrollment(launcher: ActivityResultLauncher<android.content.Intent>) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            launcher.launch(intent)
        }
    }
}
