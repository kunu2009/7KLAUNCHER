package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.IconCache

class UtilityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Utility"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val heading = TextView(this).apply {
            text = "7K Utility"
            textSize = 24f
        }
        val sub = TextView(this).apply {
            text = "Quick tools for daily launcher power-use"
            textSize = 14f
            setPadding(0, 12, 0, 24)
        }
        val status = TextView(this).apply {
            text = buildStatusText()
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }

        content.addView(heading)
        content.addView(sub)
        content.addView(status)

        fun addAction(text: String, action: () -> Unit) {
            val btn = Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener { action() }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 16
            content.addView(btn, lp)
        }

        addAction("Wi‑Fi Settings") {
            startActivitySafely(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        addAction("Bluetooth Settings") {
            startActivitySafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        addAction("Display Settings") {
            startActivitySafely(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
        addAction("Storage Settings") {
            startActivitySafely(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        }
        addAction("Launcher App Info") {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivitySafely(intent)
        }
        addAction("Clear Launcher Icon Cache") {
            IconCache.clearAll()
            Toast.makeText(this, "Icon cache cleared", Toast.LENGTH_SHORT).show()
        }

        root.addView(content)
        setContentView(root)
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildStatusText(): String {
        val battery = getBatteryPct()?.let { "$it%" } ?: "Unknown"
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nBattery: $battery"
    }

    private fun getBatteryPct(): Int? {
        return try {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else null
        } catch (_: Throwable) {
            null
        }
    }
}
