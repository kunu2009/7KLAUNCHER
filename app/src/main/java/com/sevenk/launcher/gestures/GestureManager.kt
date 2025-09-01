package com.sevenk.launcher.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import com.sevenk.launcher.util.Perf
import kotlin.math.abs

/**
 * Comprehensive gesture management system for 7K Launcher.
 * Handles detection, mapping, and execution of gesture actions.
 */
class GestureManager(private val context: Context) {
    // Map of gesture types to their corresponding actions
    private val gestureActions = mutableMapOf<GestureType, GestureAction>()

    // Configuration options
    private var swipeSensitivity: Float = 1.0f
    private var touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityThreshold: Float = 1000f // in pixels per second

    // Gesture detection helpers
    private var velocityTracker: VelocityTracker? = null
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var isTracking: Boolean = false

    /**
     * Creates a gesture detector for a view.
     */
    fun createGestureDetector(listener: OnGestureListener): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener.onSingleTap(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                performGestureAction(GestureType.DOUBLE_TAP)
                listener.onDoubleTap(e.x, e.y)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                performGestureAction(GestureType.LONG_PRESS)
                listener.onLongPress(e.x, e.y)
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                // Determine the fling direction based on velocity and distance
                val gestureType = when {
                    abs(deltaX) > abs(deltaY) && deltaX > 0 && abs(velocityX) > velocityThreshold -> {
                        GestureType.SWIPE_RIGHT
                    }
                    abs(deltaX) > abs(deltaY) && deltaX < 0 && abs(velocityX) > velocityThreshold -> {
                        GestureType.SWIPE_LEFT
                    }
                    abs(deltaY) > abs(deltaX) && deltaY > 0 && abs(velocityY) > velocityThreshold -> {
                        GestureType.SWIPE_DOWN
                    }
                    abs(deltaY) > abs(deltaX) && deltaY < 0 && abs(velocityY) > velocityThreshold -> {
                        GestureType.SWIPE_UP
                    }
                    else -> return false
                }

                performGestureAction(gestureType)
                listener.onFling(gestureType, velocityX, velocityY)
                return true
            }
        })
    }

    /**
     * Maps a gesture type to an action.
     */
    fun mapGesture(gestureType: GestureType, action: GestureAction) {
        gestureActions[gestureType] = action
    }

    /**
     * Clears the gesture mapping for a specific gesture type.
     */
    fun clearGestureMapping(gestureType: GestureType) {
        gestureActions.remove(gestureType)
    }

    /**
     * Gets the current action for a gesture type.
     */
    fun getActionForGesture(gestureType: GestureType): GestureAction? {
        return gestureActions[gestureType]
    }

    /**
     * Sets the swipe sensitivity.
     * Higher values make gestures easier to trigger.
     */
    fun setSwipeSensitivity(sensitivity: Float) {
        swipeSensitivity = sensitivity.coerceIn(0.5f, 2.0f)
        velocityThreshold = 1000f / swipeSensitivity
    }

    /**
     * Performs the action associated with a gesture type.
     */
    fun performGestureAction(gestureType: GestureType): Boolean {
        return Perf.trace("GesturePerform_$gestureType") {
            val action = gestureActions[gestureType] ?: return@trace false
            action.perform(context)
            true
        }
    }

    /**
     * Handles a touch event for manual gesture detection.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.clear()
                velocityTracker = velocityTracker ?: VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                initialX = event.x
                initialY = event.y
                isTracking = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTracking) return false
                velocityTracker?.addMovement(event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isTracking) return false

                velocityTracker?.apply {
                    addMovement(event)
                    computeCurrentVelocity(1000) // in pixels per second

                    val velocityX = xVelocity
                    val velocityY = yVelocity

                    val deltaX = event.x - initialX
                    val deltaY = event.y - initialY

                    // Determine the gesture type based on velocity and distance
                    val gestureType = when {
                        abs(deltaX) > touchSlop * 2 && abs(deltaX) > abs(deltaY) && deltaX > 0 -> {
                            GestureType.SWIPE_RIGHT
                        }
                        abs(deltaX) > touchSlop * 2 && abs(deltaX) > abs(deltaY) && deltaX < 0 -> {
                            GestureType.SWIPE_LEFT
                        }
                        abs(deltaY) > touchSlop * 2 && abs(deltaY) > abs(deltaX) && deltaY > 0 -> {
                            GestureType.SWIPE_DOWN
                        }
                        abs(deltaY) > touchSlop * 2 && abs(deltaY) > abs(deltaX) && deltaY < 0 -> {
                            GestureType.SWIPE_UP
                        }
                        else -> null
                    }

                    if (gestureType != null) {
                        performGestureAction(gestureType)
                    }
                }

                velocityTracker?.recycle()
                velocityTracker = null
                isTracking = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }

        return false
    }

    /**
     * Cleans up resources when no longer needed.
     */
    fun release() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    /**
     * Interface for receiving gesture events.
     */
    interface OnGestureListener {
        fun onSingleTap(x: Float, y: Float) {}
        fun onDoubleTap(x: Float, y: Float) {}
        fun onLongPress(x: Float, y: Float) {}
        fun onFling(gestureType: GestureType, velocityX: Float, velocityY: Float) {}
    }
}

/**
 * Enum representing different types of gestures.
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
    TWO_FINGER_SWIPE_DOWN
}

/**
 * Interface for gesture actions.
 */
interface GestureAction {
    fun perform(context: Context): Boolean
}

/**
 * Implementation of GestureAction that opens an application.
 */
class OpenAppAction(private val packageName: String) : GestureAction {
    override fun perform(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return true
        }
        return false
    }
}

/**
 * Implementation of GestureAction that opens the app drawer.
 */
class OpenAppDrawerAction : GestureAction {
    override fun perform(context: Context): Boolean {
        // This will be implemented in LauncherActivity
        return true
    }
}

/**
 * Implementation of GestureAction that opens the notification panel.
 */
class OpenNotificationPanelAction : GestureAction {
    override fun perform(context: Context): Boolean {
        // Expand status bar using reflection (this is a common approach)
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
