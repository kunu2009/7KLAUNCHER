package com.sevenk.launcher.sidebar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages quick toggle functionality for sidebar
 */
class QuickToggleManager(private val context: Context) {
    
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    private var flashlightEnabled = false
    private var cameraId: String? = null
    
    init {
        // Get back camera ID for flashlight
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    if (hasFlash == true) {
                        cameraId = id
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Camera access might be restricted
        }
    }
    
    /**
     * Toggle WiFi state
     */
    fun toggleWifi(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ requires user to manually toggle WiFi
                val intent = android.content.Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                false // Cannot determine new state
            } else {
                @Suppress("DEPRECATION")
                val currentState = wifiManager.isWifiEnabled
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = !currentState
                !currentState
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get current WiFi state
     */
    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Toggle Bluetooth state
     */
    fun toggleBluetooth(): Boolean {
        return try {
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null) return false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ requires BLUETOOTH_CONNECT permission
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                    // Open Bluetooth settings
                    val intent = android.content.Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return false
                }
            }
            
            val currentState = bluetoothAdapter.isEnabled
            if (currentState) {
                @Suppress("DEPRECATION")
                bluetoothAdapter.disable()
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter.enable()
            }
            !currentState
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get current Bluetooth state
     */
    fun isBluetoothEnabled(): Boolean {
        return try {
            val bluetoothAdapter = bluetoothManager.adapter
            bluetoothAdapter?.isEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Toggle flashlight state
     */
    fun toggleFlashlight(): Boolean {
        return try {
            val targetCameraId = cameraId ?: return false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flashlightEnabled = !flashlightEnabled
                cameraManager.setTorchMode(targetCameraId, flashlightEnabled)
                flashlightEnabled
            } else {
                false
            }
        } catch (e: Exception) {
            flashlightEnabled = false
            false
        }
    }
    
    /**
     * Get current flashlight state
     */
    fun isFlashlightEnabled(): Boolean {
        return flashlightEnabled
    }
    
    /**
     * Check if feature is available
     */
    fun isWifiAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
    }
    
    fun isBluetoothAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }
    
    fun isFlashlightAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) && cameraId != null
    }
    
    /**
     * Get required permissions for each feature
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        permissions.add(Manifest.permission.CAMERA)
        
        return permissions
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Data class for quick toggle state
 */
data class QuickToggleState(
    val isWifiEnabled: Boolean,
    val isBluetoothEnabled: Boolean,
    val isFlashlightEnabled: Boolean,
    val isWifiAvailable: Boolean,
    val isBluetoothAvailable: Boolean,
    val isFlashlightAvailable: Boolean
)
