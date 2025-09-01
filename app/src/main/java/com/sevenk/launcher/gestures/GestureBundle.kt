package com.sevenk.launcher.gestures

import android.content.Context
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import com.sevenk.launcher.haptics.HapticFeedbackManager
import com.sevenk.launcher.search.GlobalSearchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Comprehensive gesture bundle for launcher interactions
 */
class GestureBundle(
    private val context: Context,
    private val onShowAppDrawer: () -> Unit,
    private val onShowDockMenu: () -> Unit,
    private val onPageSwipe: (direction: Int) -> Unit
) {
    
    private val hapticManager = HapticFeedbackManager(context)
    private val gestureScope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        const val SWIPE_LEFT = -1
        const val SWIPE_RIGHT = 1
        const val MIN_SWIPE_DISTANCE = 100
        const val MIN_SWIPE_VELOCITY = 100
    }
    
    /**
     * Global search gesture detector (swipe down)
     */
    fun createGlobalSearchGestureDetector(): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val deltaY = e2.y - e1.y
                val deltaX = kotlin.math.abs(e2.x - e1.x)
                
                // Swipe down gesture for global search
                if (deltaY > MIN_SWIPE_DISTANCE && 
                    kotlin.math.abs(velocityY) > MIN_SWIPE_VELOCITY &&
                    deltaX < MIN_SWIPE_DISTANCE) {
                    
                    hapticManager.performHaptic(HapticFeedbackManager.HapticType.SWIPE)
                    showGlobalSearch()
                    return true
                }
                
                return false
            }
        })
    }
    
    /**
     * Dock long-press gesture detector
     */
    fun createDockGestureDetector(): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.LONG_PRESS)
                onShowDockMenu()
            }
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
                return true
            }
        })
    }
    
    /**
     * Horizontal page swipe gesture detector
     */
    fun createPageSwipeGestureDetector(): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val deltaX = e2.x - e1.x
                val deltaY = kotlin.math.abs(e2.y - e1.y)
                
                // Horizontal swipe for page navigation
                if (kotlin.math.abs(deltaX) > MIN_SWIPE_DISTANCE && 
                    kotlin.math.abs(velocityX) > MIN_SWIPE_VELOCITY &&
                    deltaY < MIN_SWIPE_DISTANCE) {
                    
                    val direction = if (deltaX > 0) SWIPE_RIGHT else SWIPE_LEFT
                    hapticManager.performHaptic(HapticFeedbackManager.HapticType.SWIPE)
                    onPageSwipe(direction)
                    return true
                }
                
                return false
            }
        })
    }
    
    /**
     * App drawer swipe up gesture detector
     */
    fun createAppDrawerGestureDetector(): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val deltaY = e1.y - e2.y
                val deltaX = kotlin.math.abs(e2.x - e1.x)
                
                // Swipe up gesture for app drawer
                if (deltaY > MIN_SWIPE_DISTANCE && 
                    kotlin.math.abs(velocityY) > MIN_SWIPE_VELOCITY &&
                    deltaX < MIN_SWIPE_DISTANCE) {
                    
                    hapticManager.performHaptic(HapticFeedbackManager.HapticType.SWIPE)
                    onShowAppDrawer()
                    return true
                }
                
                return false
            }
        })
    }
    
    /**
     * Double tap gesture detector
     */
    fun createDoubleTapGestureDetector(onDoubleTap: () -> Unit): GestureDetectorCompat {
        return GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.MEDIUM_TAP)
                onDoubleTap()
                return true
            }
        })
    }
    
    /**
     * Show global search
     */
    private fun showGlobalSearch() {
        gestureScope.launch {
            try {
                val intent = Intent(context, GlobalSearchActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to system search
                val intent = Intent(Intent.ACTION_SEARCH_LONG_PRESS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    // No search available
                }
            }
        }
    }
    
    /**
     * Apply gesture to view
     */
    fun applyGestureToView(view: View, gestureType: GestureType) {
        val detector = when (gestureType) {
            GestureType.GLOBAL_SEARCH -> createGlobalSearchGestureDetector()
            GestureType.DOCK_MENU -> createDockGestureDetector()
            GestureType.PAGE_SWIPE -> createPageSwipeGestureDetector()
            GestureType.APP_DRAWER -> createAppDrawerGestureDetector()
            GestureType.DOUBLE_TAP -> createDoubleTapGestureDetector { /* Custom action */ }
        }
        
        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
        }
    }
    
    enum class GestureType {
        GLOBAL_SEARCH,
        DOCK_MENU,
        PAGE_SWIPE,
        APP_DRAWER,
        DOUBLE_TAP
    }
}
