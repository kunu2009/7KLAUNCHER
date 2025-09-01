package com.sevenk.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

object IconCache {
    private val cache: LruCache<String, Bitmap> by lazy {
        val maxMem = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMem / 8 // use 1/8 of available memory
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun getBitmap(context: Context, key: String, drawable: Drawable, targetSizePx: Int? = null): Bitmap {
        cache.get(key)?.let { return it }
        val bmp = drawableToBitmap(drawable, targetSizePx)
        cache.put(key, bmp)
        return bmp
    }

    fun getBitmapForPackage(context: Context, packageName: String, targetSizePx: Int? = null): Bitmap {
        val key = if (targetSizePx != null) "$packageName@$targetSizePx" else packageName
        cache.get(key)?.let { return it }
        // Prefer a user-provided custom icon if available
        try {
            val custom = CustomIconManager.getCustomIconBitmap(context, packageName)
            if (custom != null) {
                val bmp = if (targetSizePx != null && (custom.width != targetSizePx || custom.height != targetSizePx))
                    Bitmap.createScaledBitmap(custom, targetSizePx, targetSizePx, true) else custom
                cache.put(key, bmp)
                return bmp
            }
        } catch (_: Throwable) {}
        // Next, try default icon bundled with the app for internal/specified packages
        try {
            val def = DefaultIconProvider.getDefaultIcon(context, packageName)
            if (def != null) {
                val bmp = if (targetSizePx != null && (def.width != targetSizePx || def.height != targetSizePx))
                    Bitmap.createScaledBitmap(def, targetSizePx, targetSizePx, true) else def
                cache.put(key, bmp)
                return bmp
            }
        } catch (_: Throwable) {}
        val pm = context.packageManager
        return try {
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = drawableToBitmap(drawable, targetSizePx)
            cache.put(key, bmp)
            bmp
        } catch (_: Throwable) {
            // Fallback to host app icon to avoid crashes for synthetic/internal packages
            val hostKey = if (targetSizePx != null) "host:${context.packageName}@$targetSizePx" else "host:${context.packageName}"
            cache.get(hostKey)?.let { return it }
            val hostDrawable = pm.getApplicationIcon(context.packageName)
            val hostBmp = drawableToBitmap(hostDrawable, targetSizePx)
            cache.put(hostKey, hostBmp)
            // Also populate the requested key to avoid repeated lookups
            cache.put(key, hostBmp)
            hostBmp
        }
    }

    private fun drawableToBitmap(drawable: Drawable, targetSizePx: Int? = null): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && targetSizePx == null) {
            return drawable.bitmap
        }
        val width = targetSizePx ?: (drawable.intrinsicWidth.takeIf { it > 0 } ?: 128)
        val height = targetSizePx ?: (drawable.intrinsicHeight.takeIf { it > 0 } ?: 128)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    fun invalidate(packageName: String) {
        // Remove both sized and unsized keys for this package
        val toRemove = mutableListOf<String>()
        synchronized(cache) {
            // LruCache doesn't expose keys; track by trying common sizes or clear by scanning via reflection isn't ideal.
            // As a pragmatic approach, clear entire cache for reliability when a custom icon changes.
        }
        // Full clear to ensure UI refresh of icons everywhere
        cache.evictAll()
    }
}
