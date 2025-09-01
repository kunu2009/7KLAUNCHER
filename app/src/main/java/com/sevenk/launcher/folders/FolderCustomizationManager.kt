package com.sevenk.launcher.folders

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.sevenk.launcher.R

/**
 * Manages folder appearance customization options
 */
class FolderCustomizationManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("folder_customization_prefs", Context.MODE_PRIVATE)

    /**
     * Enum representing folder style options
     */
    enum class FolderStyle {
        GLASS_PANEL,
        CIRCLE,
        SQUARE,
        ROUNDED_SQUARE
    }

    /**
     * Enum representing more specific folder icon layouts
     */
    enum class FolderIconLayout {
        GRID_2x2,
        GRID_3x3,
        PREVIEW_4,
        GRID,    // Standard grid layout (2x2)
        STACK,   // Stacked layout (cards)
        SCATTER, // Scattered preview
        MINIMAL  // Simple folder icon
    }

    /**
     * Get customization for a specific folder
     */
    fun getFolderCustomization(folderId: String): FolderCustomization {
        return FolderCustomization(
            backgroundColor = prefs.getInt("${folderId}_background_color", getDefaultBackgroundColor()),
            backgroundAlpha = prefs.getFloat("${folderId}_background_alpha", 0.85f),
            textColor = prefs.getInt("${folderId}_text_color", Color.WHITE),
            iconLayout = getFolderIconLayout(folderId),
            rounded = prefs.getBoolean("${folderId}_rounded", true),
            blurEnabled = prefs.getBoolean("${folderId}_blur", true),
            titleEnabled = prefs.getBoolean("${folderId}_title_enabled", true),
            useCustomColors = prefs.getBoolean("${folderId}_use_custom_colors", false)
        )
    }

    /**
     * Save customization for a specific folder
     */
    fun saveFolderCustomization(folderId: String, customization: FolderCustomization) {
        prefs.edit().apply {
            putInt("${folderId}_background_color", customization.backgroundColor)
            putFloat("${folderId}_background_alpha", customization.backgroundAlpha)
            putInt("${folderId}_text_color", customization.textColor)
            putString("${folderId}_icon_layout", customization.iconLayout.name)
            putBoolean("${folderId}_rounded", customization.rounded)
            putBoolean("${folderId}_blur", customization.blurEnabled)
            putBoolean("${folderId}_title_enabled", customization.titleEnabled)
            putBoolean("${folderId}_use_custom_colors", customization.useCustomColors)
        }.apply()
    }

    /**
     * Delete customization for a specific folder
     */
    fun deleteFolderCustomization(folderId: String) {
        prefs.edit().remove("${folderId}_background_color")
            .remove("${folderId}_background_alpha")
            .remove("${folderId}_text_color")
            .remove("${folderId}_icon_layout")
            .remove("${folderId}_rounded")
            .remove("${folderId}_blur")
            .remove("${folderId}_title_enabled")
            .remove("${folderId}_use_custom_colors")
            .apply()
    }

    /**
     * Get the folder icon layout for a folder
     */
    private fun getFolderIconLayout(folderId: String): FolderIconLayout {
        val layoutName = prefs.getString("${folderId}_icon_layout", FolderIconLayout.GRID.name)
        return try {
            FolderIconLayout.valueOf(layoutName ?: FolderIconLayout.GRID.name)
        } catch (e: Exception) {
            FolderIconLayout.GRID
        }
    }

    /**
     * Get the default background color
     */
    private fun getDefaultBackgroundColor(): Int {
        return try {
            // Use a default folder background color since the resource doesn't exist
            Color.parseColor("#33000000") // Semi-transparent black
        } catch (e: Exception) {
            Color.parseColor("#33000000")
        }
    }

    /**
     * Data class representing folder customization options
     */
    data class FolderCustomization(
        val backgroundColor: Int,
        val backgroundAlpha: Float,
        val textColor: Int,
        val iconLayout: FolderIconLayout,
        val rounded: Boolean,
        val blurEnabled: Boolean,
        val titleEnabled: Boolean,
        val useCustomColors: Boolean,
        val style: FolderStyle = FolderStyle.GLASS_PANEL // Added style property
    )
}
