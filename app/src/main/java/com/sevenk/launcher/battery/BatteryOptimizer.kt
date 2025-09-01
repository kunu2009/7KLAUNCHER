package com.sevenk.launcher.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

/**
 * Battery optimization manager for efficient launcher operation
 */
class BatteryOptimizer(private val context: Context) : DefaultLifecycleObserver {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs = context.getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
    private val optimizationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var backgroundJobs = mutableListOf<Job>()
    private var isLowPowerMode = false
    
    companion object {
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val CRITICAL_BATTERY_THRESHOLD = 10
    }
    
    /**
     * Initialize battery optimization
     */
    fun initialize() {
        checkBatteryStatus()
        startBatteryMonitoring()
    }
    
    /**
     * Check current battery status
     */
    private fun checkBatteryStatus() {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            
            val isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            
            updatePowerMode(batteryPct, isCharging)
        }
    }
    
    /**
     * Start monitoring battery changes
     */
    private fun startBatteryMonitoring() {
        optimizationScope.launch {
            while (isActive) {
                checkBatteryStatus()
                delay(30000) // Check every 30 seconds
            }
        }
    }
    
    /**
     * Update power mode based on battery level
     */
    private fun updatePowerMode(batteryLevel: Int, isCharging: Boolean) {
        val shouldEnableLowPower = batteryLevel <= LOW_BATTERY_THRESHOLD && !isCharging
        
        if (shouldEnableLowPower != isLowPowerMode) {
            isLowPowerMode = shouldEnableLowPower
            
            if (isLowPowerMode) {
                enableBatterySavingMode()
            } else {
                disableBatterySavingMode()
            }
        }
        
        // Critical battery handling
        if (batteryLevel <= CRITICAL_BATTERY_THRESHOLD && !isCharging) {
            enableCriticalBatteryMode()
        }
    }
    
    /**
     * Enable battery saving optimizations
     */
    private fun enableBatterySavingMode() {
        // Reduce animation durations
        prefs.edit().putBoolean("battery_save_animations", true).apply()
        
        // Cancel non-essential background tasks
        cancelBackgroundJobs()
        
        // Reduce update frequencies
        prefs.edit().putLong("update_interval", 60000).apply() // 1 minute
        
        android.util.Log.i("BatteryOptimizer", "Battery saving mode enabled")
    }
    
    /**
     * Disable battery saving optimizations
     */
    private fun disableBatterySavingMode() {
        // Restore normal animation durations
        prefs.edit().putBoolean("battery_save_animations", false).apply()
        
        // Restore normal update frequencies
        prefs.edit().putLong("update_interval", 15000).apply() // 15 seconds
        
        android.util.Log.i("BatteryOptimizer", "Battery saving mode disabled")
    }
    
    /**
     * Enable critical battery mode with aggressive optimizations
     */
    private fun enableCriticalBatteryMode() {
        // Cancel all non-essential operations
        cancelBackgroundJobs()
        
        // Disable animations completely
        prefs.edit().putBoolean("disable_animations", true).apply()
        
        // Reduce screen updates
        prefs.edit().putLong("update_interval", 120000).apply() // 2 minutes
        
        // Clear caches to free memory
        clearCaches()
        
        android.util.Log.w("BatteryOptimizer", "Critical battery mode enabled")
    }
    
    /**
     * Cancel background jobs to save battery
     */
    private fun cancelBackgroundJobs() {
        backgroundJobs.forEach { it.cancel() }
        backgroundJobs.clear()
    }
    
    /**
     * Clear caches to free memory and reduce battery usage
     */
    private fun clearCaches() {
        optimizationScope.launch(Dispatchers.IO) {
            try {
                // Clear app cache
                context.cacheDir.deleteRecursively()
                
                // Suggest garbage collection
                System.gc()
                
                android.util.Log.i("BatteryOptimizer", "Caches cleared for battery optimization")
            } catch (e: Exception) {
                android.util.Log.e("BatteryOptimizer", "Error clearing caches", e)
            }
        }
    }
    
    /**
     * Add background job for tracking
     */
    fun addBackgroundJob(job: Job) {
        backgroundJobs.add(job)
    }
    
    /**
     * Check if battery saving mode is active
     */
    fun isBatterySavingMode(): Boolean = isLowPowerMode
    
    /**
     * Get recommended animation duration based on battery status
     */
    fun getAnimationDuration(defaultDuration: Long): Long {
        return when {
            prefs.getBoolean("disable_animations", false) -> 0L
            prefs.getBoolean("battery_save_animations", false) -> defaultDuration / 2
            else -> defaultDuration
        }
    }
    
    /**
     * Get recommended update interval
     */
    fun getUpdateInterval(): Long {
        return prefs.getLong("update_interval", 15000)
    }
    
    /**
     * Check if device is in power save mode
     */
    fun isDeviceInPowerSaveMode(): Boolean {
        return powerManager.isPowerSaveMode
    }
    
    override fun onResume(owner: LifecycleOwner) {
        checkBatteryStatus()
    }
    
    override fun onPause(owner: LifecycleOwner) {
        // Reduce background activity when app is paused
        if (isLowPowerMode) {
            cancelBackgroundJobs()
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        optimizationScope.cancel()
        cancelBackgroundJobs()
    }
}
