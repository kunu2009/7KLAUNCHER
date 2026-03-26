package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BatteryDoctorActivity : AppCompatActivity() {

    private lateinit var batteryStatusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Battery Doctor"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF111111.toInt())
        }

        content.addView(TextView(this).apply {
            text = "7K Battery Doctor"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        })

        content.addView(TextView(this).apply {
            text = "Battery diagnostics + quick optimization shortcuts"
            textSize = 13f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(0, 8, 0, 16)
        })

        batteryStatusView = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1F1F1F.toInt())
            setPadding(14, 14, 14, 14)
        }
        content.addView(batteryStatusView)

        val btnBatterySettings = Button(this).apply {
            text = "Open Battery Settings"
            isAllCaps = false
            setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) } }
        }
        val btnUsage = Button(this).apply {
            text = "Open Battery Usage"
            isAllCaps = false
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } }
        }
        val btnRefresh = Button(this).apply {
            text = "Refresh Health"
            isAllCaps = false
            setOnClickListener { renderBatteryData() }
        }

        content.addView(btnBatterySettings)
        content.addView(btnUsage)
        content.addView(btnRefresh)

        root.addView(content)
        setContentView(root)

        renderBatteryData()
    }

    private fun renderBatteryData() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val healthHint = when {
            pct >= 80 -> "Excellent"
            pct >= 50 -> "Good"
            pct >= 30 -> "Moderate"
            else -> "Low - consider charging"
        }

        val charging = if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) "Charging" else "Not Charging"
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Unplugged"
        }

        batteryStatusView.text = "Level: ${if (pct >= 0) "$pct%" else "Unknown"}\nStatus: $charging\nPower source: $plugType\nHealth hint: $healthHint"
    }
}
