package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrivacyShieldActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("sevenk_privacy_shield", MODE_PRIVATE) }
    private lateinit var scoreView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Privacy Shield"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF121212.toInt())
        }

        content.addView(TextView(this).apply {
            text = "7K Privacy Shield"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        })

        content.addView(TextView(this).apply {
            text = "Personal privacy control center"
            textSize = 13f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(0, 8, 0, 16)
        })

        scoreView = TextView(this).apply {
            textSize = 20f
            setPadding(14, 14, 14, 14)
            setBackgroundColor(0xFF1F1F1F.toInt())
            setTextColor(0xFF80CBC4.toInt())
        }
        content.addView(scoreView)

        addToggle(content, "Hide App Usage Labels", "hide_labels", false)
        addToggle(content, "Enable Local Lock Flag", "local_lock_enabled", false)
        addToggle(content, "Block Screenshot Reminder", "anti_screenshot_hint", true)

        val appPermBtn = Button(this).apply {
            text = "Open App Permissions"
            isAllCaps = false
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS)) }
            }
        }

        val secBtn = Button(this).apply {
            text = "Open Security Settings"
            isAllCaps = false
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            }
        }

        content.addView(appPermBtn)
        content.addView(secBtn)

        root.addView(content)
        setContentView(root)

        refreshScore()
    }

    private fun addToggle(parent: LinearLayout, label: String, key: String, default: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tv = TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(this).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                refreshScore()
            }
        }
        row.addView(tv)
        row.addView(sw)

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 10
        parent.addView(row, lp)
    }

    private fun refreshScore() {
        var score = 40
        if (prefs.getBoolean("hide_labels", false)) score += 20
        if (prefs.getBoolean("local_lock_enabled", false)) score += 20
        if (prefs.getBoolean("anti_screenshot_hint", true)) score += 20

        scoreView.text = "Privacy score: $score / 100"
    }
}
