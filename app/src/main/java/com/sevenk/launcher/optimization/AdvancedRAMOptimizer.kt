package com.sevenk.launcher.optimization

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Advanced RAM optimization system with lazy loading, intelligent caching, and memory management
 */
class AdvancedRAMOptimizer(private val context: Context) {
    
    private val packageManager = context.packageManager
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Multi-level caching system
    private val iconCache: LruCache<String, Drawable>
    private val bitmapCache: LruCache<String, Bitmap>
    private val labelCache: LruCache<String, String>
    private val appInfoCache: LruCache<String, ApplicationInfo>
    
    // Weak references for additional caching
    private val weakIconCache = ConcurrentHashMap<String, WeakReference<Drawable>>()
    private val weakBitmapCache = ConcurrentHashMap<String, WeakReference<Bitmap>>()
    
    // Memory monitoring
    private val memoryThresholds = MemoryThresholds()
    private val loadingTasks = ConcurrentHashMap<String, Job>()
    private val accessCounter = ConcurrentHashMap<String, AtomicInteger>()
    
    // Lazy loading management
    private val visibleItems = mutableSetOf<String>()
    private val preloadQueue = mutableListOf<String>()
    private var isPreloading = false
    
    data class MemoryThresholds(
        val lowMemoryThreshold: Double = 0.75,      // 75% memory usage
        val criticalMemoryThreshold: Double = 0.85, // 85% memory usage
        val emergencyMemoryThreshold: Double = 0.95  // 95% memory usage
    )
    
    enum class CacheSize(val icons: Int, val bitmaps: Int, val labels: Int, val appInfo: Int) {
        MINIMAL(25, 10, 50, 25),
        SMALL(50, 25, 100, 50),
        MEDIUM(100, 50, 200, 100),
        LARGE(200, 100, 400, 200),
        MAXIMUM(400, 200, 800, 400)
    }
    
    companion object {
        private const val TAG = "AdvancedRAMOptimizer"
        private const val CLEANUP_INTERVAL = 30_000L // 30 seconds
        private const val PRELOAD_BATCH_SIZE = 10
        private const val ACCESS_COUNT_THRESHOLD = 5
        private const val MEMORY_CHECK_INTERVAL = 5_000L // 5 seconds
    }
    
    init {
        val cacheSize = getOptimalCacheSize()
        
        // Initialize caches with dynamic sizing
        iconCache = object : LruCache<String, Drawable>(cacheSize.icons) {
            override fun sizeOf(key: String, value: Drawable): Int {
                return if (value.intrinsicWidth > 0 && value.intrinsicHeight > 0) {
                    value.intrinsicWidth * value.intrinsicHeight * 4 / 1024 // Approximate KB
                } else 1
            }
            
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
                if (evicted) {
                    // Move to weak cache for potential reuse
                    weakIconCache[key] = WeakReference(oldValue)
                }
            }
        }
        
        bitmapCache = object : LruCache<String, Bitmap>(cacheSize.bitmaps) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024 // Size in KB
            }
            
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
                if (evicted && !oldValue.isRecycled) {
                    weakBitmapCache[key] = WeakReference(oldValue)
                }
            }
        }
        
        labelCache = LruCache(cacheSize.labels)
        appInfoCache = LruCache(cacheSize.appInfo)
        
        startMemoryMonitoring()
        schedulePeriodicCleanup()
    }
    
    /**
     * Get optimal cache size based on available memory
     */
    private fun getOptimalCacheSize(): CacheSize {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val availableMemoryMB = maxMemory / (1024 * 1024)
        
        return when {
            availableMemoryMB < 128 -> CacheSize.MINIMAL
            availableMemoryMB < 256 -> CacheSize.SMALL
            availableMemoryMB < 512 -> CacheSize.MEDIUM
            availableMemoryMB < 1024 -> CacheSize.LARGE
            else -> CacheSize.MAXIMUM
        }
    }
    
    /**
     * Get app icon with lazy loading and multi-level caching
     */
    suspend fun getAppIcon(packageName: String, loadImmediately: Boolean = false): Drawable? {
        // Check primary cache
        iconCache.get(packageName)?.let { return it }
        
        // Check weak cache
        weakIconCache[packageName]?.get()?.let { 
            iconCache.put(packageName, it) // Promote back to primary cache
            weakIconCache.remove(packageName)
            return it 
        }
        
        // If not loading immediately and memory is constrained, defer loading
        if (!loadImmediately && isMemoryConstrained()) {
            return null
        }
        
        // Load asynchronously
        return loadIconAsync(packageName)
    }
    
    /**
     * Load app icon asynchronously with intelligent queueing
     */
    private suspend fun loadIconAsync(packageName: String): Drawable? {
        // Cancel existing loading task for this package
        loadingTasks[packageName]?.cancel()
        
        val job = backgroundScope.async {
            try {
                val appInfo = getAppInfo(packageName) ?: return@async null
                val icon = packageManager.getApplicationIcon(appInfo)
                
                // Cache the result
                withContext(Dispatchers.Main) {
                    iconCache.put(packageName, icon)
                    incrementAccessCount(packageName)
                }
                
                icon
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load icon for $packageName", e)
                null
            }
        }
        
        loadingTasks[packageName] = job
        
        return try {
            job.await()
        } catch (e: CancellationException) {
            null
        } finally {
            loadingTasks.remove(packageName)
        }
    }
    
    /**
     * Get app info with caching
     */
    private suspend fun getAppInfo(packageName: String): ApplicationInfo? {
        appInfoCache.get(packageName)?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfoCache.put(packageName, appInfo)
                appInfo
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    
    /**
     * Get app label with caching
     */
    suspend fun getAppLabel(packageName: String): String? {
        labelCache.get(packageName)?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                val appInfo = getAppInfo(packageName) ?: return@withContext null
                val label = packageManager.getApplicationLabel(appInfo).toString()
                labelCache.put(packageName, label)
                label
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load label for $packageName", e)
                null
            }
        }
    }
    
    /**
     * Preload icons for visible items
     */
    fun preloadVisibleItems(packageNames: List<String>) {
        visibleItems.clear()
        visibleItems.addAll(packageNames)
        
        if (!isPreloading && !isMemoryConstrained()) {
            startPreloading()
        }
    }
    
    /**
     * Start intelligent preloading
     */
    private fun startPreloading() {
        if (isPreloading) return
        
        isPreloading = true
        backgroundScope.launch {
            val itemsToPreload = visibleItems.filter { packageName ->
                iconCache.get(packageName) == null && weakIconCache[packageName]?.get() == null
            }
            
            itemsToPreload.chunked(PRELOAD_BATCH_SIZE).forEach { batch ->
                if (isMemoryConstrained()) {
                    Log.d(TAG, "Stopping preload due to memory constraints")
                    return@launch
                }
                
                batch.forEach { packageName ->
                    loadIconAsync(packageName)
                }
                
                delay(50) // Small delay between batches
            }
            
            isPreloading = false
        }
    }
    
    /**
     * Optimize RecyclerView for memory efficiency
     */
    fun optimizeRecyclerView(recyclerView: RecyclerView) {
        val memoryState = getCurrentMemoryState()
        
        when (memoryState) {
            MemoryState.CRITICAL, MemoryState.EMERGENCY -> {
                recyclerView.setItemViewCacheSize(5)
                recyclerView.recycledViewPool.setMaxRecycledViews(0, 3)
                recyclerView.itemAnimator = null
            }
            MemoryState.LOW -> {
                recyclerView.setItemViewCacheSize(10)
                recyclerView.recycledViewPool.setMaxRecycledViews(0, 5)
            }
            MemoryState.NORMAL -> {
                recyclerView.setItemViewCacheSize(20)
                recyclerView.recycledViewPool.setMaxRecycledViews(0, 10)
            }
            MemoryState.ABUNDANT -> {
                recyclerView.setItemViewCacheSize(30)
                recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
            }
        }
        
        recyclerView.setHasFixedSize(true)
    }
    
    /**
     * Get current memory state
     */
    private fun getCurrentMemoryState(): MemoryState {
        val memoryUsage = getMemoryUsagePercentage()
        
        return when {
            memoryUsage >= memoryThresholds.emergencyMemoryThreshold -> MemoryState.EMERGENCY
            memoryUsage >= memoryThresholds.criticalMemoryThreshold -> MemoryState.CRITICAL
            memoryUsage >= memoryThresholds.lowMemoryThreshold -> MemoryState.LOW
            memoryUsage < 0.5 -> MemoryState.ABUNDANT
            else -> MemoryState.NORMAL
        }
    }
    
    /**
     * Check if memory is constrained
     */
    fun isMemoryConstrained(): Boolean {
        return getCurrentMemoryState() in arrayOf(MemoryState.LOW, MemoryState.CRITICAL, MemoryState.EMERGENCY)
    }
    
    /**
     * Get memory usage percentage
     */
    fun getMemoryUsagePercentage(): Double {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory()
        return used.toDouble() / max.toDouble()
    }
    
    /**
     * Force aggressive cleanup
     */
    fun forceCleanup() {
        Log.d(TAG, "Forcing aggressive cleanup")
        
        // Clear weak caches
        weakIconCache.clear()
        weakBitmapCache.clear()
        
        // Trim caches
        val currentState = getCurrentMemoryState()
        when (currentState) {
            MemoryState.EMERGENCY -> {
                iconCache.evictAll()
                bitmapCache.evictAll()
                labelCache.evictAll()
                appInfoCache.trimToSize(10)
            }
            MemoryState.CRITICAL -> {
                iconCache.trimToSize(iconCache.maxSize() / 4)
                bitmapCache.trimToSize(bitmapCache.maxSize() / 4)
                labelCache.trimToSize(labelCache.maxSize() / 2)
            }
            MemoryState.LOW -> {
                iconCache.trimToSize(iconCache.maxSize() / 2)
                bitmapCache.trimToSize(bitmapCache.maxSize() / 2)
            }
            else -> {
                // Normal cleanup
                cleanupUnusedItems()
            }
        }
        
        // Cancel loading tasks
        loadingTasks.values.forEach { it.cancel() }
        loadingTasks.clear()
        
        // Suggest garbage collection
        System.gc()
    }
    
    /**
     * Cleanup unused items based on access patterns
     */
    private fun cleanupUnusedItems() {
        val keysToRemove = mutableListOf<String>()
        
        accessCounter.forEach { (key, count) ->
            if (count.get() == 0) {
                keysToRemove.add(key)
            } else {
                count.set(0) // Reset counter
            }
        }
        
        keysToRemove.forEach { key ->
            iconCache.remove(key)
            bitmapCache.remove(key)
            weakIconCache.remove(key)
            weakBitmapCache.remove(key)
            accessCounter.remove(key)
        }
        
        Log.d(TAG, "Cleaned up ${keysToRemove.size} unused cache entries")
    }
    
    /**
     * Increment access counter for cache priority
     */
    private fun incrementAccessCount(key: String) {
        accessCounter.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
    }
    
    /**
     * Start memory monitoring
     */
    private fun startMemoryMonitoring() {
        backgroundScope.launch {
            while (isActive) {
                val memoryState = getCurrentMemoryState()
                
                when (memoryState) {
                    MemoryState.CRITICAL, MemoryState.EMERGENCY -> {
                        withContext(Dispatchers.Main) {
                            forceCleanup()
                        }
                    }
                    MemoryState.LOW -> {
                        withContext(Dispatchers.Main) {
                            cleanupUnusedItems()
                        }
                    }
                    else -> {
                        // Normal state, continue monitoring
                    }
                }
                
                delay(MEMORY_CHECK_INTERVAL)
            }
        }
    }
    
    /**
     * Schedule periodic cleanup
     */
    private fun schedulePeriodicCleanup() {
        backgroundScope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL)
                
                if (!isMemoryConstrained()) {
                    withContext(Dispatchers.Main) {
                        cleanupUnusedItems()
                    }
                }
            }
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            iconCacheSize = iconCache.size(),
            iconCacheHits = iconCache.hitCount().toLong(),
            iconCacheMisses = iconCache.missCount().toLong(),
            bitmapCacheSize = bitmapCache.size(),
            memoryUsage = getMemoryUsagePercentage(),
            memoryState = getCurrentMemoryState()
        )
    }
    
    /**
     * Cleanup all resources
     */
    fun cleanup() {
        backgroundScope.cancel()
        
        loadingTasks.values.forEach { it.cancel() }
        loadingTasks.clear()
        
        iconCache.evictAll()
        bitmapCache.evictAll()
        labelCache.evictAll()
        appInfoCache.evictAll()
        
        weakIconCache.clear()
        weakBitmapCache.clear()
        accessCounter.clear()
    }
    
    enum class MemoryState {
        ABUNDANT,   // < 50% memory usage
        NORMAL,     // 50-75% memory usage
        LOW,        // 75-85% memory usage
        CRITICAL,   // 85-95% memory usage
        EMERGENCY   // > 95% memory usage
    }
    
    data class CacheStats(
        val iconCacheSize: Int,
        val iconCacheHits: Long,
        val iconCacheMisses: Long,
        val bitmapCacheSize: Int,
        val memoryUsage: Double,
        val memoryState: MemoryState
    ) {
        val iconCacheHitRate: Double
            get() = if (iconCacheHits + iconCacheMisses > 0) {
                iconCacheHits.toDouble() / (iconCacheHits + iconCacheMisses).toDouble()
            } else 0.0
    }
}