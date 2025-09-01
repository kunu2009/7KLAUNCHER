package com.sevenk.calcvault.security

import android.content.Context
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

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Calc Vault")
            .setSubtitle("Confirm your identity to proceed")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
