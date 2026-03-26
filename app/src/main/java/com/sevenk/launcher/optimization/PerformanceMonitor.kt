package com.sevenk.launcher.optimization

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Comprehensive performance monitoring system that tracks and optimizes launcher performance
 */
class PerformanceMonitor(private val context: Context) : DefaultLifecycleObserver {
    
    // Core optimizers
    private var batteryOptimizer: AdvancedBatteryOptimizer? = null
    private var ramOptimizer: AdvancedRAMOptimizer? = null
    private var cpuOptimizer: AdvancedCPUOptimizer? = null
    private var launcherOptimizer: LauncherSpecificOptimizer? = null
    
    // Performance tracking
    private val performanceMetrics = PerformanceMetrics()
    private val alertThresholds = AlertThresholds()
    
    // Monitoring state
    private val _monitoringState = MutableStateFlow(MonitoringState())
    val monitoringState: StateFlow<MonitoringState> = _monitoringState.asStateFlow()
    
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Performance history
    private val performanceHistory = Collections.synchronizedList(mutableListOf<PerformanceSnapshot>())
    private val maxHistorySize = 300 // 5 minutes at 1-second intervals
    
    // Alert system
    private val activeAlerts = ConcurrentHashMap<AlertType, Alert>()
    private val alertCallbacks = mutableListOf<(Alert) -> Unit>()
    
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MONITORING_INTERVAL = 1000L // 1 second
        private const val HISTORY_CLEANUP_INTERVAL = 60000L // 1 minute
        private const val ALERT_COOLDOWN = 30000L // 30 seconds
        private const val PERFORMANCE_LOG_FILE = "launcher_performance.json"
    }
    
    init {
        startPerformanceMonitoring()
        scheduleHistoryCleanup()
    }
    
    /**
     * Initialize all optimizers
     */
    fun initializeOptimizers() {
        try {
            batteryOptimizer = AdvancedBatteryOptimizer(context)
            ramOptimizer = AdvancedRAMOptimizer(context)
            cpuOptimizer = AdvancedCPUOptimizer(context)
            launcherOptimizer = LauncherSpecificOptimizer(context)
            
            Log.d(TAG, "All optimizers initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize optimizers", e)
            triggerAlert(AlertType.INITIALIZATION_FAILED, "Optimizer initialization failed: ${e.message}")
        }
    }
    
    /**
     * Start comprehensive performance monitoring
     */
    private fun startPerformanceMonitoring() {
        monitoringScope.launch {
            while (isActive) {
                try {
                    val snapshot = capturePerformanceSnapshot()
                    updatePerformanceMetrics(snapshot)
                    checkPerformanceThresholds(snapshot)
                    
                    _monitoringState.value = _monitoringState.value.copy(
                        isMonitoring = true,
                        lastUpdate = System.currentTimeMillis(),
                        currentSnapshot = snapshot
                    )
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Performance monitoring iteration failed", e)
                }
                
                delay(MONITORING_INTERVAL)
            }
        }
    }
    
    /**
     * Capture current performance snapshot
     */
    private fun capturePerformanceSnapshot(): PerformanceSnapshot {
        val timestamp = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        
        // Memory metrics
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        val memoryUsage = (usedMemory.toDouble() / maxMemory.toDouble()) * 100.0
        
        // Battery metrics
        val batteryStats = batteryOptimizer?.getCurrentPowerProfile()?.let { profile ->
            BatteryMetrics(
                powerProfile = profile.name,
                isLowPowerMode = profile in arrayOf(
                    AdvancedBatteryOptimizer.PowerProfile.POWER_SAVER,
                    AdvancedBatteryOptimizer.PowerProfile.ULTRA_POWER_SAVER,
                    AdvancedBatteryOptimizer.PowerProfile.EMERGENCY
                ),
                adaptiveRefreshRate = batteryOptimizer?.getOptimalRefreshInterval() ?: 0L
            )
        } ?: BatteryMetrics("UNKNOWN", false, 0L)
        
        // RAM metrics
        val ramStats = ramOptimizer?.getCacheStats()?.let { stats ->
            RAMMetrics(
                cacheHitRate = stats.iconCacheHitRate,
                memoryState = stats.memoryState.name,
                iconCacheSize = stats.iconCacheSize,
                memoryUsage = stats.memoryUsage
            )
        } ?: RAMMetrics(0.0, "UNKNOWN", 0, memoryUsage / 100.0)
        
        // CPU metrics
        val cpuStats = cpuOptimizer?.getPerformanceStats()?.let { stats ->
            CPUMetrics(
                averageFrameTime = stats.averageFrameTime,
                droppedFramePercentage = stats.droppedFramePercentage,
                activeBatches = stats.activeBatches,
                gpuAcceleratedViews = stats.gpuAcceleratedViews
            )
        } ?: CPUMetrics(0f, 0f, 0, 0)
        
        // Launcher metrics
        val launcherStats = launcherOptimizer?.getOptimizationStats()?.let { stats ->
            LauncherMetrics(
                preloadedApps = stats.preloadedApps,
                cacheHitRate = stats.cacheHitRate,
                databaseSize = stats.databaseSize,
                currentRefreshInterval = stats.currentRefreshInterval
            )
        } ?: LauncherMetrics(0, 0.0, 0L, 0L)
        
        return PerformanceSnapshot(
            timestamp = timestamp,
            memoryUsageMB = usedMemory / (1024 * 1024),
            memoryUsagePercent = memoryUsage,
            availableMemoryMB = freeMemory / (1024 * 1024),
            batteryMetrics = batteryStats,
            ramMetrics = ramStats,
            cpuMetrics = cpuStats,
            launcherMetrics = launcherStats
        )
    }
    
    /**
     * Update performance metrics with new snapshot
     */
    private fun updatePerformanceMetrics(snapshot: PerformanceSnapshot) {
        synchronized(performanceHistory) {
            performanceHistory.add(snapshot)
            
            // Keep history size manageable
            while (performanceHistory.size > maxHistorySize) {
                performanceHistory.removeAt(0)
            }
        }
        
        // Update aggregated metrics
        performanceMetrics.apply {
            totalSnapshots.incrementAndGet()
            
            // Update memory metrics
            peakMemoryUsage.set(max(peakMemoryUsage.get(), snapshot.memoryUsageMB))
            averageMemoryUsage = calculateAverageMemoryUsage()
            
            // Update frame metrics
            if (snapshot.cpuMetrics.averageFrameTime > 0) {
                averageFrameTime = calculateAverageFrameTime()
                if (snapshot.cpuMetrics.droppedFramePercentage > 5.0f) {
                    frameDropEvents.incrementAndGet()
                }
            }
            
            // Update optimization events
            if (snapshot.batteryMetrics.isLowPowerMode) {
                lowPowerModeEvents.incrementAndGet()
            }
            
            if (snapshot.ramMetrics.memoryState in arrayOf("LOW", "CRITICAL", "EMERGENCY")) {
                memoryPressureEvents.incrementAndGet()
            }
        }
    }
    
    /**
     * Check performance thresholds and trigger alerts
     */
    private fun checkPerformanceThresholds(snapshot: PerformanceSnapshot) {
        // Memory usage alert
        if (snapshot.memoryUsagePercent > alertThresholds.memoryUsageThreshold) {
            triggerAlert(
                AlertType.HIGH_MEMORY_USAGE,
                "Memory usage: ${snapshot.memoryUsagePercent.toInt()}%",
                AlertSeverity.WARNING
            )
        }
        
        // Frame rate alert
        if (snapshot.cpuMetrics.droppedFramePercentage > alertThresholds.frameDropThreshold) {
            triggerAlert(
                AlertType.FRAME_DROPS,
                "Frame drops: ${snapshot.cpuMetrics.droppedFramePercentage.toInt()}%",
                AlertSeverity.WARNING
            )
        }
        
        // Battery optimization alert
        if (snapshot.batteryMetrics.isLowPowerMode && 
            snapshot.batteryMetrics.adaptiveRefreshRate > alertThresholds.refreshRateThreshold) {
            triggerAlert(
                AlertType.BATTERY_OPTIMIZATION,
                "High refresh rate in low power mode",
                AlertSeverity.INFO
            )
        }
        
        // Cache performance alert
        if (snapshot.ramMetrics.cacheHitRate < alertThresholds.cacheHitRateThreshold) {
            triggerAlert(
                AlertType.LOW_CACHE_PERFORMANCE,
                "Cache hit rate: ${(snapshot.ramMetrics.cacheHitRate * 100).toInt()}%",
                AlertSeverity.INFO
            )
        }
    }
    
    /**
     * Trigger performance alert
     */
    private fun triggerAlert(type: AlertType, message: String, severity: AlertSeverity = AlertSeverity.WARNING) {
        val currentTime = System.currentTimeMillis()
        val existingAlert = activeAlerts[type]
        
        // Check cooldown period
        if (existingAlert != null && currentTime - existingAlert.timestamp < ALERT_COOLDOWN) {
            return
        }
        
        val alert = Alert(
            type = type,
            message = message,
            severity = severity,
            timestamp = currentTime
        )
        
        activeAlerts[type] = alert
        
        // Notify callbacks
        alertCallbacks.forEach { callback ->
            try {
                callback(alert)
            } catch (e: Exception) {
                Log.w(TAG, "Alert callback failed", e)
            }
        }
        
        Log.w(TAG, "Performance Alert [${severity.name}] ${type.name}: $message")
    }
    
    /**
     * Get current performance summary
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val recentSnapshots = synchronized(performanceHistory) {
            performanceHistory.takeLast(60) // Last minute
        }
        
        return PerformanceSummary(
            monitoringDuration = performanceMetrics.totalSnapshots.get() * MONITORING_INTERVAL,
            averageMemoryUsage = performanceMetrics.averageMemoryUsage,
            peakMemoryUsage = performanceMetrics.peakMemoryUsage.get(),
            averageFrameTime = performanceMetrics.averageFrameTime,
            frameDropEvents = performanceMetrics.frameDropEvents.get(),
            lowPowerModeEvents = performanceMetrics.lowPowerModeEvents.get(),
            memoryPressureEvents = performanceMetrics.memoryPressureEvents.get(),
            activeAlerts = activeAlerts.values.toList(),
            currentOptimizationState = getCurrentOptimizationState(),
            recentTrend = calculatePerformanceTrend(recentSnapshots)
        )
    }
    
    /**
     * Get current optimization state
     */
    private fun getCurrentOptimizationState(): OptimizationState {
        return OptimizationState(
            batteryOptimizationActive = batteryOptimizer != null,
            ramOptimizationActive = ramOptimizer != null,
            cpuOptimizationActive = cpuOptimizer != null,
            launcherOptimizationActive = launcherOptimizer != null,
            totalOptimizersActive = listOfNotNull(
                batteryOptimizer, ramOptimizer, cpuOptimizer, launcherOptimizer
            ).size
        )
    }
    
    /**
     * Calculate performance trend
     */
    private fun calculatePerformanceTrend(snapshots: List<PerformanceSnapshot>): PerformanceTrend {
        if (snapshots.size < 2) return PerformanceTrend.STABLE
        
        val firstHalf = snapshots.take(snapshots.size / 2)
        val secondHalf = snapshots.takeLast(snapshots.size / 2)
        
        val firstAvgMemory = firstHalf.map { it.memoryUsagePercent }.average()
        val secondAvgMemory = secondHalf.map { it.memoryUsagePercent }.average()
        
        val memoryDiff = secondAvgMemory - firstAvgMemory
        
        return when {
            memoryDiff > 5.0 -> PerformanceTrend.DEGRADING
            memoryDiff < -5.0 -> PerformanceTrend.IMPROVING
            else -> PerformanceTrend.STABLE
        }
    }
    
    /**
     * Calculate average memory usage
     */
    private fun calculateAverageMemoryUsage(): Double {
        synchronized(performanceHistory) {
            return if (performanceHistory.isNotEmpty()) {
                performanceHistory.map { it.memoryUsageMB }.average()
            } else 0.0
        }
    }
    
    /**
     * Calculate average frame time
     */
    private fun calculateAverageFrameTime(): Float {
        synchronized(performanceHistory) {
            val frameTimes = performanceHistory.mapNotNull { 
                if (it.cpuMetrics.averageFrameTime > 0) it.cpuMetrics.averageFrameTime else null
            }
            return if (frameTimes.isNotEmpty()) {
                frameTimes.average().toFloat()
            } else 0f
        }
    }
    
    /**
     * Export performance data to JSON
     */
    fun exportPerformanceData(): String {
        val json = JSONObject().apply {
            put("exportTimestamp", System.currentTimeMillis())
            put("monitoringDuration", performanceMetrics.totalSnapshots.get() * MONITORING_INTERVAL)
            put("summary", JSONObject().apply {
                val summary = getPerformanceSummary()
                put("averageMemoryUsage", summary.averageMemoryUsage)
                put("peakMemoryUsage", summary.peakMemoryUsage)
                put("averageFrameTime", summary.averageFrameTime)
                put("frameDropEvents", summary.frameDropEvents)
                put("performanceTrend", summary.recentTrend.name)
            })
            
            put("snapshots", JSONArray().apply {
                synchronized(performanceHistory) {
                    performanceHistory.forEach { snapshot ->
                        put(JSONObject().apply {
                            put("timestamp", snapshot.timestamp)
                            put("memoryUsageMB", snapshot.memoryUsageMB)
                            put("memoryUsagePercent", snapshot.memoryUsagePercent)
                            put("frameTime", snapshot.cpuMetrics.averageFrameTime)
                            put("cacheHitRate", snapshot.ramMetrics.cacheHitRate)
                        })
                    }
                }
            })
        }
        
        return json.toString(2)
    }
    
    /**
     * Save performance data to file
     */
    fun savePerformanceData() {
        backgroundScope.launch {
            try {
                val file = File(context.filesDir, PERFORMANCE_LOG_FILE)
                FileWriter(file).use { writer ->
                    writer.write(exportPerformanceData())
                }
                Log.d(TAG, "Performance data saved to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save performance data", e)
            }
        }
    }
    
    /**
     * Add alert callback
     */
    fun addAlertCallback(callback: (Alert) -> Unit) {
        alertCallbacks.add(callback)
    }
    
    /**
     * Remove alert callback
     */
    fun removeAlertCallback(callback: (Alert) -> Unit) {
        alertCallbacks.remove(callback)
    }
    
    /**
     * Schedule periodic history cleanup
     */
    private fun scheduleHistoryCleanup() {
        backgroundScope.launch {
            while (isActive) {
                delay(HISTORY_CLEANUP_INTERVAL)
                
                synchronized(performanceHistory) {
                    val cutoffTime = System.currentTimeMillis() - (maxHistorySize * MONITORING_INTERVAL)
                    performanceHistory.removeAll { it.timestamp < cutoffTime }
                }
                
                // Clear old alerts
                val alertCutoffTime = System.currentTimeMillis() - (ALERT_COOLDOWN * 3)
                activeAlerts.values.removeAll { it.timestamp < alertCutoffTime }
            }
        }
    }
    
    override fun onStart(owner: LifecycleOwner) {
        launcherOptimizer?.updateInteractionState(true)
    }
    
    override fun onStop(owner: LifecycleOwner) {
        launcherOptimizer?.updateInteractionState(false)
        savePerformanceData()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
    }
    
    /**
     * Cleanup all resources
     */
    fun cleanup() {
        backgroundScope.cancel()
        monitoringScope.cancel()
        
        batteryOptimizer?.cleanup()
        ramOptimizer?.cleanup()
        cpuOptimizer?.cleanup()
        launcherOptimizer?.cleanup()
        
        alertCallbacks.clear()
        activeAlerts.clear()
        
        synchronized(performanceHistory) {
            performanceHistory.clear()
        }
    }
    
    // Data classes for performance tracking
    data class MonitoringState(
        val isMonitoring: Boolean = false,
        val lastUpdate: Long = 0L,
        val currentSnapshot: PerformanceSnapshot? = null
    )
    
    data class PerformanceSnapshot(
        val timestamp: Long,
        val memoryUsageMB: Long,
        val memoryUsagePercent: Double,
        val availableMemoryMB: Long,
        val batteryMetrics: BatteryMetrics,
        val ramMetrics: RAMMetrics,
        val cpuMetrics: CPUMetrics,
        val launcherMetrics: LauncherMetrics
    )
    
    data class BatteryMetrics(
        val powerProfile: String,
        val isLowPowerMode: Boolean,
        val adaptiveRefreshRate: Long
    )
    
    data class RAMMetrics(
        val cacheHitRate: Double,
        val memoryState: String,
        val iconCacheSize: Int,
        val memoryUsage: Double
    )
    
    data class CPUMetrics(
        val averageFrameTime: Float,
        val droppedFramePercentage: Float,
        val activeBatches: Int,
        val gpuAcceleratedViews: Int
    )
    
    data class LauncherMetrics(
        val preloadedApps: Int,
        val cacheHitRate: Double,
        val databaseSize: Long,
        val currentRefreshInterval: Long
    )
    
    data class PerformanceSummary(
        val monitoringDuration: Long,
        val averageMemoryUsage: Double,
        val peakMemoryUsage: Long,
        val averageFrameTime: Float,
        val frameDropEvents: Long,
        val lowPowerModeEvents: Long,
        val memoryPressureEvents: Long,
        val activeAlerts: List<Alert>,
        val currentOptimizationState: OptimizationState,
        val recentTrend: PerformanceTrend
    )
    
    data class OptimizationState(
        val batteryOptimizationActive: Boolean,
        val ramOptimizationActive: Boolean,
        val cpuOptimizationActive: Boolean,
        val launcherOptimizationActive: Boolean,
        val totalOptimizersActive: Int
    )
    
    data class Alert(
        val type: AlertType,
        val message: String,
        val severity: AlertSeverity,
        val timestamp: Long
    )
    
    enum class AlertType {
        HIGH_MEMORY_USAGE,
        FRAME_DROPS,
        BATTERY_OPTIMIZATION,
        LOW_CACHE_PERFORMANCE,
        INITIALIZATION_FAILED
    }
    
    enum class AlertSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    enum class PerformanceTrend {
        IMPROVING, STABLE, DEGRADING
    }
    
    private class PerformanceMetrics {
        val totalSnapshots = AtomicLong(0)
        val peakMemoryUsage = AtomicLong(0)
        var averageMemoryUsage = 0.0
        var averageFrameTime = 0f
        val frameDropEvents = AtomicLong(0)
        val lowPowerModeEvents = AtomicLong(0)
        val memoryPressureEvents = AtomicLong(0)
    }
    
    private class AlertThresholds {
        val memoryUsageThreshold = 85.0 // 85%
        val frameDropThreshold = 10.0f // 10% dropped frames
        val refreshRateThreshold = 100L // 100ms refresh rate
        val cacheHitRateThreshold = 0.7 // 70% cache hit rate
    }
}