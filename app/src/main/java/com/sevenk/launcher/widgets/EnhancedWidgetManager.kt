package com.sevenk.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Enhanced widget manager with resizing and advanced widget support
 */
class EnhancedWidgetManager(
    private val context: Context,
    private val appWidgetHost: AppWidgetHost,
    private val appWidgetManager: AppWidgetManager
) {
    
    private val _widgets = MutableStateFlow<List<LauncherWidget>>(emptyList())
    val widgets: StateFlow<List<LauncherWidget>> = _widgets
    
    private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
    
    init {
        loadSavedWidgets()
    }
    
    data class LauncherWidget(
        val id: Int,
        val packageName: String,
        val className: String,
        val width: Int,
        val height: Int,
        val x: Int = 0,
        val y: Int = 0,
        val page: Int = 0,
        val view: View? = null
    )
    
    data class WidgetSize(
        val width: Int,
        val height: Int,
        val minWidth: Int = 1,
        val minHeight: Int = 1,
        val maxWidth: Int = 4,
        val maxHeight: Int = 4
    )
    
    /**
     * Add widget to launcher
     */
    fun addWidget(widgetId: Int, packageName: String, className: String, width: Int, height: Int): Boolean {
        try {
            val widget = LauncherWidget(
                id = widgetId,
                packageName = packageName,
                className = className,
                width = width,
                height = height
            )
            
            val currentWidgets = _widgets.value.toMutableList()
            currentWidgets.add(widget)
            _widgets.value = currentWidgets
            
            saveWidgets()
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Remove widget from launcher
     */
    fun removeWidget(widgetId: Int): Boolean {
        try {
            val currentWidgets = _widgets.value.toMutableList()
            val removed = currentWidgets.removeAll { it.id == widgetId }
            
            if (removed) {
                _widgets.value = currentWidgets
                appWidgetHost.deleteAppWidgetId(widgetId)
                saveWidgets()
            }
            
            return removed
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Resize widget
     */
    fun resizeWidget(widgetId: Int, newWidth: Int, newHeight: Int): Boolean {
        try {
            val currentWidgets = _widgets.value.toMutableList()
            val widgetIndex = currentWidgets.indexOfFirst { it.id == widgetId }
            
            if (widgetIndex >= 0) {
                val widget = currentWidgets[widgetIndex]
                val resizedWidget = widget.copy(width = newWidth, height = newHeight)
                currentWidgets[widgetIndex] = resizedWidget
                _widgets.value = currentWidgets
                saveWidgets()
                return true
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Move widget to new position
     */
    fun moveWidget(widgetId: Int, newX: Int, newY: Int, newPage: Int = 0): Boolean {
        try {
            val currentWidgets = _widgets.value.toMutableList()
            val widgetIndex = currentWidgets.indexOfFirst { it.id == widgetId }
            
            if (widgetIndex >= 0) {
                val widget = currentWidgets[widgetIndex]
                val movedWidget = widget.copy(x = newX, y = newY, page = newPage)
                currentWidgets[widgetIndex] = movedWidget
                _widgets.value = currentWidgets
                saveWidgets()
                return true
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Create widget view
     */
    fun createWidgetView(widgetId: Int): View? {
        return try {
            appWidgetHost.createView(context, widgetId, 
                appWidgetManager.getAppWidgetInfo(widgetId))
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get widget info
     */
    fun getWidgetInfo(widgetId: Int): LauncherWidget? {
        return _widgets.value.find { it.id == widgetId }
    }
    
    /**
     * Get widgets for specific page
     */
    fun getWidgetsForPage(page: Int): List<LauncherWidget> {
        return _widgets.value.filter { it.page == page }
    }
    
    /**
     * Get all available widget providers
     */
    fun getAvailableWidgetProviders(): List<WidgetProviderInfo> {
        val providers = mutableListOf<WidgetProviderInfo>()
        
        try {
            val widgetProviders = appWidgetManager.installedProviders
            
            for (provider in widgetProviders) {
                val info = WidgetProviderInfo(
                    packageName = provider.provider.packageName,
                    className = provider.provider.className,
                    label = provider.loadLabel(context.packageManager),
                    icon = provider.loadIcon(context, 0),
                    minWidth = provider.minWidth,
                    minHeight = provider.minHeight,
                    previewImage = provider.previewImage
                )
                providers.add(info)
            }
        } catch (e: Exception) {
            // Handle error
        }
        
        return providers
    }
    
    /**
     * Check if position is available for widget
     */
    fun isPositionAvailable(x: Int, y: Int, width: Int, height: Int, page: Int, excludeWidgetId: Int = -1): Boolean {
        val pageWidgets = getWidgetsForPage(page).filter { it.id != excludeWidgetId }
        
        for (widget in pageWidgets) {
            // Check if rectangles overlap
            val overlapX = !(x >= widget.x + widget.width || x + width <= widget.x)
            val overlapY = !(y >= widget.y + widget.height || y + height <= widget.y)
            
            if (overlapX && overlapY) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Find best position for widget
     */
    fun findBestPosition(width: Int, height: Int, page: Int): Pair<Int, Int>? {
        val gridWidth = 4
        val gridHeight = 6
        
        // Try to find the first available position
        for (y in 0..gridHeight - height) {
            for (x in 0..gridWidth - width) {
                if (isPositionAvailable(x, y, width, height, page)) {
                    return Pair(x, y)
                }
            }
        }
        
        return null
    }
    
    /**
     * Save widgets to preferences
     */
    private fun saveWidgets() {
        try {
            val editor = prefs.edit()
            
            // Clear existing widget preferences
            val allPrefs = prefs.all
            allPrefs.keys.filter { it.startsWith("widget_") }.forEach { key ->
                editor.remove(key)
            }
            
            // Save current widgets
            val currentWidgets = _widgets.value
            editor.putInt("widget_count", currentWidgets.size)
            
            currentWidgets.forEachIndexed { index, widget ->
                editor.putInt("widget_${index}_id", widget.id)
                editor.putString("widget_${index}_package", widget.packageName)
                editor.putString("widget_${index}_class", widget.className)
                editor.putInt("widget_${index}_width", widget.width)
                editor.putInt("widget_${index}_height", widget.height)
                editor.putInt("widget_${index}_x", widget.x)
                editor.putInt("widget_${index}_y", widget.y)
                editor.putInt("widget_${index}_page", widget.page)
            }
            
            editor.apply()
        } catch (e: Exception) {
            // Handle save error
        }
    }
    
    /**
     * Load saved widgets from preferences
     */
    private fun loadSavedWidgets() {
        try {
            val widgetCount = prefs.getInt("widget_count", 0)
            val widgets = mutableListOf<LauncherWidget>()
            
            for (i in 0 until widgetCount) {
                val id = prefs.getInt("widget_${i}_id", -1)
                val packageName = prefs.getString("widget_${i}_package", "") ?: ""
                val className = prefs.getString("widget_${i}_class", "") ?: ""
                val width = prefs.getInt("widget_${i}_width", 1)
                val height = prefs.getInt("widget_${i}_height", 1)
                val x = prefs.getInt("widget_${i}_x", 0)
                val y = prefs.getInt("widget_${i}_y", 0)
                val page = prefs.getInt("widget_${i}_page", 0)
                
                if (id != -1 && packageName.isNotEmpty() && className.isNotEmpty()) {
                    val widget = LauncherWidget(
                        id = id,
                        packageName = packageName,
                        className = className,
                        width = width,
                        height = height,
                        x = x,
                        y = y,
                        page = page
                    )
                    widgets.add(widget)
                }
            }
            
            _widgets.value = widgets
        } catch (e: Exception) {
            // Handle load error
            _widgets.value = emptyList()
        }
    }
    
    /**
     * Clear all widgets
     */
    fun clearAllWidgets() {
        val currentWidgets = _widgets.value
        currentWidgets.forEach { widget ->
            appWidgetHost.deleteAppWidgetId(widget.id)
        }
        
        _widgets.value = emptyList()
        saveWidgets()
    }
    
    data class WidgetProviderInfo(
        val packageName: String,
        val className: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
        val minWidth: Int,
        val minHeight: Int,
        val previewImage: Int = 0
    )
}