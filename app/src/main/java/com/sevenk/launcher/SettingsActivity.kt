package com.sevenk.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sevenk.launcher.backup.SimpleBackupManager
import com.sevenk.launcher.backup.BackupManagerContract
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val swShowDock = findViewById<Switch>(R.id.swShowDock)
        val swShowSidebar = findViewById<Switch>(R.id.swShowSidebar)
        val swShowLabels = findViewById<Switch>(R.id.swShowLabels)
        val swHideSearch = findViewById<Switch>(R.id.swHideSearch)
        val swSwipeClose = findViewById<Switch>(R.id.swSwipeClose)
        val swPowerSaver = findViewById<Switch>(R.id.swPowerSaver)
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
        val swShowClock = findViewById<Switch>(R.id.swShowClock)
        val btnDone = findViewById<Button>(R.id.btnDone)
        val btnManageDock = findViewById<Button>(R.id.btnManageDockApps)
        val btnManageSidebar = findViewById<Button>(R.id.btnManageSidebarApps)
        
        // New competitive features
        val btnIconPacks = findViewById<Button>(R.id.btnIconPacks)
        val btnGestures = findViewById<Button>(R.id.btnGestures)
        val btnPrivacy = findViewById<Button>(R.id.btnPrivacy)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnRestore = findViewById<Button>(R.id.btnRestore)

        // Load
        swShowDock.isChecked = prefs.getBoolean("show_dock", true)
        swShowSidebar.isChecked = prefs.getBoolean("show_sidebar", true)
        swShowLabels.isChecked = prefs.getBoolean("show_labels", true)
        swHideSearch.isChecked = prefs.getBoolean("hide_search_box", false)
        swSwipeClose.isChecked = prefs.getBoolean("enable_swipe_close", false)
        swPowerSaver.isChecked = prefs.getBoolean("launcher_power_saver", false)
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
        val labels = entries.map { it.second }.toTypedArray()
        val current = loadPackageList(key).toMutableSet()
        val checked = packages.map { current.contains(it) }.toBooleanArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = packages[which]
                if (isChecked) current.add(pkg) else current.remove(pkg)
            }
            .setPositiveButton("Save") { d, _ ->
                // Save in label order for consistency
                val ordered = packages.filter { current.contains(it) }
                savePackageList(key, ordered)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                        else -> {
                            Toast.makeText(this@SettingsActivity, "Unknown result from backup restore", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error restoring backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
