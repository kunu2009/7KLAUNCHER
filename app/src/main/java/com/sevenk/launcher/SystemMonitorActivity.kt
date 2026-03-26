package com.sevenk.launcher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.monitoring.SystemMonitor
import java.util.Locale
import kotlin.math.roundToInt

class SystemMonitorActivity : AppCompatActivity() {
    
    private lateinit var systemMonitor: SystemMonitor
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    
    // UI elements
    private lateinit var batteryPercentageText: TextView
    private lateinit var batteryTemperatureText: TextView
    private lateinit var batteryVoltageText: TextView
    private lateinit var batteryHealthText: TextView
    private lateinit var batteryChargingText: TextView
    
    private lateinit var ramTotalText: TextView
    private lateinit var ramUsedText: TextView
    private lateinit var ramAvailableText: TextView
    private lateinit var ramLauncherText: TextView
    private lateinit var ramHeapText: TextView
    
    private lateinit var cpuUsageText: TextView
    private lateinit var cpuTemperatureText: TextView
    private lateinit var cpuCoresText: TextView
    private lateinit var cpuFrequencyText: TextView
    
    private lateinit var storageTotalText: TextView
    private lateinit var storageUsedText: TextView
    private lateinit var storageAvailableText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_monitor)
        
        supportActionBar?.title = "System Monitor"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        systemMonitor = SystemMonitor(this)
        handler = Handler(Looper.getMainLooper())
        
        initializeViews()
        startMonitoring()
    }
    
    private fun initializeViews() {
        // Battery
        batteryPercentageText = findViewById(R.id.batteryPercentage)
        batteryTemperatureText = findViewById(R.id.batteryTemperature)
        batteryVoltageText = findViewById(R.id.batteryVoltage)
        batteryHealthText = findViewById(R.id.batteryHealth)
        batteryChargingText = findViewById(R.id.batteryCharging)
        
        // RAM
        ramTotalText = findViewById(R.id.ramTotal)
        ramUsedText = findViewById(R.id.ramUsed)
        ramAvailableText = findViewById(R.id.ramAvailable)
        ramLauncherText = findViewById(R.id.ramLauncher)
        ramHeapText = findViewById(R.id.ramHeap)
        
        // CPU
        cpuUsageText = findViewById(R.id.cpuUsage)
        cpuTemperatureText = findViewById(R.id.cpuTemperature)
        cpuCoresText = findViewById(R.id.cpuCores)
        cpuFrequencyText = findViewById(R.id.cpuFrequency)
        
        // Storage
        storageTotalText = findViewById(R.id.storageTotal)
        storageUsedText = findViewById(R.id.storageUsed)
        storageAvailableText = findViewById(R.id.storageAvailable)
    }
    
    private fun startMonitoring() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateSystemInfo()
                handler.postDelayed(this, 2000) // Update every 2 seconds
            }
        }
        handler.post(updateRunnable)
    }
    
    private fun updateSystemInfo() {
        try {
            // Battery info
            val batteryInfo = systemMonitor.getBatteryInfo()
            batteryPercentageText.text = "${batteryInfo.percentage}%"
            batteryTemperatureText.text = "${batteryInfo.temperature.roundToInt()}°C"
            batteryVoltageText.text = "${String.format(Locale.getDefault(), "%.2f", batteryInfo.voltage)}V"
            batteryHealthText.text = batteryInfo.health
            batteryChargingText.text = if (batteryInfo.isCharging) "Charging" else "Not Charging"
            
            // RAM info
            val ramInfo = systemMonitor.getRAMInfo()
            ramTotalText.text = formatBytes(ramInfo.totalRAM)
            ramUsedText.text = formatBytes(ramInfo.usedRAM)
            ramAvailableText.text = formatBytes(ramInfo.availableRAM)
            ramLauncherText.text = formatBytes(ramInfo.launcherRAMUsage)
            ramHeapText.text = "${formatBytes(ramInfo.heapUsed)} / ${formatBytes(ramInfo.heapMax)}"
            
            // CPU info
            val cpuInfo = systemMonitor.getCPUInfo()
            cpuUsageText.text = "${String.format(Locale.getDefault(), "%.1f", cpuInfo.usage)}%"
            cpuTemperatureText.text = if (cpuInfo.temperature > 0) "${cpuInfo.temperature.roundToInt()}°C" else "N/A"
            cpuCoresText.text = "${cpuInfo.cores} cores"
            cpuFrequencyText.text = if (cpuInfo.frequency > 0) "${cpuInfo.frequency / 1000} MHz" else "N/A"
            
            // Storage info
            val storageInfo = systemMonitor.getStorageInfo()
            storageTotalText.text = formatBytes(storageInfo.totalStorage)
            storageUsedText.text = formatBytes(storageInfo.usedStorage)
            storageAvailableText.text = formatBytes(storageInfo.availableStorage)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}