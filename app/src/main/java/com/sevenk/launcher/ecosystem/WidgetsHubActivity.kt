package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.SettingsActivity

class WidgetsHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Widgets"

        val prefs = getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE)

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val heading = TextView(this).apply {
            text = "7K Widgets Hub"
            textSize = 24f
        }

        val sub = TextView(this).apply {
            text = "Tune widget-style UI toggles instantly"
            textSize = 14f
            setPadding(0, 12, 0, 20)
        }

        content.addView(heading)
        content.addView(sub)

        fun addToggle(label: String, key: String, defaultValue: Boolean) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sw = Switch(this).apply {
                isChecked = prefs.getBoolean(key, defaultValue)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                    Toast.makeText(this@WidgetsHubActivity, "$label ${if (checked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(tv)
            row.addView(sw)
            content.addView(row)
        }

        addToggle("Show Clock", "show_clock", true)
        addToggle("Show Labels", "show_labels", true)
        addToggle("Hide Search Box", "hide_search_box", false)
        addToggle("Show Dock", "show_dock", true)
        addToggle("Show Sidebar", "show_sidebar", true)

        val openSettings = Button(this).apply {
            text = "Open Full Launcher Settings"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(this@WidgetsHubActivity, SettingsActivity::class.java))
            }
        }

        val hint = TextView(this).apply {
            text = "Tip: return to home to see changes immediately."
            textSize = 13f
            setPadding(0, 18, 0, 0)
            gravity = Gravity.CENTER
        }

        content.addView(openSettings)
        content.addView(hint)

        root.addView(content)
        setContentView(root)
    }
}
