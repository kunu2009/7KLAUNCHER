package com.sevenk.launcher.sidebar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sevenk.launcher.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity for configuring sidebar quick toggles
 */
class SidebarQuickTogglesActivity : AppCompatActivity() {
    
    private lateinit var quickToggleManager: QuickToggleManager
    private lateinit var wifiSwitch: Switch
    private lateinit var bluetoothSwitch: Switch
    private lateinit var flashlightSwitch: Switch
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            updateToggleStates()
        } else {
            Toast.makeText(this, "Some permissions were denied. Quick toggles may not work properly.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sidebar_quick_toggles)
        
        quickToggleManager = QuickToggleManager(this)
        
        initViews()
        setupToggleListeners()
        checkPermissions()
        updateToggleStates()
    }
    
    private fun initViews() {
        wifiSwitch = findViewById(R.id.wifiSwitch)
        bluetoothSwitch = findViewById(R.id.bluetoothSwitch)
        flashlightSwitch = findViewById(R.id.flashlightSwitch)
        
        // Disable unavailable features
        wifiSwitch.isEnabled = quickToggleManager.isWifiAvailable()
        bluetoothSwitch.isEnabled = quickToggleManager.isBluetoothAvailable()
        flashlightSwitch.isEnabled = quickToggleManager.isFlashlightAvailable()
    }
    
    private fun setupToggleListeners() {
        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != quickToggleManager.isWifiEnabled()) {
                val newState = quickToggleManager.toggleWifi()
                // Update switch to reflect actual state
                lifecycleScope.launch {
                    delay(500) // Give time for state change
                    wifiSwitch.isChecked = quickToggleManager.isWifiEnabled()
                }
            }
        }
        
        bluetoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != quickToggleManager.isBluetoothEnabled()) {
                val newState = quickToggleManager.toggleBluetooth()
                // Update switch to reflect actual state
                lifecycleScope.launch {
                    delay(500) // Give time for state change
                    bluetoothSwitch.isChecked = quickToggleManager.isBluetoothEnabled()
                }
            }
        }
        
        flashlightSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != quickToggleManager.isFlashlightEnabled()) {
                val newState = quickToggleManager.toggleFlashlight()
                flashlightSwitch.isChecked = newState
            }
        }
    }
    
    private fun checkPermissions() {
        val requiredPermissions = quickToggleManager.getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun updateToggleStates() {
        wifiSwitch.isChecked = quickToggleManager.isWifiEnabled()
        bluetoothSwitch.isChecked = quickToggleManager.isBluetoothEnabled()
        flashlightSwitch.isChecked = quickToggleManager.isFlashlightEnabled()
    }
    
    override fun onResume() {
        super.onResume()
        updateToggleStates()
    }
}
