package com.sevenk.launcher.developer

import android.content.Context
import android.os.Build
import com.sevenk.launcher.errors.ErrorReporter
import com.sevenk.launcher.performance.PerformanceOptimizer

/**
 * Hidden developer settings menu
 */
class DeveloperSettings(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("developer_settings", Context.MODE_PRIVATE)
    private val errorReporter = ErrorReporter.getInstance(context)
    
    companion object {
        private const val TAP_COUNT_THRESHOLD = 7
        private const val TAP_TIMEOUT_MS = 3000L
    }
    
    private var tapCount = 0
    private var lastTapTime = 0L
    
    /**
     * Handle version tap for developer menu access
     */
    fun handleVersionTap(): Boolean {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
            tapCount = 1
        } else {
            tapCount++
        }
        
        lastTapTime = currentTime
        
        if (tapCount >= TAP_COUNT_THRESHOLD) {
            tapCount = 0
            return true // Show developer menu
        }
        
        return false
    }
    
    /**
     * Check if developer mode is enabled
     */
    fun isDeveloperModeEnabled(): Boolean {
        return prefs.getBoolean("developer_mode", false)
    }
    
    /**
     * Enable developer mode
     */
    fun enableDeveloperMode() {
        prefs.edit().putBoolean("developer_mode", true).apply()
    }
    
    /**
     * Disable developer mode
     */
    fun disableDeveloperMode() {
        prefs.edit().putBoolean("developer_mode", false).apply()
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): Map<String, String> {
        val performanceOptimizer = PerformanceOptimizer(context)
        val metrics = performanceOptimizer.getPerformanceMetrics()
        
        return mapOf(
            "App Version" to getAppVersion(),
            "Android Version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Memory Usage" to getMemoryUsage(),
            "Performance Score" to calculatePerformanceScore(metrics).toString(),
            "Error Count" to getErrorCount().toString(),
            "Developer Mode" to isDeveloperModeEnabled().toString()
        )
    }
    
    /**
     * Get app version
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get memory usage info
     */
    private fun getMemoryUsage(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        return "${usedMemory}MB / ${maxMemory}MB"
    }
    
    /**
     * Calculate performance score
     */
    private fun calculatePerformanceScore(metrics: Map<String, Long>): Int {
        var score = 100
        
        // Deduct points for slow operations
        metrics["scroll_duration"]?.let { duration ->
            if (duration > 50) score -= ((duration - 50) / 10).toInt()
        }
        
        metrics["app_launch_time"]?.let { duration ->
            if (duration > 300) score -= ((duration - 300) / 50).toInt()
        }
        
        return maxOf(0, minOf(100, score))
    }
    
    /**
     * Get error count from error reporter
     */
    private fun getErrorCount(): Int {
        return errorReporter.getRecentErrors(100).size
    }
    
    /**
     * Export logs for debugging
     */
    fun exportLogs(): String {
        val debugInfo = getDebugInfo()
        val errorLogs = errorReporter.getRecentErrors(50)
        
        return buildString {
            appendLine("=== 7K LAUNCHER DEBUG EXPORT ===")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine()
            
            appendLine("=== SYSTEM INFO ===")
            debugInfo.forEach { (key, value) ->
                appendLine("$key: $value")
            }
            appendLine()
            
            appendLine("=== RECENT ERRORS ===")
            if (errorLogs.isEmpty()) {
                appendLine("No recent errors")
            } else {
                errorLogs.takeLast(10).forEach { log ->
                    appendLine(log)
                }
            }
            appendLine()
            
            appendLine("=== END DEBUG EXPORT ===")
        }
    }
    
    /**
     * Clear all debug data
     */
    fun clearDebugData() {
        errorReporter.clearLogs()
        prefs.edit().clear().apply()
    }
    
    /**
     * Force crash for testing
     */
    fun forceCrash() {
        if (isDeveloperModeEnabled()) {
            throw RuntimeException("Developer-triggered test crash")
        }
    }
    
    /**
     * Simulate performance issue
     */
    fun simulatePerformanceIssue() {
        if (isDeveloperModeEnabled()) {
            Thread.sleep(2000) // Simulate 2-second delay
        }
    }
}
