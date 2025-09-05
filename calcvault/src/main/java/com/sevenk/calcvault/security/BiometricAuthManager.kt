package com.sevenk.calcvault.security

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class BiometricAuthManager(
    private val context: Context,
    private val onResult: (BiometricAuthResult) -> Unit
) {

    enum class BiometricAuthResult {
        SUCCESS,
        ERROR,
        FAILURE
    }

    fun authenticate() {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(context as AppCompatActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(BiometricAuthResult.ERROR)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(BiometricAuthResult.SUCCESS)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(BiometricAuthResult.FAILURE)
                }
            })

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Calc Vault")
            .setSubtitle("Confirm your identity to proceed")

        val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: allowed to combine BIOMETRIC_STRONG and DEVICE_CREDENTIAL
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ).build()
        } else {
            // API 29: must use deprecated setDeviceCredentialAllowed(true)
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true).build()
        }

        biometricPrompt.authenticate(promptInfo)
    }
}
