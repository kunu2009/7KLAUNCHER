package com.sevenk.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sevenk.launcher.backup.SimpleBackupManager
import com.sevenk.launcher.backup.BackupManagerContract
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE) }
    private val backupManager: BackupManagerContract by lazy { SimpleBackupManager(this) }

    // Activity result launchers for backup/restore
    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackup(it) }
    }
    
    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(it) }
    }

    // Wallpaper picker (from gallery)
    private val pickWallpaperLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { applyWallpaperFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val swShowDock = findViewById<CompoundButton>(R.id.swShowDock)
        val swShowSidebar = findViewById<CompoundButton>(R.id.swShowSidebar)
        val swShowLabels = findViewById<CompoundButton>(R.id.swShowLabels)
        val swHideSearch = findViewById<CompoundButton>(R.id.swHideSearch)
        val swSwipeClose = findViewById<CompoundButton>(R.id.swSwipeClose)
        val swPowerSaver = findViewById<CompoundButton>(R.id.swPowerSaver)
        val tvColumns = findViewById<TextView>(R.id.tvColumnsValue)
        val sbColumns = findViewById<SeekBar>(R.id.sbColumns)
        val tvIconSize = findViewById<TextView>(R.id.tvIconSizeValue)
        val sbIconSize = findViewById<SeekBar>(R.id.sbIconSize)
        // New Dock customization controls
        val tvDockIconSize = findViewById<TextView>(R.id.tvDockIconSizeValue)
        val sbDockIconSize = findViewById<SeekBar>(R.id.sbDockIconSize)
        val tvDockLength = findViewById<TextView>(R.id.tvDockLengthValue)
        val sbDockLength = findViewById<SeekBar>(R.id.sbDockLength)
        val spDockPosition = findViewById<Spinner>(R.id.spDockPosition)
        // Sidebar customization
        val tvSidebarIconSize = findViewById<TextView>(R.id.tvSidebarIconSizeValue)
        val sbSidebarIconSize = findViewById<SeekBar>(R.id.sbSidebarIconSize)
        val tvSidebarLength = findViewById<TextView>(R.id.tvSidebarLengthValue)
        val sbSidebarLength = findViewById<SeekBar>(R.id.sbSidebarLength)
        // Home/Folder columns
        val tvHomeCols = findViewById<TextView>(R.id.tvHomeColumnsValue)
        val sbHomeCols = findViewById<SeekBar>(R.id.sbHomeColumns)
        val tvFolderCols = findViewById<TextView>(R.id.tvFolderColumnsValue)
        val sbFolderCols = findViewById<SeekBar>(R.id.sbFolderColumns)
        // Clock toggle
        val swShowClock = findViewById<CompoundButton>(R.id.swShowClock)
        val btnDone = findViewById<Button>(R.id.btnDone)
        val btnManageDock = findViewById<Button>(R.id.btnManageDockApps)
        val btnManageSidebar = findViewById<Button>(R.id.btnManageSidebarApps)
        // Wallpaper & Storage
        val btnWallpaperGallery = findViewById<Button>(R.id.btnWallpaperGallery)
        val btnWallpaperPreloaded = findViewById<Button>(R.id.btnWallpaperPreloaded)
        val tvStorageSummary = findViewById<TextView>(R.id.tvStorageSummary)
        val btnClearCache = findViewById<Button>(R.id.btnClearCache)
        val btnClearWebData = findViewById<Button>(R.id.btnClearWebData)
        val btnClearTemp = findViewById<Button>(R.id.btnClearTemp)
        // New settings controls
        val swDynamicColor = findViewById<CompoundButton>(R.id.swDynamicColor)
        val spAccentColor = findViewById<Spinner>(R.id.spAccentColor)
        val spLabelLines = findViewById<Spinner>(R.id.spLabelLines)
        val spLabelFontSize = findViewById<Spinner>(R.id.spLabelFontSize)
        val spDrawerSortOrder = findViewById<Spinner>(R.id.spDrawerSortOrder)
        val swDrawerAlphabetHeaders = findViewById<CompoundButton>(R.id.swDrawerAlphabetHeaders)
        val sbDockOpacity = findViewById<SeekBar>(R.id.sbDockOpacity)
        val tvDockOpacityValue = findViewById<TextView>(R.id.tvDockOpacityValue)
        val swPageDots = findViewById<CompoundButton>(R.id.swPageDots)
        val swParallax = findViewById<CompoundButton>(R.id.swParallax)
        val swHomeLock = findViewById<CompoundButton>(R.id.swHomeLock)
        val swSearchApps = findViewById<CompoundButton>(R.id.swSearchApps)
        val swSearchContacts = findViewById<CompoundButton>(R.id.swSearchContacts)
        val swSearchWeb = findViewById<CompoundButton>(R.id.swSearchWeb)
        val btnManageHiddenApps = findViewById<Button>(R.id.btnManageHiddenApps)
        val spAutoClearCache = findViewById<Spinner>(R.id.spAutoClearCache)
        
        // New competitive features
        val btnIconPacks = findViewById<Button>(R.id.btnIconPacks)
        val btnGestures = findViewById<Button>(R.id.btnGestures)
        val btnPrivacy = findViewById<Button>(R.id.btnPrivacy)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnRestore = findViewById<Button>(R.id.btnRestore)
        val btnSystemMonitor = findViewById<Button>(R.id.btnSystemMonitor)

        // Load
        swShowDock.isChecked = prefs.getBoolean("show_dock", true)
        swShowSidebar.isChecked = prefs.getBoolean("show_sidebar", true)
        swShowLabels.isChecked = prefs.getBoolean("show_labels", true)
        swHideSearch.isChecked = prefs.getBoolean("hide_search_box", false)
        swSwipeClose.isChecked = prefs.getBoolean("enable_swipe_close", false)
        swPowerSaver.isChecked = prefs.getBoolean("launcher_power_saver", false)
        // Theme & labels
        swDynamicColor.isChecked = prefs.getBoolean("dynamic_color", true)
        val accentValues = resources.getStringArray(R.array.accent_color_values)
        val savedAccent = prefs.getString("accent_color", "SYSTEM") ?: "SYSTEM"
        val accentIndex = accentValues.indexOfFirst { it == savedAccent }.let { if (it < 0) 0 else it }
        spAccentColor.setSelection(accentIndex, false)
        spAccentColor.isEnabled = !swDynamicColor.isChecked
        // Label lines (1 or 2)
        val savedLines = prefs.getInt("label_lines", 1).coerceIn(1, 2)
        spLabelLines.setSelection(if (savedLines == 1) 0 else 1, false)
        // Label font size (0 small, 1 medium, 2 large)
        val savedFont = prefs.getInt("label_font_size", 1).coerceIn(0, 2)
        spLabelFontSize.setSelection(savedFont, false)
        val columns = prefs.getInt("drawer_columns", 5).coerceIn(3, 7)
        tvColumns.text = columns.toString()
        sbColumns.max = 7
        sbColumns.min = 3
        sbColumns.progress = columns
        val iconSize = prefs.getInt("icon_size", 0).coerceIn(0, 2) // 0 small,1 medium,2 large
        tvIconSize.text = when (iconSize) { 0->"Small"; 1->"Medium"; else->"Large" }
        sbIconSize.max = 2
        sbIconSize.min = 0
        sbIconSize.progress = iconSize
        // Drawer extras
        val sortOrder = prefs.getString("drawer_sort_order", "az") ?: "az"
        spDrawerSortOrder.setSelection(when (sortOrder) { "most"->1; "recent"->2; else->0 }, false)
        swDrawerAlphabetHeaders.isChecked = prefs.getBoolean("drawer_alphabet_headers", true)

        // Dock icon size
        val dockIconSize = prefs.getInt("dock_icon_size", 1).coerceIn(0, 2)
        tvDockIconSize.text = when (dockIconSize) { 0->"Small"; 1->"Medium"; else->"Large" }
        sbDockIconSize.max = 2
        sbDockIconSize.min = 0
        sbDockIconSize.progress = dockIconSize
        // Dock length (item count)
        val dockLength = prefs.getInt("dock_length", 5).coerceIn(3, 12)
        tvDockLength.text = dockLength.toString()
        sbDockLength.max = 12
        sbDockLength.min = 3
        sbDockLength.progress = dockLength
        // Dock position spinner (0 Left, 1 Center, 2 Right)
        val dockPos = when (prefs.getString("dock_position", "right")) {
            "left" -> 0
            "center" -> 1
            else -> 2
        }
        spDockPosition.setSelection(dockPos, false)
        // Dock opacity
        val dockOpacity = prefs.getInt("dock_opacity", 100).coerceIn(0, 100)
        sbDockOpacity.max = 100
        sbDockOpacity.progress = dockOpacity
        tvDockOpacityValue.text = "${dockOpacity}%"

        // Sidebar icon size
        val sidebarIcon = prefs.getInt("sidebar_icon_size", 1).coerceIn(0, 2)
        tvSidebarIconSize.text = when (sidebarIcon) { 0->"Small"; 1->"Medium"; else->"Large" }
        sbSidebarIconSize.max = 2
        sbSidebarIconSize.min = 0
        sbSidebarIconSize.progress = sidebarIcon
        // Sidebar length (visible items cap)
        val sidebarLen = prefs.getInt("sidebar_length", 12).coerceIn(4, 20)
        tvSidebarLength.text = sidebarLen.toString()
        sbSidebarLength.max = 20
        sbSidebarLength.min = 4
        sbSidebarLength.progress = sidebarLen

        // Home/Folder columns
        val homeCols = prefs.getInt("home_columns", 4).coerceIn(3, 6)
        tvHomeCols.text = homeCols.toString()
        sbHomeCols.max = 6
        sbHomeCols.min = 3
        sbHomeCols.progress = homeCols

        val folderCols = prefs.getInt("folder_columns", 4).coerceIn(3, 6)
        tvFolderCols.text = folderCols.toString()
        sbFolderCols.max = 6
        sbFolderCols.min = 3
        sbFolderCols.progress = folderCols

        // Clock toggle
        swShowClock.isChecked = prefs.getBoolean("show_clock", true)
        // Home experience
        swPageDots.isChecked = prefs.getBoolean("show_page_dots", true)
        swParallax.isChecked = prefs.getBoolean("wallpaper_parallax", false)
        swHomeLock.isChecked = prefs.getBoolean("home_locked", false)
        // Search sources
        swSearchApps.isChecked = prefs.getBoolean("search_src_apps", true)
        swSearchContacts.isChecked = prefs.getBoolean("search_src_contacts", false)
        swSearchWeb.isChecked = prefs.getBoolean("search_src_web", true)
        // Auto clear cache threshold
        val thresholds = resources.getIntArray(R.array.auto_clear_cache_values_mb)
        val savedThresh = prefs.getInt("auto_clear_cache_mb", 50)
        val threshIndex = thresholds.indexOfFirst { it == savedThresh }.let { if (it < 0) 2 else it }
        spAutoClearCache.setSelection(threshIndex, false)

        // Save handlers
        val toggleSaver = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            when (buttonView.id) {
                R.id.swShowDock -> prefs.edit().putBoolean("show_dock", isChecked).apply()
                R.id.swShowSidebar -> prefs.edit().putBoolean("show_sidebar", isChecked).apply()
                R.id.swShowLabels -> prefs.edit().putBoolean("show_labels", isChecked).apply()
                R.id.swHideSearch -> prefs.edit().putBoolean("hide_search_box", isChecked).apply()
                R.id.swSwipeClose -> prefs.edit().putBoolean("enable_swipe_close", isChecked).apply()
                R.id.swPowerSaver -> prefs.edit().putBoolean("launcher_power_saver", isChecked).apply()
            }
        }
        swShowDock.setOnCheckedChangeListener(toggleSaver)
        swShowSidebar.setOnCheckedChangeListener(toggleSaver)
        swShowLabels.setOnCheckedChangeListener(toggleSaver)
        swHideSearch.setOnCheckedChangeListener(toggleSaver)
        swSwipeClose.setOnCheckedChangeListener(toggleSaver)
        swPowerSaver.setOnCheckedChangeListener(toggleSaver)
        swShowClock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_clock", isChecked).apply()
        }

        // New settings listeners
        swDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dynamic_color", isChecked).apply()
            spAccentColor.isEnabled = !isChecked
        }
        spAccentColor.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val value = accentValues[position]
                prefs.edit().putString("accent_color", value).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        spLabelLines.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val lines = if (position == 0) 1 else 2
                prefs.edit().putInt("label_lines", lines).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        spLabelFontSize.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt("label_font_size", position.coerceIn(0, 2)).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        spDrawerSortOrder.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val value = when (position) { 1->"most"; 2->"recent"; else->"az" }
                prefs.edit().putString("drawer_sort_order", value).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        swDrawerAlphabetHeaders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("drawer_alphabet_headers", isChecked).apply()
        }
        sbDockOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress.coerceIn(0, 100)
                tvDockOpacityValue.text = "${v}%"
                prefs.edit().putInt("dock_opacity", v).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        swPageDots.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_page_dots", isChecked).apply()
        }
        swParallax.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wallpaper_parallax", isChecked).apply()
        }
        swHomeLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("home_locked", isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "Home layout locked" else "Home layout unlocked",
                Toast.LENGTH_SHORT
            ).show()
        }
        swSearchApps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("search_src_apps", isChecked).apply()
        }
        swSearchContacts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("search_src_contacts", isChecked).apply()
        }
        swSearchWeb.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("search_src_web", isChecked).apply()
        }
        btnManageHiddenApps.setOnClickListener {
            openManageListDialog("hidden_packages", title = "Manage Hidden Apps")
        }
        spAutoClearCache.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val thresholdsLocal = resources.getIntArray(R.array.auto_clear_cache_values_mb)
                val mb = thresholdsLocal.getOrNull(position) ?: 0
                prefs.edit().putInt("auto_clear_cache_mb", mb).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        sbColumns.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(3, 7)
                tvColumns.text = value.toString()
                prefs.edit().putInt("drawer_columns", value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbIconSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val label = when (progress.coerceIn(0, 2)) { 0->"Small"; 1->"Medium"; else->"Large" }
                tvIconSize.text = label
                prefs.edit().putInt("icon_size", progress.coerceIn(0, 2)).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbDockIconSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceIn(0, 2)
                tvDockIconSize.text = when (clamped) { 0->"Small"; 1->"Medium"; else->"Large" }
                prefs.edit().putInt("dock_icon_size", clamped).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbDockLength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(3, 12)
                tvDockLength.text = value.toString()
                prefs.edit().putInt("dock_length", value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        spDockPosition.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val value = when (position) { 0->"left"; 1->"center"; else->"right" }
                prefs.edit().putString("dock_position", value).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        sbSidebarIconSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceIn(0, 2)
                tvSidebarIconSize.text = when (clamped) { 0->"Small"; 1->"Medium"; else->"Large" }
                prefs.edit().putInt("sidebar_icon_size", clamped).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbSidebarLength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(4, 20)
                tvSidebarLength.text = value.toString()
                prefs.edit().putInt("sidebar_length", value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbHomeCols.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(3, 6)
                tvHomeCols.text = value.toString()
                prefs.edit().putInt("home_columns", value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbFolderCols.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(3, 6)
                tvFolderCols.text = value.toString()
                prefs.edit().putInt("folder_columns", value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnDone.setOnClickListener { finish() }

        btnManageDock.setOnClickListener { openManageListDialog("dock_packages", title = "Manage Dock Apps") }
        btnManageSidebar.setOnClickListener { openManageListDialog("sidebar_packages", title = "Manage Sidebar Apps") }
        
        // New competitive feature buttons
        btnIconPacks.setOnClickListener {
            startActivity(android.content.Intent(this, IconPackSelectionActivity::class.java))
        }
        
        btnGestures.setOnClickListener {
            startActivity(android.content.Intent(this, GestureSettingsActivity::class.java))
        }
        
        btnPrivacy.setOnClickListener {
            startActivity(android.content.Intent(this, AppPrivacyActivity::class.java))
        }
        
        btnBackup.setOnClickListener {
            performBackup()
        }
        
        btnRestore.setOnClickListener {
            performRestore()
        }
        
        btnSystemMonitor.setOnClickListener {
            startActivity(Intent(this, SystemMonitorActivity::class.java))
        }

        // Wallpaper actions
        btnWallpaperGallery.setOnClickListener {
            pickWallpaperLauncher.launch("image/*")
        }
        btnWallpaperPreloaded.setOnClickListener {
            showWallpaperStorePicker()
        }

        // Storage summary and actions
        tvStorageSummary.text = buildStorageSummary()
        btnClearCache.setOnClickListener {
            clearAppCache()
            tvStorageSummary.text = buildStorageSummary()
            Toast.makeText(this, getString(R.string.clear_cache), Toast.LENGTH_SHORT).show()
        }
        btnClearWebData.setOnClickListener {
            clearWebData()
            tvStorageSummary.text = buildStorageSummary()
            Toast.makeText(this, getString(R.string.clear_web_data), Toast.LENGTH_SHORT).show()
        }
        btnClearTemp.setOnClickListener {
            clearTempFiles()
            tvStorageSummary.text = buildStorageSummary()
            Toast.makeText(this, getString(R.string.clear_temp_files), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openManageListDialog(key: String, title: String) {
        val pm = packageManager
        val main = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val entries = pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedBy { it.second }
        val packages = entries.map { it.first }
        val current = loadPackageList(key).toMutableSet()

        showGlassMultiSelectSheet(
            title = title,
            entries = entries,
            initiallySelected = current,
            onSave = { selected ->
                val ordered = packages.filter { selected.contains(it) }
                savePackageList(key, ordered)
            }
        )
    }

    private fun loadPackageList(key: String): List<String> {
        val raw = prefs.getString(key, "") ?: ""
        return raw.split(';').mapNotNull { val t = it.trim(); if (t.isEmpty()) null else t }
    }

    private fun savePackageList(key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString(";"))
            .apply()
    }
    
    private fun performBackup() {
        val filename = backupManager.generateBackupFilename()
        createBackupLauncher.launch(filename)
    }
    
    private fun performRestore() {
        restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
    }
    
    private fun exportBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backup = backupManager.createBackup()
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    val success = backupManager.exportBackup(backup, outputStream)
                    if (success) {
                        Toast.makeText(this@SettingsActivity, "Backup created successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SettingsActivity, "Failed to create backup", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error creating backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun importBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val backup = backupManager.importBackup(inputStream)
                    val result = backupManager.restoreBackup(backup!!)
                    when (result) {
                        is BackupManagerContract.BackupRestoreResult.Success -> {
                            Toast.makeText(this@SettingsActivity, "Backup restored successfully. Please restart the launcher.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is BackupManagerContract.BackupRestoreResult.Error -> {
                            Toast.makeText(this@SettingsActivity, "Failed to restore backup: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error restoring backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Wallpaper & Storage helpers ---
    private fun applyWallpaperFromUri(uri: Uri) {
        try {
            val wm = WallpaperManager.getInstance(this)
            contentResolver.openInputStream(uri)?.use { stream ->
                wm.setStream(stream)
            }
            prefs.edit()
                .remove("preloaded_wallpaper_asset")
                .remove("custom_bg_uri")
                .apply()
            Toast.makeText(this, getString(R.string.wallpaper_applied), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_applying_wallpaper), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWallpaperStorePicker() {
        val assetsList = try {
            assets.list("")
                ?.filter { name ->
                    val lower = name.lowercase(Locale.ROOT)
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                }
                ?.sortedWith(compareBy<String> { fileName ->
                    fileName.substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE
                }.thenBy { it })
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }

        if (assetsList.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_preloaded_wallpapers), Toast.LENGTH_SHORT).show()
            return
        }

        val labels = assetsList.map { fileName ->
            if (fileName.equals("7.jpeg", ignoreCase = true)) "$fileName (default)" else fileName
        }.toTypedArray()

        val actions = labels.mapIndexed { which, label ->
            label to {
                val selected = assetsList[which]
                applyWallpaperFromAsset(selected)
            }
        }
        showGlassActionSheet(getString(R.string.choose_preloaded), actions)
    }

    private fun showGlassActionSheet(title: String, actions: List<Pair<String, () -> Unit>>) {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        })

        actions.forEach { (label, onClick) ->
            val item = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundResource(R.drawable.glass_panel)
                foreground = AppCompatResources.getDrawable(this@SettingsActivity, android.R.drawable.list_selector_background)
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

        root.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { sheet.dismiss() }
        })

        sheet.setContentView(root)
        sheet.show()
    }

    private fun showGlassMultiSelectSheet(
        title: String,
        entries: List<Pair<String, String>>,
        initiallySelected: Set<String>,
        onSave: (Set<String>) -> Unit
    ) {
        val selected = initiallySelected.toMutableSet()
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(8))
        })

        val summary = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(dp(8), 0, dp(8), dp(10))
        }
        root.addView(summary)

        fun updateSummary() {
            summary.text = "Selected ${selected.size} of ${entries.size}"
        }

        entries.forEach { (pkg, label) ->
            val item = TextView(this).apply {
                textSize = 14f
                setPadding(dp(16), dp(12), dp(16), dp(12))
                foreground = AppCompatResources.getDrawable(this@SettingsActivity, android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
            }

            fun renderState() {
                val isChecked = selected.contains(pkg)
                item.text = if (isChecked) "✓ $label" else label
                item.setTextColor(if (isChecked) 0xFF80CBC4.toInt() else 0xFFFFFFFF.toInt())
                item.setBackgroundResource(R.drawable.glass_panel)
            }

            item.setOnClickListener {
                if (selected.contains(pkg)) selected.remove(pkg) else selected.add(pkg)
                renderState()
                updateSummary()
            }
            renderState()

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            root.addView(item, lp)
        }

        updateSummary()

        root.addView(TextView(this).apply {
            text = "Save"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(this@SettingsActivity, android.R.drawable.list_selector_background)
            setOnClickListener {
                onSave(selected)
                sheet.dismiss()
            }
        })

        root.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { sheet.dismiss() }
        })

        sheet.setContentView(root)
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applyWallpaperFromAsset(assetName: String) {
        try {
            val wm = WallpaperManager.getInstance(this)
            assets.open(assetName).use { stream ->
                wm.setStream(stream)
            }
            prefs.edit()
                .putString("preloaded_wallpaper_asset", assetName)
                .remove("custom_bg_uri")
                .putBoolean("default_wallpaper_initialized", true)
                .apply()
            Toast.makeText(this, getString(R.string.wallpaper_applied), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.error_applying_wallpaper), Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildStorageSummary(): CharSequence {
        val appSize = formatSizeSafe(File(applicationInfo.sourceDir).length())
        val userData = formatSizeSafe(estimateUserDataBytes())
        val cache = formatSizeSafe(estimateCacheBytes())
        return getString(R.string.storage_summary, appSize, userData, cache)
    }

    private fun estimateCacheBytes(): Long {
        var total = 0L
        total += dirSize(cacheDir)
        externalCacheDir?.let { total += dirSize(it) }
        return total
    }

    private fun estimateUserDataBytes(): Long {
        var total = 0L
        total += dirSize(filesDir)
        try { total += dirSize(File(filesDir.parentFile, "shared_prefs")) } catch (_: Throwable) {}
        try { total += dirSize(File(filesDir.parentFile, "databases")) } catch (_: Throwable) {}
        externalMediaDirs?.forEach { total += dirSize(it) }
        getExternalFilesDir(null)?.let { total += dirSize(it) }
        return total
    }

    private fun dirSize(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) return f.length()
        var sum = 0L
        f.listFiles()?.forEach { child ->
            sum += dirSize(child)
        }
        return sum
    }

    private fun formatSizeSafe(bytes: Long): String {
        val unit = 1024.0
        if (bytes <= 0) return "0 B"
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(4)
        return if (exp == 0) "$bytes B" else {
            val pre = "KMGTPE"[exp - 1]
            String.format(Locale.getDefault(), "%.2f %sB", bytes / unit.pow(exp.toDouble()), pre)
        }
    }

    private fun clearAppCache() {
        try { cacheDir.deleteRecursively() } catch (_: Throwable) {}
        try { externalCacheDir?.deleteRecursively() } catch (_: Throwable) {}
    }

    private fun clearWebData() {
        try {
            WebStorage.getInstance().deleteAllData()
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        } catch (_: Throwable) {}
    }

    private fun clearTempFiles() {
        try { File(cacheDir, "temp").deleteRecursively() } catch (_: Throwable) {}
    }
}
