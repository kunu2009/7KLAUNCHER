package com.sevenk.launcher.grid

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Manages custom grid sizes and layout calculations
 */
class CustomGridManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("grid_settings", Context.MODE_PRIVATE)
    
    data class GridConfiguration(
        val columns: Int,
        val rows: Int,
        val iconSize: Int,
        val spacing: Int,
        val paddingHorizontal: Int,
        val paddingVertical: Int
    )
    
    companion object {
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 8
        const val MIN_ROWS = 4
        const val MAX_ROWS = 12
        const val DEFAULT_COLUMNS = 5
        const val DEFAULT_ROWS = 6
    }
    
    /**
     * Get current grid configuration for home screen
     */
    fun getHomeGridConfig(): GridConfiguration {
        val columns = prefs.getInt("home_columns", DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val rows = prefs.getInt("home_rows", DEFAULT_ROWS).coerceIn(MIN_ROWS, MAX_ROWS)
        
        return calculateGridConfig(columns, rows, "home")
    }
    
    /**
     * Get current grid configuration for app drawer
     */
    fun getDrawerGridConfig(): GridConfiguration {
        val columns = prefs.getInt("drawer_columns", DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val rows = prefs.getInt("drawer_rows", DEFAULT_ROWS).coerceIn(MIN_ROWS, MAX_ROWS)
        
        return calculateGridConfig(columns, rows, "drawer")
    }
    
    /**
     * Get current grid configuration for folders
     */
    fun getFolderGridConfig(): GridConfiguration {
        val columns = prefs.getInt("folder_columns", 3).coerceIn(2, 5)
        val rows = prefs.getInt("folder_rows", 3).coerceIn(2, 5)
        
        return calculateGridConfig(columns, rows, "folder")
    }
    
    /**
     * Set home screen grid size
     */
    fun setHomeGridSize(columns: Int, rows: Int) {
        prefs.edit()
            .putInt("home_columns", columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
            .putInt("home_rows", rows.coerceIn(MIN_ROWS, MAX_ROWS))
            .apply()
    }
    
    /**
     * Set app drawer grid size
     */
    fun setDrawerGridSize(columns: Int, rows: Int) {
        prefs.edit()
            .putInt("drawer_columns", columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
            .putInt("drawer_rows", rows.coerceIn(MIN_ROWS, MAX_ROWS))
            .apply()
    }
    
    /**
     * Set folder grid size
     */
    fun setFolderGridSize(columns: Int, rows: Int) {
        prefs.edit()
            .putInt("folder_columns", columns.coerceIn(2, 5))
            .putInt("folder_rows", rows.coerceIn(2, 5))
            .apply()
    }
    
    /**
     * Calculate optimal grid configuration based on screen size
     */
    private fun calculateGridConfig(columns: Int, rows: Int, type: String): GridConfiguration {
        val displayMetrics = getDisplayMetrics()
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Base padding and spacing
        val basePadding = (16 * displayMetrics.density).toInt()
        val baseSpacing = (8 * displayMetrics.density).toInt()
        
        // Adjust available space based on type
        val availableWidth = when (type) {
            "home" -> screenWidth - basePadding * 2
            "drawer" -> screenWidth - basePadding * 2
            "folder" -> min((screenWidth * 0.8).toInt(), (400 * displayMetrics.density).toInt())
            else -> screenWidth - basePadding * 2
        }
        
        val availableHeight = when (type) {
            "home" -> (screenHeight * 0.6).toInt() // Account for dock, status bar, etc.
            "drawer" -> (screenHeight * 0.8).toInt()
            "folder" -> min((screenHeight * 0.6).toInt(), (400 * displayMetrics.density).toInt())
            else -> (screenHeight * 0.6).toInt()
        }
        
        // Calculate icon size based on available space
        val spacingWidth = (columns - 1) * baseSpacing
        val spacingHeight = (rows - 1) * baseSpacing
        
        val iconWidthFromColumns = (availableWidth - spacingWidth) / columns
        val iconHeightFromRows = (availableHeight - spacingHeight) / rows
        
        // Use the smaller dimension to ensure icons fit
        val calculatedIconSize = min(iconWidthFromColumns, iconHeightFromRows)
        
        // Apply size constraints
        val minIconSize = (48 * displayMetrics.density).toInt()
        val maxIconSize = (96 * displayMetrics.density).toInt()
        val iconSize = calculatedIconSize.coerceIn(minIconSize, maxIconSize)
        
        // Recalculate spacing if needed
        val totalIconWidth = columns * iconSize
        val totalIconHeight = rows * iconSize
        
        val spacing = min(
            (availableWidth - totalIconWidth) / max(1, columns - 1),
            (availableHeight - totalIconHeight) / max(1, rows - 1)
        ).coerceAtLeast(baseSpacing)
        
        // Calculate padding to center the grid
        val usedWidth = totalIconWidth + (columns - 1) * spacing
        val usedHeight = totalIconHeight + (rows - 1) * spacing
        
        val paddingHorizontal = max(basePadding, (screenWidth - usedWidth) / 2)
        val paddingVertical = max(basePadding, (availableHeight - usedHeight) / 2)
        
        return GridConfiguration(
            columns = columns,
            rows = rows,
            iconSize = iconSize,
            spacing = spacing,
            paddingHorizontal = paddingHorizontal,
            paddingVertical = paddingVertical
        )
    }
    
    /**
     * Get display metrics
     */
    private fun getDisplayMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics
    }
    
    /**
     * Calculate position for item in grid
     */
    fun calculateItemPosition(index: Int, config: GridConfiguration): Pair<Int, Int> {
        val row = index / config.columns
        val column = index % config.columns
        
        val x = config.paddingHorizontal + column * (config.iconSize + config.spacing)
        val y = config.paddingVertical + row * (config.iconSize + config.spacing)
        
        return Pair(x, y)
    }
    
    /**
     * Calculate grid index from coordinates
     */
    fun calculateGridIndex(x: Float, y: Float, config: GridConfiguration): Int? {
        val adjustedX = x - config.paddingHorizontal
        val adjustedY = y - config.paddingVertical
        
        if (adjustedX < 0 || adjustedY < 0) return null
        
        val column = (adjustedX / (config.iconSize + config.spacing)).toInt()
        val row = (adjustedY / (config.iconSize + config.spacing)).toInt()
        
        if (column >= config.columns || row >= config.rows) return null
        
        return row * config.columns + column
    }
    
    /**
     * Get optimal grid size for screen
     */
    fun getOptimalGridSize(targetIconSize: Int): Pair<Int, Int> {
        val displayMetrics = getDisplayMetrics()
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val basePadding = (16 * displayMetrics.density).toInt()
        val baseSpacing = (8 * displayMetrics.density).toInt()
        
        val availableWidth = screenWidth - basePadding * 2
        val availableHeight = (screenHeight * 0.6).toInt()
        
        val columns = max(MIN_COLUMNS, min(MAX_COLUMNS, 
            (availableWidth + baseSpacing) / (targetIconSize + baseSpacing)))
        val rows = max(MIN_ROWS, min(MAX_ROWS, 
            (availableHeight + baseSpacing) / (targetIconSize + baseSpacing)))
        
        return Pair(columns, rows)
    }
    
    /**
     * Reset all grid settings to defaults
     */
    fun resetToDefaults() {
        prefs.edit()
            .putInt("home_columns", DEFAULT_COLUMNS)
            .putInt("home_rows", DEFAULT_ROWS)
            .putInt("drawer_columns", DEFAULT_COLUMNS)
            .putInt("drawer_rows", DEFAULT_ROWS)
            .putInt("folder_columns", 3)
            .putInt("folder_rows", 3)
            .apply()
    }
}
