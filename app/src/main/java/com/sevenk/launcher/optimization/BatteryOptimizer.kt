package com.sevenk.launcher.optimization

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Battery optimization utilities for the launcher
 */
class BatteryOptimizer(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Delayed tasks for power saving
    private val delayedTasks = ConcurrentHashMap<String, Runnable>()
    
    companion object {
        private const val POWER_SAVE_ANIMATION_DELAY = 300L
        private const val POWER_SAVE_REFRESH_DELAY = 1000L
        private const val POWER_SAVE_ICON_CACHE_SIZE = 25 // Reduced from 50
        private const val NORMAL_ICON_CACHE_SIZE = 50
    }
    
    /**
     * Check if power saver mode is enabled
     */
    fun isPowerSaverEnabled(): Boolean {
        return prefs.getBoolean("launcher_power_saver", false)
    }
    
    /**
     * Optimize animations based on power saver mode
     */
    fun optimizeAnimations(view: View, animation: Animation? = null) {
        if (isPowerSaverEnabled()) {
            // Disable or reduce animations in power saver mode
            view.animate().cancel()
            animation?.cancel()
            
            // Use instant visibility changes instead of animations
            view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        } else {
            // Normal animations
            animation?.let { view.startAnimation(it) }
        }
    }
    
    /**
     * Optimize RecyclerView for power saving
     */
    fun optimizeRecyclerView(recyclerView: RecyclerView) {
        if (isPowerSaverEnabled()) {
            // Reduce cache sizes in power saver mode
            recyclerView.setItemViewCacheSize(10) // Reduced from 20
            recyclerView.recycledViewPool.setMaxRecycledViews(0, 5) // Reduced from 10
            
            // Disable item animations
            recyclerView.itemAnimator = null
            
            // Reduce update frequency
            recyclerView.setHasFixedSize(true)
        } else {
            // Normal settings
            recyclerView.setItemViewCacheSize(20)
            recyclerView.recycledViewPool.setMaxRecycledViews(0, 10)
        }
    }
    
    /**
     * Get optimal icon cache size based on power mode
     */
    fun getOptimalIconCacheSize(): Int {
        return if (isPowerSaverEnabled()) POWER_SAVE_ICON_CACHE_SIZE else NORMAL_ICON_CACHE_SIZE
    }
    
    /**
     * Delay task execution in power saver mode
     */
    fun delayTask(taskId: String, task: Runnable, delay: Long = POWER_SAVE_REFRESH_DELAY) {
        if (isPowerSaverEnabled()) {
            // Cancel previous task
            delayedTasks[taskId]?.let { mainHandler.removeCallbacks(it) }
            
            // Schedule new task
            val delayedTask = Runnable {
                task.run()
                delayedTasks.remove(taskId)
            }
            delayedTasks[taskId] = delayedTask
            mainHandler.postDelayed(delayedTask, delay)
        } else {
            // Execute immediately in normal mode
            task.run()
        }
    }
    
    /**
     * Optimize background tasks
     */
    fun optimizeBackgroundTask(task: suspend () -> Unit) {
        if (isPowerSaverEnabled()) {
            // Use lower priority dispatcher
            backgroundScope.launch(Dispatchers.IO) {
                delay(100) // Small delay to reduce CPU usage
                task()
            }
        } else {
            backgroundScope.launch {
                task()
            }
        }
    }
    
    /**
     * Cancel all delayed tasks
     */
    fun cancelAllDelayedTasks() {
        delayedTasks.values.forEach { mainHandler.removeCallbacks(it) }
        delayedTasks.clear()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelAllDelayedTasks()
        backgroundScope.cancel()
    }
}