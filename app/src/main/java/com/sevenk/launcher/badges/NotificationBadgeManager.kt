package com.sevenk.launcher.badges

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Manages notification badges for app icons
 */
class NotificationBadgeManager(private val context: Context) {
    
    private val badgeCache = mutableMapOf<String, Int>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    
    /**
     * Get notification count for a package
     */
    fun getNotificationCount(packageName: String): Int {
        return badgeCache[packageName] ?: 0
    }
    
    /**
     * Update notification counts from active notifications
     */
    fun updateNotificationCounts() {
        badgeCache.clear()
        
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return
            }
            
            // Note: This requires notification access permission
            // In a real implementation, you'd need to use NotificationListenerService
            // For now, we'll simulate with placeholder logic
            
            val notificationCounts = getActiveNotificationCounts()
            badgeCache.putAll(notificationCounts)
            
        } catch (e: SecurityException) {
            // Handle permission not granted
        }
    }
    
    /**
     * Create app icon with notification badge overlay
     */
    fun createBadgedIcon(originalIcon: Drawable, packageName: String, iconSize: Int): Bitmap {
        val count = getNotificationCount(packageName)
        if (count <= 0) {
            return originalIcon.toBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        }
        
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw original icon
        originalIcon.setBounds(0, 0, iconSize, iconSize)
        originalIcon.draw(canvas)
        
        // Draw badge
        drawNotificationBadge(canvas, count, iconSize)
        
        return bitmap
    }
    
    private fun drawNotificationBadge(canvas: Canvas, count: Int, iconSize: Int) {
        val badgeSize = (iconSize * 0.3f).coerceAtLeast(24f)
        val badgeX = iconSize - badgeSize / 2f
        val badgeY = badgeSize / 2f
        
        // Draw badge background
        canvas.drawCircle(badgeX, badgeY, badgeSize / 2f, paint)
        
        // Draw count text
        val countText = if (count > 99) "99+" else count.toString()
        textPaint.textSize = badgeSize * 0.6f
        
        val textBounds = Rect()
        textPaint.getTextBounds(countText, 0, countText.length, textBounds)
        val textY = badgeY + textBounds.height() / 2f
        
        canvas.drawText(countText, badgeX, textY, textPaint)
    }
    
    /**
     * Placeholder for getting active notification counts
     * In a real implementation, this would use NotificationListenerService
     */
    private fun getActiveNotificationCounts(): Map<String, Int> {
        // Simulate notification counts for demo purposes
        return mapOf(
            "com.whatsapp" to 5,
            "com.google.android.gm" to 12,
            "com.facebook.katana" to 3,
            "com.instagram.android" to 8,
            "com.twitter.android" to 2
        )
    }
    
    /**
     * Check if notification access permission is granted
     */
    fun hasNotificationAccess(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }
    
    /**
     * Clear all cached notification counts
     */
    fun clearCache() {
        badgeCache.clear()
    }
    
    /**
     * Set custom badge count for testing
     */
    fun setBadgeCount(packageName: String, count: Int) {
        badgeCache[packageName] = count
    }
}
