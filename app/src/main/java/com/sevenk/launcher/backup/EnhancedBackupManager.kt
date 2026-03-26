package com.sevenk.launcher.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced backup and restore manager for 7K Launcher settings and data
 */
class EnhancedBackupManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
    private val dockPrefs = context.getSharedPreferences("dock_apps", Context.MODE_PRIVATE)
    private val sidebarPrefs = context.getSharedPreferences("sidebar_apps", Context.MODE_PRIVATE)
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    @Serializable
    data class LauncherBackup(
        val version: Int = 1,
        val timestamp: Long = System.currentTimeMillis(),
        val launcherSettings: Map<String, String>,
        val dockApps: List<String>,
        val sidebarApps: List<String>,
        val gestureSettings: Map<String, String>,
        val iconPackSettings: Map<String, String>,
        val customWallpaperUri: String? = null,
        val appHiddenStates: Map<String, Boolean> = emptyMap(),
        val customAppNames: Map<String, String> = emptyMap(),
        val widgetSettings: List<WidgetBackupData> = emptyList()
    )
    
    @Serializable
    data class WidgetBackupData(
        val id: Int,
        val packageName: String,
        val className: String,
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int
    )
    
    /**
     * Create a complete backup of launcher settings
     */
    suspend fun createBackup(): LauncherBackup = withContext(Dispatchers.IO) {
        val launcherSettings = mutableMapOf<String, String>()
        val gestureSettings = mutableMapOf<String, String>()
        val iconPackSettings = mutableMapOf<String, String>()
        val hiddenApps = mutableMapOf<String, Boolean>()
        val customNames = mutableMapOf<String, String>()
        
        // Backup launcher settings
        prefs.all.forEach { (key, value) ->
            when {
                key.startsWith("gesture_") -> gestureSettings[key] = value.toString()
                key.startsWith("iconpack_") || key == "selected_icon_pack" -> 
                    iconPackSettings[key] = value.toString()
                key.startsWith("hidden_app_") -> 
                    hiddenApps[key.removePrefix("hidden_app_")] = value as? Boolean ?: false
                key.startsWith("custom_name_") ->
                    customNames[key.removePrefix("custom_name_")] = value.toString()
                else -> launcherSettings[key] = value.toString()
            }
        }
        
        // Backup dock apps
        val dockApps = mutableListOf<String>()
        dockPrefs.all.forEach { (key, value) ->
            if (key.startsWith("app_")) {
                dockApps.add(value.toString())
            }
        }
        
        // Backup sidebar apps
        val sidebarApps = mutableListOf<String>()
        sidebarPrefs.all.forEach { (key, value) ->
            if (key.startsWith("app_")) {
                sidebarApps.add(value.toString())
            }
        }
        
        LauncherBackup(
            launcherSettings = launcherSettings,
            dockApps = dockApps,
            sidebarApps = sidebarApps,
            gestureSettings = gestureSettings,
            iconPackSettings = iconPackSettings,
            customWallpaperUri = prefs.getString("custom_bg_uri", null),
            appHiddenStates = hiddenApps,
            customAppNames = customNames
        )
    }
    
    /**
     * Export backup to file
     */
    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = createBackup()
            val jsonString = json.encodeToString(backup)
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Import backup from file
     */
    suspend fun importBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.readBytes().toString(Charsets.UTF_8)
                val backup = json.decodeFromString<LauncherBackup>(jsonString)
                restoreFromBackup(backup)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Restore settings from backup data
     */
    private suspend fun restoreFromBackup(backup: LauncherBackup) = withContext(Dispatchers.IO) {
        // Clear existing settings
        prefs.edit().clear().apply()
        dockPrefs.edit().clear().apply()
        sidebarPrefs.edit().clear().apply()
        
        // Restore launcher settings
        val prefsEditor = prefs.edit()
        backup.launcherSettings.forEach { (key, value) ->
            // Try to restore with correct type
            when {
                value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) ->
                    prefsEditor.putBoolean(key, value.toBoolean())
                value.toIntOrNull() != null -> prefsEditor.putInt(key, value.toInt())
                value.toFloatOrNull() != null -> prefsEditor.putFloat(key, value.toFloat())
                else -> prefsEditor.putString(key, value)
            }
        }
        
        // Restore gesture settings
        backup.gestureSettings.forEach { (key, value) ->
            prefsEditor.putString(key, value)
        }
        
        // Restore icon pack settings
        backup.iconPackSettings.forEach { (key, value) ->
            prefsEditor.putString(key, value)
        }
        
        // Restore hidden app states
        backup.appHiddenStates.forEach { (packageName, hidden) ->
            prefsEditor.putBoolean("hidden_app_$packageName", hidden)
        }
        
        // Restore custom app names
        backup.customAppNames.forEach { (packageName, customName) ->
            prefsEditor.putString("custom_name_$packageName", customName)
        }
        
        // Restore custom wallpaper
        backup.customWallpaperUri?.let { uri ->
            prefsEditor.putString("custom_bg_uri", uri)
        }
        
        prefsEditor.apply()
        
        // Restore dock apps
        val dockEditor = dockPrefs.edit()
        backup.dockApps.forEachIndexed { index, packageName ->
            dockEditor.putString("app_$index", packageName)
        }
        dockEditor.apply()
        
        // Restore sidebar apps
        val sidebarEditor = sidebarPrefs.edit()
        backup.sidebarApps.forEachIndexed { index, packageName ->
            sidebarEditor.putString("app_$index", packageName)
        }
        sidebarEditor.apply()
    }
    
    /**
     * Get suggested backup filename
     */
    fun getSuggestedBackupFilename(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "7K_Launcher_Backup_$timestamp.json"
    }
    
    /**
     * Validate backup file
     */
    suspend fun validateBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.readBytes().toString(Charsets.UTF_8)
                val backup = json.decodeFromString<LauncherBackup>(jsonString)
                backup.version > 0 && backup.timestamp > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get backup info without importing
     */
    suspend fun getBackupInfo(uri: Uri): BackupInfo? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.readBytes().toString(Charsets.UTF_8)
                val backup = json.decodeFromString<LauncherBackup>(jsonString)
                BackupInfo(
                    version = backup.version,
                    timestamp = backup.timestamp,
                    dockAppsCount = backup.dockApps.size,
                    sidebarAppsCount = backup.sidebarApps.size,
                    hasCustomWallpaper = backup.customWallpaperUri != null,
                    hiddenAppsCount = backup.appHiddenStates.size,
                    customNamesCount = backup.customAppNames.size
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    data class BackupInfo(
        val version: Int,
        val timestamp: Long,
        val dockAppsCount: Int,
        val sidebarAppsCount: Int,
        val hasCustomWallpaper: Boolean,
        val hiddenAppsCount: Int,
        val customNamesCount: Int
    )
}