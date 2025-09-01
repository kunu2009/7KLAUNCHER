package com.sevenk.launcher.privacy

import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager as AndroidXBiometricManager
import androidx.biometric.BiometricPrompt as AndroidXBiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages app privacy features including hiding and locking apps
 */
class AppPrivacyManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("app_privacy", Context.MODE_PRIVATE)
    
    enum class PrivacyLevel {
        NONE,           // App is visible and accessible
        HIDDEN,         // App is hidden from app drawer but accessible via search
        LOCKED,         // App requires authentication to launch
        HIDDEN_LOCKED   // App is both hidden and locked
    }
    
    data class AppPrivacySettings(
        val packageName: String,
        val privacyLevel: PrivacyLevel,
        val requireBiometric: Boolean = true,
        val allowPinFallback: Boolean = true,
        val customPin: String? = null
    )
    
    /**
     * Set privacy level for an app
     */
    fun setAppPrivacyLevel(packageName: String, level: PrivacyLevel) {
        prefs.edit()
            .putString("privacy_$packageName", level.name)
            .apply()
    }
    
    /**
     * Get privacy level for an app
     */
    fun getAppPrivacyLevel(packageName: String): PrivacyLevel {
        val levelName = prefs.getString("privacy_$packageName", PrivacyLevel.NONE.name)
        return try {
            PrivacyLevel.valueOf(levelName ?: PrivacyLevel.NONE.name)
        } catch (e: IllegalArgumentException) {
            PrivacyLevel.NONE
        }
    }
    
    /**
     * Check if app should be hidden from app drawer
     */
    fun isAppHidden(packageName: String): Boolean {
        val level = getAppPrivacyLevel(packageName)
        return level == PrivacyLevel.HIDDEN || level == PrivacyLevel.HIDDEN_LOCKED
    }
    
    /**
     * Check if app requires authentication to launch
     */
    fun isAppLocked(packageName: String): Boolean {
        val level = getAppPrivacyLevel(packageName)
        return level == PrivacyLevel.LOCKED || level == PrivacyLevel.HIDDEN_LOCKED
    }
    
    /**
     * Get all hidden apps
     */
    fun getHiddenApps(): Set<String> {
        return prefs.all.entries
            .filter { it.key.startsWith("privacy_") }
            .mapNotNull { entry ->
                val packageName = entry.key.removePrefix("privacy_")
                val level = try {
                    PrivacyLevel.valueOf(entry.value.toString())
                } catch (e: IllegalArgumentException) {
                    PrivacyLevel.NONE
                }
                if (level == PrivacyLevel.HIDDEN || level == PrivacyLevel.HIDDEN_LOCKED) {
                    packageName
                } else null
            }
            .toSet()
    }
    
    /**
     * Get all locked apps
     */
    fun getLockedApps(): Set<String> {
        return prefs.all.entries
            .filter { it.key.startsWith("privacy_") }
            .mapNotNull { entry ->
                val packageName = entry.key.removePrefix("privacy_")
                val level = try {
                    PrivacyLevel.valueOf(entry.value.toString())
                } catch (e: IllegalArgumentException) {
                    PrivacyLevel.NONE
                }
                if (level == PrivacyLevel.LOCKED || level == PrivacyLevel.HIDDEN_LOCKED) {
                    packageName
                } else null
            }
            .toSet()
    }
    
    /**
     * Set custom PIN for app authentication
     */
    fun setAppPin(packageName: String, pin: String) {
        prefs.edit()
            .putString("pin_$packageName", pin)
            .apply()
    }
    
    /**
     * Verify PIN for app
     */
    fun verifyAppPin(packageName: String, enteredPin: String): Boolean {
        val storedPin = prefs.getString("pin_$packageName", null)
        return storedPin != null && storedPin == enteredPin
    }
    
    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val biometricManager = AndroidXBiometricManager.from(context)
            when (biometricManager.canAuthenticate(AndroidXBiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                AndroidXBiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        } else {
            false
        }
    }
    
    /**
     * Authenticate user with biometric or PIN
     */
    suspend fun authenticateForApp(
        activity: FragmentActivity,
        packageName: String,
        appName: String
    ): AuthenticationResult {
        return if (isBiometricAvailable()) {
            authenticateWithBiometric(activity, appName)
        } else {
            // Fallback to PIN authentication
            AuthenticationResult.PinRequired
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun authenticateWithBiometric(
        activity: FragmentActivity,
        appName: String
    ): AuthenticationResult = suspendCancellableCoroutine { continuation ->
        
        val biometricPrompt = AndroidXBiometricPrompt(activity, 
            ContextCompat.getMainExecutor(context),
            object : AndroidXBiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AndroidXBiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    continuation.resume(AuthenticationResult.Success)
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    continuation.resume(AuthenticationResult.Error(errString.toString()))
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    continuation.resume(AuthenticationResult.Failed)
                }
            })
        
        val promptInfo = AndroidXBiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock $appName")
            .setSubtitle("Use your biometric credential to access this app")
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
        
        continuation.invokeOnCancellation {
            // Handle cancellation if needed
        }
    }
    
    /**
     * Launch app with privacy check
     */
    suspend fun launchAppWithPrivacyCheck(
        activity: FragmentActivity,
        packageName: String,
        appName: String,
        launchIntent: Intent,
        onAuthenticationRequired: (AuthenticationResult) -> Unit
    ) {
        if (!isAppLocked(packageName)) {
            // App is not locked, launch directly
            try {
                activity.startActivity(launchIntent)
            } catch (e: Exception) {
                // Handle launch error
            }
            return
        }
        
        // App is locked, require authentication
        val authResult = authenticateForApp(activity, packageName, appName)
        when (authResult) {
            is AuthenticationResult.Success -> {
                try {
                    activity.startActivity(launchIntent)
                } catch (e: Exception) {
                    // Handle launch error
                }
            }
            else -> {
                onAuthenticationRequired(authResult)
            }
        }
    }
    
    /**
     * Clear all privacy settings
     */
    fun clearAllPrivacySettings() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Export privacy settings for backup
     */
    fun exportPrivacySettings(): Map<String, String> {
        return prefs.all.mapValues { it.value.toString() }
    }
    
    /**
     * Import privacy settings from backup
     */
    fun importPrivacySettings(settings: Map<String, String>) {
        val editor = prefs.edit()
        editor.clear()
        for ((key, value) in settings) {
            editor.putString(key, value)
        }
        editor.apply()
    }
    
    sealed class AuthenticationResult {
        object Success : AuthenticationResult()
        object Failed : AuthenticationResult()
        object PinRequired : AuthenticationResult()
        data class Error(val message: String) : AuthenticationResult()
    }
}
