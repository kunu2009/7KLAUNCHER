package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity

class OfflineFirstAppStoreActivity : AppCompatActivity() {

    data class OfflineApp(val name: String, val description: String, val category: String, val launch: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Offline First AppStore"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101214.toInt())
        }

        content.addView(TextView(this).apply {
            text = "7K Offline First AppStore"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        })

        content.addView(TextView(this).apply {
            text = "Only internal/native 7K experiences. No external app promotions."
            textSize = 13f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(0, 8, 0, 16)
        })

        val stats = TextView(this).apply {
            text = "0 apps"
            textSize = 12f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1B2227.toInt())
        }
        content.addView(stats)

        val search = EditText(this).apply {
            hint = "Search offline apps"
            setHintTextColor(0xFF90A4AE.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1F1F1F.toInt())
            setPadding(12, 12, 12, 12)
        }
        val lpSearch = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpSearch.topMargin = 10
        content.addView(search, lpSearch)

        val cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        content.addView(cardsContainer)

        val apps = listOf(
            OfflineApp("7K Smart Notes+", "Notes power mode", "Productivity") { startActivity(Intent(this, com.sevenk.launcher.notes.ui.NotesActivity::class.java)) },
            OfflineApp("7K Tasks Commander", "Mission-style task control", "Productivity") { startActivity(Intent(this, TasksCommanderActivity::class.java)) },
            OfflineApp("7K File Forge", "Local private workspace", "Utility") { startActivity(Intent(this, FileForgeActivity::class.java)) },
            OfflineApp("7K Budget Guardian", "Budget protection", "Finance") { startActivity(Intent(this, BudgetGuardianActivity::class.java)) },
            OfflineApp("7K Privacy Shield", "Privacy control center", "Security") { startActivity(Intent(this, PrivacyShieldActivity::class.java)) },
            OfflineApp("7K Battery Doctor", "Battery diagnostics", "Utility") { startActivity(Intent(this, BatteryDoctorActivity::class.java)) },
            OfflineApp("7K Studio Templates", "Creative quick templates", "Creative") { startActivity(Intent(this, StudioTemplatesActivity::class.java)) },
            OfflineApp("7K Studio", "Photo + video editor", "Creative") { startActivity(Intent(this, com.sevenk.studio.StudioActivity::class.java)) }
        )

        fun render(query: String) {
            cardsContainer.removeAllViews()
            val filtered = apps.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
            stats.text = "${filtered.size} of ${apps.size} apps • Offline curated"

            filtered.forEach { app ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(14, 14, 14, 14)
                    setBackgroundColor(0xFF1E1E1E.toInt())
                }

                card.addView(TextView(this).apply {
                    text = app.name
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                })
                card.addView(TextView(this).apply {
                    text = app.description
                    textSize = 12f
                    setTextColor(0xFF80CBC4.toInt())
                    setPadding(0, 6, 0, 2)
                })
                card.addView(TextView(this).apply {
                    text = "Category: ${app.category}"
                    textSize = 11f
                    setTextColor(0xFFB0BEC5.toInt())
                    setPadding(0, 0, 0, 10)
                })

                val launchBtn = Button(this).apply {
                    text = "Open"
                    isAllCaps = false
                    setOnClickListener { app.launch.invoke() }
                }
                card.addView(launchBtn)

                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 10
                cardsContainer.addView(card, lp)
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                render(s?.toString().orEmpty().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        render("")

        root.addView(content)
        setContentView(root)
    }
}
