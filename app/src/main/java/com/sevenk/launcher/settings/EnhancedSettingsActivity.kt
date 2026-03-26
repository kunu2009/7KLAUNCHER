package com.sevenk.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sevenk.launcher.R
import com.sevenk.launcher.backup.EnhancedBackupManager
import com.sevenk.launcher.themes.ThemeManager
import com.sevenk.launcher.privacy.AppPrivacyManager
import com.sevenk.launcher.IconPackSelectionActivity
import com.sevenk.launcher.GestureSettingsActivity
import kotlinx.coroutines.launch

class EnhancedSettingsActivity : AppCompatActivity() {
    
    private lateinit var enhancedBackupManager: EnhancedBackupManager
    private lateinit var themeManager: ThemeManager
    private lateinit var appPrivacyManager: AppPrivacyManager
    
    private val prefs by lazy { getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE) }
    
    // Activity result launchers
    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackup(it) }
    }
    
    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_settings)
        
        // Initialize managers
        enhancedBackupManager = EnhancedBackupManager(this)
        themeManager = ThemeManager(this)
        appPrivacyManager = AppPrivacyManager(this)
        
        setupActionBar()
        setupSettingsCategories()
    }
    
    private fun setupActionBar() {
        supportActionBar?.apply {
            title = "7K Launcher Settings"
            setDisplayHomeAsUpEnabled(true)
            elevation = 0f
        }
    }
    
    private fun setupSettingsCategories() {
        setupAppearanceSettings()
        setupHomeScreenSettings()
        setupAppDrawerSettings()
        setupGestureSettings()
        setupPrivacySettings()
        setupPerformanceSettings()
        setupBackupSettings()
        setupAdvancedSettings()
    }
    
    private fun setupAppearanceSettings() {
        val appearanceCard = findViewById<MaterialCardView>(R.id.appearanceCard)
        val themeSpinner = findViewById<Spinner>(R.id.themeSpinner)
        val iconSizeSeeker = findViewById<SeekBar>(R.id.iconSizeSeeker)
        val showLabelsSwitch = findViewById<SwitchMaterial>(R.id.showLabelsSwitch)
        val glassEffectSwitch = findViewById<SwitchMaterial>(R.id.glassEffectSwitch)
        val iconPackButton = findViewById<Button>(R.id.iconPackButton)
        
        // Theme selection
        val themes = listOf("Light", "Dark", "Auto")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner.adapter = themeAdapter
        
        val currentTheme = prefs.getString("theme", "Auto")
        val currentIndex = themes.indexOf(currentTheme)
        if (currentIndex >= 0) themeSpinner.setSelection(currentIndex)
        
        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("theme", themes[position]).apply()
                themeManager.applyTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Icon size
        val iconSize = prefs.getInt("icon_size", 50)
        iconSizeSeeker.progress = iconSize
        iconSizeSeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.edit().putInt("icon_size", progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Show labels
        showLabelsSwitch.isChecked = prefs.getBoolean("show_labels", true)
        showLabelsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_labels", isChecked).apply()
        }
        
        // Glass effects
        glassEffectSwitch.isChecked = prefs.getBoolean("enable_runtime_blur", true)
        glassEffectSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_runtime_blur", isChecked).apply()
        }
        
        // Icon pack selection
        iconPackButton.setOnClickListener {
            startActivity(Intent(this, IconPackSelectionActivity::class.java))
        }
    }
    
    private fun setupHomeScreenSettings() {
        val gridSizeSpinner = findViewById<Spinner>(R.id.gridSizeSpinner)
        val pageIndicatorSwitch = findViewById<SwitchMaterial>(R.id.pageIndicatorSwitch)
        val infiniteScrollSwitch = findViewById<SwitchMaterial>(R.id.infiniteScrollSwitch)
        
        // Grid size options
        val gridSizes = arrayOf("3x4", "4x5", "4x6", "5x6", "5x7")
        val gridAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, gridSizes)
        gridAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSizeSpinner.adapter = gridAdapter
        
        val currentGrid = prefs.getString("grid_size", "4x5")
        val gridIndex = gridSizes.indexOf(currentGrid)
        if (gridIndex >= 0) gridSizeSpinner.setSelection(gridIndex)
        
        gridSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("grid_size", gridSizes[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Page indicator
        pageIndicatorSwitch.isChecked = prefs.getBoolean("show_page_indicator", true)
        pageIndicatorSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_page_indicator", isChecked).apply()
        }
        
        // Infinite scroll
        infiniteScrollSwitch.isChecked = prefs.getBoolean("infinite_scroll", false)
        infiniteScrollSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("infinite_scroll", isChecked).apply()
        }
    }
    
    private fun setupAppDrawerSettings() {
        val drawerStyleSpinner = findViewById<Spinner>(R.id.drawerStyleSpinner)
        val alphabeticalSortSwitch = findViewById<SwitchMaterial>(R.id.alphabeticalSortSwitch)
        val searchHistorySwitch = findViewById<SwitchMaterial>(R.id.searchHistorySwitch)
        val appSuggestionsSwitch = findViewById<SwitchMaterial>(R.id.appSuggestionsSwitch)
        
        // Drawer style
        val drawerStyles = arrayOf("Paged", "Vertical List", "Categories")
        val styleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, drawerStyles)
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        drawerStyleSpinner.adapter = styleAdapter
        
        val currentStyle = prefs.getString("drawer_style", "Paged")
        val styleIndex = drawerStyles.indexOf(currentStyle)
        if (styleIndex >= 0) drawerStyleSpinner.setSelection(styleIndex)
        
        drawerStyleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("drawer_style", drawerStyles[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Alphabetical sort
        alphabeticalSortSwitch.isChecked = prefs.getBoolean("alphabetical_sort", true)
        alphabeticalSortSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alphabetical_sort", isChecked).apply()
        }
        
        // Search history
        searchHistorySwitch.isChecked = prefs.getBoolean("search_history", true)
        searchHistorySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("search_history", isChecked).apply()
        }
        
        // App suggestions
        appSuggestionsSwitch.isChecked = prefs.getBoolean("app_suggestions", true)
        appSuggestionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("app_suggestions", isChecked).apply()
        }
    }
    
    private fun setupGestureSettings() {
        val gestureSettingsButton = findViewById<Button>(R.id.gestureSettingsButton)
        val hapticFeedbackSwitch = findViewById<SwitchMaterial>(R.id.hapticFeedbackSwitch)
        val gestureSensitivitySeeker = findViewById<SeekBar>(R.id.gestureSensitivitySeeker)
        
        gestureSettingsButton.setOnClickListener {
            startActivity(Intent(this, GestureSettingsActivity::class.java))
        }
        
        // Haptic feedback
        hapticFeedbackSwitch.isChecked = prefs.getBoolean("haptic_feedback", true)
        hapticFeedbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("haptic_feedback", isChecked).apply()
        }
        
        // Gesture sensitivity
        val sensitivity = prefs.getInt("gesture_sensitivity", 50)
        gestureSensitivitySeeker.progress = sensitivity
        gestureSensitivitySeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.edit().putInt("gesture_sensitivity", progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupPrivacySettings() {
        val privacyModeSwitch = findViewById<SwitchMaterial>(R.id.privacyModeSwitch)
        val hiddenAppsButton = findViewById<Button>(R.id.hiddenAppsButton)
        val lockedAppsButton = findViewById<Button>(R.id.lockedAppsButton)
        val biometricRequiredSwitch = findViewById<SwitchMaterial>(R.id.biometricRequiredSwitch)
        
        privacyModeSwitch.isChecked = prefs.getBoolean("privacy_mode", false)
        privacyModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("privacy_mode", isChecked).apply()
            appPrivacyManager.togglePrivacyMode()
        }
        
        hiddenAppsButton.setOnClickListener {
            // TODO: Open hidden apps management
            val hiddenApps = prefs.getStringSet("hidden_apps", emptySet())?.size ?: 0
            Toast.makeText(this, "Hidden apps: $hiddenApps", Toast.LENGTH_SHORT).show()
        }
        
        lockedAppsButton.setOnClickListener {
            // TODO: Open locked apps management
            val lockedApps = prefs.getStringSet("locked_apps", emptySet())?.size ?: 0
            Toast.makeText(this, "Locked apps: $lockedApps", Toast.LENGTH_SHORT).show()
        }
        
        // Biometric authentication
        biometricRequiredSwitch.isEnabled = true // Simplified
        biometricRequiredSwitch.isChecked = prefs.getBoolean("require_biometric", true)
        biometricRequiredSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("require_biometric", isChecked).apply()
        }
    }
    
    private fun setupPerformanceSettings() {
        val batteryOptimizationSwitch = findViewById<SwitchMaterial>(R.id.batteryOptimizationSwitch)
        val memoryOptimizationSwitch = findViewById<SwitchMaterial>(R.id.memoryOptimizationSwitch)
        val animationScaleSeeker = findViewById<SeekBar>(R.id.animationScaleSeeker)
        val preloadAppsSwitch = findViewById<SwitchMaterial>(R.id.preloadAppsSwitch)
        
        // Battery optimization
        batteryOptimizationSwitch.isChecked = prefs.getBoolean("battery_optimization", true)
        batteryOptimizationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("battery_optimization", isChecked).apply()
        }
        
        // Memory optimization
        memoryOptimizationSwitch.isChecked = prefs.getBoolean("memory_optimization", true)
        memoryOptimizationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("memory_optimization", isChecked).apply()
        }
        
        // Animation scale
        val animationScale = prefs.getInt("animation_scale", 50)
        animationScaleSeeker.progress = animationScale
        animationScaleSeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.edit().putInt("animation_scale", progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // App preloading
        preloadAppsSwitch.isChecked = prefs.getBoolean("preload_apps", false)
        preloadAppsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("preload_apps", isChecked).apply()
        }
    }
    
    private fun setupBackupSettings() {
        val exportButton = findViewById<Button>(R.id.exportButton)
        val importButton = findViewById<Button>(R.id.importButton)
        val autoBackupSwitch = findViewById<SwitchMaterial>(R.id.autoBackupSwitch)
        val lastBackupText = findViewById<TextView>(R.id.lastBackupText)
        
        exportButton.setOnClickListener {
            createBackupLauncher.launch(enhancedBackupManager.getSuggestedBackupFilename())
        }
        
        importButton.setOnClickListener {
            restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }
        
        // Auto backup
        autoBackupSwitch.isChecked = prefs.getBoolean("auto_backup", false)
        autoBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_backup", isChecked).apply()
            if (isChecked) {
                // TODO: Schedule periodic backups
                Toast.makeText(this, "Auto backup enabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Last backup info
        val lastBackupTime = prefs.getLong("last_backup_time", 0)
        if (lastBackupTime > 0) {
            val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault())
            lastBackupText.text = "Last backup: ${dateFormat.format(java.util.Date(lastBackupTime))}"
        } else {
            lastBackupText.text = "No backup found"
        }
    }
    
    private fun setupAdvancedSettings() {
        val debugModeSwitch = findViewById<SwitchMaterial>(R.id.debugModeSwitch)
        val resetSettingsButton = findViewById<Button>(R.id.resetSettingsButton)
        val aboutButton = findViewById<Button>(R.id.aboutButton)
        
        // Debug mode
        debugModeSwitch.isChecked = prefs.getBoolean("debug_mode", false)
        debugModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_mode", isChecked).apply()
            Toast.makeText(this, if (isChecked) "Debug mode enabled" else "Debug mode disabled", Toast.LENGTH_SHORT).show()
        }
        
        // Reset settings
        resetSettingsButton.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset Settings")
                .setMessage("This will reset all launcher settings to default. Are you sure?")
                .setPositiveButton("Reset") { _, _ ->
                    resetAllSettings()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // About
        aboutButton.setOnClickListener {
            showAboutDialog()
        }
    }
    
    private fun exportBackup(uri: Uri) {
        lifecycleScope.launch {
            val success = enhancedBackupManager.exportBackup(uri)
            if (success) {
                prefs.edit().putLong("last_backup_time", System.currentTimeMillis()).apply()
                Toast.makeText(this@EnhancedSettingsActivity, "Backup exported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@EnhancedSettingsActivity, "Failed to export backup", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun importBackup(uri: Uri) {
        lifecycleScope.launch {
            val isValid = enhancedBackupManager.validateBackup(uri)
            if (!isValid) {
                Toast.makeText(this@EnhancedSettingsActivity, "Invalid backup file", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val backupInfo = enhancedBackupManager.getBackupInfo(uri)
            val message = buildString {
                append("Restore backup from ")
                append(java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(backupInfo?.timestamp ?: 0)))
                append("?\n\nThis backup contains:")
                append("\n• ${backupInfo?.dockAppsCount ?: 0} dock apps")
                append("\n• ${backupInfo?.sidebarAppsCount ?: 0} sidebar apps")
                append("\n• ${backupInfo?.hiddenAppsCount ?: 0} hidden apps")
                append("\n• ${backupInfo?.customNamesCount ?: 0} custom app names")
                if (backupInfo?.hasCustomWallpaper == true) append("\n• Custom wallpaper")
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this@EnhancedSettingsActivity)
                .setTitle("Restore Backup")
                .setMessage(message)
                .setPositiveButton("Restore") { _, _ ->
                    lifecycleScope.launch {
                        val success = enhancedBackupManager.importBackup(uri)
                        if (success) {
                            Toast.makeText(this@EnhancedSettingsActivity, "Backup restored successfully. Please restart the launcher.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@EnhancedSettingsActivity, "Failed to restore backup", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun resetAllSettings() {
        prefs.edit().clear().apply()
        Toast.makeText(this, "Settings reset. Please restart the launcher.", Toast.LENGTH_LONG).show()
        finish()
    }
    
    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About 7K Launcher")
            .setMessage("""
                Version: $version
                
                7K Launcher - A modern, customizable Android launcher with glass UI effects, gesture support, and privacy features.
                
                Features:
                • Glass UI with blur effects
                • Comprehensive gesture system
                • Icon pack support
                • App hiding and locking
                • Advanced backup/restore
                • Performance optimization
                • Customizable themes
                
                Developed with ❤️ for Android
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}