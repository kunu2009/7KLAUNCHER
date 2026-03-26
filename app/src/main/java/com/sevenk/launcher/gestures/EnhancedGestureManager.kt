package com.sevenk.launcher.gestures

import android.content.Context
import android.content.Intent
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.util.Log
import androidx.core.view.GestureDetectorCompat
import com.sevenk.launcher.search.GlobalSearchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enhanced gesture system with customizable actions and haptic feedback
 */
class EnhancedGestureManager(
    private val context: Context,
    private val onShowAppDrawer: () -> Unit,
    private val onShowDockMenu: () -> Unit,
    private val onPageSwipe: (direction: Int) -> Unit,
    private val onShowQuickSettings: () -> Unit = {},
    private val onTriggerScreenLock: () -> Unit = {}
) {
    
    private val gestureScope = CoroutineScope(Dispatchers.Main)
    private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
    
    // Gesture configuration
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val velocityThreshold = 1000f
    private val minimumSwipeDistance = 100f
    
    // Simple haptic feedback helper
    private fun performHapticFeedback(type: Int = HapticFeedbackConstants.VIRTUAL_KEY) {
        try {
            // Since we need a view to perform haptic feedback, we'll skip this in this context
            Log.d("EnhancedGestureManager", "Haptic feedback requested: $type")
        } catch (e: Exception) {
            Log.e("EnhancedGestureManager", "Haptic feedback failed", e)
        }
    }
    
    companion object {
        const val SWIPE_LEFT = -1
        const val SWIPE_RIGHT = 1
        const val SWIPE_UP = -2
        const val SWIPE_DOWN = 2
    }
    
    /**
     * Create enhanced gesture detector with full gesture support
     */
    fun createEnhancedGestureDetector(): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Handle single tap actions based on preferences
                val singleTapAction = prefs.getString("gesture_single_tap", "none")
                handleGestureAction(singleTapAction)
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val doubleTapAction = prefs.getString("gesture_double_tap", "lock_screen")
                handleGestureAction(doubleTapAction)
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val longPressAction = prefs.getString("gesture_long_press", "none")
                // Prevent accidental dock menu popups: only allow dock menu long-press
                // when gesture starts near the bottom area where dock interactions are expected.
                if (longPressAction == "dock_menu") {
                    val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
                    val dockZoneStartY = screenHeight * 0.72f
                    if (e.y < dockZoneStartY) return
                }
                handleGestureAction(longPressAction)
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
                val absVelocityX = kotlin.math.abs(velocityX)
                val absVelocityY = kotlin.math.abs(velocityY)
                
                // Determine gesture direction and trigger appropriate action
                when {
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && 
                    kotlin.math.abs(deltaX) > minimumSwipeDistance &&
                    absVelocityX > velocityThreshold -> {
                        // Horizontal swipe
                        if (deltaX > 0) {
                            handleSwipeRight()
                        } else {
                            handleSwipeLeft()
                        }
                    }
                    kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) && 
                    kotlin.math.abs(deltaY) > minimumSwipeDistance &&
                    absVelocityY > velocityThreshold -> {
                        // Vertical swipe
                        if (deltaY > 0) {
                            handleSwipeDown()
                        } else {
                            handleSwipeUp()
                        }
                    }
                }
                
                return true
            }
        })
    }
    
    private fun handleSwipeUp() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val swipeUpAction = prefs.getString("gesture_swipe_up", "app_drawer")
        handleGestureAction(swipeUpAction)
    }
    
    private fun handleSwipeDown() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val swipeDownAction = prefs.getString("gesture_swipe_down", "notifications")
        handleGestureAction(swipeDownAction)
    }
    
    private fun handleSwipeLeft() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onPageSwipe(SWIPE_LEFT)
    }
    
    private fun handleSwipeRight() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onPageSwipe(SWIPE_RIGHT)
    }
    
    /**
     * Handle gesture actions based on user preferences
     */
    private fun handleGestureAction(action: String?) {
        gestureScope.launch {
            when (action) {
                "app_drawer" -> onShowAppDrawer()
                "dock_menu" -> onShowDockMenu()
                "global_search" -> showGlobalSearch()
                "quick_settings" -> onShowQuickSettings()
                "notifications" -> showNotifications()
                "lock_screen" -> onTriggerScreenLock()
                "voice_search" -> showVoiceSearch()
                "recent_apps" -> showRecentApps()
                else -> { /* Do nothing for "none" or unknown actions */ }
            }
        }
    }
    
    private fun showGlobalSearch() {
        try {
            val intent = Intent(context, GlobalSearchActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to system search
            try {
                val intent = Intent(Intent.ACTION_SEARCH_LONG_PRESS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (ex: Exception) {
                // No search available
            }
        }
    }
    
    private fun showNotifications() {
        try {
            val statusBarService = context.getSystemService(Context.STATUS_BAR_SERVICE)
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandNotificationsPanel = statusBarManager.getMethod("expandNotificationsPanel")
            expandNotificationsPanel.invoke(statusBarService)
        } catch (e: Exception) {
            // Fallback: try alternative method
            try {
                val intent = Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS")
                context.sendBroadcast(intent)
            } catch (ex: Exception) {
                // No fallback available
            }
        }
    }
    
    private fun showVoiceSearch() {
        try {
            val intent = Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("android.speech.action.WEB_SEARCH")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (ex: Exception) {
                // No voice search available
            }
        }
    }
    
    private fun showRecentApps() {
        try {
            val intent = Intent("android.intent.action.SHOW_RECENT_APPS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            // Recent apps not accessible
        }
    }
    
    /**
     * Get available gesture actions for settings
     */
    fun getAvailableGestureActions(): List<Pair<String, String>> {
        return listOf(
            "none" to "None",
            "app_drawer" to "Open App Drawer",
            "dock_menu" to "Show Dock Menu",
            "global_search" to "Global Search",
            "quick_settings" to "Quick Settings",
            "notifications" to "Show Notifications",
            "lock_screen" to "Lock Screen",
            "voice_search" to "Voice Search",
            "recent_apps" to "Recent Apps"
        )
    }
}