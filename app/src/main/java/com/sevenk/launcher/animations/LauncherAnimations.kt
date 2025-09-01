package com.sevenk.launcher.animations

import android.animation.*
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.sevenk.launcher.battery.BatteryOptimizer

/**
 * Comprehensive animation system for launcher interactions
 */
class LauncherAnimations(private val context: Context) {
    
    private val batteryOptimizer = BatteryOptimizer(context)
    
    companion object {
        private const val DEFAULT_DURATION = 300L
        private const val FAST_DURATION = 150L
        private const val SLOW_DURATION = 500L
    }
    
    /**
     * Dock fade and slide animation
     */
    fun animateDockShow(dockView: View, fromBottom: Boolean = true) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) return
        
        val translationY = if (fromBottom) dockView.height.toFloat() else -dockView.height.toFloat()
        
        dockView.alpha = 0f
        dockView.translationY = translationY
        dockView.visibility = View.VISIBLE
        
        val animatorSet = AnimatorSet()
        val fadeIn = ObjectAnimator.ofFloat(dockView, "alpha", 0f, 1f)
        val slideIn = ObjectAnimator.ofFloat(dockView, "translationY", translationY, 0f)
        
        animatorSet.playTogether(fadeIn, slideIn)
        animatorSet.duration = duration
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }
    
    fun animateDockHide(dockView: View, toBottom: Boolean = true, onComplete: (() -> Unit)? = null) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) {
            dockView.visibility = View.GONE
            onComplete?.invoke()
            return
        }
        
        val translationY = if (toBottom) dockView.height.toFloat() else -dockView.height.toFloat()
        
        val animatorSet = AnimatorSet()
        val fadeOut = ObjectAnimator.ofFloat(dockView, "alpha", 1f, 0f)
        val slideOut = ObjectAnimator.ofFloat(dockView, "translationY", 0f, translationY)
        
        animatorSet.playTogether(fadeOut, slideOut)
        animatorSet.duration = duration
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.doOnEnd {
            dockView.visibility = View.GONE
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Sidebar slide animation
     */
    fun animateSidebarShow(sidebarView: View, fromRight: Boolean = true) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) return
        
        val translationX = if (fromRight) sidebarView.width.toFloat() else -sidebarView.width.toFloat()
        
        sidebarView.alpha = 0f
        sidebarView.translationX = translationX
        sidebarView.visibility = View.VISIBLE
        
        val animatorSet = AnimatorSet()
        val fadeIn = ObjectAnimator.ofFloat(sidebarView, "alpha", 0f, 1f)
        val slideIn = ObjectAnimator.ofFloat(sidebarView, "translationX", translationX, 0f)
        
        animatorSet.playTogether(fadeIn, slideIn)
        animatorSet.duration = duration
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }
    
    fun animateSidebarHide(sidebarView: View, toRight: Boolean = true, onComplete: (() -> Unit)? = null) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) {
            sidebarView.visibility = View.GONE
            onComplete?.invoke()
            return
        }
        
        val translationX = if (toRight) sidebarView.width.toFloat() else -sidebarView.width.toFloat()
        
        val animatorSet = AnimatorSet()
        val fadeOut = ObjectAnimator.ofFloat(sidebarView, "alpha", 1f, 0f)
        val slideOut = ObjectAnimator.ofFloat(sidebarView, "translationX", 0f, translationX)
        
        animatorSet.playTogether(fadeOut, slideOut)
        animatorSet.duration = duration
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.doOnEnd {
            sidebarView.visibility = View.GONE
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * App launch scale and blur animation
     */
    fun animateAppLaunch(appView: View, onComplete: (() -> Unit)? = null) {
        val duration = batteryOptimizer.getAnimationDuration(FAST_DURATION)
        if (duration == 0L) {
            onComplete?.invoke()
            return
        }
        
        val scaleX = ObjectAnimator.ofFloat(appView, "scaleX", 1f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(appView, "scaleY", 1f, 1.2f, 1f)
        val alpha = ObjectAnimator.ofFloat(appView, "alpha", 1f, 0.8f, 1f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = duration
        animatorSet.interpolator = OvershootInterpolator()
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    /**
     * Search expand animation
     */
    fun animateSearchExpand(searchView: View, onComplete: (() -> Unit)? = null) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) {
            onComplete?.invoke()
            return
        }
        
        searchView.scaleX = 0.8f
        searchView.scaleY = 0.8f
        searchView.alpha = 0f
        searchView.visibility = View.VISIBLE
        
        val scaleX = ObjectAnimator.ofFloat(searchView, "scaleX", 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(searchView, "scaleY", 0.8f, 1f)
        val alpha = ObjectAnimator.ofFloat(searchView, "alpha", 0f, 1f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = duration
        animatorSet.interpolator = OvershootInterpolator()
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    fun animateSearchCollapse(searchView: View, onComplete: (() -> Unit)? = null) {
        val duration = batteryOptimizer.getAnimationDuration(FAST_DURATION)
        if (duration == 0L) {
            searchView.visibility = View.GONE
            onComplete?.invoke()
            return
        }
        
        val scaleX = ObjectAnimator.ofFloat(searchView, "scaleX", 1f, 0.8f)
        val scaleY = ObjectAnimator.ofFloat(searchView, "scaleY", 1f, 0.8f)
        val alpha = ObjectAnimator.ofFloat(searchView, "alpha", 1f, 0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = duration
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.doOnEnd {
            searchView.visibility = View.GONE
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Icon bounce animation
     */
    fun animateIconBounce(iconView: View) {
        val duration = batteryOptimizer.getAnimationDuration(FAST_DURATION)
        if (duration == 0L) return
        
        val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.1f, 1f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = duration
        animatorSet.interpolator = OvershootInterpolator()
        animatorSet.start()
    }
    
    /**
     * Folder open animation
     */
    fun animateFolderOpen(folderView: View, onComplete: (() -> Unit)? = null) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) {
            onComplete?.invoke()
            return
        }
        
        folderView.scaleX = 0.3f
        folderView.scaleY = 0.3f
        folderView.alpha = 0f
        folderView.visibility = View.VISIBLE
        
        val scaleX = ObjectAnimator.ofFloat(folderView, "scaleX", 0.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(folderView, "scaleY", 0.3f, 1f)
        val alpha = ObjectAnimator.ofFloat(folderView, "alpha", 0f, 1f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = duration
        animatorSet.interpolator = OvershootInterpolator()
        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }
    
    /**
     * Page transition animation
     */
    fun animatePageTransition(
        outgoingPage: View,
        incomingPage: View,
        direction: Int, // -1 for left, 1 for right
        onComplete: (() -> Unit)? = null
    ) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) {
            outgoingPage.visibility = View.GONE
            incomingPage.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }
        
        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
        
        // Setup incoming page
        incomingPage.translationX = screenWidth * direction
        incomingPage.visibility = View.VISIBLE
        
        // Animate outgoing page
        val outgoingAnim = ObjectAnimator.ofFloat(outgoingPage, "translationX", 0f, -screenWidth * direction)
        
        // Animate incoming page
        val incomingAnim = ObjectAnimator.ofFloat(incomingPage, "translationX", screenWidth * direction, 0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(outgoingAnim, incomingAnim)
        animatorSet.duration = duration
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.doOnEnd {
            outgoingPage.visibility = View.GONE
            outgoingPage.translationX = 0f
            onComplete?.invoke()
        }
        animatorSet.start()
    }
    
    /**
     * Ripple effect animation
     */
    fun animateRipple(view: View, centerX: Float, centerY: Float) {
        val duration = batteryOptimizer.getAnimationDuration(FAST_DURATION)
        if (duration == 0L) return
        
        val maxRadius = kotlin.math.max(view.width, view.height).toFloat()
        
        val rippleView = View(context).apply {
            setBackgroundResource(android.R.drawable.list_selector_background)
            alpha = 0.3f
        }
        
        (view as? ViewGroup)?.addView(rippleView)
        
        val scaleX = ObjectAnimator.ofFloat(rippleView, "scaleX", 0f, maxRadius / 100f)
        val scaleY = ObjectAnimator.ofFloat(rippleView, "scaleY", 0f, maxRadius / 100f)
        val alpha = ObjectAnimator.ofFloat(rippleView, "alpha", 0.3f, 0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = duration
        animatorSet.doOnEnd {
            (view as? ViewGroup)?.removeView(rippleView)
        }
        animatorSet.start()
    }
    
    /**
     * Stagger animation for lists
     */
    fun animateStaggeredList(views: List<View>, delay: Long = 50L) {
        val duration = batteryOptimizer.getAnimationDuration(DEFAULT_DURATION)
        if (duration == 0L) return
        
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            
            val animator = AnimatorSet()
            val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
            val slideUp = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f)
            
            animator.playTogether(fadeIn, slideUp)
            animator.duration = duration
            animator.startDelay = delay * index
            animator.interpolator = DecelerateInterpolator()
            animator.start()
        }
    }
}
