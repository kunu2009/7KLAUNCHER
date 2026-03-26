package com.sevenk.launcher.optimization

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.sevenk.launcher.AppInfo
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * RAM optimization utilities for the launcher
 */
class RAMOptimizer(private val context: Context) {
    
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Optimized caches with weak references
    private val iconCache = LruCache<String, Bitmap>(25) // Reduced size
    private val appInfoCache = ConcurrentHashMap<String, WeakReference<AppInfo>>()
    private val layoutCache = ConcurrentHashMap<String, WeakReference<Any>>()
    
    companion object {
        private const val MAX_CACHE_SIZE_MB = 8 // Maximum cache size in MB
        private const val CACHE_CLEANUP_INTERVAL = 30000L // 30 seconds
    }
    
    /**
     * Get optimized icon cache
     */
    fun getIconCache(): LruCache<String, Bitmap> = iconCache
    
    /**
     * Cache app info with weak reference
     */
    fun cacheAppInfo(packageName: String, appInfo: AppInfo) {
        appInfoCache[packageName] = WeakReference(appInfo)
    }
    
    /**
     * Get cached app info
     */
    fun getCachedAppInfo(packageName: String): AppInfo? {
        return appInfoCache[packageName]?.get()
    }
    
    /**
     * Clear unused cache entries
     */
    fun clearUnusedCache() {
        backgroundScope.launch {
            // Clean up weak references that have been garbage collected
            val iterator = appInfoCache.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.get() == null) {
                    iterator.remove()
                }
            }
            
            // Clean up layout cache
            val layoutIterator = layoutCache.iterator()
            while (layoutIterator.hasNext()) {
                val entry = layoutIterator.next()
                if (entry.value.get() == null) {
                    layoutIterator.remove()
                }
            }
        }
    }
    
    /**
     * Force garbage collection if memory is low
     */
    fun forceGCIfNeeded() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercentage = (usedMemory * 100) / maxMemory
        
        if (memoryUsagePercentage > 80) {
            // Clear caches aggressively
            iconCache.evictAll()
            appInfoCache.clear()
            layoutCache.clear()
            
            // Suggest garbage collection
            System.gc()
        }
    }
    
    /**
     * Get current memory usage information
     */
    fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val freeMemory = runtime.freeMemory()
        
        return MemoryInfo(
            usedMemory = usedMemory,
            freeMemory = freeMemory,
            maxMemory = maxMemory,
            usagePercentage = (usedMemory * 100) / maxMemory
        )
    }
    
    /**
     * Start periodic cache cleanup
     */
    fun startPeriodicCleanup() {
        backgroundScope.launch {
            while (isActive) {
                delay(CACHE_CLEANUP_INTERVAL)
                clearUnusedCache()
                
                // Force GC if memory usage is high
                val memoryInfo = getMemoryUsage()
                if (memoryInfo.usagePercentage > 70) {
                    forceGCIfNeeded()
                }
            }
        }
    }
    
    /**
     * Cleanup all resources
     */
    fun cleanup() {
        iconCache.evictAll()
        appInfoCache.clear()
        layoutCache.clear()
        backgroundScope.cancel()
    }
    
    /**
     * Data class for memory information
     */
    data class MemoryInfo(
        val usedMemory: Long,
        val freeMemory: Long,
        val maxMemory: Long,
        val usagePercentage: Long
    )
}