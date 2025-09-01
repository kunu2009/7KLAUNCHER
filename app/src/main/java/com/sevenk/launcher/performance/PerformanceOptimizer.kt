package com.sevenk.launcher.performance

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Performance optimization utilities for smooth scrolling and fast app launches
 */
class PerformanceOptimizer(private val context: Context) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Icon cache for fast loading
    private val iconCache = LruCache<String, Bitmap>(50)
    
    // Preload cache for frequently used apps
    private val preloadCache = ConcurrentHashMap<String, Any>()
    
    // Performance metrics
    private val performanceMetrics = mutableMapOf<String, Long>()
    
    companion object {
        private const val SCROLL_DEBOUNCE_MS = 100L
        private const val PRELOAD_DELAY_MS = 200L
    }
    
    /**
     * Optimize RecyclerView for smooth scrolling
     */
    fun optimizeRecyclerView(recyclerView: RecyclerView) {
        // Enable item animator optimizations
        recyclerView.itemAnimator?.apply {
            addDuration = 120
            changeDuration = 120
            moveDuration = 120
            removeDuration = 120
        }
        
        // Set optimal cache sizes
        recyclerView.setItemViewCacheSize(20)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 10)
        
        // Enable nested scrolling optimization
        recyclerView.isNestedScrollingEnabled = true
        
        // Add scroll listener for performance monitoring
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var scrollStartTime = 0L
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        scrollStartTime = System.currentTimeMillis()
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (scrollStartTime > 0) {
                            val scrollDuration = System.currentTimeMillis() - scrollStartTime
                            recordMetric("scroll_duration", scrollDuration)
                        }
                    }
                }
            }
        })
    }
    
    /**
     * Preload app icons for faster display
     */
    fun preloadAppIcons(packageNames: List<String>) {
        backgroundScope.launch {
            packageNames.forEach { packageName ->
                try {
                    val icon = context.packageManager.getApplicationIcon(packageName)
                    // Cache the drawable for later use
                    preloadCache[packageName] = icon
                } catch (e: Exception) {
                    // App might be uninstalled
                }
            }
        }
    }
    
    /**
     * Get cached icon or load asynchronously
     */
    fun getCachedIcon(packageName: String, size: Int, callback: (Bitmap?) -> Unit) {
        val cacheKey = "${packageName}_$size"
        
        // Check memory cache first
        iconCache.get(cacheKey)?.let { cachedBitmap ->
            callback(cachedBitmap)
            return
        }
        
        // Load asynchronously
        backgroundScope.launch {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(drawable, size)
                
                // Cache the result
                iconCache.put(cacheKey, bitmap)
                
                // Return on main thread
                mainHandler.post { callback(bitmap) }
            } catch (e: Exception) {
                mainHandler.post { callback(null) }
            }
        }
    }
    
    /**
     * Optimize app launch performance
     */
    fun optimizeAppLaunch(packageName: String, callback: () -> Unit) {
        val startTime = System.currentTimeMillis()
        
        // Pre-warm the app launch
        backgroundScope.launch {
            try {
                // Preload app info if not cached
                if (!preloadCache.containsKey(packageName)) {
                    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                    preloadCache[packageName] = appInfo
                }
                
                // Small delay to ensure smooth animation
                delay(50)
                
                mainHandler.post {
                    callback()
                    val launchTime = System.currentTimeMillis() - startTime
                    recordMetric("app_launch_time", launchTime)
                }
            } catch (e: Exception) {
                mainHandler.post { callback() }
            }
        }
    }
    
    /**
     * Debounce scroll events for better performance
     */
    fun debounceScrollEvent(action: () -> Unit) {
        mainHandler.removeCallbacksAndMessages("scroll_debounce")
        mainHandler.postDelayed({
            action()
        }, "scroll_debounce", SCROLL_DEBOUNCE_MS)
    }
    
    /**
     * Memory cleanup for better performance
     */
    fun performMemoryCleanup() {
        backgroundScope.launch {
            // Clear old cache entries
            iconCache.evictAll()
            
            // Clear preload cache of unused items
            val currentTime = System.currentTimeMillis()
            preloadCache.entries.removeAll { entry ->
                // Remove entries older than 5 minutes
                entry.value is Long && currentTime - entry.value as Long > 300000
            }
            
            // Suggest garbage collection
            System.gc()
        }
    }
    
    /**
     * Record performance metric
     */
    private fun recordMetric(name: String, value: Long) {
        performanceMetrics[name] = value
        
        // Log slow operations
        when (name) {
            "scroll_duration" -> if (value > 100) logSlowOperation(name, value)
            "app_launch_time" -> if (value > 500) logSlowOperation(name, value)
        }
    }
    
    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): Map<String, Long> {
        return performanceMetrics.toMap()
    }
    
    /**
     * Log slow operations for debugging
     */
    private fun logSlowOperation(operation: String, duration: Long) {
        android.util.Log.w("PerformanceOptimizer", "Slow $operation: ${duration}ms")
    }
    
    /**
     * Convert drawable to bitmap with specified size
     */
    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        backgroundScope.cancel()
        iconCache.evictAll()
        preloadCache.clear()
        performanceMetrics.clear()
    }
}

/**
 * Extension function for Handler to post delayed with token
 */
private fun Handler.postDelayed(runnable: Runnable, token: Any, delayMillis: Long) {
    val tokenRunnable = Runnable {
        runnable.run()
    }
    postDelayed(tokenRunnable, delayMillis)
}
