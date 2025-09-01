package com.sevenk.launcher.util

import android.os.SystemClock
import android.util.Log
import androidx.tracing.Trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Performance utilities for tracking and measuring app performance.
 * Enhanced version that collects metrics for important operations.
 */
object Perf {
    private const val TAG = "7KLauncherPerf"
    private val metrics = ConcurrentHashMap<String, MutableList<Long>>()
    private val thresholds = mapOf(
        "AppDrawerOpen" to 100L, // 100ms threshold for app drawer opening
        "IconPackLoading" to 200L, // 200ms threshold for icon pack loading
        "GlassBlurApply" to 50L, // 50ms threshold for applying glass blur effect
        "WidgetInflate" to 150L, // 150ms threshold for widget inflation
        "HomePageSwitch" to 80L // 80ms threshold for home page switching
    )

    fun begin(name: String) {
        try { Trace.beginSection(name.take(127)) } catch (_: Throwable) {}
    }

    fun end() {
        try { Trace.endSection() } catch (_: Throwable) {}
    }

    inline fun <T> trace(name: String, block: () -> T): T {
        begin(name)
        val startTime = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
        } finally {
            val endTime = SystemClock.elapsedRealtimeNanos()
            val durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)
            recordMetric(name, durationMs)
            end()
        }
    }

    /**
     * Records a performance metric for the given operation.
     */
    fun recordMetric(name: String, durationMs: Long) {
        metrics.getOrPut(name) { mutableListOf() }.add(durationMs)

        // Log slow operations based on thresholds
        val threshold = thresholds[name]
        if (threshold != null && durationMs > threshold) {
            Log.w(TAG, "SLOW OPERATION: $name took $durationMs ms (threshold: $threshold ms)")
        }
    }

    /**
     * Gets the average duration for a specific operation.
     */
    fun getAverageDuration(name: String): Double {
        val durations = metrics[name] ?: return 0.0
        if (durations.isEmpty()) return 0.0
        return durations.average()
    }

    /**
     * Resets collected metrics.
     */
    fun resetMetrics() {
        metrics.clear()
    }

    /**
     * Dumps all performance metrics to the log.
     */
    fun dumpMetrics() {
        Log.i(TAG, "===== PERFORMANCE METRICS =====")
        metrics.forEach { (name, durations) ->
            if (durations.isNotEmpty()) {
                val avg = durations.average()
                val min = durations.minOrNull() ?: 0
                val max = durations.maxOrNull() ?: 0
                val count = durations.size
                Log.i(TAG, "$name: avg=${avg.toInt()}ms, min=${min}ms, max=${max}ms, count=$count")
            }
        }
        Log.i(TAG, "================================")
    }

    /**
     * Creates a performance report that can be displayed to the user or sent for analysis.
     */
    suspend fun generatePerformanceReport(): Map<String, Map<String, Any>> = withContext(Dispatchers.Default) {
        val report = mutableMapOf<String, Map<String, Any>>()

        metrics.forEach { (name, durations) ->
            if (durations.isNotEmpty()) {
                val avg = durations.average()
                val min = durations.minOrNull() ?: 0
                val max = durations.maxOrNull() ?: 0
                val count = durations.size
                val threshold = thresholds[name]
                val status = if (threshold != null) {
                    if (avg <= threshold) "GOOD" else "NEEDS_IMPROVEMENT"
                } else "UNKNOWN"

                report[name] = mapOf(
                    "avgMs" to avg,
                    "minMs" to min,
                    "maxMs" to max,
                    "count" to count,
                    "threshold" to (threshold ?: 0),
                    "status" to status
                )
            }
        }

        report
    }
}
