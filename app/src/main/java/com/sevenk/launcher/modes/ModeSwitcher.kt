package com.sevenk.launcher.modes

import android.content.Context
import android.graphics.Color
import com.sevenk.launcher.haptics.HapticFeedbackManager

/**
 * Mode switcher for study/relax/game layouts
 */
class ModeSwitcher(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("mode_switcher", Context.MODE_PRIVATE)
    private val hapticManager = HapticFeedbackManager(context)
    
    enum class LauncherMode {
        NORMAL, STUDY, RELAX, GAME
    }
    
    data class ModeConfig(
        val name: String,
        val description: String,
        val primaryColor: Int,
        val accentColor: Int,
        val hideDistractions: Boolean = false,
        val enableFocus: Boolean = false,
        val customAnimations: Boolean = false,
        val reducedMotion: Boolean = false,
        val hiddenApps: List<String> = emptyList(),
        val priorityApps: List<String> = emptyList()
    )
    
    private val modeConfigs = mapOf(
        LauncherMode.NORMAL to ModeConfig(
            name = "Normal",
            description = "Default launcher experience",
            primaryColor = Color.parseColor("#1976D2"),
            accentColor = Color.parseColor("#42A5F5")
        ),
        LauncherMode.STUDY to ModeConfig(
            name = "Study",
            description = "Focus mode with minimal distractions",
            primaryColor = Color.parseColor("#2E7D32"),
            accentColor = Color.parseColor("#4CAF50"),
            hideDistractions = true,
            enableFocus = true,
            reducedMotion = true,
            hiddenApps = listOf(
                "com.instagram.android",
                "com.snapchat.android",
                "com.tiktok",
                "com.facebook.katana",
                "com.twitter.android",
                "com.youtube.android"
            ),
            priorityApps = listOf(
                "com.google.android.apps.docs.editors.docs",
                "com.microsoft.office.word",
                "com.adobe.reader",
                "org.mozilla.firefox",
                "com.google.android.calendar"
            )
        ),
        LauncherMode.RELAX to ModeConfig(
            name = "Relax",
            description = "Calm interface for downtime",
            primaryColor = Color.parseColor("#5E35B1"),
            accentColor = Color.parseColor("#9C27B0"),
            reducedMotion = true,
            priorityApps = listOf(
                "com.spotify.music",
                "com.netflix.mediaclient",
                "com.amazon.kindle",
                "com.headspace.android",
                "com.calm.android"
            )
        ),
        LauncherMode.GAME to ModeConfig(
            name = "Game",
            description = "Performance optimized for gaming",
            primaryColor = Color.parseColor("#D32F2F"),
            accentColor = Color.parseColor("#F44336"),
            customAnimations = true,
            priorityApps = listOf(
                "com.supercell.clashofclans",
                "com.king.candycrushsaga",
                "com.mojang.minecraftpe",
                "com.roblox.client",
                "com.epicgames.fortnite"
            )
        )
    )
    
    /**
     * Get current mode
     */
    fun getCurrentMode(): LauncherMode {
        val modeName = prefs.getString("current_mode", LauncherMode.NORMAL.name)
        return try {
            LauncherMode.valueOf(modeName ?: LauncherMode.NORMAL.name)
        } catch (e: Exception) {
            LauncherMode.NORMAL
        }
    }
    
    /**
     * Set current mode
     */
    fun setMode(mode: LauncherMode) {
        val previousMode = getCurrentMode()
        prefs.edit().putString("current_mode", mode.name).apply()
        
        // Apply mode configuration
        applyModeConfig(mode)
        
        // Trigger haptic feedback
        hapticManager.performHaptic(HapticFeedbackManager.HapticType.MODE_CHANGE)
        
        // Notify listeners
        notifyModeChanged(previousMode, mode)
    }
    
    /**
     * Get mode configuration
     */
    fun getModeConfig(mode: LauncherMode): ModeConfig {
        return modeConfigs[mode] ?: modeConfigs[LauncherMode.NORMAL]!!
    }
    
    /**
     * Get current mode configuration
     */
    fun getCurrentModeConfig(): ModeConfig {
        return getModeConfig(getCurrentMode())
    }
    
    /**
     * Apply mode configuration
     */
    private fun applyModeConfig(mode: LauncherMode) {
        val config = getModeConfig(mode)
        
        // Store mode-specific settings
        prefs.edit().apply {
            putBoolean("hide_distractions", config.hideDistractions)
            putBoolean("enable_focus", config.enableFocus)
            putBoolean("custom_animations", config.customAnimations)
            putBoolean("reduced_motion", config.reducedMotion)
            putInt("primary_color", config.primaryColor)
            putInt("accent_color", config.accentColor)
            
            // Store hidden apps
            putStringSet("hidden_apps", config.hiddenApps.toSet())
            putStringSet("priority_apps", config.priorityApps.toSet())
            
            apply()
        }
    }
    
    /**
     * Check if app should be hidden in current mode
     */
    fun isAppHidden(packageName: String): Boolean {
        val config = getCurrentModeConfig()
        return config.hideDistractions && config.hiddenApps.contains(packageName)
    }
    
    /**
     * Check if app is priority in current mode
     */
    fun isAppPriority(packageName: String): Boolean {
        val config = getCurrentModeConfig()
        return config.priorityApps.contains(packageName)
    }
    
    /**
     * Get priority apps for current mode
     */
    fun getPriorityApps(): List<String> {
        return getCurrentModeConfig().priorityApps
    }
    
    /**
     * Get hidden apps for current mode
     */
    fun getHiddenApps(): List<String> {
        return getCurrentModeConfig().hiddenApps
    }
    
    /**
     * Check if focus mode is enabled
     */
    fun isFocusModeEnabled(): Boolean {
        return getCurrentModeConfig().enableFocus
    }
    
    /**
     * Check if reduced motion is enabled
     */
    fun isReducedMotionEnabled(): Boolean {
        return getCurrentModeConfig().reducedMotion
    }
    
    /**
     * Get mode-specific colors
     */
    fun getModeColors(): Pair<Int, Int> {
        val config = getCurrentModeConfig()
        return Pair(config.primaryColor, config.accentColor)
    }
    
    /**
     * Cycle to next mode
     */
    fun cycleMode() {
        val currentMode = getCurrentMode()
        val modes = LauncherMode.values()
        val currentIndex = modes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % modes.size
        setMode(modes[nextIndex])
    }
    
    /**
     * Get all available modes
     */
    fun getAllModes(): List<LauncherMode> {
        return LauncherMode.values().toList()
    }
    
    /**
     * Get mode usage statistics
     */
    fun getModeStats(): Map<LauncherMode, Long> {
        val stats = mutableMapOf<LauncherMode, Long>()
        LauncherMode.values().forEach { mode ->
            val timeSpent = prefs.getLong("mode_time_${mode.name}", 0L)
            stats[mode] = timeSpent
        }
        return stats
    }
    
    /**
     * Track time spent in mode
     */
    private var modeStartTime = System.currentTimeMillis()
    
    private fun notifyModeChanged(previousMode: LauncherMode, newMode: LauncherMode) {
        // Track time spent in previous mode
        val timeSpent = System.currentTimeMillis() - modeStartTime
        val previousTime = prefs.getLong("mode_time_${previousMode.name}", 0L)
        prefs.edit().putLong("mode_time_${previousMode.name}", previousTime + timeSpent).apply()
        
        // Reset timer for new mode
        modeStartTime = System.currentTimeMillis()
        
        // Store mode change history
        val changeHistory = prefs.getStringSet("mode_changes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        changeHistory.add("${System.currentTimeMillis()}:${previousMode.name}:${newMode.name}")
        
        // Keep only last 100 changes
        if (changeHistory.size > 100) {
            val sortedChanges = changeHistory.sorted()
            changeHistory.clear()
            changeHistory.addAll(sortedChanges.takeLast(100))
        }
        
        prefs.edit().putStringSet("mode_changes", changeHistory).apply()
    }
    
    /**
     * Get suggested mode based on time and context
     */
    fun getSuggestedMode(): LauncherMode {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 6..11 -> LauncherMode.STUDY // Morning - study time
            in 12..17 -> LauncherMode.NORMAL // Afternoon - normal use
            in 18..21 -> LauncherMode.RELAX // Evening - relax time
            else -> LauncherMode.NORMAL // Night/early morning - normal
        }
    }
    
    /**
     * Auto-switch mode based on time
     */
    fun enableAutoSwitch(enabled: Boolean) {
        prefs.edit().putBoolean("auto_switch_enabled", enabled).apply()
    }
    
    /**
     * Check if auto-switch is enabled
     */
    fun isAutoSwitchEnabled(): Boolean {
        return prefs.getBoolean("auto_switch_enabled", false)
    }
}
