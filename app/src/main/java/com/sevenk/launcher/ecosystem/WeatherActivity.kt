package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WeatherActivity : AppCompatActivity() {

    private data class DayForecast(val day: String, val high: Int, val low: Int, val condition: String, val icon: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Weather"

        val root = ScrollView(this).apply {
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 24, 16, 24)
        }

        // Header
        val heading = TextView(this).apply {
            text = "7K Weather"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val sub = TextView(this).apply {
            text = "Quick weather reference"
            textSize = 13f
            setTextColor(0xFFAAAAA.toInt())
            setPadding(0, 8, 0, 24)
        }

        content.addView(heading)
        content.addView(sub)

        // Today's Weather Card
        val todayCard = CardView(this).apply {
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(0xFF1E3A8A.toInt())
        }

        val todayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val todayLabel = TextView(this).apply {
            text = "Today"
            textSize = 14f
            setTextColor(0xFFADD8E6.toInt())
        }

        val tempText = TextView(this).apply {
            text = "72°F"
            textSize = 56f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val conditionText = TextView(this).apply {
            text = "Partly Cloudy"
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 8, 0, 0)
        }

        val detailsText = TextView(this).apply {
            text = "Humidity: 65% | Wind: 8 mph | UV: 5"
            textSize = 12f
            setTextColor(0xFFAAAAA.toInt())
            setPadding(0, 8, 0, 0)
        }

        todayLayout.addView(todayLabel)
        todayLayout.addView(tempText)
        todayLayout.addView(conditionText)
        todayLayout.addView(detailsText)
        todayCard.addView(todayLayout)
        content.addView(todayCard)

        // Forecast Section
        val forecastTitle = TextView(this).apply {
            text = "7-Day Forecast"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 12)
        }
        content.addView(forecastTitle)

        val forecasts = listOf(
            DayForecast("Mon", 75, 62, "Sunny", "☀️"),
            DayForecast("Tue", 73, 61, "Partly Cloudy", "⛅"),
            DayForecast("Wed", 70, 59, "Rainy", "🌧️"),
            DayForecast("Thu", 68, 57, "Cloudy", "☁️"),
            DayForecast("Fri", 76, 63, "Sunny", "☀️"),
            DayForecast("Sat", 78, 65, "Sunny", "☀️"),
            DayForecast("Sun", 74, 62, "Partly Cloudy", "⛅"),
        )

        forecasts.forEach { forecast ->
            val card = CardView(this).apply {
                radius = 10f
                cardElevation = 2f
                setCardBackgroundColor(0xFF2D2D2D.toInt())
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 12, 12, 12)
            }

            val dayView = TextView(this).apply {
                text = forecast.day
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val iconView = TextView(this).apply {
                text = forecast.icon
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val condView = TextView(this).apply {
                text = forecast.condition
                textSize = 11f
                setTextColor(0xFFAAAAA.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tempView = TextView(this).apply {
                text = "${forecast.high}°/${forecast.low}°"
                textSize = 12f
                setTextColor(0xFF0D7377.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            layout.addView(dayView)
            layout.addView(iconView)
            layout.addView(condView)
            layout.addView(tempView)
            card.addView(layout)

            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.bottomMargin = 10
            content.addView(card, cardParams)
        }

        // Info Card
        val infoCard = CardView(this).apply {
            radius = 10f
            cardElevation = 2f
            setCardBackgroundColor(0xFF2D2D2D.toInt())
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }

        val infoTitle = TextView(this).apply {
            text = "About this data"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val infoText = TextView(this).apply {
            text = "Weather data is offline cached & reference-based.\nUpdate location in Settings → Weather."
            textSize = 11f
            setTextColor(0xFFAAAAA.toInt())
            setPadding(0, 8, 0, 0)
        }

        infoLayout.addView(infoTitle)
        infoLayout.addView(infoText)
        infoCard.addView(infoLayout)

        val infoParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        infoParams.topMargin = 24
        content.addView(infoCard, infoParams)

        root.addView(content)
        setContentView(root)
    }
}
