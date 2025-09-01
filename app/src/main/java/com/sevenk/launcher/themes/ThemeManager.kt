package com.sevenk.launcher.themes

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.*

/**
 * Theme management system with light/dark toggle and scheduling
 */
class ThemeManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    
    enum class ThemeMode {
        SYSTEM, LIGHT, DARK, AUTO_SCHEDULE
    }
    
    /**
     * Get current theme mode
     */
    fun getCurrentThemeMode(): ThemeMode {
        val mode = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(mode ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
    
    /**
     * Set theme mode
     */
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        applyTheme()
    }
    
    /**
     * Apply current theme
     */
    fun applyTheme() {
        val mode = getCurrentThemeMode()
        val nightMode = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.AUTO_SCHEDULE -> {
                if (shouldUseDarkTheme()) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        }
        
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    /**
     * Set night mode schedule
     */
    fun setNightModeSchedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        prefs.edit()
            .putInt("night_start_hour", startHour)
            .putInt("night_start_minute", startMinute)
            .putInt("night_end_hour", endHour)
            .putInt("night_end_minute", endMinute)
            .apply()
        
        if (getCurrentThemeMode() == ThemeMode.AUTO_SCHEDULE) {
            applyTheme()
        }
    }
    
    /**
     * Get night mode schedule
     */
    fun getNightModeSchedule(): NightSchedule {
        return NightSchedule(
            startHour = prefs.getInt("night_start_hour", 22),
            startMinute = prefs.getInt("night_start_minute", 0),
            endHour = prefs.getInt("night_end_hour", 6),
            endMinute = prefs.getInt("night_end_minute", 0)
        )
    }
    
    /**
     * Check if dark theme should be used based on schedule
     */
    private fun shouldUseDarkTheme(): Boolean {
        val schedule = getNightModeSchedule()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute
        
        val startTime = schedule.startHour * 60 + schedule.startMinute
        val endTime = schedule.endHour * 60 + schedule.endMinute
        
        return if (startTime < endTime) {
            // Same day schedule (e.g., 10:00 - 18:00)
            currentTime in startTime..endTime
        } else {
            // Overnight schedule (e.g., 22:00 - 06:00)
            currentTime >= startTime || currentTime <= endTime
        }
    }
    
    /**
     * Check if system is in dark mode
     */
    fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * Check if current theme is dark
     */
    fun isCurrentThemeDark(): Boolean {
        return when (getCurrentThemeMode()) {
            ThemeMode.SYSTEM -> isSystemInDarkMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO_SCHEDULE -> shouldUseDarkTheme()
        }
    }
    
    /**
     * Toggle between light and dark theme
     */
    fun toggleTheme() {
        val currentMode = getCurrentThemeMode()
        val newMode = when (currentMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> if (isSystemInDarkMode()) ThemeMode.LIGHT else ThemeMode.DARK
            ThemeMode.AUTO_SCHEDULE -> if (shouldUseDarkTheme()) ThemeMode.LIGHT else ThemeMode.DARK
        }
        setThemeMode(newMode)
    }
}

data class NightSchedule(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)
