package com.sevenk.launcher.ui.glass

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import com.sevenk.launcher.R

/**
 * Enhanced Glass Effect Helper with adaptive theming and performance optimization
 */
class GlassEffectHelper(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val DEFAULT_BLUR_RADIUS = 20f
        private const val LIGHT_BLUR_RADIUS = 16f
        private const val HEAVY_BLUR_RADIUS = 30f
        private var blurFailureCount = 0
        private const val MAX_BLUR_FAILURES = 3
    }
    
    /**
     * Apply adaptive glass effect with runtime blur on supported devices
     */
    fun applyGlassEffect(view: View, intensity: GlassIntensity = GlassIntensity.NORMAL) {
        // Apply glass background drawable
        view.background = ContextCompat.getDrawable(context, R.drawable.glass_panel)
        
        // Apply runtime blur on Android 12+ if enabled and supported
        if (shouldUseRuntimeBlur()) {
            applyRuntimeBlur(view, intensity.getBlurRadius())
        }
    }
    
    /**
     * Apply runtime blur effect if supported
     */
    private fun applyRuntimeBlur(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val renderEffect = RenderEffect.createBlurEffect(
                    radius,
                    radius,
                    Shader.TileMode.CLAMP
                )
                view.setRenderEffect(renderEffect)
            } catch (e: Exception) {
                blurFailureCount++
                if (blurFailureCount >= MAX_BLUR_FAILURES) {
                    // Disable blur if it consistently fails
                    prefs.edit().putBoolean("enable_runtime_blur", false).apply()
                }
            }
        }
    }
    
    /**
     * Remove glass effect from view
     */
    fun removeGlassEffect(view: View) {
        view.background = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(null)
            } catch (e: Exception) {
                // Ignore removal errors
            }
        }
    }
    
    /**
     * Check if runtime blur should be used
     */
    private fun shouldUseRuntimeBlur(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (blurFailureCount >= MAX_BLUR_FAILURES) return false
        return prefs.getBoolean("enable_runtime_blur", true)
    }
    
    /**
     * Apply glow effect to view (for active states)
     */
    fun applyGlowEffect(view: View, glowColor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.outlineSpotShadowColor = glowColor
                view.outlineAmbientShadowColor = glowColor
                view.elevation = 12f
            } catch (e: Exception) {
                // Graceful fallback
                view.elevation = 8f
            }
        } else {
            view.elevation = 8f
        }
    }
    
    /**
     * Remove glow effect from view
     */
    fun removeGlowEffect(view: View) {
        view.elevation = 4f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.outlineSpotShadowColor = 0
                view.outlineAmbientShadowColor = 0
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    /**
     * Glass intensity levels
     */
    enum class GlassIntensity {
        LIGHT,
        NORMAL,
        HEAVY;
        
        fun getBlurRadius(): Float = when (this) {
            LIGHT -> LIGHT_BLUR_RADIUS
            NORMAL -> DEFAULT_BLUR_RADIUS
            HEAVY -> HEAVY_BLUR_RADIUS
        }
    }
}