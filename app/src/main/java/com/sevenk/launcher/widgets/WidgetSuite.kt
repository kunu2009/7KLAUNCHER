package com.sevenk.launcher.widgets

import android.content.Context
import android.graphics.*
import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.sevenk.launcher.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Widget suite with Today card, Music widget, and Quick Notes
 */
class WidgetSuite(private val context: Context) {
    
    /**
     * Create Today card widget
     */
    fun createTodayCard(): CardView {
        val cardView = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#80FFFFFF"))
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        
        // Date and time
        val dateText = TextView(context).apply {
            text = getCurrentDateString()
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val timeText = TextView(context).apply {
            text = getCurrentTimeString()
            textSize = 32f
            setTextColor(Color.parseColor("#1976D2"))
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Weather placeholder
        val weatherText = TextView(context).apply {
            text = "22°C • Sunny"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
        }
        
        container.addView(dateText)
        container.addView(timeText)
        container.addView(weatherText)
        cardView.addView(container)
        
        return cardView
    }
    
    /**
     * Create Music widget
     */
    fun createMusicWidget(): CardView {
        val cardView = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#80000000"))
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
        }
        
        // Album art placeholder
        val albumArt = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60)
            setBackgroundColor(Color.parseColor("#424242"))
        }
        
        // Music info
        val musicInfo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 0, 16, 0)
            }
        }
        
        val songTitle = TextView(context).apply {
            text = "No music playing"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val artistName = TextView(context).apply {
            text = "Tap to open music app"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
        }
        
        musicInfo.addView(songTitle)
        musicInfo.addView(artistName)
        
        container.addView(albumArt)
        container.addView(musicInfo)
        cardView.addView(container)
        
        return cardView
    }
    
    /**
     * Create Quick Notes widget
     */
    fun createQuickNotesWidget(): CardView {
        val cardView = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#80FFF9C4"))
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        
        val title = TextView(context).apply {
            text = "Quick Notes"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val noteText = TextView(context).apply {
            text = "Tap to add a quick note..."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            minHeight = 80
        }
        
        container.addView(title)
        container.addView(noteText)
        cardView.addView(container)
        
        return cardView
    }
    
    /**
     * Create minimal clock widget
     */
    fun createClockWidget(): CardView {
        val cardView = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(Color.parseColor("#80FFFFFF"))
        }
        
        val timeText = TextView(context).apply {
            text = getCurrentTimeString()
            textSize = 28f
            setTextColor(Color.parseColor("#1976D2"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }
        
        cardView.addView(timeText)
        return cardView
    }
    
    /**
     * Create minimal calendar widget
     */
    fun createCalendarWidget(): CardView {
        val cardView = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(Color.parseColor("#80FFFFFF"))
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }
        
        val calendar = Calendar.getInstance()
        
        val monthText = TextView(context).apply {
            text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }
        
        val dayText = TextView(context).apply {
            text = calendar.get(Calendar.DAY_OF_MONTH).toString()
            textSize = 32f
            setTextColor(Color.parseColor("#1976D2"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }
        
        val weekdayText = TextView(context).apply {
            text = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = android.view.Gravity.CENTER
        }
        
        container.addView(monthText)
        container.addView(dayText)
        container.addView(weekdayText)
        cardView.addView(container)
        
        return cardView
    }
    
    private fun getCurrentTimeString(): String {
        val calendar = Calendar.getInstance()
        val is24Hour = DateFormat.is24HourFormat(context)
        val format = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(format, Locale.getDefault()).format(calendar.time)
    }
    
    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(calendar.time)
    }
}
