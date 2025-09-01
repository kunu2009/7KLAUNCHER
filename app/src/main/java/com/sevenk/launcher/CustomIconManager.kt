package com.sevenk.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object CustomIconManager {
    private const val DIR = "custom_icons"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    private fun fileFor(context: Context, packageName: String): File = File(dir(context), "$packageName.png")

    fun hasCustomIcon(context: Context, packageName: String): Boolean = fileFor(context, packageName).exists()

    fun getCustomIconBitmap(context: Context, packageName: String): Bitmap? {
        val f = fileFor(context, packageName)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    fun setCustomIconFromUri(context: Context, packageName: String, uri: Uri, targetSizePx: Int? = null): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                var bmp = BitmapFactory.decodeStream(input) ?: return false
                if (targetSizePx != null) {
                    bmp = Bitmap.createScaledBitmap(bmp, targetSizePx, targetSizePx, true)
                }
                saveBitmap(context, packageName, bmp)
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun saveBitmap(context: Context, packageName: String, bitmap: Bitmap): Boolean {
        return try {
            val outFile = fileFor(context, packageName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun removeCustomIcon(context: Context, packageName: String): Boolean {
        val f = fileFor(context, packageName)
        return if (f.exists()) f.delete() else false
    }
}
