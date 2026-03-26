package com.sevenk.launcher.iconpack

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enhanced helper class to integrate icon pack functionality with existing app adapters
 * Supports multiple icon pack formats and provides caching for performance
 */
class IconPackHelper(private val context: Context) {
    
    private val iconPackManager = IconPackManager(context)
    private val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    
    // Cache for processed icons
    private val iconCache = mutableMapOf<String, Drawable>()
    
    init {
        // Load saved icon pack on initialization
        val savedIconPack = prefs.getString("selected_icon_pack", "")
        if (!savedIconPack.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                iconPackManager.loadIconPack(savedIconPack)
            }
        }
    }
    
    /**
     * Get icon for app, applying current icon pack if available
     */
    fun getAppIcon(packageName: String, className: String?, defaultIcon: Drawable): Drawable {
        val cacheKey = "$packageName|$className"
        
        // Return cached icon if available
        iconCache[cacheKey]?.let { return it }
        
        val componentName = if (className != null) {
            ComponentName(packageName, className)
        } else {
            // Create a generic component name for package-only matching
            ComponentName(packageName, "$packageName.MainActivity")
        }
        
        val iconPackIcon = iconPackManager.getIconForComponent(componentName)
        val resultIcon = iconPackIcon ?: defaultIcon
        
        // Cache the result
        iconCache[cacheKey] = resultIcon
        
        return resultIcon
    }
    
    /**
     * Get available icon packs
     */
    suspend fun getAvailableIconPacks(): List<IconPackManager.IconPack> {
        return iconPackManager.getInstalledIconPacks()
    }
    
    /**
     * Apply selected icon pack
     */
    suspend fun applyIconPack(packageName: String) {
        iconPackManager.loadIconPack(packageName)
        
        // Save selection
        prefs.edit()
            .putString("selected_icon_pack", packageName)
            .apply()
    }
    
    /**
     * Get currently selected icon pack
     */
    fun getCurrentIconPack(): String {
        return prefs.getString("selected_icon_pack", "") ?: ""
    }
    
    /**
     * Clear icon cache (call when icon pack changes)
     */
    fun refreshIcons() {
        iconPackManager.clearCache()
    }

    /**
     * Clear any cached icon pack data if needed
     */
    fun clearCache() {
        iconPackManager.clearCache()
    }
}
