package com.sevenk.launcher.gesture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import com.sevenk.launcher.util.Perf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Comprehensive gesture management system for 7K Launcher.
 * Manages gesture detection, custom actions, and provides
 * an extensible framework for gesture-based interactions.
 */
class GestureManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("gesture_prefs", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    // Gesture sensitivity settings (customizable by user)
    private var swipeSensitivity = 1.0f
    private var doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()
    private var longPressTimeout = ViewConfiguration.getLongPressTimeout()

    // Active gesture callbacks
    private val gestureCallbacks = mutableMapOf<GestureType, MutableList<GestureCallback>>()

    // Velocity tracker for advanced gesture detection
    private var velocityTracker: VelocityTracker? = null

    // Multi-touch tracking
    private val pointerIds = mutableSetOf<Int>()
    private var multiTouchStartDistance = 0f

    /**
     * Enum defining supported gesture types
     */
    enum class GestureType {
        SWIPE_UP,
        SWIPE_DOWN,
        SWIPE_LEFT,
        SWIPE_RIGHT,
        DOUBLE_TAP,
        LONG_PRESS,
        PINCH_IN,
        PINCH_OUT,
        TWO_FINGER_SWIPE_UP,
        TWO_FINGER_SWIPE_DOWN,
        EDGE_SWIPE_LEFT,
        EDGE_SWIPE_RIGHT
    }
    
    /**
     * Enum defining available actions for gestures
     */
    enum class GestureAction {
        NONE,
        OPEN_APP_DRAWER,
        CLOSE_APP_DRAWER,
        OPEN_SETTINGS,
        OPEN_LAUNCHER_SETTINGS,
        OPEN_SEARCH,
        TOGGLE_SIDEBAR,
        LAUNCH_APP,
        SHOW_NOTIFICATIONS,
        SHOW_QUICK_SETTINGS,
        LOCK_SCREEN,
        TAKE_SCREENSHOT,
        OPEN_RECENT_APPS,
        TOGGLE_WIFI,
        TOGGLE_BLUETOOTH,
        TOGGLE_FLASHLIGHT,
        TOGGLE_DARK_MODE,
        OPEN_CAMERA,
        OPEN_CALENDAR,
        OPEN_CALCULATOR,
        OPEN_TODO_LIST,
        OPEN_VAULT,
        REFRESH_WIDGETS
    }
    
    /**
     * Data class to store gesture configuration
     */
    data class GestureConfig(
        val type: GestureType,
        val action: GestureAction,
        val targetPackage: String? = null,
        val enabled: Boolean = true,
        val sensitivity: Float = 1.0f,
        val edgeSize: Int = 0 // For edge gestures, size of edge sensitive area in dp
    )

    /**
     * Interface for gesture callbacks
     */
    interface GestureCallback {
        fun onGestureDetected(gestureType: GestureType, event: MotionEvent?): Boolean
    }

    private var gestureConfigs = mutableMapOf<GestureType, GestureConfig>()
    
    init {
        loadGestureConfigs()
        initSensitivitySettings()
    }

    /**
     * Initialize gesture sensitivity settings from preferences
     */
    private fun initSensitivitySettings() {
        swipeSensitivity = prefs.getFloat("swipe_sensitivity", 1.0f)
        doubleTapTimeout = prefs.getInt("double_tap_timeout", ViewConfiguration.getDoubleTapTimeout())
        longPressTimeout = prefs.getInt("long_press_timeout", ViewConfiguration.getLongPressTimeout())
    }
    
    /**
     * Load gesture configurations from preferences
     */
    private fun loadGestureConfigs() {
        // Default configurations
        val defaults = mapOf(
            GestureType.SWIPE_UP to GestureAction.OPEN_APP_DRAWER,
            GestureType.SWIPE_DOWN to GestureAction.SHOW_NOTIFICATIONS,
            GestureType.DOUBLE_TAP to GestureAction.LOCK_SCREEN,
            GestureType.LONG_PRESS to GestureAction.OPEN_LAUNCHER_SETTINGS,
            GestureType.EDGE_SWIPE_RIGHT to GestureAction.TOGGLE_SIDEBAR
        )
        
        // Clear existing configs
        gestureConfigs.clear()

        // Load all gesture configs from preferences
        for (gestureType in GestureType.values()) {
            val defaultAction = defaults[gestureType] ?: GestureAction.NONE

            // Get the saved action for this gesture type
            val actionName = prefs.getString("gesture_${gestureType.name}", defaultAction.name)
            val action = try {
                GestureAction.valueOf(actionName ?: defaultAction.name)
            } catch (e: IllegalArgumentException) {
                defaultAction
            }
            
            // Get additional settings
            val enabled = prefs.getBoolean("gesture_${gestureType.name}_enabled", true)
            val sensitivity = prefs.getFloat("gesture_${gestureType.name}_sensitivity", 1.0f)
            val edgeSize = prefs.getInt("gesture_${gestureType.name}_edge_size", 40) // Default 40dp
            val targetPackage = prefs.getString("gesture_${gestureType.name}_target_package", null)

            // Create and store the config
            val config = GestureConfig(
                type = gestureType,
                action = action,
                targetPackage = targetPackage,
                enabled = enabled,
                sensitivity = sensitivity,
                edgeSize = edgeSize
            )

            gestureConfigs[gestureType] = config
        }
    }
    
    /**
     * Save a gesture configuration to preferences
     */
    fun saveGestureConfig(config: GestureConfig) {
        // Update in-memory map
        gestureConfigs[config.type] = config
        
        // Save to preferences
        prefs.edit()
            .putString("gesture_${config.type.name}", config.action.name)
            .putBoolean("gesture_${config.type.name}_enabled", config.enabled)
            .putFloat("gesture_${config.type.name}_sensitivity", config.sensitivity)
            .putInt("gesture_${config.type.name}_edge_size", config.edgeSize)
            .apply()

        if (config.targetPackage != null) {
            prefs.edit()
                .putString("gesture_${config.type.name}_target_package", config.targetPackage)
                .apply()
        } else {
            prefs.edit().remove("gesture_${config.type.name}_target_package").apply()
        }
    }
    
    /**
     * Get the current configuration for a gesture type
     */
    fun getGestureConfig(type: GestureType): GestureConfig {
        return gestureConfigs[type] ?: GestureConfig(type, GestureAction.NONE)
    }
    
    /**
     * Get all gesture configurations
     */
    fun getAllGestureConfigs(): List<GestureConfig> {
        return gestureConfigs.values.toList()
    }
    
    /**
     * Set global swipe sensitivity
     */
    fun setSwipeSensitivity(sensitivity: Float) {
        swipeSensitivity = sensitivity.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat("swipe_sensitivity", swipeSensitivity).apply()
    }

    /**
     * Register a callback for a specific gesture type
     */
    fun registerCallback(type: GestureType, callback: GestureCallback) {
        val callbacks = gestureCallbacks.getOrPut(type) { mutableListOf() }
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    /**
     * Unregister a callback for a specific gesture type
     */
    fun unregisterCallback(type: GestureType, callback: GestureCallback) {
        gestureCallbacks[type]?.remove(callback)
    }

    /**
     * Creates a GestureDetector with the appropriate listeners
     */
    fun createGestureDetector(): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false

                return handleFling(e1, e2, velocityX, velocityY)
            }

            override fun onLongPress(e: MotionEvent) {
                val config = gestureConfigs[GestureType.LONG_PRESS]
                if (config?.enabled == true) {
                    notifyGestureDetected(GestureType.LONG_PRESS, e)
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val config = gestureConfigs[GestureType.DOUBLE_TAP]
                if (config?.enabled == true) {
                    return notifyGestureDetected(GestureType.DOUBLE_TAP, e)
                }
                return false
            }
        })
    }

    /**
     * Process a touch event for multi-touch gestures
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        // Initialize velocity tracker if needed
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Start tracking a new gesture
                pointerIds.clear()
                pointerIds.add(event.getPointerId(0))
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Add this pointer to tracking
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                pointerIds.add(pointerId)

                // If we now have 2 pointers, record the starting distance for pinch gestures
                if (pointerIds.size == 2) {
                    multiTouchStartDistance = getMultiTouchDistance(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Handle multi-touch gestures
                if (pointerIds.size >= 2) {
                    // Check for pinch gestures if we have 2+ pointers
                    handlePinchGesture(event)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Remove this pointer from tracking
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                pointerIds.remove(pointerId)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Finalize any velocity-based detection
                velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())

                // Clean up
                pointerIds.clear()
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }

        return false
    }

    /**
     * Handle fling gestures based on velocity and direction
     */
    private fun handleFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        // Calculate distance and direction
        val distanceX = e2.x - e1.x
        val distanceY = e2.y - e1.y
        val screenWidth = context.resources.displayMetrics.widthPixels

        // Apply sensitivity adjustment
        val adjustedVelocityX = velocityX * swipeSensitivity
        val adjustedVelocityY = velocityY * swipeSensitivity

        // Check if it's an edge swipe
        val isLeftEdge = e1.x < screenWidth * 0.1
        val isRightEdge = e1.x > screenWidth * 0.9

        // Determine swipe direction based on the dominant axis
        if (abs(distanceY) > abs(distanceX)) {
            // Vertical swipe
            if (abs(adjustedVelocityY) > minimumFlingVelocity) {
                if (distanceY < 0) {
                    // Swipe up
                    val gestureType = if (pointerIds.size >= 2) {
                        GestureType.TWO_FINGER_SWIPE_UP
                    } else {
                        GestureType.SWIPE_UP
                    }

                    return notifyGestureDetected(gestureType, e2)
                } else {
                    // Swipe down
                    val gestureType = if (pointerIds.size >= 2) {
                        GestureType.TWO_FINGER_SWIPE_DOWN
                    } else {
                        GestureType.SWIPE_DOWN
                    }

                    return notifyGestureDetected(gestureType, e2)
                }
            }
        } else {
            // Horizontal swipe
            if (abs(adjustedVelocityX) > minimumFlingVelocity) {
                if (distanceX < 0) {
                    // Swipe left
                    val gestureType = if (isRightEdge) {
                        GestureType.EDGE_SWIPE_LEFT
                    } else {
                        GestureType.SWIPE_LEFT
                    }

                    return notifyGestureDetected(gestureType, e2)
                } else {
                    // Swipe right
                    val gestureType = if (isLeftEdge) {
                        GestureType.EDGE_SWIPE_RIGHT
                    } else {
                        GestureType.SWIPE_RIGHT
                    }

                    return notifyGestureDetected(gestureType, e2)
                }
            }
        }

        return false
    }

    /**
     * Handle pinch gestures
     */
    private fun handlePinchGesture(event: MotionEvent) {
        // Only process if we have exactly 2 pointers
        if (pointerIds.size != 2) return

        // Get current distance between pointers
        val currentDistance = getMultiTouchDistance(event)

        // Calculate the change in distance
        val distanceChange = currentDistance - multiTouchStartDistance

        // If the change is significant enough, trigger pinch gesture
        if (abs(distanceChange) > touchSlop * 3) {
            val gestureType = if (distanceChange < 0) {
                GestureType.PINCH_IN
            } else {
                GestureType.PINCH_OUT
            }

            val config = gestureConfigs[gestureType]
            if (config?.enabled == true) {
                notifyGestureDetected(gestureType, event)

                // Reset starting distance to avoid repeat triggers
                multiTouchStartDistance = currentDistance
            }
        }
    }

    /**
     * Calculate distance between two touch points
     */
    private fun getMultiTouchDistance(event: MotionEvent): Float {
        // Ensure we have at least 2 pointers
        if (event.pointerCount < 2) return 0f

        // Find indexes of our tracked pointers
        val pointerList = pointerIds.toList()
        if (pointerList.size < 2) return 0f

        val pointerIndex1 = event.findPointerIndex(pointerList[0])
        val pointerIndex2 = event.findPointerIndex(pointerList[1])

        // Calculate distance using Pythagorean theorem
        val x1 = event.getX(pointerIndex1)
        val y1 = event.getY(pointerIndex1)
        val x2 = event.getX(pointerIndex2)
        val y2 = event.getY(pointerIndex2)

        val dx = x2 - x1
        val dy = y2 - y1

        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Notify registered callbacks about a detected gesture
     */
    private fun notifyGestureDetected(type: GestureType, event: MotionEvent?): Boolean {
        val config = gestureConfigs[type]
        if (config?.enabled != true) return false

        // First check if any callback wants to handle this gesture
        var handled = false
        gestureCallbacks[type]?.forEach { callback ->
            if (callback.onGestureDetected(type, event)) {
                handled = true
            }
        }

        // If not handled by callbacks, perform the default action
        if (!handled) {
            performAction(config.action, config.targetPackage)
        }

        return true
    }

    /**
     * Execute an action for a specific gesture type
     */
    fun executeGestureAction(gestureType: GestureType, callback: GestureCallback): Boolean {
        val config = getGestureConfig(gestureType)

        // Skip if the gesture is disabled
        if (!config.enabled) return false

        // Let the callback handle it first
        if (callback.onGestureDetected(gestureType, null)) {
            return true
        }

        // Otherwise perform the configured action
        return performAction(config.action, config.targetPackage)
    }

    /**
     * Perform the action associated with a gesture
     */
    fun performAction(action: GestureAction, targetPackage: String? = null): Boolean {
        return Perf.trace("GestureAction_$action") {
            when (action) {
                GestureAction.NONE -> false

                GestureAction.LAUNCH_APP -> {
                    if (targetPackage != null) {
                        val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return@trace true
                        }
                    }
                    false
                }

                GestureAction.SHOW_NOTIFICATIONS -> {
                    try {
                        val statusBarService = context.getSystemService("statusbar")
                        val statusBarClass = Class.forName("android.app.StatusBarManager")
                        val method = statusBarClass.getMethod("expandNotificationsPanel")
                        method.invoke(statusBarService)
                        true
                    } catch (e: Exception) {
                        // Fallback for older devices
                        try {
                            val intent = Intent("android.settings.NOTIFICATION_SETTINGS")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            true
                        } catch (e2: Exception) {
                            false
                        }
                    }
                }

                GestureAction.SHOW_QUICK_SETTINGS -> {
                    try {
                        val statusBarService = context.getSystemService("statusbar")
                        val statusBarClass = Class.forName("android.app.StatusBarManager")
                        val method = statusBarClass.getMethod("expandSettingsPanel")
                        method.invoke(statusBarService)
                        true
                    } catch (e: Exception) {
                        // Fallback
                        try {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            true
                        } catch (e2: Exception) {
                            false
                        }
                    }
                }

                GestureAction.LOCK_SCREEN -> {
                    // This requires device admin permission
                    try {
                        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                        val lockMethod = devicePolicyManager::class.java.getMethod("lockNow")
                        lockMethod.invoke(devicePolicyManager)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                GestureAction.TAKE_SCREENSHOT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Modern screenshot approach requires special permissions
                        // This is just a placeholder and would require implementation using MediaProjection API
                        false
                    } else {
                        false
                    }
                }

                GestureAction.TOGGLE_WIFI -> {
                    // Requires permissions and uses different APIs in different Android versions
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10+ requires special panel
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(panelIntent)
                        } else {
                            // Older versions can toggle directly
                            val wifiManager = context.getSystemService(Context.WIFI_SERVICE)
                            val setWifiMethod = wifiManager::class.java.getMethod("setWifiEnabled", Boolean::class.java)
                            val isEnabled = wifiManager::class.java.getMethod("isWifiEnabled").invoke(wifiManager) as Boolean
                            setWifiMethod.invoke(wifiManager, !isEnabled)
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                GestureAction.TOGGLE_BLUETOOTH -> {
                    // Similar to WiFi, requires permissions
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val panelIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(panelIntent)
                        } else {
                            // Attempt to toggle via BluetoothAdapter
                            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                            if (bluetoothAdapter != null) {
                                if (bluetoothAdapter.isEnabled) {
                                    bluetoothAdapter.disable()
                                } else {
                                    bluetoothAdapter.enable()
                                }
                            }
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                GestureAction.TOGGLE_FLASHLIGHT -> {
                    // Requires camera permission
                    try {
                        // This would need to be implemented with CameraManager
                        // Placeholder for flashlight toggle
                        false
                    } catch (e: Exception) {
                        false
                    }
                }

                GestureAction.TOGGLE_DARK_MODE -> {
                    // This would require integration with the launcher theme system
                    false
                }

                // Other actions would be handled by callbacks from the launcher
                else -> false
            }
        }
    }
}

/**
 * Gesture detector for home screen
 */
class HomeGestureDetector(
    private val context: Context,
    private val gestureManager: GestureManager,
    private val callback: GestureManager.GestureCallback
) {
    
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()
    
    private var lastTapTime = 0L
    private var tapCount = 0
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                
                // Handle double tap detection
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < doubleTapTimeout) {
                    tapCount++
                    if (tapCount == 2) {
                        gestureManager.executeGestureAction(GestureManager.GestureType.DOUBLE_TAP, callback)
                        tapCount = 0
                        return true
                    }
                } else {
                    tapCount = 1
                }
                lastTapTime = currentTime
            }
            
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                
                // Check for long press
                val currentTime = System.currentTimeMillis()
                if (currentTime - downTime > longPressTimeout) {
                    val deltaX = abs(event.x - downX)
                    val deltaY = abs(event.y - downY)
                    if (deltaX < touchSlop && deltaY < touchSlop) {
                        gestureManager.executeGestureAction(GestureManager.GestureType.LONG_PRESS, callback)
                        return true
                    }
                }
            }
            
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                
                if (distance > touchSlop) {
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    
                    // Determine swipe direction
                    val gestureType = when {
                        abs(deltaY) > abs(deltaX) -> {
                            if (deltaY < 0) GestureManager.GestureType.SWIPE_UP
                            else GestureManager.GestureType.SWIPE_DOWN
                        }
                        else -> {
                            if (deltaX < 0) GestureManager.GestureType.SWIPE_LEFT
                            else GestureManager.GestureType.SWIPE_RIGHT
                        }
                    }
                    
                    gestureManager.executeGestureAction(gestureType, callback)
                }
                
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        
        return false
    }
}
