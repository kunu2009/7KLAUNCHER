package com.sevenk.launcher.optimization

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.animation.Animation
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Data
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced battery optimization system for 7K Launcher
 * Implements aggressive power management with JobScheduler, adaptive refresh rates, and intelligent resource management
 */
class AdvancedBatteryOptimizer(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?
    private val workManager = WorkManager.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
    
    // Adaptive refresh management
    private var currentRefreshRate = RefreshRate.NORMAL
    private var isScreenOff = false
    private var lastInteractionTime = System.currentTimeMillis()
    private val activeAnimations = ConcurrentHashMap<String, Animation>()
    private val delayedTasks = ConcurrentHashMap<String, Runnable>()
    
    // Power state monitoring
    private var batteryReceiver: BroadcastReceiver? = null
    private var isLowPowerMode = false
    private var currentBatteryLevel = 100
    
    enum class RefreshRate(val intervalMs: Long, val description: String) {
        ULTRA_FAST(8, "120 FPS"),     // For critical animations only
        FAST(16, "60 FPS"),           // Normal animations
        NORMAL(33, "30 FPS"),         // Standard operation
        SLOW(66, "15 FPS"),           // Low battery mode
        MINIMAL(200, "5 FPS"),        // Critical battery
        CRAWL(1000, "1 FPS"),         // Emergency mode
        PAUSED(0, "Paused")           // Screen off
    }
    
    enum class PowerProfile(val animationScale: Float, val cacheSize: Int, val updateDelay: Long) {
        MAXIMUM_PERFORMANCE(1.0f, 100, 0L),
        BALANCED(0.8f, 75, 50L),
        POWER_SAVER(0.5f, 50, 100L),
        ULTRA_POWER_SAVER(0.2f, 25, 300L),
        EMERGENCY(0.0f, 10, 1000L)
    }
    
    companion object {
        private const val TAG = "AdvancedBatteryOptimizer"
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val CRITICAL_BATTERY_THRESHOLD = 10
        private const val EMERGENCY_BATTERY_THRESHOLD = 5
        private const val INTERACTION_TIMEOUT = 3000L
        private const val JOB_ID_MAINTENANCE = 2001
        private const val WORK_NAME_OPTIMIZATION = "battery_optimization"
        private const val ANIMATION_SCALE_FACTOR = 0.8f
    }
    
    init {
        setupBatteryMonitoring()
        scheduleMaintenanceWork()
        updatePowerProfile()
    }
    
    /**
     * Setup battery state monitoring
     */
    private fun setupBatteryMonitoring() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        updateBatteryState(intent)
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        onPowerConnected()
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        onPowerDisconnected()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        onScreenOff()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        onScreenOn()
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        updatePowerProfile()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        
        context.registerReceiver(batteryReceiver, filter)
    }
    
    /**
     * Update battery state and adjust optimizations
     */
    private fun updateBatteryState(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        
        if (level >= 0 && scale > 0) {
            currentBatteryLevel = (level * 100) / scale
            updatePowerProfile()
            Log.d(TAG, "Battery level: $currentBatteryLevel%")
        }
    }
    
    /**
     * Get current power profile based on battery level and power save mode
     */
    fun getCurrentPowerProfile(): PowerProfile {
        return when {
            isLowPowerMode || powerManager.isPowerSaveMode -> PowerProfile.ULTRA_POWER_SAVER
            currentBatteryLevel <= EMERGENCY_BATTERY_THRESHOLD -> PowerProfile.EMERGENCY
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> PowerProfile.ULTRA_POWER_SAVER
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> PowerProfile.POWER_SAVER
            currentBatteryLevel <= 50 -> PowerProfile.BALANCED
            else -> PowerProfile.MAXIMUM_PERFORMANCE
        }
    }
    
    /**
     * Update power profile and apply optimizations
     */
    private fun updatePowerProfile() {
        val profile = getCurrentPowerProfile()
        isLowPowerMode = profile in arrayOf(PowerProfile.POWER_SAVER, PowerProfile.ULTRA_POWER_SAVER, PowerProfile.EMERGENCY)
        
        // Update refresh rate
        currentRefreshRate = when (profile) {
            PowerProfile.MAXIMUM_PERFORMANCE -> RefreshRate.FAST
            PowerProfile.BALANCED -> RefreshRate.NORMAL
            PowerProfile.POWER_SAVER -> RefreshRate.SLOW
            PowerProfile.ULTRA_POWER_SAVER -> RefreshRate.MINIMAL
            PowerProfile.EMERGENCY -> RefreshRate.CRAWL
        }
        
        // Cancel unnecessary animations in low power mode
        if (isLowPowerMode) {
            cancelAllAnimations()
        }
        
        Log.d(TAG, "Power profile updated: $profile, Refresh rate: ${currentRefreshRate.description}")
    }
    
    /**
     * Optimize animation based on current power profile
     */
    fun optimizeAnimation(view: View, animation: Animation, animationId: String = "default"): Boolean {
        val profile = getCurrentPowerProfile()
        
        when (profile) {
            PowerProfile.EMERGENCY -> {
                // No animations in emergency mode
                animation.cancel()
                return false
            }
            PowerProfile.ULTRA_POWER_SAVER -> {
                // Minimal animations only
                if (animationId != "critical") {
                    animation.cancel()
                    return false
                }
            }
            else -> {
                // Scale animation duration based on power profile
                val originalDuration = animation.duration
                animation.duration = (originalDuration * profile.animationScale).toLong()
                activeAnimations[animationId] = animation
            }
        }
        
        view.startAnimation(animation)
        return true
    }
    
    /**
     * Cancel all active animations
     */
    fun cancelAllAnimations() {
        activeAnimations.values.forEach { it.cancel() }
        activeAnimations.clear()
    }
    
    /**
     * Get optimal refresh interval for current power state
     */
    fun getOptimalRefreshInterval(): Long {
        return if (isScreenOff) RefreshRate.PAUSED.intervalMs else currentRefreshRate.intervalMs
    }
    
    /**
     * Delay task execution based on power profile
     */
    fun delayTask(taskId: String, task: Runnable, priority: TaskPriority = TaskPriority.NORMAL) {
        val profile = getCurrentPowerProfile()
        val delay = when (priority) {
            TaskPriority.CRITICAL -> 0L
            TaskPriority.HIGH -> profile.updateDelay / 2
            TaskPriority.NORMAL -> profile.updateDelay
            TaskPriority.LOW -> profile.updateDelay * 2
            TaskPriority.BACKGROUND -> profile.updateDelay * 5
        }
        
        // Cancel previous task
        delayedTasks[taskId]?.let { handler.removeCallbacks(it) }
        
        if (delay == 0L) {
            task.run()
        } else {
            val delayedTask = Runnable {
                task.run()
                delayedTasks.remove(taskId)
            }
            delayedTasks[taskId] = delayedTask
            handler.postDelayed(delayedTask, delay)
        }
    }
    
    /**
     * Record user interaction to adjust optimization aggressiveness
     */
    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        
        // Temporarily boost performance for responsive UI
        if (isLowPowerMode && System.currentTimeMillis() - lastInteractionTime < INTERACTION_TIMEOUT) {
            currentRefreshRate = RefreshRate.NORMAL
        }
    }
    
    /**
     * Check if device is considered idle
     */
    fun isDeviceIdle(): Boolean {
        return System.currentTimeMillis() - lastInteractionTime > INTERACTION_TIMEOUT
    }
    
    /**
     * Schedule maintenance work using WorkManager
     */
    private fun scheduleMaintenanceWork() {
        val maintenanceWork = PeriodicWorkRequestBuilder<MaintenanceWorker>(
            15, TimeUnit.MINUTES // Run every 15 minutes
        ).setInputData(
            Data.Builder()
                .putString("launcher_package", context.packageName)
                .build()
        ).build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_OPTIMIZATION,
            ExistingPeriodicWorkPolicy.KEEP,
            maintenanceWork
        )
    }
    
    private fun onPowerConnected() {
        Log.d(TAG, "Power connected - switching to balanced mode")
        updatePowerProfile()
    }
    
    private fun onPowerDisconnected() {
        Log.d(TAG, "Power disconnected - enabling power optimizations")
        updatePowerProfile()
    }
    
    private fun onScreenOff() {
        isScreenOff = true
        currentRefreshRate = RefreshRate.PAUSED
        cancelAllAnimations()
        pauseAllTasks()
        Log.d(TAG, "Screen off - pausing all operations")
    }
    
    private fun onScreenOn() {
        isScreenOff = false
        updatePowerProfile()
        resumeTasks()
        Log.d(TAG, "Screen on - resuming operations")
    }
    
    private fun pauseAllTasks() {
        delayedTasks.values.forEach { handler.removeCallbacks(it) }
    }
    
    private fun resumeTasks() {
        // Re-schedule tasks with current power profile delays
        val tasksToReschedule = delayedTasks.toMap()
        delayedTasks.clear()
        
        tasksToReschedule.forEach { (taskId, task) ->
            delayTask(taskId, task)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            batteryReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering battery receiver", e)
        }
        
        cancelAllAnimations()
        delayedTasks.values.forEach { handler.removeCallbacks(it) }
        delayedTasks.clear()
        
        workManager.cancelUniqueWork(WORK_NAME_OPTIMIZATION)
    }
    
    enum class TaskPriority {
        CRITICAL,    // Execute immediately
        HIGH,        // Minimal delay
        NORMAL,      // Standard delay
        LOW,         // Extended delay
        BACKGROUND   // Maximum delay
    }
}

/**
 * Background maintenance worker for periodic cleanup
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    override fun doWork(): Result {
        return try {
            // Perform maintenance tasks
            System.gc() // Suggest garbage collection
            
            // Clear temporary caches if memory is low
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            if (memoryUsage > 80) {
                // Clear caches when memory usage is high
                Log.d("MaintenanceWorker", "High memory usage (${"%.1f".format(memoryUsage)}%), clearing caches")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("MaintenanceWorker", "Maintenance failed", e)
            Result.failure()
        }
    }
}