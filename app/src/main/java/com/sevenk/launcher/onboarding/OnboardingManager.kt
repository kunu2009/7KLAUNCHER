package com.sevenk.launcher.onboarding

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.sevenk.launcher.haptics.HapticFeedbackManager

/**
 * Onboarding system with tips and tricks overlay
 */
class OnboardingManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("onboarding_settings", Context.MODE_PRIVATE)
    private val hapticManager = HapticFeedbackManager(context)
    private var currentOverlay: View? = null
    
    data class OnboardingTip(
        val id: String,
        val title: String,
        val description: String,
        val targetView: View? = null,
        val position: TipPosition = TipPosition.CENTER
    )
    
    enum class TipPosition {
        TOP, CENTER, BOTTOM, ABOVE_TARGET, BELOW_TARGET
    }
    
    private val onboardingTips = listOf(
        OnboardingTip(
            id = "swipe_gestures",
            title = "Swipe Gestures",
            description = "Swipe down from anywhere to open global search\nSwipe up from bottom to open app drawer\nSwipe left/right to navigate pages"
        ),
        OnboardingTip(
            id = "dock_features",
            title = "Dock Features",
            description = "Long press dock apps for quick actions\nDrag apps to rearrange\nSwipe up on dock apps for shortcuts"
        ),
        OnboardingTip(
            id = "sidebar_access",
            title = "Sidebar Access",
            description = "Swipe from right edge to open sidebar\nAccess quick toggles and settings\nView battery and network status"
        ),
        OnboardingTip(
            id = "customization",
            title = "Customization",
            description = "Long press home screen to customize\nChange icon packs, themes, and layouts\nCreate folders and organize apps"
        ),
        OnboardingTip(
            id = "search_features",
            title = "Search Features",
            description = "Search apps, contacts, and web\nUse voice search with microphone\nAccess recent searches quickly"
        )
    )
    
    /**
     * Check if onboarding should be shown
     */
    fun shouldShowOnboarding(): Boolean {
        return !prefs.getBoolean("onboarding_completed", false)
    }
    
    /**
     * Start onboarding flow
     */
    fun startOnboarding(rootView: ViewGroup) {
        if (!shouldShowOnboarding()) return
        
        showTip(0, rootView)
    }
    
    /**
     * Show specific tip
     */
    private fun showTip(tipIndex: Int, rootView: ViewGroup) {
        if (tipIndex >= onboardingTips.size) {
            completeOnboarding()
            return
        }
        
        val tip = onboardingTips[tipIndex]
        dismissCurrentOverlay()
        
        val overlay = createTipOverlay(tip) { nextTip ->
            if (nextTip) {
                showTip(tipIndex + 1, rootView)
            } else {
                completeOnboarding()
            }
        }
        
        rootView.addView(overlay)
        currentOverlay = overlay
        
        hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
    }
    
    /**
     * Create tip overlay
     */
    private fun createTipOverlay(tip: OnboardingTip, onAction: (Boolean) -> Unit): View {
        val overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            isClickable = true
        }
        
        val tipCard = createTipCard(tip, onAction)
        
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            when (tip.position) {
                TipPosition.TOP -> gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                TipPosition.CENTER -> gravity = Gravity.CENTER
                TipPosition.BOTTOM -> gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                TipPosition.ABOVE_TARGET -> {
                    gravity = Gravity.CENTER_HORIZONTAL
                    // Position above target view if available
                }
                TipPosition.BELOW_TARGET -> {
                    gravity = Gravity.CENTER_HORIZONTAL
                    // Position below target view if available
                }
            }
            setMargins(32, 64, 32, 64)
        }
        
        overlay.addView(tipCard, layoutParams)
        return overlay
    }
    
    /**
     * Create tip card
     */
    private fun createTipCard(tip: OnboardingTip, onAction: (Boolean) -> Unit): CardView {
        val cardView = CardView(context).apply {
            radius = 16f
            cardElevation = 12f
            setCardBackgroundColor(Color.WHITE)
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        
        // Title
        val titleText = TextView(context).apply {
            text = tip.title
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Description
        val descriptionText = TextView(context).apply {
            text = tip.description
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 16, 0, 24)
        }
        
        // Buttons
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        
        val skipButton = TextView(context).apply {
            text = "Skip"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(16, 12, 16, 12)
            background = context.getDrawable(android.R.drawable.list_selector_background)
            setOnClickListener {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
                onAction(false)
            }
        }
        
        val nextButton = TextView(context).apply {
            text = "Next"
            textSize = 16f
            setTextColor(Color.parseColor("#1976D2"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(16, 12, 16, 12)
            background = context.getDrawable(android.R.drawable.list_selector_background)
            setOnClickListener {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
                onAction(true)
            }
        }
        
        buttonContainer.addView(skipButton)
        buttonContainer.addView(nextButton)
        
        container.addView(titleText)
        container.addView(descriptionText)
        container.addView(buttonContainer)
        
        cardView.addView(container)
        return cardView
    }
    
    /**
     * Show quick tip
     */
    fun showQuickTip(title: String, description: String, rootView: ViewGroup, duration: Long = 3000) {
        dismissCurrentOverlay()
        
        val tip = OnboardingTip("quick_tip", title, description)
        val overlay = createQuickTipOverlay(tip)
        
        rootView.addView(overlay)
        currentOverlay = overlay
        
        // Auto dismiss after duration
        overlay.postDelayed({
            dismissCurrentOverlay()
        }, duration)
        
        hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
    }
    
    /**
     * Create quick tip overlay
     */
    private fun createQuickTipOverlay(tip: OnboardingTip): View {
        val overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
        }
        
        val tipCard = CardView(context).apply {
            radius = 12f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#333333"))
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        
        val titleText = TextView(context).apply {
            text = tip.title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val descriptionText = TextView(context).apply {
            text = tip.description
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 8, 0, 0)
        }
        
        container.addView(titleText)
        container.addView(descriptionText)
        tipCard.addView(container)
        
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setMargins(32, 100, 32, 0)
        }
        
        overlay.addView(tipCard, layoutParams)
        return overlay
    }
    
    /**
     * Dismiss current overlay
     */
    private fun dismissCurrentOverlay() {
        currentOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            currentOverlay = null
        }
    }
    
    /**
     * Complete onboarding
     */
    private fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        dismissCurrentOverlay()
        hapticManager.performHaptic(HapticFeedbackManager.HapticType.SUCCESS)
    }
    
    /**
     * Reset onboarding
     */
    fun resetOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", false).apply()
    }
    
    /**
     * Mark specific tip as seen
     */
    fun markTipAsSeen(tipId: String) {
        prefs.edit().putBoolean("tip_seen_$tipId", true).apply()
    }
    
    /**
     * Check if tip was seen
     */
    fun wasTipSeen(tipId: String): Boolean {
        return prefs.getBoolean("tip_seen_$tipId", false)
    }
}
