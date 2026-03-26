package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class AppStoreActivity : AppCompatActivity() {

    data class SevenKApp(
        val name: String,
        val description: String,
        val packageName: String? = null,
        val isEmbedded: Boolean = false
    )

    private val sevenKApps = listOf(
        SevenKApp("7K Utility", "Quick access system tools & device info", "internal.7kutility", true),
        SevenKApp("7K Games", "Tap Sprint mini-game with scoring", "internal.7kgames", true),
        SevenKApp("7K Widgets", "Toggle launcher UI features", "internal.7kwidgets", true),
        SevenKApp("7K Studio", "Advanced launcher customization", "internal.7kstudio", true),
        SevenKApp("7K Calendar", "Offline calendar with notes", "internal.7kcalendar", true),
        SevenKApp("7K Music", "Local music player & organizer", "internal.7kmusic", true),
        SevenKApp("7K Weather", "Offline weather with forecasts", "internal.7kweather", true),
        SevenKApp("7K Files", "Native file manager", "internal.7kfiles", true),
        SevenKApp("7K Notes", "Quick note editor", "internal.7knotes", true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K AppStore"

        val root = ScrollView(this).apply {
            setBackgroundColor(0xFF1A1A1A.toInt())
        }
        
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 24, 16, 24)
        }

        // Header
        val heading = TextView(this).apply {
            text = "7K AppStore"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val sub = TextView(this).apply {
            text = "Powerful 7K ecosystem of integrated apps"
            textSize = 14f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 8, 0, 24)
        }

        content.addView(heading)
        content.addView(sub)

        // Ecosystem Stats Card
        val stats = QuickLauncher(this).getEcosystemStats()
        val statsCard = createCardView(
            "7K Ecosystem Status",
            "Installed: ${stats.installedApps}/${stats.totalApps} apps\nCompletion: ${stats.completionPercentage}%",
            0xFF2D2D2D.toInt()
        )
        content.addView(statsCard)

        // Web Hub Button
        val ecosystemButton = Button(this).apply {
            text = "🌐 Open 7K Web Hub"
            isAllCaps = false
            textSize = 15f
            setBackgroundColor(0xFF0D7377.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                openUrl("https://7klawprep.me/")
            }
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.bottomMargin = 20
        content.addView(ecosystemButton, btnParams)

        // Apps Section Title
        val appsTitle = TextView(this).apply {
            text = "Core Apps"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 12)
        }
        content.addView(appsTitle)

        // Apps Grid
        sevenKApps.forEach { app ->
            val appCard = createAppCardView(app)
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.bottomMargin = 12
            content.addView(appCard, cardParams)
        }

        // Info Card
        val infoCard = createCardView(
            "Why 7K?",
            "Integrated apps designed specifically for this launcher.\n✓ Offline & fast\n✓ Privacy-focused\n✓ Zero tracking\n✓ Native performance",
            0xFF2D2D2D.toInt()
        )
        content.addView(infoCard)

        root.addView(content)
        setContentView(root)
    }

    private fun createCardView(title: String, content: String, bgColor: Int): CardView {
        val card = CardView(this).apply {
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(bgColor)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val contentView = TextView(this).apply {
            text = content
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            lineHeight = 22
        }

        layout.addView(titleView)
        layout.addView(contentView)
        card.addView(layout)
        return card
    }

    private fun createAppCardView(app: SevenKApp): CardView {
        val card = CardView(this).apply {
            radius = 10f
            cardElevation = 2f
            setCardBackgroundColor(0xFF252525.toInt())
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }

        val nameView = TextView(this).apply {
            text = app.name
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val descView = TextView(this).apply {
            text = app.description
            textSize = 12f
            setTextColor(0xFFAAAAA.toInt())
            setPadding(0, 4, 0, 8)
        }

        val statusView = TextView(this).apply {
            text = if (app.isEmbedded) "✓ Embedded & Offline" else "Built-in"
            textSize = 11f
            setTextColor(0xFF0D7377.toInt())
        }

        layout.addView(nameView)
        layout.addView(descView)
        layout.addView(statusView)
        card.addView(layout)
        return card
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }
}
