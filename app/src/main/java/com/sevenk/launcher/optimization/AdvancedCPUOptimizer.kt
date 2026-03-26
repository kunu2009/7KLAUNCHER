package com.sevenk.launcher.optimization

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderNode
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Advanced CPU optimization system focusing on GPU acceleration, debounced operations, and intelligent batching
 */
class AdvancedCPUOptimizer(private val context: Context) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Debounced operations management
    private val debouncedOperations = ConcurrentHashMap<String, Job>()
    private val batchedOperations = ConcurrentHashMap<String, BatchedOperation>()
    
    // Performance monitoring
    private val frameTimeTracker = FrameTimeTracker()
    private val cpuUsageMonitor = CPUUsageMonitor()
    
    // GPU acceleration helpers
    private val gpuAcceleratedViews = mutableSetOf<View>()
    private val renderNodes = ConcurrentHashMap<String, RenderNode>()
    
    // Update batching
    private val updateBatcher = UpdateBatcher()
    private val isOptimizationActive = AtomicBoolean(true)
    
    companion object {
        private const val TAG = "AdvancedCPUOptimizer"
        private const val DEFAULT_DEBOUNCE_DELAY = 150L
        private const val BATCH_DELAY = 16L // One frame at 60fps
        private const val MAX_BATCH_SIZE = 50
        private const val FRAME_TIME_THRESHOLD = 16.67f // 60 FPS target
        private const val CPU_USAGE_CHECK_INTERVAL = 2000L
    }
    
    init {
        startPerformanceMonitoring()
        setupChoreographerCallback()
    }
    
    /**
     * Enable GPU acceleration for a view hierarchy
     */
    fun enableGPUAcceleration(view: View, aggressive: Boolean = false) {
        try {
            // Enable hardware acceleration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                gpuAcceleratedViews.add(view)
                
                if (aggressive) {
                    // Enable additional GPU optimizations
                    ViewCompat.setHasTransientState(view, true)
                    
                    if (view is ViewGroup) {
                        optimizeViewGroupForGPU(view)
                    }
                }
                
                Log.d(TAG, "GPU acceleration enabled for ${view::class.simpleName}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable GPU acceleration", e)
        }
    }
    
    /**
     * Optimize ViewGroup for GPU rendering
     */
    private fun optimizeViewGroupForGPU(viewGroup: ViewGroup) {
        // Disable clipping for better GPU performance
        viewGroup.clipChildren = false
        viewGroup.clipToPadding = false
        
        // Enable drawing cache
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            viewGroup.isDrawingCacheEnabled = true
        }
        
        // Optimize children
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                optimizeViewGroupForGPU(child)
            } else {
                enableGPUAcceleration(child)
            }
        }
    }
    
    /**
     * Debounce an operation to reduce CPU usage
     */
    fun debounceOperation(
        operationId: String,
        delayMs: Long = DEFAULT_DEBOUNCE_DELAY,
        operation: suspend () -> Unit
    ) {
        // Cancel existing operation
        debouncedOperations[operationId]?.cancel()
        
        // Schedule new operation
        val job = uiScope.launch {
            delay(delayMs)
            try {
                operation()
            } catch (e: Exception) {
                Log.w(TAG, "Debounced operation failed: $operationId", e)
            } finally {
                debouncedOperations.remove(operationId)
            }
        }
        
        debouncedOperations[operationId] = job
    }
    
    /**
     * Batch multiple operations together to reduce CPU overhead
     */
    fun batchOperation(
        batchId: String,
        operation: suspend () -> Unit,
        priority: BatchPriority = BatchPriority.NORMAL
    ) {
        val batchedOp = batchedOperations.getOrPut(batchId) {
            BatchedOperation(batchId, priority)
        }
        
        batchedOp.addOperation(operation)
        
        // Execute batch if it's full or after delay
        if (batchedOp.size >= MAX_BATCH_SIZE) {
            executeBatch(batchId)
        } else {
            scheduleDelayedBatchExecution(batchId)
        }
    }
    
    /**
     * Execute a batch of operations
     */
    private fun executeBatch(batchId: String) {
        val batch = batchedOperations.remove(batchId) ?: return
        
        uiScope.launch {
            val startTime = System.nanoTime()
            
            try {
                batch.execute()
                
                val executionTime = (System.nanoTime() - startTime) / 1_000_000f
                Log.d(TAG, "Batch $batchId executed in ${executionTime}ms (${batch.size} operations)")
                
            } catch (e: Exception) {
                Log.w(TAG, "Batch execution failed: $batchId", e)
            }
        }
    }
    
    /**
     * Schedule delayed batch execution
     */
    private fun scheduleDelayedBatchExecution(batchId: String) {
        mainHandler.removeCallbacks { executeBatch(batchId) }
        mainHandler.postDelayed({ executeBatch(batchId) }, BATCH_DELAY)
    }
    
    /**
     * Optimize RecyclerView for CPU performance
     */
    fun optimizeRecyclerViewCPU(recyclerView: RecyclerView) {
        // Enable GPU acceleration
        enableGPUAcceleration(recyclerView, aggressive = true)
        
        // Optimize drawing
        recyclerView.setHasFixedSize(true)
        recyclerView.isDrawingCacheEnabled = false // Prefer GPU layers
        
        // Reduce overdraw
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false
        
        // Optimize scrolling performance
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // Re-enable expensive operations
                        enableExpensiveOperations(recyclerView)
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING,
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        // Disable expensive operations during scroll
                        disableExpensiveOperations(recyclerView)
                    }
                }
            }
        })
        
        Log.d(TAG, "RecyclerView CPU optimizations applied")
    }
    
    /**
     * Disable expensive operations during scrolling
     */
    private fun disableExpensiveOperations(recyclerView: RecyclerView) {
        // Temporarily disable item animations
        recyclerView.itemAnimator = null
        
        // Reduce nested scroll handling
        recyclerView.isNestedScrollingEnabled = false
        
        // Batch any pending layout operations
        batchOperation("scroll_layout_${recyclerView.hashCode()}", {
            recyclerView.requestLayout()
        }, BatchPriority.HIGH)
    }
    
    /**
     * Re-enable expensive operations after scrolling
     */
    private fun enableExpensiveOperations(recyclerView: RecyclerView) {
        debounceOperation("restore_animations_${recyclerView.hashCode()}", 100) {
            // Restore item animations after scroll settles
            if (recyclerView.itemAnimator == null) {
                recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
            }
            recyclerView.isNestedScrollingEnabled = true
        }
    }
    
    /**
     * Optimize animation performance
     */
    fun optimizeAnimation(animator: ValueAnimator): ValueAnimator {
        // Use GPU-friendly interpolators
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        
        // Reduce update frequency on low-end devices
        if (isLowEndDevice()) {
            val originalDuration = animator.duration
            animator.duration = max(originalDuration / 2, 100L)
        }
        
        // Add frame skip logic for performance
        animator.addUpdateListener { animation ->
            if (frameTimeTracker.isFrameDropped()) {
                // Skip this frame if we're dropping frames
                return@addUpdateListener
            }
            
            // Normal animation update
            (animation.animatedValue as? Float)?.let { value ->
                // Process animation value
            }
        }
        
        return animator
    }
    
    /**
     * Create optimized render node for complex drawing
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    fun createOptimizedRenderNode(name: String, width: Int, height: Int): RenderNode? {
        return try {
            val renderNode = RenderNode(name).apply {
                setPosition(0, 0, width, height)
                
                // Enable GPU optimizations
                setHasOverlappingRendering(false)
                setClipToBounds(false)
            }
            
            renderNodes[name] = renderNode
            renderNode
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create render node: $name", e)
            null
        }
    }
    
    /**
     * Batch UI updates to reduce layout passes
     */
    fun batchUIUpdates(updates: List<() -> Unit>) {
        if (updates.isEmpty()) return
        
        updateBatcher.batchUpdates(updates)
    }
    
    /**
     * Check if device is considered low-end
     */
    private fun isLowEndDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        
        return maxMemory < 512 || // Less than 512MB RAM
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
                cpuUsageMonitor.getAverageCPUUsage() > 80.0
    }
    
    /**
     * Setup Choreographer callback for frame time monitoring
     */
    private fun setupChoreographerCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    frameTimeTracker.recordFrame(frameTimeNanos)
                    
                    if (isOptimizationActive.get()) {
                        Choreographer.getInstance().postFrameCallback(this)
                    }
                }
            })
        }
    }
    
    /**
     * Start performance monitoring
     */
    private fun startPerformanceMonitoring() {
        backgroundScope.launch {
            while (isActive) {
                cpuUsageMonitor.updateCPUUsage()
                
                val avgFrameTime = frameTimeTracker.getAverageFrameTime()
                if (avgFrameTime > FRAME_TIME_THRESHOLD) {
                    Log.w(TAG, "Performance degradation detected. Avg frame time: ${avgFrameTime}ms")
                    optimizeForPerformance()
                }
                
                delay(CPU_USAGE_CHECK_INTERVAL)
            }
        }
    }
    
    /**
     * Apply emergency performance optimizations
     */
    private fun optimizeForPerformance() {
        uiScope.launch {
            // Disable non-essential GPU acceleration temporarily
            gpuAcceleratedViews.forEach { view ->
                if (view.layerType == View.LAYER_TYPE_HARDWARE) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            }
            
            // Clear render node cache
            renderNodes.clear()
            
            // Force garbage collection
            System.gc()
            
            Log.d(TAG, "Emergency performance optimizations applied")
        }
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): PerformanceStats {
        return PerformanceStats(
            averageFrameTime = frameTimeTracker.getAverageFrameTime(),
            droppedFramePercentage = frameTimeTracker.getDroppedFramePercentage(),
            averageCPUUsage = cpuUsageMonitor.getAverageCPUUsage(),
            activeBatches = batchedOperations.size,
            activeDebouncedOps = debouncedOperations.size,
            gpuAcceleratedViews = gpuAcceleratedViews.size
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        isOptimizationActive.set(false)
        
        // Cancel all operations
        debouncedOperations.values.forEach { it.cancel() }
        debouncedOperations.clear()
        
        batchedOperations.clear()
        
        // Clear GPU acceleration
        gpuAcceleratedViews.forEach { view ->
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        gpuAcceleratedViews.clear()
        
        renderNodes.clear()
        
        backgroundScope.cancel()
        uiScope.cancel()
    }
    
    /**
     * Batched operation container
     */
    private class BatchedOperation(
        val id: String,
        val priority: BatchPriority
    ) {
        private val operations = mutableListOf<suspend () -> Unit>()
        
        val size: Int get() = operations.size
        
        fun addOperation(operation: suspend () -> Unit) {
            operations.add(operation)
        }
        
        suspend fun execute() {
            operations.forEach { operation ->
                try {
                    operation()
                } catch (e: Exception) {
                    Log.w(TAG, "Batched operation failed", e)
                }
            }
        }
    }
    
    /**
     * Frame time tracking for performance monitoring
     */
    private class FrameTimeTracker {
        private val frameTimes = mutableListOf<Long>()
        private val maxSamples = 120 // 2 seconds at 60fps
        private var lastFrameTime = 0L
        
        fun recordFrame(frameTimeNanos: Long) {
            if (lastFrameTime != 0L) {
                val frameTime = (frameTimeNanos - lastFrameTime) / 1_000_000 // Convert to ms
                
                synchronized(frameTimes) {
                    frameTimes.add(frameTime)
                    if (frameTimes.size > maxSamples) {
                        frameTimes.removeAt(0)
                    }
                }
            }
            lastFrameTime = frameTimeNanos
        }
        
        fun getAverageFrameTime(): Float {
            synchronized(frameTimes) {
                return if (frameTimes.isNotEmpty()) {
                    frameTimes.average().toFloat()
                } else 0f
            }
        }
        
        fun isFrameDropped(): Boolean {
            synchronized(frameTimes) {
                return frameTimes.lastOrNull()?.let { it > FRAME_TIME_THRESHOLD } ?: false
            }
        }
        
        fun getDroppedFramePercentage(): Float {
            synchronized(frameTimes) {
                if (frameTimes.isEmpty()) return 0f
                
                val droppedFrames = frameTimes.count { it > FRAME_TIME_THRESHOLD }
                return (droppedFrames.toFloat() / frameTimes.size) * 100f
            }
        }
    }
    
    /**
     * CPU usage monitoring
     */
    private class CPUUsageMonitor {
        private val cpuUsageHistory = mutableListOf<Double>()
        private val maxSamples = 30
        
        fun updateCPUUsage() {
            // Simple CPU usage estimation based on available processors and load
            val runtime = Runtime.getRuntime()
            val processors = runtime.availableProcessors()
            val freeMemory = runtime.freeMemory()
            val totalMemory = runtime.totalMemory()
            
            // Rough estimation based on memory pressure
            val memoryUsage = 1.0 - (freeMemory.toDouble() / totalMemory.toDouble())
            val estimatedCPU = memoryUsage * 100.0
            
            synchronized(cpuUsageHistory) {
                cpuUsageHistory.add(estimatedCPU)
                if (cpuUsageHistory.size > maxSamples) {
                    cpuUsageHistory.removeAt(0)
                }
            }
        }
        
        fun getAverageCPUUsage(): Double {
            synchronized(cpuUsageHistory) {
                return if (cpuUsageHistory.isNotEmpty()) {
                    cpuUsageHistory.average()
                } else 0.0
            }
        }
    }
    
    /**
     * UI update batcher
     */
    private class UpdateBatcher {
        private val pendingUpdates = mutableListOf<() -> Unit>()
        private var batchScheduled = false
        
        fun batchUpdates(updates: List<() -> Unit>) {
            synchronized(pendingUpdates) {
                pendingUpdates.addAll(updates)
                
                if (!batchScheduled) {
                    batchScheduled = true
                    Handler(Looper.getMainLooper()).post {
                        executeBatch()
                    }
                }
            }
        }
        
        private fun executeBatch() {
            val updates = synchronized(pendingUpdates) {
                val copy = pendingUpdates.toList()
                pendingUpdates.clear()
                batchScheduled = false
                copy
            }
            
            updates.forEach { update ->
                try {
                    update()
                } catch (e: Exception) {
                    Log.w(TAG, "Batched UI update failed", e)
                }
            }
        }
    }
    
    enum class BatchPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }
    
    data class PerformanceStats(
        val averageFrameTime: Float,
        val droppedFramePercentage: Float,
        val averageCPUUsage: Double,
        val activeBatches: Int,
        val activeDebouncedOps: Int,
        val gpuAcceleratedViews: Int
    )
}