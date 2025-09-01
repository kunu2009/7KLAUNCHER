package com.sevenk.launcher.adaptive

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap

/**
 * Helper class for handling adaptive icons on Android 8.0+ (API 26+)
 */
class AdaptiveIconHelper(private val context: Context) {
    
    companion object {
        private const val ADAPTIVE_ICON_SIZE = 108f
        private const val ICON_SIZE = 72f
        private const val SCALE_FACTOR = ICON_SIZE / ADAPTIVE_ICON_SIZE
    }
    
    /**
     * Check if adaptive icons are supported on this device
     */
    fun isAdaptiveIconSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    /**
     * Get app icon with adaptive icon handling
     */
    fun getAppIcon(packageName: String, size: Int): Bitmap? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(appInfo)
            
            if (isAdaptiveIconSupported() && drawable is AdaptiveIconDrawable) {
                createAdaptiveIconBitmap(drawable, size)
            } else {
                drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create bitmap from adaptive icon with proper scaling and masking
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAdaptiveIconBitmap(adaptiveIcon: AdaptiveIconDrawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Create circular mask for adaptive icon
        val path = Path()
        path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        canvas.clipPath(path)
        
        // Scale and center the adaptive icon
        val scale = size * SCALE_FACTOR / ADAPTIVE_ICON_SIZE
        val offset = (size - size * SCALE_FACTOR) / 2f
        
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(offset / scale, offset / scale)
        
        // Draw background layer
        adaptiveIcon.background?.let { background ->
            background.setBounds(0, 0, ADAPTIVE_ICON_SIZE.toInt(), ADAPTIVE_ICON_SIZE.toInt())
            background.draw(canvas)
        }
        
        // Draw foreground layer
        adaptiveIcon.foreground?.let { foreground ->
            foreground.setBounds(0, 0, ADAPTIVE_ICON_SIZE.toInt(), ADAPTIVE_ICON_SIZE.toInt())
            foreground.draw(canvas)
        }
        
        canvas.restore()
        
        return bitmap
    }
    
    /**
     * Create themed adaptive icon with custom background
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun createThemedAdaptiveIcon(
        adaptiveIcon: AdaptiveIconDrawable,
        backgroundColor: Int,
        size: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Create circular background
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = backgroundColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Create circular mask
        val path = Path()
        path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        canvas.clipPath(path)
        
        // Scale and center the foreground only
        val scale = size * 0.6f / ADAPTIVE_ICON_SIZE // Smaller scale for themed icons
        val offset = (size - size * 0.6f) / 2f
        
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(offset / scale, offset / scale)
        
        // Draw only foreground layer for themed appearance
        adaptiveIcon.foreground?.let { foreground ->
            foreground.setBounds(0, 0, ADAPTIVE_ICON_SIZE.toInt(), ADAPTIVE_ICON_SIZE.toInt())
            
            // Apply color filter for monochrome themed look
            val colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            foreground.colorFilter = colorFilter
            foreground.draw(canvas)
        }
        
        canvas.restore()
        
        return bitmap
    }
    
    /**
     * Create icon with different shape masks (circle, squircle, rounded square)
     */
    fun createShapedIcon(drawable: Drawable, shape: IconShape, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val path = when (shape) {
            IconShape.CIRCLE -> createCirclePath(size)
            IconShape.ROUNDED_SQUARE -> createRoundedSquarePath(size, size * 0.2f)
            IconShape.SQUIRCLE -> createSquirclePath(size)
            IconShape.TEARDROP -> createTeardropPath(size)
        }
        
        canvas.clipPath(path)
        
        // Draw the icon
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        
        return bitmap
    }
    
    private fun createCirclePath(size: Int): Path {
        val path = Path()
        path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        return path
    }
    
    private fun createRoundedSquarePath(size: Int, cornerRadius: Float): Path {
        val path = Path()
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        return path
    }
    
    private fun createSquirclePath(size: Int): Path {
        val path = Path()
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val radius = size * 0.3f
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        return path
    }
    
    private fun createTeardropPath(size: Int): Path {
        val path = Path()
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2f
        
        // Create teardrop shape (circle with pointed top)
        path.addCircle(centerX, centerY + radius * 0.2f, radius * 0.8f, Path.Direction.CW)
        
        // Add pointed top
        path.moveTo(centerX, 0f)
        path.lineTo(centerX - radius * 0.3f, centerY - radius * 0.2f)
        path.lineTo(centerX + radius * 0.3f, centerY - radius * 0.2f)
        path.close()
        
        return path
    }
    
    enum class IconShape {
        CIRCLE,
        ROUNDED_SQUARE,
        SQUIRCLE,
        TEARDROP
    }
}
