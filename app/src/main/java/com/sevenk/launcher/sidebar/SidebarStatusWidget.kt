package com.sevenk.launcher.sidebar

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/**
 * Sidebar battery and network indicator widget
 */
class SidebarStatusWidget(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statusCard: CardView? = null
    private var batteryProgressBar: ProgressBar? = null
    private var batteryText: TextView? = null
    private var networkText: TextView? = null
    private var statusText: TextView? = null
    
    /**
     * Create status widget view
     */
    fun createWidget(): View {
        val cardView = CardView(context).apply {
            radius = 12f
            cardElevation = 6f
            setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }
        
        // Battery section
        val batteryContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        batteryText = TextView(context).apply {
            text = "Battery: --"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        batteryProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        batteryContainer.addView(batteryText)
        batteryContainer.addView(batteryProgressBar)
        
        // Network section
        networkText = TextView(context).apply {
            text = "Network: Checking..."
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 8, 0, 0)
        }
        
        // Status section
        statusText = TextView(context).apply {
            text = "Status: Good"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 4, 0, 0)
        }
        
        container.addView(batteryContainer)
        container.addView(networkText)
        container.addView(statusText)
        
        cardView.addView(container)
        statusCard = cardView
        
        // Start monitoring
        startMonitoring()
        
        return cardView
    }
    
    /**
     * Start monitoring battery and network status
     */
    private fun startMonitoring() {
        scope.launch {
            while (true) {
                updateBatteryStatus()
                updateNetworkStatus()
                updateOverallStatus()
                delay(5000) // Update every 5 seconds
            }
        }
    }
    
    /**
     * Update battery status
     */
    private fun updateBatteryStatus() {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = isDeviceCharging()
            
            batteryProgressBar?.progress = batteryLevel
            
            val batteryColor = when {
                batteryLevel > 50 -> Color.parseColor("#4CAF50") // Green
                batteryLevel > 20 -> Color.parseColor("#FF9800") // Orange
                else -> Color.parseColor("#F44336") // Red
            }
            
            batteryProgressBar?.progressTintList = android.content.res.ColorStateList.valueOf(batteryColor)
            
            val chargingText = if (isCharging) " (Charging)" else ""
            batteryText?.text = "Battery: $batteryLevel%$chargingText"
            batteryText?.setTextColor(batteryColor)
            
        } catch (e: Exception) {
            batteryText?.text = "Battery: Error"
        }
    }
    
    /**
     * Check if device is charging
     */
    private fun isDeviceCharging(): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || 
               status == BatteryManager.BATTERY_STATUS_FULL
    }
    
    /**
     * Update network status
     */
    private fun updateNetworkStatus() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            
            val networkInfo = when {
                networkCapabilities == null -> {
                    NetworkInfo("No Connection", Color.parseColor("#F44336"))
                }
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val signalStrength = getWifiSignalStrength()
                    NetworkInfo("Wi-Fi ($signalStrength)", Color.parseColor("#4CAF50"))
                }
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    NetworkInfo("Mobile Data", Color.parseColor("#2196F3"))
                }
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    NetworkInfo("Ethernet", Color.parseColor("#4CAF50"))
                }
                else -> {
                    NetworkInfo("Unknown", Color.parseColor("#FF9800"))
                }
            }
            
            networkText?.text = "Network: ${networkInfo.name}"
            networkText?.setTextColor(networkInfo.color)
            
        } catch (e: Exception) {
            networkText?.text = "Network: Error"
            networkText?.setTextColor(Color.parseColor("#F44336"))
        }
    }
    
    /**
     * Get WiFi signal strength description
     */
    private fun getWifiSignalStrength(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val rssi = wifiInfo.rssi
            
            when {
                rssi > -50 -> "Excellent"
                rssi > -60 -> "Good"
                rssi > -70 -> "Fair"
                else -> "Weak"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Update overall status
     */
    private fun updateOverallStatus() {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = isDeviceCharging()
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val hasNetwork = connectivityManager.activeNetwork != null
            
            val status = when {
                batteryLevel < 15 && !isCharging -> StatusInfo("Low Battery", Color.parseColor("#F44336"))
                !hasNetwork -> StatusInfo("No Internet", Color.parseColor("#FF9800"))
                batteryLevel > 80 && hasNetwork -> StatusInfo("Excellent", Color.parseColor("#4CAF50"))
                batteryLevel > 50 && hasNetwork -> StatusInfo("Good", Color.parseColor("#4CAF50"))
                else -> StatusInfo("Fair", Color.parseColor("#FF9800"))
            }
            
            statusText?.text = "Status: ${status.text}"
            statusText?.setTextColor(status.color)
            
        } catch (e: Exception) {
            statusText?.text = "Status: Error"
            statusText?.setTextColor(Color.parseColor("#F44336"))
        }
    }
    
    /**
     * Get detailed system information
     */
    fun getDetailedInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        try {
            // Battery info
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = isDeviceCharging()
            val batteryTemp = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            
            info["Battery Level"] = "$batteryLevel%"
            info["Charging"] = if (isCharging) "Yes" else "No"
            info["Battery Health"] = getBatteryHealth()
            
            // Network info
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (networkCapabilities != null) {
                info["Network Type"] = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Unknown"
                }
                
                info["Download Speed"] = "${networkCapabilities.linkDownstreamBandwidthKbps / 1000} Mbps"
                info["Upload Speed"] = "${networkCapabilities.linkUpstreamBandwidthKbps / 1000} Mbps"
            } else {
                info["Network Type"] = "Disconnected"
            }
            
            // Memory info
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            
            info["Memory Usage"] = "${usedMemory}MB / ${maxMemory}MB"
            info["Free Memory"] = "${maxMemory - usedMemory}MB"
            
        } catch (e: Exception) {
            info["Error"] = "Failed to get system info"
        }
        
        return info
    }
    
    /**
     * Get battery health status
     */
    private fun getBatteryHealth(): String {
        return try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            
            when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Stop monitoring and cleanup
     */
    fun destroy() {
        scope.cancel()
        statusCard = null
        batteryProgressBar = null
        batteryText = null
        networkText = null
        statusText = null
    }
}

private data class NetworkInfo(val name: String, val color: Int)
private data class StatusInfo(val text: String, val color: Int)
