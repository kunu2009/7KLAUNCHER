package com.sevenk.launcher.drawer

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages favorites and frequently used apps in app drawer
 */
class FavoritesManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("favorites_settings", Context.MODE_PRIVATE)
    private val usageTracker = ConcurrentHashMap<String, AppUsageData>()
    private val favoritesScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    data class AppUsageData(
        val packageName: String,
        var launchCount: Int = 0,
        var lastLaunchTime: Long = 0L,
        var totalUsageTime: Long = 0L,
        var isFavorite: Boolean = false
    )
    
    init {
        loadUsageData()
    }
    
    /**
     * Track app launch
     */
    fun trackAppLaunch(packageName: String) {
        favoritesScope.launch {
            val currentTime = System.currentTimeMillis()
            val usageData = usageTracker.getOrPut(packageName) {
                AppUsageData(packageName)
            }
            
            usageData.launchCount++
            usageData.lastLaunchTime = currentTime
            
            saveUsageData()
        }
    }
    
    /**
     * Track app usage time
     */
    fun trackAppUsageTime(packageName: String, usageTimeMs: Long) {
        favoritesScope.launch {
            val usageData = usageTracker.getOrPut(packageName) {
                AppUsageData(packageName)
            }
            
            usageData.totalUsageTime += usageTimeMs
            saveUsageData()
        }
    }
    
    /**
     * Add app to favorites
     */
    fun addToFavorites(packageName: String) {
        val usageData = usageTracker.getOrPut(packageName) {
            AppUsageData(packageName)
        }
        usageData.isFavorite = true
        
        favoritesScope.launch {
            saveUsageData()
        }
    }
    
    /**
     * Remove app from favorites
     */
    fun removeFromFavorites(packageName: String) {
        usageTracker[packageName]?.let { usageData ->
            usageData.isFavorite = false
        }
        
        favoritesScope.launch {
            saveUsageData()
        }
    }
    
    /**
     * Get favorite apps
     */
    fun getFavoriteApps(): List<String> {
        return usageTracker.values
            .filter { it.isFavorite }
            .sortedByDescending { it.launchCount }
            .map { it.packageName }
    }
    
    /**
     * Get frequently used apps (excluding favorites)
     */
    fun getFrequentlyUsedApps(limit: Int = 10): List<String> {
        val currentTime = System.currentTimeMillis()
        val oneWeekAgo = currentTime - (7 * 24 * 60 * 60 * 1000L)
        
        return usageTracker.values
            .filter { !it.isFavorite && it.lastLaunchTime > oneWeekAgo }
            .sortedWith(compareByDescending<AppUsageData> { it.launchCount }
                .thenByDescending { it.totalUsageTime }
                .thenByDescending { it.lastLaunchTime })
            .take(limit)
            .map { it.packageName }
    }
    
    /**
     * Get recent apps
     */
    fun getRecentApps(limit: Int = 8): List<String> {
        val currentTime = System.currentTimeMillis()
        val oneDayAgo = currentTime - (24 * 60 * 60 * 1000L)
        
        return usageTracker.values
            .filter { it.lastLaunchTime > oneDayAgo }
            .sortedByDescending { it.lastLaunchTime }
            .take(limit)
            .map { it.packageName }
    }
    
    /**
     * Get app usage score for sorting
     */
    fun getAppUsageScore(packageName: String): Double {
        val usageData = usageTracker[packageName] ?: return 0.0
        val currentTime = System.currentTimeMillis()
        
        // Calculate recency factor (more recent = higher score)
        val daysSinceLastLaunch = (currentTime - usageData.lastLaunchTime) / (24 * 60 * 60 * 1000.0)
        val recencyFactor = kotlin.math.exp(-daysSinceLastLaunch / 7.0) // Decay over a week
        
        // Calculate frequency factor
        val frequencyFactor = usageData.launchCount.toDouble()
        
        // Calculate usage time factor
        val usageTimeFactor = usageData.totalUsageTime / (60 * 1000.0) // Convert to minutes
        
        // Favorite bonus
        val favoriteBonus = if (usageData.isFavorite) 100.0 else 0.0
        
        return (frequencyFactor * 2.0 + usageTimeFactor * 0.5 + favoriteBonus) * recencyFactor
    }
    
    /**
     * Check if app is favorite
     */
    fun isFavorite(packageName: String): Boolean {
        return usageTracker[packageName]?.isFavorite ?: false
    }
    
    /**
     * Get app launch count
     */
    fun getAppLaunchCount(packageName: String): Int {
        return usageTracker[packageName]?.launchCount ?: 0
    }
    
    /**
     * Save usage data to preferences
     */
    private fun saveUsageData() {
        val editor = prefs.edit()
        
        usageTracker.forEach { (packageName, usageData) ->
            editor.putInt("${packageName}_count", usageData.launchCount)
            editor.putLong("${packageName}_last", usageData.lastLaunchTime)
            editor.putLong("${packageName}_usage", usageData.totalUsageTime)
            editor.putBoolean("${packageName}_fav", usageData.isFavorite)
        }
        
        editor.apply()
    }
    
    /**
     * Load usage data from preferences
     */
    private fun loadUsageData() {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        installedApps.forEach { appInfo ->
            val packageName = appInfo.packageName
            val launchCount = prefs.getInt("${packageName}_count", 0)
            val lastLaunchTime = prefs.getLong("${packageName}_last", 0L)
            val totalUsageTime = prefs.getLong("${packageName}_usage", 0L)
            val isFavorite = prefs.getBoolean("${packageName}_fav", false)
            
            if (launchCount > 0 || isFavorite) {
                usageTracker[packageName] = AppUsageData(
                    packageName = packageName,
                    launchCount = launchCount,
                    lastLaunchTime = lastLaunchTime,
                    totalUsageTime = totalUsageTime,
                    isFavorite = isFavorite
                )
            }
        }
    }
    
    /**
     * Clear all usage data
     */
    fun clearUsageData() {
        usageTracker.clear()
        prefs.edit().clear().apply()
    }
    
    /**
     * Export usage data for backup
     */
    fun exportUsageData(): Map<String, AppUsageData> {
        return usageTracker.toMap()
    }
    
    /**
     * Import usage data from backup
     */
    fun importUsageData(data: Map<String, AppUsageData>) {
        usageTracker.clear()
        usageTracker.putAll(data)
        
        favoritesScope.launch {
            saveUsageData()
        }
    }
    
    fun cleanup() {
        favoritesScope.cancel()
    }
}
