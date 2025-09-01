package com.sevenk.launcher.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.sevenk.launcher.R
import com.sevenk.launcher.util.Perf

/**
 * Utility class for managing glass effects across the launcher.
 * Handles blur effects, adaptive tinting, and fallbacks for older devices.
 */
class GlassManager(private val context: Context) {
    // Default values for glass properties
    private var defaultBlurRadius = 20f
    private var defaultLightOpacity = 0.4f // 40% opacity in light mode
    private var defaultDarkOpacity = 0.15f // 15% opacity in dark mode

    // Current values (can be customized)
    private var currentBlurRadius = defaultBlurRadius
    private var currentLightOpacity = defaultLightOpacity
    private var currentDarkOpacity = defaultDarkOpacity

    // Border settings
    private var borderEnabled = true
    private var borderWidth = 1
    private var borderLightOpacity = 0.15f
    private var borderDarkOpacity = 0.25f

    // Cached tint colors
    private var lightModeTint = Color.WHITE
    private var darkModeTint = Color.parseColor("#13262F") // Gable Green from 7K palette

    // Accent colors for active states
    private var primaryAccentLight = Color.parseColor("#17557b") // Chathams Blue
    private var secondaryAccentLight = Color.parseColor("#366e8d") // Calypso
    private var primaryAccentDark = Color.parseColor("#366e8d") // Calypso
    private var secondaryAccentDark = Color.parseColor("#17557b") // Chathams Blue

    // Wallpaper adaptive colors
    private var adaptiveEnabled = true
    private var adaptiveLightSurfaceColor: Int? = null
    private var adaptiveDarkSurfaceColor: Int? = null
    private var adaptiveAccentColor: Int? = null

    // Track if we're in dark mode
    private var isDarkMode = false

    // Cached blur effects for reuse
    private val blurEffectCache = mutableMapOf<Float, RenderEffect>()

    /**
     * Sets the blur radius for glass effects.
     * @param radius Blur radius (1-50)
     */
    fun setBlurRadius(radius: Float) {
        currentBlurRadius = radius.coerceIn(1f, 50f)
        // Clear any cached effects since the radius changed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurEffectCache.clear()
        }
    }

    /**
     * Sets the opacity for glass effects.
     * @param opacity Opacity value (0.0-1.0)
     * @param forDarkMode Whether to set opacity for dark mode
     */
    fun setOpacity(opacity: Float, forDarkMode: Boolean) {
        if (forDarkMode) {
            currentDarkOpacity = opacity.coerceIn(0.05f, 0.5f)
        } else {
            currentLightOpacity = opacity.coerceIn(0.1f, 0.8f)
        }
    }

    /**
     * Updates whether we're in dark mode or not.
     * @param darkMode True if in dark mode
     */
    fun setDarkMode(darkMode: Boolean) {
        isDarkMode = darkMode
    }

    /**
     * Sets whether to enable adaptive coloring based on wallpaper.
     * @param enabled True to enable adaptive coloring
     */
    fun setAdaptiveEnabled(enabled: Boolean) {
        adaptiveEnabled = enabled
    }

    /**
     * Sets whether to show borders on glass elements.
     * @param enabled True to enable borders
     */
    fun setBorderEnabled(enabled: Boolean) {
        borderEnabled = enabled
    }

    /**
     * Sets border width for glass elements.
     * @param width Border width in pixels
     */
    fun setBorderWidth(width: Int) {
        borderWidth = width.coerceIn(0, 3)
    }

    /**
     * Updates adaptive colors based on the wallpaper.
     * @param wallpaper Wallpaper bitmap to extract colors from
     */
    fun updateAdaptiveColorsFromWallpaper(wallpaper: Bitmap) {
        Perf.trace("AdaptiveColorExtraction") {
            // Use Palette API to extract colors
            Palette.from(wallpaper).generate { palette ->
                palette?.let {
                    // Extract primary colors
                    val vibrantSwatch = it.vibrantSwatch
                    val darkVibrantSwatch = it.darkVibrantSwatch
                    val lightVibrantSwatch = it.lightVibrantSwatch
                    val mutedSwatch = it.mutedSwatch
                    val darkMutedSwatch = it.darkMutedSwatch
                    val lightMutedSwatch = it.lightMutedSwatch

                    // Decide on accent color (prefer vibrant)
                    adaptiveAccentColor = vibrantSwatch?.rgb
                        ?: darkVibrantSwatch?.rgb
                        ?: lightVibrantSwatch?.rgb
                        ?: mutedSwatch?.rgb

                    // Decide on light mode surface color (prefer light muted)
                    adaptiveLightSurfaceColor = lightMutedSwatch?.rgb
                        ?: lightVibrantSwatch?.rgb
                        ?: mutedSwatch?.rgb

                    // Decide on dark mode surface color (prefer dark muted)
                    adaptiveDarkSurfaceColor = darkMutedSwatch?.rgb
                        ?: darkVibrantSwatch?.rgb
                        ?: mutedSwatch?.rgb

                    // If we extracted an accent color, update accent colors
                    adaptiveAccentColor?.let { accentColor ->
                        // Create light/dark variants
                        val lightAccent = lightenColor(accentColor, 0.2f)
                        val darkAccent = darkenColor(accentColor, 0.2f)

                        // Update accent colors
                        primaryAccentLight = lightAccent
                        secondaryAccentLight = accentColor
                        primaryAccentDark = accentColor
                        secondaryAccentDark = darkAccent
                    }
                }
            }
        }
    }

    /**
     * Lightens a color by the given factor.
     */
    private fun lightenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = hsv[2] + factor
        return Color.HSVToColor(hsv)
    }

    /**
     * Darkens a color by the given factor.
     */
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = hsv[2] - factor
        return Color.HSVToColor(hsv)
    }

    /**
     * Applies a glass effect to a view.
     * This will apply:
     * 1. The appropriate background drawable
     * 2. Runtime blur if available (Android 12+)
     * 3. Proper tinting for light/dark mode
     *
     * @param view The view to apply the glass effect to
     * @param isActive Whether this view is in an active/selected state
     */
    fun applyGlassEffect(view: View, isActive: Boolean = false) {
        Perf.trace("GlassBlurApply") {
            // Apply the glass panel drawable as background
            view.background = AppCompatResources.getDrawable(context, R.drawable.glass_panel)

            // Apply runtime blur if available (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyRuntimeBlur(view)
            }

            // Determine base tint color based on mode and adaptive settings
            val baseTintColor = if (isDarkMode) {
                if (adaptiveEnabled && adaptiveDarkSurfaceColor != null) {
                    adaptiveDarkSurfaceColor!!
                } else {
                    darkModeTint
                }
            } else {
                if (adaptiveEnabled && adaptiveLightSurfaceColor != null) {
                    adaptiveLightSurfaceColor!!
                } else {
                    lightModeTint
                }
            }

            // Determine opacity based on mode
            val opacity = if (isDarkMode) currentDarkOpacity else currentLightOpacity

            // Create tint with alpha
            val tintColor = ColorUtils.setAlphaComponent(baseTintColor, (255 * opacity).toInt())

            // If active, blend with accent color
            val finalTintColor = if (isActive) {
                val accentColor = if (isDarkMode) primaryAccentDark else primaryAccentLight
                blendColors(tintColor, accentColor, 0.3f)
            } else {
                tintColor
            }

            view.background?.setTint(finalTintColor)

            // Apply border if enabled
            if (borderEnabled && borderWidth > 0) {
                val borderAlpha = (255 * if (isDarkMode) borderDarkOpacity else borderLightOpacity).toInt()
                val borderColor = if (isActive) {
                    val accentColor = if (isDarkMode) secondaryAccentDark else secondaryAccentLight
                    ColorUtils.setAlphaComponent(accentColor, (255 * 0.7f).toInt())
                } else {
                    ColorUtils.setAlphaComponent(Color.WHITE, borderAlpha)
                }

                // Border would be applied through drawable properties
                // Here we would need a custom drawable with border support
                // or use a layer-list drawable with a stroke
            }
        }
    }

    /**
     * Blends two colors together.
     * @param color1 First color
     * @param color2 Second color
     * @param ratio Blend ratio (0.0 = all color1, 1.0 = all color2)
     * @return Blended color
     */
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio
        val r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio
        val g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio
        val b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    /**
     * Applies a glass effect to a view with a custom tint color.
     * @param view The view to apply the glass effect to
     * @param tintColor The tint color to apply
     * @param opacity The opacity to apply
     * @param applyBlur Whether to apply blur effect
     */
    fun applyGlassEffect(view: View, tintColor: Int, opacity: Float, applyBlur: Boolean = true) {
        Perf.trace("GlassBlurCustomApply") {
            // Apply the glass panel drawable as background
            view.background = AppCompatResources.getDrawable(context, R.drawable.glass_panel)

            // Apply runtime blur if available and requested (Android 12+)
            if (applyBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyRuntimeBlur(view)
            }

            // Apply custom tint with specified opacity
            val finalTintColor = ColorUtils.setAlphaComponent(tintColor, (255 * opacity).toInt())
            view.background?.setTint(finalTintColor)
        }
    }

    /**
     * Applies a glass card effect to a view (slightly different style for cards).
     * @param view The view to apply the glass card effect to
     */
    fun applyGlassCardEffect(view: View, isActive: Boolean = false) {
        Perf.trace("GlassCardApply") {
            // Apply the glass card drawable as background (more rounded corners)
            view.background = AppCompatResources.getDrawable(context, R.drawable.glass_card)

            // Apply runtime blur if available (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyRuntimeBlur(view)
            }

            // Determine base tint color based on mode and adaptive settings
            val baseTintColor = if (isDarkMode) {
                if (adaptiveEnabled && adaptiveDarkSurfaceColor != null) {
                    adaptiveDarkSurfaceColor!!
                } else {
                    darkModeTint
                }
            } else {
                if (adaptiveEnabled && adaptiveLightSurfaceColor != null) {
                    adaptiveLightSurfaceColor!!
                } else {
                    lightModeTint
                }
            }

            // Use slightly higher opacity for cards
            val opacity = if (isDarkMode) {
                currentDarkOpacity * 1.2f
            } else {
                currentLightOpacity * 1.1f
            }.coerceAtMost(1.0f)

            // Create tint with alpha
            val tintColor = ColorUtils.setAlphaComponent(baseTintColor, (255 * opacity).toInt())

            // If active, blend with accent color
            val finalTintColor = if (isActive) {
                val accentColor = if (isDarkMode) primaryAccentDark else primaryAccentLight
                blendColors(tintColor, accentColor, 0.3f)
            } else {
                tintColor
            }

            view.background?.setTint(finalTintColor)

            // Apply drop shadow (would need to be part of the drawable)
        }
    }

    /**
     * Applies a runtime blur effect to a view on Android 12+.
     * @param view The view to blur
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyRuntimeBlur(view: View) {
        try {
            // Check if we have this blur radius cached
            val cachedEffect = blurEffectCache[currentBlurRadius]

            if (cachedEffect != null) {
                view.setRenderEffect(cachedEffect)
            } else {
                // Create and cache a new blur effect
                val renderEffect = RenderEffect.createBlurEffect(
                    currentBlurRadius,
                    currentBlurRadius,
                    Shader.TileMode.CLAMP
                )
                blurEffectCache[currentBlurRadius] = renderEffect
                view.setRenderEffect(renderEffect)
            }
        } catch (e: Exception) {
            // Fallback gracefully if blur fails
        }
    }

    /**
     * Gets the current blur radius.
     * @return Current blur radius
     */
    fun getBlurRadius(): Float {
        return currentBlurRadius
    }

    /**
     * Gets the current opacity for the given mode.
     * @param forDarkMode Whether to get opacity for dark mode
     * @return Current opacity
     */
    fun getOpacity(forDarkMode: Boolean): Float {
        return if (forDarkMode) currentDarkOpacity else currentLightOpacity
    }

    /**
     * Gets the accent color for the current mode.
     * @param secondary Whether to get the secondary accent color
     * @return Accent color for the current mode
     */
    fun getAccentColor(secondary: Boolean = false): Int {
        return if (isDarkMode) {
            if (secondary) secondaryAccentDark else primaryAccentDark
        } else {
            if (secondary) secondaryAccentLight else primaryAccentLight
        }
    }

    /**
     * Applies a subtle glow effect to a view.
     * @param view The view to apply the glow to
     * @param glowColor The color of the glow
     * @param glowRadius The radius of the glow
     */
    fun applyGlowEffect(view: View, glowColor: Int, glowRadius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val outlineSpotShadowColor = glowColor
                val outlineAmbientShadowColor = glowColor

                view.outlineSpotShadowColor = outlineSpotShadowColor
                view.outlineAmbientShadowColor = outlineAmbientShadowColor

                // Set elevation to create shadow/glow effect
                view.elevation = glowRadius
            } catch (e: Exception) {
                // Fallback gracefully if glow fails
            }
        }
    }
}
