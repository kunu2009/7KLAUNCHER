package com.sevenk.launcher

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Utility class to manage glass-like blur effects on views
 * for a consistent look across the launcher
 */
class GlassManager(private val context: Context) {

    // Default blur radius for general UI elements
    private val defaultRadius = 20f

    // Apply blur effect to a view
    fun applyBlur(view: View, radius: Float = defaultRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val effect = RenderEffect.createBlurEffect(
                    radius,
                    radius,
                    Shader.TileMode.CLAMP
                )
                view.setRenderEffect(effect)
            } catch (e: Exception) {
                // Ignore if effect can't be applied
            }
        }
    }

    // Clear the blur effect from a view
    fun clearBlur(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(null)
            } catch (e: Exception) {
                // Ignore if effect can't be cleared
            }
        }
    }

    // Apply different blur intensities based on the view type
    fun applySystemBlurs(views: Map<View, ViewType>) {
        views.forEach { (view, type) ->
            val radius = when (type) {
                ViewType.DOCK -> 25f
                ViewType.SIDEBAR -> 25f
                ViewType.APP_DRAWER -> 20f
                ViewType.SEARCH_BAR -> 15f
                ViewType.DIALOG -> 30f
            }
            applyBlur(view, radius)
        }
    }

    // View types with different blur intensities
    enum class ViewType {
        DOCK,
        SIDEBAR,
        APP_DRAWER,
        SEARCH_BAR,
        DIALOG
    }
}
