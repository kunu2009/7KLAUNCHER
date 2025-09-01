package com.sevenk.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Loads built-in default icons provided with the app (e.g., for internal apps we create).
 * Place files under assets/default_icons/ named by package, e.g.:
 *   app/src/main/assets/default_icons/<package>.png
 *
 * Precedence used by IconCache:
 *   CustomIconManager > DefaultIconProvider > System app icon
 */
object DefaultIconProvider {
    private const val ASSET_DIR = "default_icons"

    fun getDefaultIcon(context: Context, packageName: String): Bitmap? {
        // First, support built-in vector drawables for our internal synthetic packages
        val vectorBitmap = when (packageName) {
            // Keep these in sync with LauncherActivity internal constants
            "internal.7kitihaas" -> loadVectorAsBitmap(context, R.drawable.ic_7k_itihaas)
            "internal.7kpolyglot" -> loadVectorAsBitmap(context, R.drawable.ic_7k_polyglot)
            "internal.7keco" -> loadVectorAsBitmap(context, R.drawable.ic_7k_eco)
            "internal.7klife" -> loadVectorAsBitmap(context, R.drawable.ic_7k_life)
            else -> null
        }
        if (vectorBitmap != null) return vectorBitmap

        // Next, look for bundled asset files by package name
        val am = context.assets
        val fileNamePng = "$ASSET_DIR/${packageName}.png"
        val fileNameWebp = "$ASSET_DIR/${packageName}.webp"
        return try {
            // Prefer PNG; fall back to WEBP if present
            when {
                assetExists(context, fileNamePng) ->
                    am.open(fileNamePng).use { BitmapFactory.decodeStream(it) }
                assetExists(context, fileNameWebp) ->
                    am.open(fileNameWebp).use { BitmapFactory.decodeStream(it) }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun assetExists(context: Context, path: String): Boolean = try {
        context.assets.open(path).close(); true
    } catch (_: Throwable) { false }

    private fun loadVectorAsBitmap(context: Context, resId: Int): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return null
            drawableToBitmap(drawable)
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }
}
