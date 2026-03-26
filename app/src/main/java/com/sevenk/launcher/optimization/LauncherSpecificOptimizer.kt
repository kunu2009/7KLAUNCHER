package com.sevenk.launcher.optimization

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.collection.LruCache
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Launcher-specific optimizations focusing on visible app preloading, lightweight database, and gesture optimization
 */
class LauncherSpecificOptimizer(private val context: Context) : DefaultLifecycleObserver {
    
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val prefs = context.getSharedPreferences("launcher_optimization", Context.MODE_PRIVATE)
    
    // Optimized database helper
    private val dbHelper = OptimizedLauncherDatabase(context)
    
    // Visible app management
    private val visibleAppPreloader = VisibleAppPreloader()
    private val appUsageTracker = AppUsageTracker()
    
    // Gesture optimization
    private val gestureOptimizer = GestureOptimizer(context)
    
    // Background processing
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Adaptive refresh management
    private val adaptiveRefreshManager = AdaptiveRefreshManager()
    
    companion object {
        private const val TAG = "LauncherOptimizer"
        private const val VISIBLE_APPS_CACHE_SIZE = 50
        private const val PRELOAD_BATCH_SIZE = 10
        private const val USAGE_TRACKING_LIMIT = 100
        private const val DATABASE_VERSION = 2
        private const val REFRESH_INTERVAL_ACTIVE = 100L
        private const val REFRESH_INTERVAL_IDLE = 1000L
        private const val REFRESH_INTERVAL_BACKGROUND = 5000L
    }
    
    init {
        startAdaptiveRefresh()
    }
    
    /**
     * Preload only visible apps to reduce memory usage
     */
    fun preloadVisibleApps(visiblePackages: List<String>) {
        visibleAppPreloader.updateVisibleApps(visiblePackages)
    }
    
    /**
     * Get app data with intelligent caching and preloading
     */
    suspend fun getAppData(packageName: String, isVisible: Boolean = false): AppData? {
        return visibleAppPreloader.getAppData(packageName, isVisible)
    }
    
    /**
     * Track app usage for intelligent preloading
     */
    fun trackAppUsage(packageName: String) {
        appUsageTracker.recordUsage(packageName)
        
        // Update database in background
        backgroundScope.launch {
            dbHelper.updateAppUsage(packageName)
        }
    }
    
    /**
     * Get most used apps for prioritized preloading
     */
    suspend fun getMostUsedApps(limit: Int = 20): List<String> {
        return withContext(Dispatchers.IO) {
            dbHelper.getMostUsedApps(limit)
        }
    }
    
    /**
     * Optimize gesture detection with reduced CPU usage
     */
    fun optimizeGestureDetection(motionEvent: MotionEvent): GestureResult {
        return gestureOptimizer.processGesture(motionEvent)
    }
    
    /**
     * Update adaptive refresh rate based on user interaction
     */
    fun updateInteractionState(isActive: Boolean) {
        adaptiveRefreshManager.setActive(isActive)
    }
    
    /**
     * Get current adaptive refresh interval
     */
    fun getCurrentRefreshInterval(): Long {
        return adaptiveRefreshManager.getCurrentInterval()
    }
    
    /**
     * Batch database operations for efficiency
     */
    fun batchDatabaseOperations(operations: List<() -> Unit>) {
        backgroundScope.launch {
            dbHelper.executeBatch(operations)
        }
    }
    
    /**
     * Clear unused cache entries
     */
    fun clearUnusedCache() {
        visibleAppPreloader.clearUnusedCache()
        appUsageTracker.cleanup()
    }
    
    /**
     * Get launcher optimization statistics
     */
    fun getOptimizationStats(): LauncherOptimizationStats {
        return LauncherOptimizationStats(
            preloadedApps = visibleAppPreloader.getPreloadedCount(),
            cacheHitRate = visibleAppPreloader.getCacheHitRate(),
            databaseSize = dbHelper.getDatabaseSize(),
            currentRefreshInterval = adaptiveRefreshManager.getCurrentInterval(),
            gestureOptimizationActive = gestureOptimizer.isOptimizationActive(),
            trackedApps = appUsageTracker.getTrackedAppCount()
        )
    }
    
    private fun startAdaptiveRefresh() {
        // Start with active refresh rate
        adaptiveRefreshManager.setActive(true)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        adaptiveRefreshManager.setActive(true)
        visibleAppPreloader.onForeground()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        adaptiveRefreshManager.setActive(false)
        visibleAppPreloader.onBackground()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
    }
    
    fun cleanup() {
        backgroundScope.cancel()
        mainScope.cancel()
        dbHelper.close()
        gestureOptimizer.cleanup()
        visibleAppPreloader.cleanup()
    }
    
    /**
     * Visible app preloader with intelligent caching
     */
    private inner class VisibleAppPreloader {
        private val appDataCache = LruCache<String, AppData>(VISIBLE_APPS_CACHE_SIZE)
        private val visibleApps = mutableSetOf<String>()
        private val preloadingQueue = ConcurrentHashMap<String, Job>()
        private var cacheHits = 0L
        private var cacheMisses = 0L
        private var isInForeground = true
        
        fun updateVisibleApps(packages: List<String>) {
            val newVisible = packages.toSet()
            val toRemove = visibleApps - newVisible
            val toAdd = newVisible - visibleApps
            
            // Remove non-visible apps from cache
            toRemove.forEach { pkg ->
                appDataCache.remove(pkg)
                preloadingQueue[pkg]?.cancel()
                preloadingQueue.remove(pkg)
            }
            
            visibleApps.clear()
            visibleApps.addAll(newVisible)
            
            // Preload new visible apps
            if (isInForeground && toAdd.isNotEmpty()) {
                preloadApps(toAdd.toList())
            }
        }
        
        suspend fun getAppData(packageName: String, isVisible: Boolean): AppData? {
            // Check cache first
            appDataCache.get(packageName)?.let {
                cacheHits++
                return it
            }
            
            cacheMisses++
            
            // Load data if visible or high priority
            if (isVisible || isHighPriority(packageName)) {
                return loadAppData(packageName)
            }
            
            return null
        }
        
        private fun preloadApps(packages: List<String>) {
            packages.chunked(PRELOAD_BATCH_SIZE).forEach { batch ->
                backgroundScope.launch {
                    batch.forEach { packageName ->
                        if (visibleApps.contains(packageName)) {
                            preloadAppData(packageName)
                        }
                    }
                }
            }
        }
        
        private suspend fun preloadAppData(packageName: String) {
            if (preloadingQueue.contains(packageName)) return
            
            val job = backgroundScope.async {
                loadAppData(packageName)
            }
            
            preloadingQueue[packageName] = job
            
            try {
                val appData = job.await()
                if (appData != null && visibleApps.contains(packageName)) {
                    appDataCache.put(packageName, appData)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to preload app data for $packageName", e)
            } finally {
                preloadingQueue.remove(packageName)
            }
        }
        
        private suspend fun loadAppData(packageName: String): AppData? {
            return withContext(Dispatchers.IO) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    
                    AppData(
                        packageName = packageName,
                        label = label,
                        icon = icon,
                        lastUsed = appUsageTracker.getLastUsedTime(packageName),
                        usageCount = appUsageTracker.getUsageCount(packageName)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load app data for $packageName", e)
                    null
                }
            }
        }
        
        private fun isHighPriority(packageName: String): Boolean {
            return appUsageTracker.getUsageCount(packageName) > 10 ||
                    System.currentTimeMillis() - appUsageTracker.getLastUsedTime(packageName) < 24 * 60 * 60 * 1000 // Used within 24 hours
        }
        
        fun onForeground() {
            isInForeground = true
        }
        
        fun onBackground() {
            isInForeground = false
            // Cancel preloading when in background
            preloadingQueue.values.forEach { it.cancel() }
            preloadingQueue.clear()
        }
        
        fun clearUnusedCache() {
            val unusedKeys = mutableListOf<String>()
            
            // Remove apps not in visible set and not recently used
            appDataCache.snapshot().forEach { (key, appData) ->
                if (!visibleApps.contains(key) && 
                    System.currentTimeMillis() - appData.lastUsed > 60 * 60 * 1000) { // 1 hour
                    unusedKeys.add(key)
                }
            }
            
            unusedKeys.forEach { appDataCache.remove(it) }
            Log.d(TAG, "Cleared ${unusedKeys.size} unused cache entries")
        }
        
        fun getPreloadedCount(): Int = appDataCache.size()
        
        fun getCacheHitRate(): Double {
            val total = cacheHits + cacheMisses
            return if (total > 0) cacheHits.toDouble() / total.toDouble() else 0.0
        }
        
        fun cleanup() {
            preloadingQueue.values.forEach { it.cancel() }
            preloadingQueue.clear()
            appDataCache.evictAll()
        }
    }
    
    /**
     * App usage tracking for intelligent preloading
     */
    private inner class AppUsageTracker {
        private val usageData = ConcurrentHashMap<String, UsageData>()
        private val maxEntries = USAGE_TRACKING_LIMIT
        
        fun recordUsage(packageName: String) {
            val currentTime = System.currentTimeMillis()
            val usage = usageData.getOrPut(packageName) { UsageData() }
            
            usage.count++
            usage.lastUsed = currentTime
            
            // Cleanup if too many entries
            if (usageData.size > maxEntries) {
                cleanup()
            }
        }
        
        fun getUsageCount(packageName: String): Int {
            return usageData[packageName]?.count ?: 0
        }
        
        fun getLastUsedTime(packageName: String): Long {
            return usageData[packageName]?.lastUsed ?: 0L
        }
        
        fun getTrackedAppCount(): Int = usageData.size
        
        fun cleanup() {
            val cutoffTime = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L // 30 days
            val toRemove = usageData.filterValues { it.lastUsed < cutoffTime }.keys
            toRemove.forEach { usageData.remove(it) }
        }
    }
    
    private data class UsageData(
        var count: Int = 0,
        var lastUsed: Long = 0L
    )
    
    /**
     * Optimized gesture detection
     */
    private class GestureOptimizer(context: Context) {
        private val viewConfiguration = ViewConfiguration.get(context)
        private val touchSlop = viewConfiguration.scaledTouchSlop
        private val minimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
        private val maximumFlingVelocity = viewConfiguration.scaledMaximumFlingVelocity
        
        private var velocityTracker: VelocityTracker? = null
        private var isOptimizationActive = AtomicBoolean(true)
        private var lastEventTime = 0L
        private val eventThrottleMs = 16L // ~60fps
        
        fun processGesture(event: MotionEvent): GestureResult {
            if (!isOptimizationActive.get()) {
                return GestureResult.IGNORED
            }
            
            // Throttle events to reduce CPU usage
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEventTime < eventThrottleMs && 
                event.action != MotionEvent.ACTION_DOWN && 
                event.action != MotionEvent.ACTION_UP) {
                return GestureResult.THROTTLED
            }
            lastEventTime = currentTime
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    return GestureResult.DOWN
                }
                
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    return GestureResult.MOVE
                }
                
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.let { tracker ->
                        tracker.addMovement(event)
                        tracker.computeCurrentVelocity(1000)
                        
                        val velocityX = tracker.xVelocity
                        val velocityY = tracker.yVelocity
                        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
                        
                        tracker.recycle()
                        velocityTracker = null
                        
                        return if (speed > minimumFlingVelocity) {
                            when {
                                abs(velocityY) > abs(velocityX) -> {
                                    if (velocityY < 0) GestureResult.SWIPE_UP
                                    else GestureResult.SWIPE_DOWN
                                }
                                abs(velocityX) > abs(velocityY) -> {
                                    if (velocityX < 0) GestureResult.SWIPE_LEFT
                                    else GestureResult.SWIPE_RIGHT
                                }
                                else -> GestureResult.TAP
                            }
                        } else {
                            GestureResult.TAP
                        }
                    }
                    return GestureResult.UP
                }
                
                else -> return GestureResult.OTHER
            }
        }
        
        fun isOptimizationActive(): Boolean = isOptimizationActive.get()
        
        fun setOptimizationActive(active: Boolean) {
            isOptimizationActive.set(active)
        }
        
        fun cleanup() {
            velocityTracker?.recycle()
            velocityTracker = null
        }
    }
    
    /**
     * Adaptive refresh rate management
     */
    private class AdaptiveRefreshManager {
        private var isActive = true
        private var lastInteractionTime = System.currentTimeMillis()
        
        fun setActive(active: Boolean) {
            isActive = active
            if (active) {
                lastInteractionTime = System.currentTimeMillis()
            }
        }
        
        fun getCurrentInterval(): Long {
            return when {
                !isActive -> REFRESH_INTERVAL_BACKGROUND
                System.currentTimeMillis() - lastInteractionTime < 5000 -> REFRESH_INTERVAL_ACTIVE
                else -> REFRESH_INTERVAL_IDLE
            }
        }
    }
    
    /**
     * Optimized launcher database
     */
    private class OptimizedLauncherDatabase(context: Context) : SQLiteOpenHelper(
        context, "launcher_optimized.db", null, DATABASE_VERSION
    ) {
        
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE app_usage (
                    package_name TEXT PRIMARY KEY,
                    usage_count INTEGER DEFAULT 0,
                    last_used INTEGER DEFAULT 0
                )
            """)
            
            db.execSQL("CREATE INDEX idx_usage_count ON app_usage(usage_count DESC)")
            db.execSQL("CREATE INDEX idx_last_used ON app_usage(last_used DESC)")
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_count ON app_usage(usage_count DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_last_used ON app_usage(last_used DESC)")
            }
        }
        
        fun updateAppUsage(packageName: String) {
            writableDatabase.use { db ->
                db.execSQL("""
                    INSERT OR REPLACE INTO app_usage (package_name, usage_count, last_used)
                    VALUES (?, COALESCE((SELECT usage_count FROM app_usage WHERE package_name = ?), 0) + 1, ?)
                """, arrayOf(packageName, packageName, System.currentTimeMillis()))
            }
        }
        
        fun getMostUsedApps(limit: Int): List<String> {
            return readableDatabase.use { db ->
                val cursor = db.rawQuery(
                    "SELECT package_name FROM app_usage ORDER BY usage_count DESC, last_used DESC LIMIT ?",
                    arrayOf(limit.toString())
                )
                
                val result = mutableListOf<String>()
                cursor.use {
                    while (it.moveToNext()) {
                        result.add(it.getString(0))
                    }
                }
                result
            }
        }
        
        fun executeBatch(operations: List<() -> Unit>) {
            writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    operations.forEach { it() }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        
        fun getDatabaseSize(): Long {
            return readableDatabase.use { db ->
                val cursor = db.rawQuery("SELECT page_count * page_size as size FROM pragma_page_count(), pragma_page_size()", null)
                cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else 0L
                }
            }
        }
    }
    
    enum class GestureResult {
        DOWN, MOVE, UP, TAP,
        SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
        IGNORED, THROTTLED, OTHER
    }
    
    data class AppData(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable,
        val lastUsed: Long,
        val usageCount: Int
    )
    
    data class LauncherOptimizationStats(
        val preloadedApps: Int,
        val cacheHitRate: Double,
        val databaseSize: Long,
        val currentRefreshInterval: Long,
        val gestureOptimizationActive: Boolean,
        val trackedApps: Int
    )
}