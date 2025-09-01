package com.sevenk.launcher

import android.content.res.Resources

/**
 * Extension functions for common operations
 */

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Float.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density

// Helper functions to match existing call sites like dp(72)
fun dp(value: Int): Int = (value * Resources.getSystem().displayMetrics.density).toInt()
fun dp(value: Float): Float = value * Resources.getSystem().displayMetrics.density
