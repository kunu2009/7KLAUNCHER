package com.sevenk.launcher.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Manages haptic feedback for gestures and interactions
 */
class HapticFeedbackManager(private val context: Context) {
    
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    private val prefs = context.getSharedPreferences("haptic_settings", Context.MODE_PRIVATE)
    
    enum class HapticType {
        LIGHT_TAP,
        MEDIUM_TAP,
        HEAVY_TAP,
        SUCCESS,
        ERROR,
        SELECTION,
        LONG_PRESS,
        SWIPE,
        SCROLL,
        MODE_CHANGE
    }
    
    /**
     * Check if haptic feedback is enabled
     */
    fun isHapticEnabled(): Boolean {
        return prefs.getBoolean("haptic_enabled", true)
    }
    
    /**
     * Set haptic feedback enabled state
     */
    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
    }
    
    /**
     * Get haptic intensity (0-100)
     */
    fun getHapticIntensity(): Int {
        return prefs.getInt("haptic_intensity", 50)
    }
    
    /**
     * Set haptic intensity (0-100)
     */
    fun setHapticIntensity(intensity: Int) {
        prefs.edit().putInt("haptic_intensity", intensity.coerceIn(0, 100)).apply()
    }
    
    /**
     * Perform haptic feedback
     */
    fun performHaptic(type: HapticType) {
        if (!isHapticEnabled() || !vibrator.hasVibrator()) return
        
        when (type) {
            HapticType.LIGHT_TAP -> performLightTap()
            HapticType.MEDIUM_TAP -> performMediumTap()
            HapticType.HEAVY_TAP -> performHeavyTap()
            HapticType.SUCCESS -> performSuccess()
            HapticType.ERROR -> performError()
            HapticType.SELECTION -> performSelection()
            HapticType.LONG_PRESS -> performLongPress()
            HapticType.SWIPE -> performSwipe()
            HapticType.SCROLL -> performScroll()
            HapticType.MODE_CHANGE -> performModeChange()
        }
    }
    
    /**
     * Perform haptic feedback on view
     */
    fun performHaptic(view: View, type: HapticType) {
        if (!isHapticEnabled()) return
        
        val hapticConstant = when (type) {
            HapticType.LIGHT_TAP -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticType.MEDIUM_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            else -> {
                performHaptic(type)
                return
            }
        }
        
        view.performHapticFeedback(hapticConstant)
    }
    
    private fun performLightTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.3f).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(10, intensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }
    
    private fun performMediumTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.6f).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(25, intensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    }
    
    private fun performHeavyTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 1.0f).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(50, intensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
    
    private fun performSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.7f).toInt().coerceIn(1, 255)
            val pattern = longArrayOf(0, 30, 20, 30)
            val amplitudes = intArrayOf(0, intensity, 0, intensity)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 30, 20, 30), -1)
        }
    }
    
    private fun performError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.8f).toInt().coerceIn(1, 255)
            val pattern = longArrayOf(0, 100, 50, 100, 50, 100)
            val amplitudes = intArrayOf(0, intensity, 0, intensity, 0, intensity)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 50, 100, 50, 100), -1)
        }
    }
    
    private fun performSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.4f).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(15, intensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }
    
    private fun performLongPress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.8f).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(75, intensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(75)
        }
    }
    
    private fun performSwipe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.5f).toInt().coerceIn(1, 255)
            val pattern = longArrayOf(0, 20, 10, 20)
            val amplitudes = intArrayOf(0, intensity, intensity / 2, intensity)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 20, 10, 20), -1)
        }
    }
    
    private fun performScroll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.2f).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(5, intensity)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(5)
        }
    }
    
    private fun performModeChange() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (getHapticIntensity() * 0.7f).toInt().coerceIn(1, 255)
            val pattern = longArrayOf(0, 30, 20, 40)
            val amplitudes = intArrayOf(0, intensity / 2, intensity / 3, intensity)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 30, 20, 40), -1)
        }
    }
}
