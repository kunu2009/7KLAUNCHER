package com.sevenk.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        val iconSize = prefs.getInt("icon_size", 1).coerceIn(0, 2)
        iconSizeSeeker.progress = iconSize
        iconSizeSeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mapped = progress.coerceIn(0, 2)
                    prefs.edit().putInt("icon_size", mapped).apply()
                    val label = when (mapped) {
                        0 -> "Small"
                        2 -> "Large"
                        else -> "Medium"
                    }
                    Toast.makeText(this@EnhancedSettingsActivity, "Icon size: $label", Toast.LENGTH_SHORT).show()
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
        val privacyCenterButton = findViewById<Button>(R.id.privacyCenterButton)
        val lockTimeoutButton = findViewById<Button>(R.id.lockTimeoutButton)
        val authModeButton = findViewById<Button>(R.id.authModeButton)
        val biometricRequiredSwitch = findViewById<SwitchMaterial>(R.id.biometricRequiredSwitch)

        privacyModeSwitch.isChecked = appPrivacyManager.isPrivacyModeEnabled()
        privacyModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            appPrivacyManager.setPrivacyModeEnabled(isChecked)
            prefs.edit().putBoolean("privacy_mode", isChecked).apply()
            refreshPrivacyUiSummary()
        }

        hiddenAppsButton.setOnClickListener {
            startActivity(Intent(this, com.sevenk.launcher.AppPrivacyActivity::class.java))
        }

        lockedAppsButton.setOnClickListener {
            startActivity(Intent(this, com.sevenk.launcher.AppPrivacyActivity::class.java))
        }

        privacyCenterButton.setOnClickListener {
            startActivity(Intent(this, com.sevenk.launcher.ecosystem.PrivacyShieldActivity::class.java))
        }

        lockTimeoutButton.setOnClickListener {
            showLockTimeoutDialog()
        }

        authModeButton.setOnClickListener {
            showAuthModeDialog()
        }

        // Biometric authentication
        biometricRequiredSwitch.isEnabled = true // Simplified
        biometricRequiredSwitch.isChecked = prefs.getBoolean("require_biometric", true)
        biometricRequiredSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("require_biometric", isChecked).apply()
            if (!isChecked && prefs.getString("app_lock_auth_mode", "biometric_or_pin") == "biometric_only") {
                prefs.edit().putString("app_lock_auth_mode", "pin_only").apply()
            }
            refreshPrivacyUiSummary()
        }

        refreshPrivacyUiSummary()
    }

    private fun showLockTimeoutDialog() {
        val options = arrayOf("Immediate", "30 seconds", "1 minute", "5 minutes")
        val values = longArrayOf(0L, 30_000L, 60_000L, 300_000L)
        val selectedActions = options.mapIndexed { which, label ->
            label to {
                prefs.edit().putLong("app_lock_timeout_ms", values[which]).apply()
                refreshPrivacyUiSummary()
            }
        }
        showGlassActionSheet("App Lock Timeout", selectedActions)
    }

    private fun showAuthModeDialog() {
        val labels = arrayOf("Biometric + PIN fallback", "Biometric only", "PIN only")
        val values = arrayOf("biometric_or_pin", "biometric_only", "pin_only")
        val actions = labels.mapIndexed { which, label ->
            label to {
                prefs.edit().putString("app_lock_auth_mode", values[which]).apply()
                if (values[which] == "biometric_only") {
                    prefs.edit().putBoolean("require_biometric", true).apply()
                    findViewById<SwitchMaterial>(R.id.biometricRequiredSwitch)?.isChecked = true
                }
                refreshPrivacyUiSummary()
            }
        }
        showGlassActionSheet("Authentication Mode", actions)
    }

    private fun showGlassActionSheet(title: String, actions: List<Pair<String, () -> Unit>>) {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        root.addView(titleView)

        actions.forEach { (label, onClick) ->
            val item = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundResource(R.drawable.glass_panel)
                foreground = AppCompatResources.getDrawable(this@EnhancedSettingsActivity, android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    sheet.dismiss()
                    onClick.invoke()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            root.addView(item, lp)
        }

        val cancel = TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = android.view.Gravity.CENTER
            setOnClickListener { sheet.dismiss() }
        }
        root.addView(cancel)

        sheet.setContentView(root)
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshPrivacyUiSummary() {
        val hiddenCount = appPrivacyManager.getHiddenApps().size
        val lockedCount = appPrivacyManager.getLockedApps().size
        findViewById<Button>(R.id.hiddenAppsButton)?.text = "Manage Hidden Apps ($hiddenCount)"
        findViewById<Button>(R.id.lockedAppsButton)?.text = "Manage Locked Apps ($lockedCount)"

        val timeout = prefs.getLong("app_lock_timeout_ms", 0L)
        val timeoutLabel = when (timeout) {
            30_000L -> "30 seconds"
            60_000L -> "1 minute"
            300_000L -> "5 minutes"
            else -> "Immediate"
        }
        findViewById<Button>(R.id.lockTimeoutButton)?.text = "Lock Timeout: $timeoutLabel"

        val authMode = prefs.getString("app_lock_auth_mode", "biometric_or_pin") ?: "biometric_or_pin"
        val authLabel = when (authMode) {
            "biometric_only" -> "Biometric only"
            "pin_only" -> "PIN only"
            else -> "Biometric + PIN"
        }
        findViewById<Button>(R.id.authModeButton)?.text = "Authentication Mode: $authLabel"
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
            showGlassMessageSheet(
                title = "Reset Settings",
                message = "This will reset all launcher settings to default. Are you sure?",
                primaryLabel = "Reset",
                onPrimary = { resetAllSettings() },
                secondaryLabel = "Cancel"
            )
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
            
            showGlassMessageSheet(
                title = "Restore Backup",
                message = message,
                primaryLabel = "Restore",
                onPrimary = {
                    lifecycleScope.launch {
                        val success = enhancedBackupManager.importBackup(uri)
                        if (success) {
                            Toast.makeText(this@EnhancedSettingsActivity, "Backup restored successfully. Please restart the launcher.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@EnhancedSettingsActivity, "Failed to restore backup", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                secondaryLabel = "Cancel"
            )
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
        
        showGlassMessageSheet(
            title = "About 7K Launcher",
            message = """
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
            """.trimIndent(),
            primaryLabel = "OK"
        )
    }

    private fun showGlassMessageSheet(
        title: String,
        message: String,
        primaryLabel: String,
        onPrimary: (() -> Unit)? = null,
        secondaryLabel: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        root.addView(titleView)

        val messageView = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(0xFFEAF6FF.toInt())
            setBackgroundResource(R.drawable.glass_panel)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val messageLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        root.addView(messageView, messageLp)

        val primary = TextView(this).apply {
            text = primaryLabel
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(this@EnhancedSettingsActivity, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                sheet.dismiss()
                onPrimary?.invoke()
            }
        }
        val primaryLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        root.addView(primary, primaryLp)

        if (!secondaryLabel.isNullOrBlank()) {
            val secondary = TextView(this).apply {
                text = secondaryLabel
                textSize = 14f
                setTextColor(0xFF80CBC4.toInt())
                setPadding(dp(16), dp(12), dp(16), dp(12))
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    sheet.dismiss()
                    onSecondary?.invoke()
                }
            }
            root.addView(secondary)
        }

        sheet.setContentView(root)
        sheet.show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshPrivacyUiSummary()
    }
}