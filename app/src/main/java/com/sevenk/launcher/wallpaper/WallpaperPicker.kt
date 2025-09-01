package com.sevenk.launcher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

/**
 * Wallpaper picker with 7K solid palette
 */
class WallpaperPicker(private val context: Context) {
    
    private val wallpaperManager = WallpaperManager.getInstance(context)
    
    companion object {
        // 7K Brand Palette
        val SEVENK_PALETTE = listOf(
            Color.parseColor("#1976D2"), // Primary Blue
            Color.parseColor("#0D47A1"), // Dark Blue
            Color.parseColor("#42A5F5"), // Light Blue
            Color.parseColor("#E3F2FD"), // Very Light Blue
            Color.parseColor("#263238"), // Dark Grey
            Color.parseColor("#37474F"), // Medium Grey
            Color.parseColor("#607D8B"), // Blue Grey
            Color.parseColor("#90A4AE"), // Light Grey
            Color.parseColor("#FF5722"), // Accent Orange
            Color.parseColor("#4CAF50"), // Success Green
            Color.parseColor("#FFC107"), // Warning Yellow
            Color.parseColor("#F44336"), // Error Red
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#00BCD4"), // Cyan
            Color.parseColor("#795548"), // Brown
            Color.parseColor("#000000"), // Pure Black
            Color.parseColor("#FFFFFF"), // Pure White
        )
    }
    
    /**
     * Create solid color wallpaper
     */
    fun createSolidWallpaper(color: Int): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        
        return bitmap
    }
    
    /**
     * Create gradient wallpaper
     */
    fun createGradientWallpaper(startColor: Int, endColor: Int, angle: Float = 45f): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val gradient = LinearGradient(
            0f, 0f, 
            width * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat(),
            height * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat(),
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        
        val paint = Paint().apply {
            shader = gradient
        }
        
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
    
    /**
     * Create geometric pattern wallpaper
     */
    fun createGeometricWallpaper(baseColor: Int, accentColor: Int): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill background
        canvas.drawColor(baseColor)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 50
        }
        
        val gridSize = 120f
        
        // Draw geometric pattern
        for (x in 0 until (width / gridSize).toInt() + 1) {
            for (y in 0 until (height / gridSize).toInt() + 1) {
                val centerX = x * gridSize + gridSize / 2
                val centerY = y * gridSize + gridSize / 2
                
                if ((x + y) % 2 == 0) {
                    canvas.drawCircle(centerX, centerY, gridSize / 4, paint)
                } else {
                    val rect = RectF(
                        centerX - gridSize / 4,
                        centerY - gridSize / 4,
                        centerX + gridSize / 4,
                        centerY + gridSize / 4
                    )
                    canvas.drawRect(rect, paint)
                }
            }
        }
        
        return bitmap
    }
    
    /**
     * Create minimalist wallpaper with 7K branding
     */
    fun create7KBrandedWallpaper(): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Gradient background
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#0D47A1"), Color.parseColor("#1976D2"),
            Shader.TileMode.CLAMP
        )
        
        val backgroundPaint = Paint().apply {
            shader = gradient
        }
        
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Subtle 7K logo in corner
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(Color.WHITE, 30)
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
        }
        
        canvas.drawText("7K", width - 120f, height - 60f, textPaint)
        
        return bitmap
    }
    
    /**
     * Apply wallpaper
     */
    fun applyWallpaper(bitmap: Bitmap) {
        try {
            wallpaperManager.setBitmap(bitmap)
        } catch (e: Exception) {
            throw WallpaperException("Failed to set wallpaper", e)
        }
    }
    
    /**
     * Get wallpaper suggestions based on 7K palette
     */
    fun getWallpaperSuggestions(): List<WallpaperSuggestion> {
        return listOf(
            WallpaperSuggestion(
                "7K Blue",
                "Classic 7K brand color",
                WallpaperType.SOLID,
                SEVENK_PALETTE[0]
            ),
            WallpaperSuggestion(
                "Ocean Gradient",
                "Blue gradient inspired by ocean depths",
                WallpaperType.GRADIENT,
                SEVENK_PALETTE[1],
                SEVENK_PALETTE[2]
            ),
            WallpaperSuggestion(
                "Minimalist Dark",
                "Clean dark theme for focus",
                WallpaperType.SOLID,
                SEVENK_PALETTE[4]
            ),
            WallpaperSuggestion(
                "Geometric Blue",
                "Modern geometric pattern",
                WallpaperType.GEOMETRIC,
                SEVENK_PALETTE[0],
                SEVENK_PALETTE[3]
            ),
            WallpaperSuggestion(
                "7K Branded",
                "Official 7K launcher wallpaper",
                WallpaperType.BRANDED
            )
        )
    }
    
    /**
     * Generate wallpaper from suggestion
     */
    fun generateWallpaper(suggestion: WallpaperSuggestion): Bitmap {
        return when (suggestion.type) {
            WallpaperType.SOLID -> createSolidWallpaper(suggestion.primaryColor)
            WallpaperType.GRADIENT -> createGradientWallpaper(
                suggestion.primaryColor, 
                suggestion.secondaryColor ?: suggestion.primaryColor
            )
            WallpaperType.GEOMETRIC -> createGeometricWallpaper(
                suggestion.primaryColor,
                suggestion.secondaryColor ?: Color.WHITE
            )
            WallpaperType.BRANDED -> create7KBrandedWallpaper()
        }
    }
}

data class WallpaperSuggestion(
    val name: String,
    val description: String,
    val type: WallpaperType,
    val primaryColor: Int = Color.BLACK,
    val secondaryColor: Int? = null
)

enum class WallpaperType {
    SOLID, GRADIENT, GEOMETRIC, BRANDED
}

class WallpaperException(message: String, cause: Throwable? = null) : Exception(message, cause)
