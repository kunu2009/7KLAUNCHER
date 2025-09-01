package com.sevenk.launcher

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Data class representing an installed application
 */
@Serializable
data class AppInfo(
    val name: String,
    val packageName: String,
    val className: String,
    @Transient val icon: Drawable? = null,
    @Transient val applicationInfo: ApplicationInfo? = null
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, className)
    
    val isSystemApp: Boolean
        get() = applicationInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false

    override fun toString(): String = name
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName && className == other.className
    }
    
    override fun hashCode(): Int {
        return packageName.hashCode() * 31 + className.hashCode()
    }
}
