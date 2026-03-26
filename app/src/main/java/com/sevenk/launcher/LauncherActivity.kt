package com.sevenk.launcher

import android.app.WallpaperManager
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle as AndroidBundle
import android.os.Build
import android.os.Bundle
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.view.GestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.content.IntentFilter
import android.view.inputmethod.InputMethodManager
import android.app.ActivityManager
import android.widget.FrameLayout
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.sevenk.launcher.optimization.AdvancedBatteryOptimizer
import com.sevenk.launcher.optimization.AdvancedRAMOptimizer
import com.sevenk.launcher.optimization.AdvancedCPUOptimizer
import com.sevenk.launcher.optimization.LauncherSpecificOptimizer
import com.sevenk.launcher.optimization.PerformanceMonitor
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.sevenk.launcher.iconpack.IconPackHelper
import android.widget.Toast
import kotlin.math.roundToInt
import java.io.File
import com.yalantis.ucrop.UCrop
import android.view.DragEvent
import android.content.ClipDescription
import android.view.animation.DecelerateInterpolator
import com.sevenk.launcher.util.Perf
import com.sevenk.launcher.ui.glass.GlassEffectHelper
import com.sevenk.launcher.gestures.EnhancedGestureManager
import com.sevenk.launcher.backup.EnhancedBackupManager
import com.sevenk.launcher.widgets.EnhancedWidgetManager
import com.sevenk.launcher.optimization.BatteryOptimizer
import com.sevenk.launcher.optimization.RAMOptimizer
import android.os.Process
import com.sevenk.launcher.gesture.GestureManager
import com.sevenk.launcher.drawer.AppDrawerFragment
import com.sevenk.launcher.privacy.AppPrivacyManager
import com.google.android.material.bottomsheet.BottomSheetDialog

class LauncherActivity : AppCompatActivity() {
    private lateinit var appDrawerContainer: ViewGroup
    // ViewPager2-based app drawer
    private lateinit var appDrawerPager: ViewPager2
    private lateinit var selectionBar: View
    private lateinit var saveSelectionBtn: View
    private lateinit var cancelSelectionBtn: View
    // Pager adapter managing page fragments
    private lateinit var drawerPagerAdapter: AppDrawerPagerAdapter
    private lateinit var homeScreen: FrameLayout
    private lateinit var homePager: ViewPager2
    private lateinit var dock: RecyclerView
    private lateinit var sidebar: RecyclerView
    private lateinit var sidebarScrim: View
    private lateinit var sidebarEdgeHandle: View
    private lateinit var searchBox: EditText
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var gestureManager: GestureManager
    private var appList: MutableList<AppInfo> = mutableListOf()
    private lateinit var widgetsContainer: FrameLayout
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appPrivacyManager: AppPrivacyManager
    private val prefs by lazy { getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE) }
    
    // Enhanced managers
    private lateinit var glassEffectHelper: GlassEffectHelper
    private lateinit var enhancedGestureManager: EnhancedGestureManager
    private lateinit var enhancedBackupManager: EnhancedBackupManager
    private lateinit var enhancedWidgetManager: EnhancedWidgetManager
    
    // Performance optimizers
    private lateinit var batteryOptimizer: BatteryOptimizer
    private lateinit var ramOptimizer: RAMOptimizer

    private fun loadAppsAsync() {
        android.util.Log.d("LauncherActivity", "Starting loadAppsAsync")
        
        lifecycleScope.launch {
            try {
                // Load apps in background thread
                val loadedApps = withContext(Dispatchers.IO) {
                    loadInstalledApps()
                }
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    android.util.Log.d("LauncherActivity", "Loaded ${loadedApps.size} apps")
                    appList.clear()
                    appList.addAll(loadedApps)
                    
                    // Update app drawer with loaded apps
                    refreshDrawerPages(appList)
                    
                    // Update dock and sidebar
                    rebuildDock()
                    rebuildSidebar()
                    
                    // Update home pages
                    refreshHomePages()
                    
                    android.util.Log.d("LauncherActivity", "App loading completed successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Failed to load apps", e)
                // Show user-friendly error
                runOnUiThread {
                    Toast.makeText(this@LauncherActivity, "Failed to load apps", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        try {
            val packageManager = packageManager
            
            // Load regular apps with launcher intent
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val activities = packageManager.queryIntentActivities(launcherIntent, 0)
            android.util.Log.d("LauncherActivity", "Found ${activities.size} launcher activities")
            
            // Add regular apps
            for (resolveInfo in activities) {
                try {
                    val activityInfo = resolveInfo.activityInfo
                    val packageName = activityInfo.packageName
                    val className = activityInfo.name
                    
                    // Skip if this is our own launcher
                    if (packageName == this.packageName) continue
                    
                    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                    val name = packageManager.getApplicationLabel(applicationInfo).toString()
                    val icon = packageManager.getApplicationIcon(applicationInfo)
                    
                    val appInfo = AppInfo(
                        name = name,
                        packageName = packageName,
                        className = className,
                        icon = icon,
                        applicationInfo = applicationInfo,
                        usageStats = null // Usage stats can be loaded separately if needed
                    )
                    
                    apps.add(appInfo)
                    
                } catch (e: Exception) {
                    android.util.Log.w("LauncherActivity", "Failed to load app info for ${resolveInfo.activityInfo?.packageName}", e)
                }
            }
            
            // Add PWAs (Progressive Web Apps)
            try {
                val pwaIntent = Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    data = android.net.Uri.parse("https://")
                }
                
                val pwaActivities = packageManager.queryIntentActivities(pwaIntent, 0)
                android.util.Log.d("LauncherActivity", "Found ${pwaActivities.size} PWA activities")
                
                for (resolveInfo in pwaActivities) {
                    try {
                        val activityInfo = resolveInfo.activityInfo
                        val packageName = activityInfo.packageName
                        val className = activityInfo.name
                        
                        // Skip if already added or if it's our own launcher
                        if (packageName == this.packageName || 
                            apps.any { it.packageName == packageName && it.className == className }) {
                            continue
                        }
                        
                        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                        val name = resolveInfo.loadLabel(packageManager).toString()
                        val icon = activityInfo.loadIcon(packageManager)
                        
                        val appInfo = AppInfo(
                            name = name,
                            packageName = packageName,
                            className = className,
                            icon = icon,
                            applicationInfo = applicationInfo,
                            isPWA = true
                        )
                        
                        apps.add(appInfo)
                        
                    } catch (e: Exception) {
                        android.util.Log.w("LauncherActivity", "Failed to load PWA info for ${resolveInfo.activityInfo?.packageName}", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Error loading PWAs", e)
            }
            
            // Add internal synthetic apps
            addInternalApps(apps)

            // Respect hidden-app privacy settings
            if (::appPrivacyManager.isInitialized) {
                val hiddenPackages = appPrivacyManager.getHiddenApps()
                if (hiddenPackages.isNotEmpty()) {
                    apps.removeAll { it.packageName in hiddenPackages }
                }
            }
            
            // Sort apps alphabetically by name
            apps.sortBy { it.name.lowercase() }
            
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Failed to query launcher activities", e)
        }
        
        return apps
    }

    private fun addInternalApps(apps: MutableList<AppInfo>) {
        try {
            // Add internal calculator app
            val calcIcon = try {
                AppCompatResources.getDrawable(this, R.drawable.ic_app_default)
            } catch (e: Exception) {
                android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE)
            }
            apps.add(AppInfo(
                name = "7K Calculator",
                packageName = INTERNAL_CALC_PKG,
                className = "com.sevenk.calcvault.VaultActivity",
                icon = calcIcon
            ))
            
            // Add 7K Settings app
            val settingsIcon = try {
                AppCompatResources.getDrawable(this, R.drawable.ic_settings)
            } catch (e: Exception) {
                android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY)
            }
            apps.add(AppInfo(
                name = "7K Settings",
                packageName = "internal.7ksettings",
                className = "com.sevenk.launcher.SettingsActivity",
                icon = settingsIcon
            ))
            
            // Add 7K Enhanced Settings app
            val enhancedSettingsIcon = try {
                AppCompatResources.getDrawable(this, R.drawable.ic_settings)
            } catch (e: Exception) {
                android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE)
            }
            apps.add(AppInfo(
                name = "7K Enhanced Settings",
                packageName = "internal.7kenhancedsettings",
                className = "com.sevenk.launcher.settings.EnhancedSettingsActivity",
                icon = enhancedSettingsIcon
            ))
            
            // Add other internal apps if resources exist
            try {
                val studioIcon = try {
                    AppCompatResources.getDrawable(this, R.drawable.sevenk_studio_icon)
                } catch (e: Exception) {
                    try {
                        AppCompatResources.getDrawable(this, R.drawable.ic_app_default)
                    } catch (e2: Exception) {
                        android.graphics.drawable.ColorDrawable(android.graphics.Color.GREEN)
                    }
                }
                apps.add(AppInfo(
                    name = "7K Studio",
                    packageName = INTERNAL_STUDIO_PKG,
                    className = "com.sevenk.studio.StudioActivity",
                    icon = studioIcon
                ))
            } catch (e: Exception) {
                android.util.Log.w("LauncherActivity", "Studio icon not found", e)
            }

            try {
                val notesIcon = try {
                    AppCompatResources.getDrawable(this, R.drawable.ic_app_default)
                } catch (e: Exception) {
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.YELLOW)
                }
                apps.add(AppInfo(
                    name = "7K Notes",
                    packageName = INTERNAL_NOTES_PKG,
                    className = "com.sevenk.launcher.notes.NotesActivity",
                    icon = notesIcon
                ))
            } catch (e: Exception) {
                android.util.Log.w("LauncherActivity", "Notes icon not found", e)
            }

            try {
                val browserIcon = try {
                    AppCompatResources.getDrawable(this, R.drawable.ic_web)
                } catch (e: Exception) {
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE)
                }
                apps.add(AppInfo(
                    name = "7K Browser",
                    packageName = "internal.7kbrowser",
                    className = "com.sevenk.browser.BrowserActivity",
                    icon = browserIcon
                ))
            } catch (e: Exception) {
                android.util.Log.w("LauncherActivity", "Browser icon not found", e)
            }

            // Add 7K Study app
            try {
                val studyIcon = try {
                    AppCompatResources.getDrawable(this, R.drawable.ic_study_default)
                } catch (e: Exception) {
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.MAGENTA)
                }
                apps.add(AppInfo(
                    name = "7K Study",
                    packageName = INTERNAL_STUDY_PKG,
                    className = "com.sevenk.launcher.StudyActivity",
                    icon = studyIcon
                ))
            } catch (e: Exception) {
                android.util.Log.w("LauncherActivity", "Study icon not found", e)
            }

            // Add additional embedded 7K apps (web app wrappers)
            val webIcon = try {
                AppCompatResources.getDrawable(this, R.drawable.ic_web)
            } catch (_: Exception) {
                android.graphics.drawable.ColorDrawable(android.graphics.Color.CYAN)
            }

            apps.add(AppInfo(
                name = "7K Law Prep",
                packageName = INTERNAL_LAW_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Itihaas",
                packageName = INTERNAL_ITIHAAS_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Polyglot",
                packageName = INTERNAL_POLYGLOT_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Eco",
                packageName = INTERNAL_ECO_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Life",
                packageName = INTERNAL_LIFE_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Calendar",
                packageName = INTERNAL_CALENDAR2_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Weather",
                packageName = INTERNAL_WEATHER_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Music",
                packageName = INTERNAL_MUSIC_PKG,
                className = "com.sevenk.launcher.WebAppActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Utility",
                packageName = INTERNAL_UTILITY_PKG,
                className = "com.sevenk.launcher.ecosystem.UtilityActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Games",
                packageName = INTERNAL_GAMES_PKG,
                className = "com.sevenk.launcher.ecosystem.GamesActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Widgets",
                packageName = INTERNAL_WIDGETS_PKG,
                className = "com.sevenk.launcher.ecosystem.WidgetsHubActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K AppStore",
                packageName = INTERNAL_APPSTORE_PKG,
                className = "com.sevenk.launcher.ecosystem.AppStoreActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Smart Notes+",
                packageName = INTERNAL_SMART_NOTES_PLUS_PKG,
                className = "com.sevenk.launcher.notes.ui.NotesActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Tasks Commander",
                packageName = INTERNAL_TASKS_COMMANDER_PKG,
                className = "com.sevenk.launcher.ecosystem.TasksCommanderActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K File Forge",
                packageName = INTERNAL_FILE_FORGE_PKG,
                className = "com.sevenk.launcher.ecosystem.FileForgeActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Budget Guardian",
                packageName = INTERNAL_BUDGET_GUARDIAN_PKG,
                className = "com.sevenk.launcher.ecosystem.BudgetGuardianActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Privacy Shield",
                packageName = INTERNAL_PRIVACY_SHIELD_PKG,
                className = "com.sevenk.launcher.ecosystem.PrivacyShieldActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Battery Doctor",
                packageName = INTERNAL_BATTERY_DOCTOR_PKG,
                className = "com.sevenk.launcher.ecosystem.BatteryDoctorActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Studio Templates",
                packageName = INTERNAL_STUDIO_TEMPLATES_PKG,
                className = "com.sevenk.launcher.ecosystem.StudioTemplatesActivity",
                icon = webIcon
            ))
            apps.add(AppInfo(
                name = "7K Offline First AppStore",
                packageName = INTERNAL_OFFLINE_APPSTORE_PKG,
                className = "com.sevenk.launcher.ecosystem.OfflineFirstAppStoreActivity",
                icon = webIcon
            ))
            
            android.util.Log.d("LauncherActivity", "Added internal apps")
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Failed to add internal apps", e)
        }
    }

    private fun handleGestureAction(action: String, targetPackage: String?): Boolean {
        return when (action) {
            "OPEN_APP_DRAWER" -> {
                toggleAppDrawer(true)
                true
            }
            "CLOSE_APP_DRAWER" -> {
                toggleAppDrawer(false)
                true
            }
            "OPEN_SETTINGS" -> {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "OPEN_LAUNCHER_SETTINGS" -> {
                try {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "OPEN_SEARCH" -> {
                try {
                    startActivity(Intent(this, com.sevenk.launcher.search.GlobalSearchActivity::class.java))
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "TOGGLE_SIDEBAR" -> {
                try {
                    if (::sidebar.isInitialized) {
                        val isVisible = sidebar.visibility == View.VISIBLE
                        sidebar.visibility = if (isVisible) View.GONE else View.VISIBLE
                        if (::sidebarScrim.isInitialized) {
                            sidebarScrim.visibility = if (isVisible) View.GONE else View.VISIBLE
                        }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "LAUNCH_APP" -> {
                if (targetPackage != null) {
                    try {
                        val intent = packageManager.getLaunchIntentForPackage(targetPackage)
                        if (intent != null) {
                            startActivity(intent)
                            return true
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                false
            }
            "OPEN_CALCULATOR" -> {
                try {
                    startActivity(Intent(this, com.sevenk.calcvault.MainActivity::class.java))
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "OPEN_VAULT" -> {
                try {
                    startActivity(Intent(this, com.sevenk.calcvault.VaultActivity::class.java))
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "REFRESH_WIDGETS" -> {
                try {
                    restoreWidgetIfAny()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            else -> {
                // Try to delegate to gesture manager for system actions
                try {
                    val gestureAction = GestureManager.GestureAction.valueOf(action)
                    gestureManager.performAction(gestureAction, targetPackage)
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    
    // Preference change listener for app drawer updates
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "drawer_sort_order", "drawer_alphabet_headers" -> {
                // Refresh app drawer to apply new settings
                refreshDrawerPages(appList)
            }
        }
    }
    
    // Controls for runtime blur compatibility
    private var blurFailureCount = 0


    // Add a getter method to expose the app list to fragments/adapters
    fun getAppList(): List<AppInfo> = appList
    
    // Search functionality
    private var searchQuery: String = ""
    

    // Package change receiver to refresh UI immediately after install/uninstall
    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return
            val pkg = intent.data?.schemeSpecificPart ?: return
            when (action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!replacing) {
                        onPackageRemoved(pkg)
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!replacing) {
                        onPackageAdded(pkg)
                    } else {
                        // Update changed package as added during replace
                        onPackageChanged(pkg)
                    }
                }
                Intent.ACTION_PACKAGE_CHANGED -> {
                    onPackageChanged(pkg)
                }
            }
        }
    }

    // Add GlassManager for consistent glass effects
    lateinit var glassManager: GlassManager

    // New fields for enhanced app drawer
    private var isAppDrawerOpen = false
    private lateinit var appDrawerScrim: View
    private var appDrawerDragStartY = 0f
    private var appDrawerCurrentTranslationY = 0f
    private val appDrawerAnimationDuration = 300L
    private val velocityThreshold = 1000f // pixels per second

    // Spring animation constants for natural motion
    private val springStiffness = 1000f
    private val springDamping = 0.7f

    // ---- Safe initialization helpers ----
    private fun ensureSearchBoxReady() {
        try {
            if (!::searchBox.isInitialized) {
                val found: EditText? = try { findViewById(R.id.searchBox) } catch (_: Throwable) { null }
                if (found != null) {
                    searchBox = found
                    setupSearchBox()
                    return
                }
                searchBox = EditText(this).apply {
                    id = R.id.searchBox
                    visibility = View.VISIBLE
                    isFocusable = true
                    isFocusableInTouchMode = true
                    hint = "Search apps..."
                }
                val root = findViewById<ViewGroup>(android.R.id.content)
                if (searchBox.parent == null) root.addView(searchBox, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                setupSearchBox()
            }
        } catch (e: Throwable) { 
            android.util.Log.e("LauncherActivity", "Error initializing search box", e)
        }
    }
    
    private fun setupSearchBox() {
        try {
            // Disable the LauncherActivity search as AppDrawerFragment handles its own search
            /* DISABLED - AppDrawerFragment now handles search internally
            searchBox.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterApps(s?.toString() ?: "")
                }
                
                override fun afterTextChanged(s: Editable?) {}
            })
            */
            
            // Handle search box focus changes
            searchBox.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Show keyboard when search box is focused
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            
            android.util.Log.d("LauncherActivity", "Search box initialized")
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error setting up search box", e)
        }
    }
    
    private fun filterApps(query: String) {
        searchQuery = query.trim()
        if (searchQuery.isEmpty()) {
            // If search query is empty, show all apps
            refreshDrawerPages(appList)
            return
        }
        
        val filteredList = appList.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        
        refreshDrawerPages(filteredList)
    }

    private var sidebarOverlayEnabled = false
    private var sidebarOverlayOpen = false
    private lateinit var iconPackHelper: IconPackHelper
    private val REQ_PICK_BACKGROUND = 5012
    private var prewarmJob: kotlinx.coroutines.Job? = null
    private val oneShotUnlockedPackages = mutableSetOf<String>()

    private fun applyGlassBlurIfPossible(view: View?, radius: Float) {
        if (view == null) return
        // Respect device capability and user preference
        if (!shouldUseRuntimeBlur()) {
            // Ensure any previous blur is cleared
            if (Build.VERSION.SDK_INT >= 31) try { view.setRenderEffect(null) } catch (_: Throwable) {}
            return
        }
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val renderEffect = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
            view.setRenderEffect(renderEffect)
        } catch (_: Throwable) {
            // Auto-disable if this device has issues applying blur
            blurFailureCount++
            if (blurFailureCount >= 2) {
                try { prefs.edit().putBoolean("enable_runtime_blur", false).apply() } catch (_: Throwable) {}
            }
        }
    }

    private fun shouldUseRuntimeBlur(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < 31) return false
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            if (am.isLowRamDevice) return false
            // Disable expensive blur in device power-save mode
            if (this::batteryOptimizer.isInitialized) {
                val profile = AdvancedBatteryOptimizer(this).getCurrentPowerProfile()
                if (profile != AdvancedBatteryOptimizer.PowerProfile.MAXIMUM_PERFORMANCE) return false
            }
            prefs.getBoolean("enable_runtime_blur", true)
        } catch (_: Throwable) {
            false
        }
    }

    // Expose icon pack helper to fragments/adapters
    fun getIconPackHelper(): IconPackHelper? = try {
        if (::iconPackHelper.isInitialized) iconPackHelper else null
    } catch (_: Throwable) { null }

    // ---- In-app wallpaper background (SAF) ----
    private fun customBackgroundKey() = "custom_bg_uri"
    private fun preloadedWallpaperAssetKey() = "preloaded_wallpaper_asset"
    private fun defaultWallpaperInitKey() = "default_wallpaper_initialized"

    private fun ensureDefaultWallpaperSelection() {
        if (prefs.getBoolean(defaultWallpaperInitKey(), false)) return
        val existingAsset = prefs.getString(preloadedWallpaperAssetKey(), null)
        prefs.edit()
            .apply {
                if (existingAsset.isNullOrBlank()) putString(preloadedWallpaperAssetKey(), "7.jpeg")
                putBoolean(defaultWallpaperInitKey(), true)
            }
            .apply()
    }

    private fun applyPreloadedWallpaperIfAny(): Boolean {
        val assetName = prefs.getString(preloadedWallpaperAssetKey(), null)?.trim().orEmpty()
        if (assetName.isBlank()) return false
        return try {
            val bitmap = assets.open(assetName).use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
            if (bitmap != null) {
                homeScreen.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyCustomBackgroundIfAny() {
        val uriStr = prefs.getString(customBackgroundKey(), null)
        if (uriStr.isNullOrBlank()) {
            // New-user default + optional preloaded wallpaper fallback before system wallpaper.
            ensureDefaultWallpaperSelection()
            if (applyPreloadedWallpaperIfAny()) return
            // Fallback to system wallpaper/transparent
            setWallpaper()
            return
        }
        try {
            val uri = android.net.Uri.parse(uriStr)
            // Determine desired bounds (use homeScreen if laid out, else display metrics)
            val targetW = if (homeScreen.width > 0) homeScreen.width else resources.displayMetrics.widthPixels
            val targetH = if (homeScreen.height > 0) homeScreen.height else resources.displayMetrics.heightPixels

            // First pass: bounds only
            val optsBounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, optsBounds) }
            val srcW = optsBounds.outWidth.takeIf { it > 0 } ?: targetW
            val srcH = optsBounds.outHeight.takeIf { it > 0 } ?: targetH
            // Compute inSampleSize (power of two)
            fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
                var inSample = 1
                var halfW = srcW / 2
                var halfH = srcH / 2
                while ((halfW / inSample) >= reqW && (halfH / inSample) >= reqH) {
                    inSample *= 2
                }
                return inSample.coerceAtLeast(1)
            }
            val sample = computeInSampleSize(srcW, srcH, targetW, targetH)

            // Second pass: decode with sampling
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            if (bmp != null) {
                val drawable = android.graphics.drawable.BitmapDrawable(resources, bmp)
                homeScreen.background = drawable
            } else {
                setWallpaper()
            }
        } catch (t: Throwable) {
            android.util.Log.w("LauncherActivity", "Failed to load custom background; using system wallpaper", t)
            setWallpaper()
        }
    }

    private fun startPickBackgroundImage() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQ_PICK_BACKGROUND)
        } catch (t: Throwable) {
            Toast.makeText(this, "Unable to open picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCustomBackground() {
        prefs.edit().remove(customBackgroundKey()).apply()
        // Re-apply default
        setWallpaper()
    }

// Expose drop targets by hiding the drawer when a drag begins from the app drawer
fun onAppDragStart() {
    // Close the app drawer to reveal dock/sidebar/home for dropping
    toggleAppDrawer(false)
}

// Context actions for a sidebar item (covers both pinned and recents)
private fun showSidebarItemOptions(app: AppInfo) {
    val pinned = loadPackageList(KEY_SIDEBAR)
    val recents = loadPackageList(KEY_RECENTS)
    val isPinned = pinned.contains(app.packageName)
    val isRecent = recents.contains(app.packageName)

    val actions = mutableListOf<Pair<String, () -> Unit>>()
    if (isPinned) {
        actions += "Remove from Sidebar" to {
            removeFromList(KEY_SIDEBAR, app.packageName)
            rebuildSidebar()
        }
    }
    if (isRecent) {
        actions += "Remove from Recents" to {
            removeFromList(KEY_RECENTS, app.packageName)
            rebuildSidebar()
        }
    }
    actions += "Pin to Dock" to {
        if (isPinned) {
            moveBetween(KEY_SIDEBAR, KEY_DOCK, app.packageName)
        } else if (isRecent) {
            // Move from recents to dock (keeping sidebar recents intact if desired)
            moveBetween(KEY_RECENTS, KEY_DOCK, app.packageName)
        } else {
            // If neither, just add to dock
            addToList(KEY_DOCK, app.packageName)
        }
        rebuildDock()
        rebuildSidebar()
    }
    actions += "Edit Sidebar" to { startEditSidebarMode() }

    showGlassActionSheet(app.name, actions)
}

private fun showDockContextMenu(show: Boolean = true) {
    if (!show) return
    
    // Show dock configuration options
    val actions = listOf(
        "Edit Dock" to { startEditDockMode() },
        "Add Widget" to { showAddWidgetDialog() },
        "Dock Settings" to { showDockSettings() },
        "Glass Effects" to { toggleGlassEffects() }
    )
    showGlassActionSheet("Dock Options", actions)
}

private fun showGlassActionSheet(title: String, actions: List<Pair<String, () -> Unit>>) {
    val sheet = BottomSheetDialog(this)
    val root = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(20))
        setBackgroundResource(R.drawable.dialog_background)
    }

    val titleView = android.widget.TextView(this).apply {
        text = title
        textSize = 18f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(8), dp(4), dp(8), dp(12))
    }
    root.addView(titleView)

    actions.forEach { (label, onClick) ->
        val item = android.widget.TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(this@LauncherActivity, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                sheet.dismiss()
                onClick.invoke()
            }
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        root.addView(item, lp)
    }

    val cancel = android.widget.TextView(this).apply {
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

private fun showGlassInputSheet(
    title: String,
    hint: String,
    initialValue: String = "",
    submitLabel: String = "Save",
    inputType: Int? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onSubmit: (String) -> Unit
) {
    val sheet = BottomSheetDialog(this)
    val root = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(20))
        setBackgroundResource(R.drawable.dialog_background)
    }

    val titleView = android.widget.TextView(this).apply {
        text = title
        textSize = 18f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(8), dp(4), dp(8), dp(12))
    }
    root.addView(titleView)

    val input = android.widget.EditText(this).apply {
        this.hint = hint
        setText(initialValue)
        inputType?.let { this.inputType = it }
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0x99FFFFFF.toInt())
        setBackgroundResource(R.drawable.glass_panel)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        setSingleLine()
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                onSubmit(text?.toString().orEmpty())
                sheet.dismiss()
                true
            } else {
                false
            }
        }
    }
    val inputLp = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(12) }
    root.addView(input, inputLp)

    val submit = android.widget.TextView(this).apply {
        text = submitLabel
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setBackgroundResource(R.drawable.glass_panel)
        foreground = AppCompatResources.getDrawable(this@LauncherActivity, android.R.drawable.list_selector_background)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            onSubmit(input.text?.toString().orEmpty())
            sheet.dismiss()
        }
    }
    val submitLp = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }
    root.addView(submit, submitLp)

    if (!secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null) {
        val secondary = android.widget.TextView(this).apply {
            text = secondaryActionLabel
            textSize = 15f
            setTextColor(0xFFB6F4FF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(this@LauncherActivity, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                sheet.dismiss()
                onSecondaryAction.invoke()
            }
        }
        val secondaryLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        root.addView(secondary, secondaryLp)
    }

    val cancel = android.widget.TextView(this).apply {
        text = "Cancel"
        textSize = 14f
        setTextColor(0xFF80CBC4.toInt())
        setPadding(dp(16), dp(12), dp(16), dp(12))
        gravity = android.view.Gravity.CENTER
        setOnClickListener { sheet.dismiss() }
    }
    root.addView(cancel)

    sheet.setContentView(root)
    sheet.setOnShowListener {
        input.requestFocus()
        input.setSelection(input.text?.length ?: 0)
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    sheet.show()
}

fun showLauncherActionSheet(title: String, actions: List<Pair<String, () -> Unit>>) {
    showGlassActionSheet(title, actions)
}

private fun showAddWidgetDialog() {
    startAddWidgetFlow()
}

private fun showDockSettings() {
    // TODO: Open dock-specific settings
    Toast.makeText(this, "Dock settings", Toast.LENGTH_SHORT).show()
}

private fun toggleGlassEffects() {
    val currentEnabled = prefs.getBoolean("glass_effects_enabled", true)
    prefs.edit().putBoolean("glass_effects_enabled", !currentEnabled).apply()
    Toast.makeText(this, "Glass effects ${if (!currentEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
}

// Expose recent packages for assistants
fun getRecentPackages(): List<String> = loadPackageList(KEY_RECENTS)
    private val KEY_DOCK = "dock_packages"
    private val KEY_SIDEBAR = "sidebar_packages"
    private val KEY_SHOW_LABELS = "show_labels"
    private val KEY_DRAWER_COLUMNS = "drawer_columns"
    private val KEY_SECTION_ORDER = "section_order"
    private val KEY_SECTION_MAP = "section_map"
    private val KEY_RECENTS = "recent_packages"
    // Number of regular home pages (excluding special Stan page at index 0)
    private val NORMAL_HOME_PAGES = 3
    private val APPWIDGET_HOST_ID = 1024
    private val REQ_PICK_APPWIDGET = 1001
    private val REQ_BIND_APPWIDGET = 1002
    private val REQ_CONFIGURE_APPWIDGET = 1003
    private val REQ_PICK_CUSTOM_ICON = 2001
    private val KEY_WIDGET_ID = "current_widget_id"
    private var widgetsRestoredOnce: Boolean = false
    // Internal synthetic packages
    private val INTERNAL_CALC_PKG = "internal.calcvault"
    private val INTERNAL_STUDIO_PKG = "internal.7kstudio"
    private val INTERNAL_LAW_PKG = "internal.7klawprep"
    private val INTERNAL_STUDY_PKG = "internal.7kstudy"
    // New synthetic PWAs
    private val INTERNAL_ITIHAAS_PKG = "internal.7kitihaas"
    private val INTERNAL_POLYGLOT_PKG = "internal.7kpolyglot"
    private val INTERNAL_ECO_PKG = "internal.7keco"
    private val INTERNAL_LIFE_PKG = "internal.7klife"
    // Daily Essentials synthetic apps
    private val INTERNAL_NOTES_PKG = "internal.7knotes"
    private val INTERNAL_CALENDAR2_PKG = "internal.7kcalendar"
    private val INTERNAL_WEATHER_PKG = "internal.7kweather"
    private val INTERNAL_MUSIC_PKG = "internal.7kmusic"
    private val INTERNAL_UTILITY_PKG = "internal.7kutility"
    private val INTERNAL_GAMES_PKG = "internal.7kgames"
    private val INTERNAL_WIDGETS_PKG = "internal.7kwidgets"
    private val INTERNAL_APPSTORE_PKG = "internal.7kappstore"
    private val INTERNAL_SMART_NOTES_PLUS_PKG = "internal.7ksmartnotesplus"
    private val INTERNAL_TASKS_COMMANDER_PKG = "internal.7ktaskscommander"
    private val INTERNAL_FILE_FORGE_PKG = "internal.7kfileforge"
    private val INTERNAL_BUDGET_GUARDIAN_PKG = "internal.7kbudgetguardian"
    private val INTERNAL_PRIVACY_SHIELD_PKG = "internal.7kprivacyshield"
    private val INTERNAL_BATTERY_DOCTOR_PKG = "internal.7kbatterydoctor"
    private val INTERNAL_STUDIO_TEMPLATES_PKG = "internal.7kstudiotemplates"
    private val INTERNAL_OFFLINE_APPSTORE_PKG = "internal.7kofflineappstore"

    // Folders per page (persisted) - using existing Folder class
    private val homeFolders: MutableMap<Int, MutableList<Folder>> = mutableMapOf()
    private fun foldersKey(pageIndex: Int) = "folders_page_$pageIndex"
    private fun encodeFolders(list: List<Folder>): String {
        // name::pkg1,pkg2||name2::pkgA
        return list.joinToString("||") { f ->
            val name = f.name.replace("||", " ").replace("::", ": ")
            val pkgs = f.apps.joinToString(",") { it.packageName.replace(",", " ") }
            "$name::$pkgs"
        }
    }

    fun showIconOptions(app: AppInfo) {
        val dynamic = mutableListOf<String>()
        dynamic.add("Set Custom Icon")
        if (CustomIconManager.hasCustomIcon(this@LauncherActivity, app.packageName)) dynamic.add("Remove Custom Icon")
        // Home screen management
        val isOnAnyHome = try {
            val pages = getNormalHomePages()
            (0 until pages).any { idx ->
                try { loadHomePageList(idx).contains(app.packageName) } catch (_: Throwable) { false }
            }
        } catch (_: Throwable) { false }
        if (isOnAnyHome) dynamic.add("Remove from Home") else dynamic.add("Add to Home")
        // Surface app shortcuts if available
        dynamic.add("Shortcuts")
        dynamic.add("Uninstall")
        dynamic.add("App Info")
        val actions = dynamic.map { option ->
            option to {
                when (option) {
                    "Set Custom Icon" -> {
                        pendingCustomIconPkg = app.packageName
                        // Use system document picker
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        try {
                            startActivityForResult(intent, REQ_PICK_CUSTOM_ICON)
                        } catch (t: Throwable) {
                            Toast.makeText(this, "No picker available", Toast.LENGTH_SHORT).show()
                            pendingCustomIconPkg = null
                        }
                    }
                    "Shortcuts" -> {
                        showAppShortcuts(app)
                    }
                    "Add to Home" -> {
                        try {
                            val page = try { getCurrentHomePage() } catch (_: Throwable) { 1 }
                            addToHomePage(page, app.packageName)
                            refreshHomePages()
                            Toast.makeText(this, "Added to Home", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {
                            Toast.makeText(this, "Unable to add to Home", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Remove from Home" -> {
                        try {
                            removeFromAllHomePagesPublic(app.packageName)
                            refreshHomePages()
                            Toast.makeText(this, "Removed from Home", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {
                            Toast.makeText(this, "Unable to remove from Home", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Remove Custom Icon" -> {
                        val removed = CustomIconManager.removeCustomIcon(this, app.packageName)
                        if (removed) {
                            IconCache.invalidate(app.packageName)
                            refreshDrawerPages()
                            rebuildDock()
                            rebuildSidebar()
                            refreshHomePages()
                            Toast.makeText(this, "Custom icon removed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "No custom icon to remove", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Uninstall" -> {
                        try {
                            if (app.packageName != packageName &&
                                app.packageName != INTERNAL_CALC_PKG &&
                                app.packageName != INTERNAL_STUDIO_PKG &&
                                app.packageName != INTERNAL_LAW_PKG &&
                                app.packageName != INTERNAL_STUDY_PKG &&
                                app.packageName != INTERNAL_ITIHAAS_PKG &&
                                app.packageName != INTERNAL_POLYGLOT_PKG &&
                                app.packageName != INTERNAL_ECO_PKG &&
                                app.packageName != INTERNAL_LIFE_PKG &&
                                app.packageName != INTERNAL_NOTES_PKG &&
                                app.packageName != INTERNAL_CALENDAR2_PKG &&
                                app.packageName != INTERNAL_WEATHER_PKG &&
                                app.packageName != INTERNAL_MUSIC_PKG &&
                                app.packageName != INTERNAL_UTILITY_PKG &&
                                app.packageName != INTERNAL_GAMES_PKG &&
                                app.packageName != INTERNAL_WIDGETS_PKG &&
                                app.packageName != INTERNAL_APPSTORE_PKG &&
                                app.packageName != INTERNAL_SMART_NOTES_PLUS_PKG &&
                                app.packageName != INTERNAL_TASKS_COMMANDER_PKG &&
                                app.packageName != INTERNAL_FILE_FORGE_PKG &&
                                app.packageName != INTERNAL_BUDGET_GUARDIAN_PKG &&
                                app.packageName != INTERNAL_PRIVACY_SHIELD_PKG &&
                                app.packageName != INTERNAL_BATTERY_DOCTOR_PKG &&
                                app.packageName != INTERNAL_STUDIO_TEMPLATES_PKG &&
                                app.packageName != INTERNAL_OFFLINE_APPSTORE_PKG) {
                                val uri = Uri.parse("package:${app.packageName}")
                                // Prefer ACTION_DELETE (widely supported), fallback to ACTION_UNINSTALL_PACKAGE
                                val delete = Intent(Intent.ACTION_DELETE, uri).apply {
                                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    startActivity(delete)
                                } catch (_: Throwable) {
                                    val uninstall = Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri).apply {
                                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(uninstall)
                                }
                            } else {
                                Toast.makeText(this, "Cannot uninstall this app", Toast.LENGTH_SHORT).show()
                            }
                        } catch (t: Throwable) {
                            Toast.makeText(this, "Uninstall not supported", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "App Info" -> {
                        try {
                            val uri = Uri.parse("package:${app.packageName}")
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                            startActivity(intent)
                        } catch (_: Throwable) { }
                    }
                }
            }
        }
        showGlassActionSheet(app.name, actions)
    }

    private fun showAppShortcuts(app: AppInfo) {
        try {
            val la = getSystemService(LauncherApps::class.java)
            if (la == null) {
                Toast.makeText(this, "Shortcuts not supported", Toast.LENGTH_SHORT).show()
                return
            }
            val query = LauncherApps.ShortcutQuery()
            query.setPackage(app.packageName)
            // Match dynamic + pinned + manifest shortcuts
            query.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
            val user = Process.myUserHandle()
            val shortcuts: List<ShortcutInfo>? = try { la.getShortcuts(query, user) } catch (_: Throwable) { null }
            if (shortcuts.isNullOrEmpty()) {
                Toast.makeText(this, "No shortcuts available", Toast.LENGTH_SHORT).show()
                return
            }
            val actions = shortcuts.map { si ->
                (si.shortLabel?.toString() ?: si.id) to {
                    try {
                        la.startShortcut(si, null, null)
                    } catch (t: Throwable) {
                        try { la.startShortcut(app.packageName, si.id, null, null, user) } catch (_: Throwable) {}
                    }
                }
            }
            showGlassActionSheet("Shortcuts: ${app.name}", actions)
        } catch (t: Throwable) {
            Toast.makeText(this, "Unable to open shortcuts", Toast.LENGTH_SHORT).show()
        }
    }

    // -------- Sections: persistence + building --------
    private var sectionOrder: MutableList<String> = mutableListOf()
    private var sectionMap: MutableMap<String, String> = mutableMapOf()

    private fun ensureSectionsInitialized() {
        // Default section order
        if (sectionOrder.isEmpty()) {
            val savedOrder = prefs.getString(KEY_SECTION_ORDER, null)
            sectionOrder = if (!savedOrder.isNullOrBlank()) savedOrder.split(';').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            else mutableListOf("Communication", "Games", "Media", "Tools", "Social", "Shopping", "Uncategorized")
        }
        if (sectionMap.isEmpty()) {
            // Load saved map or infer once and save
            val raw = prefs.getString(KEY_SECTION_MAP, "") ?: ""
            if (raw.isNotBlank()) {
                sectionMap = raw.split(';').mapNotNull { token ->
                    val parts = token.split('=')
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap().toMutableMap()
            } else {
                // Auto-categorize current apps
                for (app in appList) sectionMap[app.packageName] = inferSection(app)
                saveSections()
            }
        }
    }

    private fun saveSections() {
        prefs.edit()
            .putString(KEY_SECTION_ORDER, sectionOrder.joinToString(";"))
            .putString(KEY_SECTION_MAP, sectionMap.entries.joinToString(";") { it.key + "=" + it.value })
            .apply()
    }

    private fun inferSection(app: AppInfo): String {
        val name = app.name.lowercase()
        val pkg = app.packageName.lowercase()
        return when {
            arrayOf("whatsapp", "message", "sms", "contact", "phone", "call", "mail", "gmail", "telegram", "signal").any { it in name || it in pkg } -> "Communication"
            arrayOf("game", "play", "pubg", "freefire", "call of duty", "clash").any { it in name || it in pkg } -> "Games"
            arrayOf("music", "video", "player", "spotify", "youtube", "gallery", "photo", "camera").any { it in name || it in pkg } -> "Media"
            arrayOf("tool", "file", "manager", "calc", "note", "utility", "settings").any { it in name || it in pkg } -> "Tools"
            arrayOf("social", "facebook", "instagram", "twitter", "x.com", "snapchat", "tiktok").any { it in name || it in pkg } -> "Social"
            arrayOf("shop", "amazon", "flipkart", "myntra", "ebay").any { it in name || it in pkg } -> "Shopping"
            else -> "Uncategorized"
        }
    }

    private fun buildSectionedList(apps: List<AppInfo>): List<SectionedItem> {
        val bySection = linkedMapOf<String, MutableList<AppInfo>>()
        // Ensure we preserve order and include known sections; unknowns go to end
        for (sec in sectionOrder) bySection[sec] = mutableListOf()
        val others = mutableListOf<AppInfo>()
        for (app in apps) {
            val sec = sectionMap[app.packageName] ?: inferSection(app)
            if (bySection.containsKey(sec)) bySection[sec]?.add(app) else others.add(app)
        }
        if (others.isNotEmpty()) {
            val name = "Uncategorized"
            if (!bySection.containsKey(name)) bySection[name] = mutableListOf()
            bySection[name]?.addAll(others)
        }

        val result = mutableListOf<SectionedItem>()
        for ((sec, list) in bySection) {
            result.add(SectionedItem.Header(sec))
            list.sortedBy { it.name }.forEach { result.add(SectionedItem.App(it)) }
        }
        return result
    }

    private fun openEditSectionsDialog() {
        val actions = listOf(
            "Add Section" to { promptAddSection() },
            "Reset Defaults" to {
                sectionOrder = mutableListOf("Communication", "Games", "Media", "Tools", "Social", "Shopping", "Uncategorized")
                saveSections()
                try {
                    if (::drawerPagerAdapter.isInitialized) drawerPagerAdapter.setSections(getDrawerSections())
                } catch (_: Throwable) {}
                refreshDrawerPages()
            }
        )
        showGlassActionSheet("Edit Sections", actions)
    }

    private fun promptAddSection() {
        showGlassInputSheet(
            title = "New Section",
            hint = "Section name",
            submitLabel = "Add"
        ) { enteredName ->
            val name = enteredName.trim().ifBlank { return@showGlassInputSheet }
            if (!sectionOrder.contains(name)) sectionOrder.add(0, name)
            saveSections()
            try {
                if (::drawerPagerAdapter.isInitialized) drawerPagerAdapter.setSections(getDrawerSections())
            } catch (_: Throwable) {}
            refreshDrawerPages()
        }
    }

    private fun getDrawerSections(): List<String> {
        // Fixed first pages: All Apps first, then 7K Apps, then user-defined sections.
        return listOf("All Apps", "7K Apps") + sectionOrder
    }

    private fun getCurrentDrawerFragment(): AppDrawerPageFragment? {
        return try {
            drawerPagerAdapter.getFragment(appDrawerPager.currentItem)
        } catch (_: Throwable) { null }
    }

    private fun getCurrentPageAdapter(): AppDrawerAdapter? = getCurrentDrawerFragment()?.getAdapter()

    private fun refreshDrawerPages() {
        refreshDrawerPages(appList)
    }

    private fun refreshDrawerPages(sourceList: List<AppInfo>) {
        try {
            // Ensure adapter sections are current
            val sections = getDrawerSections()
            if (::drawerPagerAdapter.isInitialized) {
                drawerPagerAdapter.setSections(sections)
            }

            // Update the app list in the current fragment
            val currentFragment = getCurrentDrawerFragment()
            val currentPageIndex = appDrawerPager.currentItem
            val sectionName = sections.getOrNull(currentPageIndex)

            if (currentFragment != null && sectionName != null) {
                currentFragment.updateAppsForPage(sourceList, sectionName, sectionMap)
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error refreshing drawer pages", e)
        }
    }

    // Expose Home Options dialog so fragments can trigger it on long-press
    fun showHomeOptions() {
        val hasW = hasWidget()
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += (if (isShowLabels()) "Hide Labels" else "Show Labels") to { toggleLabels() }
        actions += "Add Widget" to { startAddWidgetFlow() }
        if (hasW) actions += "Remove Widget" to { removeCurrentWidget() }
        actions += "Set Background Image" to { startPickBackgroundImage() }
        actions += "Clear Background Image" to { clearCustomBackground() }
        showGlassActionSheet("Home Options", actions)
    }

    private fun decodeFolders(raw: String): MutableList<Folder> {
        if (raw.isBlank()) return mutableListOf()
        return raw.split("||").mapNotNull { token ->
            val parts = token.split("::", limit = 2)
            if (parts.isEmpty()) null else {
                val name = parts[0]
                val pkgs = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].split(',').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
                val folder = Folder(name.ifBlank { "Folder" })
                // Convert package names to AppInfo objects (stub implementation)
                pkgs.forEach { pkg ->
                    val app = appList.find { it.packageName == pkg }
                    if (app != null) folder.addApp(app)
                }
                folder
            }
        }.toMutableList()
    }
    private fun loadFoldersFromPrefs(pageIndex: Int): MutableList<Folder> {
        val raw = prefs.getString(foldersKey(pageIndex), "") ?: ""
        return decodeFolders(raw)
    }
    private fun saveFoldersToPrefs(pageIndex: Int) {
        val list = homeFolders[pageIndex] ?: mutableListOf()
        prefs.edit().putString(foldersKey(pageIndex), encodeFolders(list)).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("LauncherActivity", "onCreate started")
        try {
            super.onCreate(savedInstanceState)
            android.util.Log.d("LauncherActivity", "super.onCreate completed")
            
            // Try fallback layout first if main layout fails
            try {
                setContentView(R.layout.activity_launcher)
                android.util.Log.d("LauncherActivity", "Main layout set successfully")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Main layout failed, trying fallback", e)
                try {
                    setContentView(R.layout.activity_launcher_fallback)
                    android.util.Log.d("LauncherActivity", "Fallback layout set successfully")
                } catch (fallbackError: Exception) {
                    android.util.Log.e("LauncherActivity", "Both layouts failed", fallbackError)
                    // Create minimal layout programmatically
                    val frameLayout = android.widget.FrameLayout(this)
                    frameLayout.id = android.R.id.content
                    setContentView(frameLayout)
                    android.util.Log.d("LauncherActivity", "Programmatic layout created")
                }
            }

            // Initialize performance optimizers early
            try {
                batteryOptimizer = BatteryOptimizer(this)
                ramOptimizer = RAMOptimizer(this)
                ramOptimizer.startPeriodicCleanup()
                android.util.Log.d("LauncherActivity", "Performance optimizers initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Failed to initialize optimizers", e)
            }

            // Initialize views with extensive logging and null checks
            android.util.Log.d("LauncherActivity", "Starting view initialization")
            
            try {
                homeScreen = findViewById(R.id.homeScreen)
                android.util.Log.d("LauncherActivity", "homeScreen found: ${homeScreen != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "homeScreen initialization failed", e)
                homeScreen = android.widget.FrameLayout(this).apply { id = R.id.homeScreen }
            }
            
            try {
                homePager = findViewById(R.id.homePager)
                android.util.Log.d("LauncherActivity", "homePager found: ${homePager != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "homePager initialization failed", e)
                homePager = androidx.viewpager2.widget.ViewPager2(this).apply { id = R.id.homePager }
            }
            
            try {
                dock = findViewById(R.id.dock)
                android.util.Log.d("LauncherActivity", "dock found: ${dock != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "dock initialization failed", e)
                dock = androidx.recyclerview.widget.RecyclerView(this).apply { id = R.id.dock }
            }
            
            try {
                sidebar = findViewById(R.id.sidebar)
                android.util.Log.d("LauncherActivity", "sidebar found: ${sidebar != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "sidebar initialization failed", e)
                sidebar = androidx.recyclerview.widget.RecyclerView(this).apply { id = R.id.sidebar }
            }
            
            try {
                sidebarScrim = findViewById(R.id.sidebarScrim)
                android.util.Log.d("LauncherActivity", "sidebarScrim found: ${sidebarScrim != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "sidebarScrim initialization failed", e)
                sidebarScrim = android.view.View(this).apply { id = R.id.sidebarScrim }
            }
            
            try {
                sidebarEdgeHandle = findViewById(R.id.sidebarEdgeHandle)
                android.util.Log.d("LauncherActivity", "sidebarEdgeHandle found: ${sidebarEdgeHandle != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "sidebarEdgeHandle initialization failed", e)
                sidebarEdgeHandle = android.view.View(this).apply { id = R.id.sidebarEdgeHandle }
            }
            
            try {
                appDrawerContainer = findViewById(R.id.appDrawerContainer)
                android.util.Log.d("LauncherActivity", "appDrawerContainer found: ${appDrawerContainer != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "appDrawerContainer initialization failed", e)
                appDrawerContainer = android.widget.FrameLayout(this).apply { id = R.id.appDrawerContainer }
            }
            
            try {
                appDrawerPager = findViewById(R.id.appDrawerPager)
                android.util.Log.d("LauncherActivity", "appDrawerPager found: ${appDrawerPager != null}")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "appDrawerPager initialization failed", e)
                appDrawerPager = androidx.viewpager2.widget.ViewPager2(this).apply { id = R.id.appDrawerPager }
            }
            
            android.util.Log.d("LauncherActivity", "View initialization completed")
            
            // Initialize icon pack helper
            try {
                iconPackHelper = IconPackHelper(this)
                android.util.Log.d("LauncherActivity", "IconPackHelper initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "IconPackHelper initialization failed", e)
            }
            
            // Initialize enhanced managers
            try {
                glassEffectHelper = GlassEffectHelper(this)
                android.util.Log.d("LauncherActivity", "GlassEffectHelper initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "GlassEffectHelper initialization failed", e)
            }
            
            try {
                enhancedBackupManager = EnhancedBackupManager(this)
                android.util.Log.d("LauncherActivity", "EnhancedBackupManager initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "EnhancedBackupManager initialization failed", e)
            }

            try {
                appPrivacyManager = AppPrivacyManager(this)
                android.util.Log.d("LauncherActivity", "AppPrivacyManager initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "AppPrivacyManager initialization failed", e)
            }
            
            try {
                enhancedWidgetManager = EnhancedWidgetManager(this, appWidgetHost, appWidgetManager)
                android.util.Log.d("LauncherActivity", "EnhancedWidgetManager initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "EnhancedWidgetManager initialization failed", e)
            }
            
            // Initialize enhanced gesture manager
            try {
                enhancedGestureManager = EnhancedGestureManager(
                    context = this,
                    onShowAppDrawer = { toggleAppDrawer(true) },
                    onShowDockMenu = { showDockContextMenu(true) },
                    onPageSwipe = { direction -> 
                        val currentItem = homePager.currentItem
                        when (direction) {
                            EnhancedGestureManager.SWIPE_LEFT -> {
                                if (currentItem < (homePager.adapter?.itemCount ?: 0) - 1) {
                                    homePager.setCurrentItem(currentItem + 1, true)
                                }
                            }
                            EnhancedGestureManager.SWIPE_RIGHT -> {
                                if (currentItem > 0) {
                                    homePager.setCurrentItem(currentItem - 1, true)
                                }
                            }
                        }
                    },
                    onShowQuickSettings = { /* TODO: Implement quick settings */ },
                    onTriggerScreenLock = { 
                        try {
                            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE)
                            // Try to lock screen if possible
                        } catch (e: Exception) {
                            // Screen lock not available
                        }
                    }
                )
                
                // Replace the old gesture detector with the enhanced one
                gestureDetector = enhancedGestureManager.createEnhancedGestureDetector()
                android.util.Log.d("LauncherActivity", "EnhancedGestureManager initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "EnhancedGestureManager initialization failed", e)
            }
            
            // Apply any saved custom launcher background (fallback to system wallpaper)
            try { applyCustomBackgroundIfAny() } catch (_: Throwable) {}
            // Apply glass blur on Android 12+ (optional, skip if fails)
            try {
                if (::glassEffectHelper.isInitialized) {
                    glassEffectHelper.applyGlassEffect(homeScreen, GlassEffectHelper.GlassIntensity.LIGHT)
                    glassEffectHelper.applyGlassEffect(dock, GlassEffectHelper.GlassIntensity.NORMAL)
                    glassEffectHelper.applyGlassEffect(sidebar, GlassEffectHelper.GlassIntensity.NORMAL)
                    val searchLocal: View? = findViewById(R.id.searchBox)
                    if (searchLocal != null) glassEffectHelper.applyGlassEffect(searchLocal, GlassEffectHelper.GlassIntensity.LIGHT)
                    val selectionLocal: View? = findViewById(R.id.selectionBar)
                    if (selectionLocal != null) glassEffectHelper.applyGlassEffect(selectionLocal, GlassEffectHelper.GlassIntensity.LIGHT)
                    android.util.Log.d("LauncherActivity", "Enhanced glass effects applied")
                } else {
                    // Fallback to old method
                    applyGlassBlurIfPossible(homeScreen, 18f)
                    applyGlassBlurIfPossible(dock, 20f)
                    applyGlassBlurIfPossible(sidebar, 20f)
                    val searchLocal: View? = findViewById(R.id.searchBox)
                    if (searchLocal != null) applyGlassBlurIfPossible(searchLocal, 16f)
                    val selectionLocal: View? = findViewById(R.id.selectionBar)
                    if (selectionLocal != null) applyGlassBlurIfPossible(selectionLocal, 16f)
                    android.util.Log.d("LauncherActivity", "Fallback glass blur applied")
                }
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Glass effects failed", e)
            }
            
            // Ensure sections are ready before wiring the drawer
            try {
                ensureSectionsInitialized()
                android.util.Log.d("LauncherActivity", "Sections initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Sections initialization failed", e)
            }
            
            // Initialize battery optimizer for adaptive performance
            try {
                batteryOptimizer = BatteryOptimizer(this)
                ramOptimizer = RAMOptimizer(this)
            } catch (_: Throwable) { }

            // Enable drop targets (dock, sidebar, home)
            try {
                setupDragDropTargets()
                android.util.Log.d("LauncherActivity", "Drag drop targets setup")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Drag drop setup failed", e)
            }
            
            // Initialize the App Drawer pager adapter early so section edits can safely update it
            if (!::drawerPagerAdapter.isInitialized) {
                try {
                    drawerPagerAdapter = AppDrawerPagerAdapter(this, getDrawerSections())
                    appDrawerPager.adapter = drawerPagerAdapter
                    appDrawerPager.offscreenPageLimit = 1
                    android.util.Log.d("LauncherActivity", "Drawer pager adapter initialized")
                } catch (e: Exception) {
                    android.util.Log.e("LauncherActivity", "Drawer pager adapter failed, using fallback", e)
                    // Fallback initialization
                    try {
                        drawerPagerAdapter = AppDrawerPagerAdapter(this, mutableListOf())
                        appDrawerPager.adapter = drawerPagerAdapter
                        android.util.Log.d("LauncherActivity", "Fallback drawer pager adapter initialized")
                    } catch (fallbackError: Exception) {
                        android.util.Log.e("LauncherActivity", "Fallback drawer pager adapter also failed", fallbackError)
                    }
                }
            }
            
            try {
                searchBox = findViewById(R.id.searchBox)
                findViewById<View>(R.id.editSectionsFab)?.setOnClickListener { openEditSectionsDialog() }
                widgetsContainer = findViewById(R.id.widgetsContainer)
                android.util.Log.d("LauncherActivity", "Additional views initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Additional views initialization failed", e)
            }

            // Ensure searchBox is always initialized to avoid lateinit crashes on resume/usage
            ensureSearchBoxReady()

            // Initialize gestures
            try {
                gestureManager = GestureManager(this)
                gestureDetector = gestureManager.createGestureDetector()
                // Register a callback to map configured actions to UI behaviors
                val callback = object : GestureManager.GestureCallback {
                    override fun onGestureDetected(gestureType: GestureManager.GestureType, event: MotionEvent?): Boolean {
                        // Resolve configured action
                        val cfg = gestureManager.getGestureConfig(gestureType)
                        return handleGestureAction(cfg.action.toString(), cfg.targetPackage)
                    }
                }
                // Register for primary gestures we support on home
                gestureManager.registerCallback(GestureManager.GestureType.SWIPE_UP, callback)
                gestureManager.registerCallback(GestureManager.GestureType.SWIPE_DOWN, callback)
                gestureManager.registerCallback(GestureManager.GestureType.DOUBLE_TAP, callback)
                gestureManager.registerCallback(GestureManager.GestureType.LONG_PRESS, callback)
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Gesture initialization failed", e)
            }

            // Widget host setup
            try {
                appWidgetManager = AppWidgetManager.getInstance(this)
                appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
                android.util.Log.d("LauncherActivity", "Widget host setup completed")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Widget host setup failed", e)
            }

            // Setup multi-page home with special Stan page injected at position 0
            try {
                homePager.adapter = HomePagerAdapter(this, NORMAL_HOME_PAGES)
                homePager.offscreenPageLimit = NORMAL_HOME_PAGES + 2 // +1 Stan, +1 To-Do
                // Toggle widget visibility by page (show only on content pages)
                homePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        try {
                            updateWidgetVisibilityForCurrentPage()
                        } catch (e: Exception) {
                            android.util.Log.e("LauncherActivity", "Widget visibility update failed", e)
                        }
                    }
                })
                android.util.Log.d("LauncherActivity", "Home pager setup completed")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Home pager setup failed", e)
            }

            // Set up gesture detection for swipe up to open app drawer
            try {
                gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        // Only handle down events from the center area of the screen
                        val screenWidth = resources.displayMetrics.widthPixels
                        val centerStart = screenWidth * 0.25f
                        val centerEnd = screenWidth * 0.75f
                        return e.x >= centerStart && e.x <= centerEnd
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (e1 == null) return false
                        
                        // Get screen dimensions
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        
                        // Define center area (avoid edges where sidebar/drawer touches happen)
                        val centerStart = screenWidth * 0.25f
                        val centerEnd = screenWidth * 0.75f
                        val topThird = screenHeight * 0.33f
                        val bottomThird = screenHeight * 0.67f
                        
                        // Check if swipe started from center area
                        val startX = e1.x
                        val startY = e1.y
                        if (startX < centerStart || startX > centerEnd) {
                            return false // Ignore swipes from sides
                        }
                        
                        val diffY = e2.y - e1.y
                        val diffX = e2.x - e1.x
                        
                        // Require stronger velocity for gesture to avoid conflicts with scrolling
                        val isVertical = kotlin.math.abs(diffY) > kotlin.math.abs(diffX)
                        val fastEnough = kotlin.math.abs(velocityY) > 1200 // Increased from 800
                        
                        if (isVertical && fastEnough) {
                            if (diffY < -150) { // Swipe up - increased threshold
                                try {
                                    toggleAppDrawer(true)
                                } catch (e: Exception) {
                                    android.util.Log.e("LauncherActivity", "Toggle app drawer failed", e)
                                }
                                return true
                            } else if (diffY > 150 && startY < topThird) { // Swipe down from top third only
                                try {
                                    startActivity(Intent(this@LauncherActivity, com.sevenk.launcher.search.GlobalSearchActivity::class.java))
                                } catch (e: Exception) {
                                    android.util.Log.e("LauncherActivity", "Global search failed", e)
                                }
                                return true
                            }
                        }
                        return false
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        // Disable onScroll for app drawer control to prevent conflicts
                        return false
                    }

                    override fun onLongPress(e: MotionEvent) {
                        try {
                            if (::appDrawerContainer.isInitialized && appDrawerContainer.visibility != View.VISIBLE) {
                                showHomeOptions()
                            }
                        } catch (_: Throwable) {}
                    }
                })
                android.util.Log.d("LauncherActivity", "Gesture detector setup completed")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Gesture detector setup failed", e)
            }

            // Set wallpaper
            try {
                setWallpaper()
                android.util.Log.d("LauncherActivity", "Wallpaper set")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Wallpaper setup failed", e)
            }
            
            // Setup dock and sidebar containers (content after apps load)
            try {
                setupDock()
                setupSidebar()
                android.util.Log.d("LauncherActivity", "Dock and sidebar setup completed")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Dock/sidebar setup failed", e)
            }

            // Optional blur for modern devices
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (!am.isLowRamDevice) {
                    initBlurPanels()
                    android.util.Log.d("LauncherActivity", "Blur panels initialized")
                }
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Blur panels setup failed", e)
            }

            // Async load apps and update UI
            try {
                loadAppsAsync()
                android.util.Log.d("LauncherActivity", "Apps loading started")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Apps loading failed", e)
            }

            // Long-press to edit dock/sidebar selections using the app drawer selection mode
            try {
                dock.setOnLongClickListener {
                    try {
                        startEditDockMode()
                    } catch (e: Exception) {
                        android.util.Log.e("LauncherActivity", "Edit dock mode failed", e)
                    }
                    true
                }
                sidebar.setOnLongClickListener {
                    try {
                        startEditSidebarMode()
                    } catch (e: Exception) {
                        android.util.Log.e("LauncherActivity", "Edit sidebar mode failed", e)
                    }
                    true
                }

                // Long-press on empty home to open quick actions
                homeScreen.setOnLongClickListener {
                    showHomeOptions()
                    true
                }

                // Also support long-press directly on the pager area (pages can consume touches)
                homePager.setOnLongClickListener {
                    showHomeOptions()
                    true
                }

                // Forward touch events to gesture detector but DO NOT consume them,
                homeScreen.isLongClickable = true
                homePager.isLongClickable = true
                homeScreen.setOnTouchListener { _, ev ->
                    gestureDetector.onTouchEvent(ev)
                    false
                }
                homePager.setOnTouchListener { _, ev ->
                    gestureDetector.onTouchEvent(ev)
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Long-press and gesture setup failed", e)
            }
            // Apply custom background (if any) once views are ready
            try {
                applyCustomBackgroundIfAny()
            } catch (_: Throwable) {}
            // Try to initialize with minimal configuration
            try {
                // Basic initialization fallback
                if (!::appWidgetManager.isInitialized) {
                    appWidgetManager = AppWidgetManager.getInstance(this)
                }
                if (!::appWidgetHost.isInitialized) {
                    appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
                }
                if (!::iconPackHelper.isInitialized) {
                    iconPackHelper = IconPackHelper(this)
                }
                
                // Load apps in fallback mode
                loadAppsAsync()
                android.util.Log.d("LauncherActivity", "Fallback initialization completed")
                
            } catch (fallbackError: Exception) {
                android.util.Log.e("LauncherActivity", "Fallback initialization also failed", fallbackError)
                // If even fallback fails, show error and finish
                Toast.makeText(this, "Critical error: Cannot start launcher", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Unhandled error during onCreate", e)
            Toast.makeText(this, "Error initializing launcher", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop receiving widget updates
        try { appWidgetHost.stopListening() } catch (_: Throwable) {}
    }

    override fun onStart() {
        super.onStart()
        // Begin receiving widget updates (required for RemoteViews to refresh)
        try { appWidgetHost.startListening() } catch (_: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        // Register preference change listener
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // Re-ensure searchBox exists in case of configuration or fallback layouts
        ensureSearchBoxReady()
        applyUserSettings()
        // Listen for package changes to auto-refresh drawer and home
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            registerReceiver(packageChangeReceiver, filter)
        } catch (_: Throwable) {}
        // Ensure fragments are attached, then restore widgets once
        if (!widgetsRestoredOnce) {
            homePager.post {
                restoreWidgetIfAny()
                widgetsRestoredOnce = true
            }
        }
        loadAppsAsync()
    }

    override fun onPause() {
        super.onPause()
        // Unregister preference change listener
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        // Stop listening for package changes to avoid leaks
        try { unregisterReceiver(packageChangeReceiver) } catch (_: Throwable) {}
    }

    // --- Package change handlers ---
    private fun onPackageRemoved(pkg: String) {
        try { IconCache.invalidate(pkg) } catch (_: Throwable) {}
        // Remove from app list
        appList = appList.filter { it.packageName != pkg }.toMutableList()
        // Remove from dock/sidebar/recents
        removeFromList(KEY_DOCK, pkg)
        removeFromList(KEY_SIDEBAR, pkg)
        removeFromList(KEY_RECENTS, pkg)
        // Remove from all home pages
        for (i in 0 until NORMAL_HOME_PAGES) {
            val list = loadHomePageList(i).toMutableList()
            if (list.remove(pkg)) saveHomePageList(i, list)
        }
        // Refresh UI
        refreshDrawerPages()
        rebuildDock()
        rebuildSidebar()
        refreshHomePages()
    }

    private fun onPackageAdded(pkg: String) {
        // Rebuild app list and refresh UI
        try { loadAppsAsync() } catch (_: Throwable) {}
        refreshDrawerPages()
        rebuildDock()
        rebuildSidebar()
        refreshHomePages()
    }

    private fun onPackageChanged(pkg: String) {
        // Invalidate icon cache and refresh adapters
        try { IconCache.invalidate(pkg) } catch (_: Throwable) {}
        try { loadAppsAsync() } catch (_: Throwable) {}
        refreshDrawerPages()
        rebuildDock()
        rebuildSidebar()
        refreshHomePages()
    }

    private fun applyUserSettings() {
        // Hide or show search box
        val hideSearch = prefs.getBoolean("hide_search_box", false)
        if (::searchBox.isInitialized) {
            searchBox.visibility = if (hideSearch) View.GONE else View.VISIBLE
        }
        // Dock/Sidebar visibility
        val showDock = prefs.getBoolean("show_dock", true)
        val showSidebar = prefs.getBoolean("show_sidebar", true)
        dock.visibility = if (showDock) View.VISIBLE else View.GONE
        sidebar.visibility = if (showSidebar) View.VISIBLE else View.GONE
        // Enable edge-driven overlay if sidebar is hidden
        enableSidebarOverlayIfHidden()
        // Label visibility for dock/sidebar adapters
        val showLabels = prefs.getBoolean("show_labels", true)
        if (::dockAdapter.isInitialized) {
            dockAdapter.showLabels = showLabels
        }
        if (::sidebarAdapter.isInitialized) {
            sidebarAdapter.showLabels = showLabels
            sidebarAdapter.iconSizePx = getSidebarIconSizePx()
            // Sidebar length may have changed; rebuild to apply new max items
            rebuildSidebar()
        }
        // Clock toggle on home
        try {
            val clock = findViewById<android.widget.TextClock>(R.id.timeClock)
            val showClock = prefs.getBoolean("show_clock", true)
            clock.visibility = if (showClock) View.VISIBLE else View.GONE
            // Long-press on clock opens Home Options (includes wallpaper set/clear)
            clock.isLongClickable = true
            clock.setOnLongClickListener {
                showHomeOptions()
                true
            }
        } catch (_: Throwable) {}
        // Update drawer swipe-close gesture
        updateDrawerGesture()
        // If columns changed, re-run setup to apply spanCount without resetting adapter
        setupAppDrawer()
        // Re-apply dock size and position
        if (::dockAdapter.isInitialized) {
            // Apply new icon size and rebuild to respect updated length
            dockAdapter.iconSizePx = getDockIconSizePx()
            applyDockDimensions()
            rebuildDock()
            applyDockPositionAndPadding()
        }
    }

    // This was a duplicate preference listener - removed

    // ---- Sidebar overlay (edge swipe) ----
    private fun enableSidebarOverlayIfHidden() {
        // Only allow overlay when user hides the sidebar in settings
        val showSidebar = prefs.getBoolean("show_sidebar", true)
        sidebarOverlayEnabled = !showSidebar
        sidebarEdgeHandle.visibility = if (sidebarOverlayEnabled) View.VISIBLE else View.GONE
        if (!sidebarOverlayEnabled) {
            // Ensure normal state
            sidebar.translationX = 0f
            sidebarScrim.visibility = View.GONE
            sidebarScrim.alpha = 0f
            sidebarOverlayOpen = false
            return
        }
        // Prepare sidebar off-screen to the right
        sidebar.post {
            val w = sidebar.width.takeIf { it > 0 } ?: return@post
            sidebar.translationX = w.toFloat()
            // Keep it invisible until drag starts to avoid intercepting touches
            if (sidebar.visibility != View.VISIBLE) sidebar.visibility = View.VISIBLE
            sidebarScrim.visibility = View.GONE
            sidebarScrim.alpha = 0f
            sidebarOverlayOpen = false
        }
    }

    private fun setupSidebarOverlayGestures() {
        // Edge handle detects horizontal drags to reveal sidebar
        sidebarEdgeHandle.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var dragging = false
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (!sidebarOverlayEnabled) return false
                
                // Only handle edge touches (rightmost 10% of screen)
                val screenWidth = resources.displayMetrics.widthPixels
                val rightEdgeStart = screenWidth * 0.9f
                
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        if (event.rawX < rightEdgeStart) {
                            return false // Not on right edge
                        }
                        dragging = true
                        // Ensure sidebar is at start position
                        sidebar.post {
                            val w = sidebar.width
                            if (w > 0) setSidebarProgress(0f)
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) return false
                        val dx = (downX - event.rawX) * -1f // finger moving left increases dx
                        val w = sidebar.width
                        if (w <= 0) return false
                        val p = (dx / w).coerceIn(0f, 1f)
                        setSidebarProgress(p)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!dragging) return false
                        dragging = false
                        val w = sidebar.width
                        val current = if (w > 0) 1f - (sidebar.translationX / w) else 0f
                        if (current > 0.3f) openSidebarOverlay(true) else closeSidebarOverlay(true)
                        return true
                    }
                }
                return false
            }
        })

        // Tap on scrim to close
        sidebarScrim.setOnClickListener {
            if (sidebarOverlayEnabled && sidebarOverlayOpen) closeSidebarOverlay(true)
        }
    }

    private fun setSidebarProgress(progress: Float) {
        val w = sidebar.width.takeIf { it > 0 } ?: return
        val p = progress.coerceIn(0f, 1f)
        sidebar.translationX = (1f - p) * w
        if (sidebarScrim.visibility != View.VISIBLE) sidebarScrim.visibility = View.VISIBLE
        sidebarScrim.alpha = 0.6f * p
    }

    private fun openSidebarOverlay(animated: Boolean = true) {
        val w = sidebar.width.takeIf { it > 0 } ?: return
        sidebarScrim.visibility = View.VISIBLE
        if (animated) {
            sidebar.animate().translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            sidebarScrim.animate().alpha(0.6f).setDuration(220).setInterpolator(DecelerateInterpolator()).withStartAction {
                sidebarScrim.visibility = View.VISIBLE
            }.start()
        } else {
            sidebar.translationX = 0f
            sidebarScrim.alpha = 0.6f
            sidebarScrim.visibility = View.VISIBLE
        }
        sidebarOverlayOpen = true
    }

    private fun closeSidebarOverlay(animated: Boolean = true) {
        val w = sidebar.width.takeIf { it > 0 } ?: return
        if (animated) {
            sidebar.animate().translationX(w.toFloat()).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            sidebarScrim.animate().alpha(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).withEndAction {
                sidebarScrim.visibility = View.GONE
            }.start()
        } else {
            sidebar.translationX = w.toFloat()
            sidebarScrim.alpha = 0f
            sidebarScrim.visibility = View.GONE
        }
        sidebarOverlayOpen = false
    }

    // ---- Dock customization helpers ----
    private fun getDockIconSizePx(): Int {
        val mode = prefs.getInt("dock_icon_size", 1).coerceIn(0, 2)
        val dimen = when (mode) {
            0 -> R.dimen.app_icon_size_small
            2 -> R.dimen.app_icon_size_large
            else -> R.dimen.app_icon_size_medium
        }
        return resources.getDimensionPixelSize(dimen)
    }

    private fun getSidebarIconSizePx(): Int {
        val mode = prefs.getInt("sidebar_icon_size", 1).coerceIn(0, 2)
        val dimen = when (mode) {
            0 -> R.dimen.app_icon_size_small
            2 -> R.dimen.app_icon_size_large
            else -> R.dimen.app_icon_size_medium
        }
        return resources.getDimensionPixelSize(dimen)
    }

    // General drawer icon size (used for background prewarm and generic calculations)
    private fun getGeneralIconSizePx(): Int {
        val mode = prefs.getInt("icon_size", 1).coerceIn(0, 2)
        val dimen = when (mode) {
            0 -> R.dimen.app_icon_size_small
            2 -> R.dimen.app_icon_size_large
            else -> R.dimen.app_icon_size_medium
        }
        return resources.getDimensionPixelSize(dimen)
    }

    // Note: This controls ONLY the initial default count. Once user selects dock apps, we do not cap the count.
    private fun getDockMaxItems(): Int = prefs.getInt("dock_initial_count", 4).coerceIn(3, 8)

    private fun getSidebarMaxItems(): Int = prefs.getInt("sidebar_length", 12).coerceIn(4, 20)

    private fun getDockPosition(): String = prefs.getString("dock_position", "right") ?: "right"

    private fun applyDockDimensions() {
        // Adjust dock height based on icon size preset
        val lp = dock.layoutParams
        val iconPx = getDockIconSizePx()
        val hasLabels = isShowLabels()
        val labelAllowance = if (hasLabels) dp(24) else 0
        val verticalPadding = dp(16)
        lp.height = iconPx + labelAllowance + verticalPadding
        dock.layoutParams = lp
    }

    private fun applyDockPositionAndPadding() {
        val lm = (dock.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager) ?: return
        val pos = getDockPosition()
        when (pos) {
            "left" -> {
                lm.stackFromEnd = false
                val top = dock.paddingTop
                val bottom = dock.paddingBottom
                val end = dock.paddingEnd
                val start = dp(16)
                dock.setPadding(start, top, end, bottom)
                if (dockAdapter.itemCount > 0) dock.scrollToPosition(0)
            }
            "center" -> {
                lm.stackFromEnd = false
                val top = dock.paddingTop
                val bottom = dock.paddingBottom
                // Estimate total content width to center items
                val itemSize = getDockIconSizePx()
                val paddingItem = dp(8) * 2
                val spacing = dp(8)
                val count = dockAdapter.itemCount
                val total = if (count <= 0) 0 else count * (itemSize + paddingItem) + (count - 1) * spacing
                val rvWidth = if (dock.width > 0) dock.width else dock.measuredWidth
                val sidePad = ((rvWidth - total) / 2).coerceAtLeast(dp(16))
                dock.setPadding(sidePad, top, sidePad, bottom)
                if (dockAdapter.itemCount > 0) dock.scrollToPosition(0)
            }
            else -> { // right
                lm.stackFromEnd = true
                val top = dock.paddingTop
                val bottom = dock.paddingBottom
                val start = dp(16)
                val end = dock.paddingEnd
                dock.setPadding(start, top, end, bottom)
                if (dockAdapter.itemCount > 0) dock.scrollToPosition(dockAdapter.itemCount - 1)
            }
        }
    }

    private fun updateDrawerGesture() {
        val enableSwipeClose = prefs.getBoolean("enable_swipe_close", false)
        if (!enableSwipeClose) {
            appDrawerContainer.setOnTouchListener(null)
            return
        }

        // Only allow forwarding swipe-down to gestureDetector when the list is at top
        appDrawerContainer.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var allowSwipeClose = false
            var thresholdReached = false
            var vt: VelocityTracker? = null
            val minDistancePx = dp(72).toFloat() // require a meaningful pull
            val minVelocity = ViewConfiguration.get(this@LauncherActivity).scaledMinimumFlingVelocity * 2
            var dragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        vt?.recycle(); vt = VelocityTracker.obtain()
                        vt?.addMovement(event)
                        downX = event.x
                        downY = event.y
                        thresholdReached = false
                        // At top if current page cannot scroll up
                        val frag = getCurrentDrawerFragment()
                        allowSwipeClose = frag?.isAtTop() == true
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        vt?.addMovement(event)
                        val dx = event.x - downX
                        val dy = event.y - downY
                        val absDx = kotlin.math.abs(dx)
                        val absDy = kotlin.math.abs(dy)
                        if (!thresholdReached) {
                            if (allowSwipeClose && absDy > absDx && absDy > dp(8)) {
                                // begin forwarding
                                thresholdReached = true
                            } else {
                                return false
                            }
                        }
                        // Interactive drag preview
                        if (allowSwipeClose) {
                            val trans = dy.coerceAtLeast(0f)
                            if (trans > 0f) {
                                dragging = true
                                val h = appDrawerContainer.height.toFloat().coerceAtLeast(1f)
                                val alpha = (1f - (trans / h)).coerceIn(0f, 1f)
                                appDrawerContainer.translationY = trans
                                appDrawerContainer.alpha = alpha
                            }
                        }
                        if (absDy > minDistancePx) {
                            // If drag surpasses meaningful pull and moving down, trigger close via gesture detector for consistency
                            if (dy > 0 && allowSwipeClose) {
                                // handled on UP with snapping; keep consuming
                                return true
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        vt?.recycle(); vt = null
                        // Snap decision
                        if (dragging) {
                            val trans = appDrawerContainer.translationY
                            val h = appDrawerContainer.height.toFloat().coerceAtLeast(1f)
                            val progress = trans / h
                            val shouldClose = progress > 0.33f || (event.actionMasked == MotionEvent.ACTION_UP && (vt?.let { it.computeCurrentVelocity(1000); it.yVelocity } ?: 0f) > minVelocity)
                            if (shouldClose) {
                                animateDrawer(false)
                            } else {
                                // restore
                                appDrawerContainer.animate().translationY(0f).alpha(1f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()
                            }
                        }
                        downX = 0f
                        downY = 0f
                        thresholdReached = false
                        dragging = false
                    }
                }
                // If allowed and threshold met, forward events to global gesture detector (which closes drawer)
                return allowSwipeClose
            }
        })
    }

    private fun initBlurPanels() {
        if (!shouldUseRuntimeBlur()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    dock.setRenderEffect(null)
                    sidebar.setRenderEffect(null)
                } catch (_: Throwable) {}
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blur = RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)
                dock.setRenderEffect(blur)
                sidebar.setRenderEffect(blur)
            } catch (_: Throwable) {
                // Fallback: disable blur for this device if it fails repeatedly
                blurFailureCount++
                if (blurFailureCount >= 2) {
                    try { prefs.edit().putBoolean("enable_runtime_blur", false).apply() } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun setupDragDropTargets() {
        val dragListener = View.OnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val ok = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    ok
                }
                DragEvent.ACTION_DROP -> {
                    val item = event.clipData?.getItemAt(0)
                    val pkg = item?.text?.toString()?.trim().orEmpty()
                    if (pkg.isEmpty()) return@OnDragListener false
                    when (v) {
                        dock -> {
                            removeFromList(KEY_DOCK, pkg)
                            removeFromList(KEY_SIDEBAR, pkg)
                            removeFromAllHomePages(pkg)
                            addToList(KEY_DOCK, pkg)
                            rebuildDock()
                            true
                        }
                        sidebar -> {
                            removeFromList(KEY_DOCK, pkg)
                            removeFromList(KEY_SIDEBAR, pkg)
                            removeFromAllHomePages(pkg)
                            addToList(KEY_SIDEBAR, pkg)
                            rebuildSidebar()
                            true
                        }
                        homeScreen, homePager -> {
                            removeFromList(KEY_DOCK, pkg)
                            removeFromList(KEY_SIDEBAR, pkg)
                            removeFromAllHomePages(pkg)
                            val contentIndex = (homePager.currentItem - 2).coerceIn(0, NORMAL_HOME_PAGES - 1)
                            addToHomePage(contentIndex, pkg)
                            refreshHomePages()
                            true
                        }
                        else -> false
                    }
                }
                else -> true
            }
        }
        dock.setOnDragListener(dragListener)
        sidebar.setOnDragListener(dragListener)
        homeScreen.setOnDragListener(dragListener)
        homePager.setOnDragListener(dragListener)
    }

    // Remove given package from all home pages
    private fun removeFromAllHomePages(pkg: String) {
        for (page in 0 until getNormalHomePages()) {
            removeFromHomePage(page, pkg)
        }
    }

    private fun showAddDialog(app: AppInfo) {
        val current = homePager.currentItem
        // With two special pages (0=TODO, 1=Stan), content pages start at position 2
        val contentIndex = (current - 2).coerceIn(0, NORMAL_HOME_PAGES - 1)
        val actions = listOf(
            "Add to Dock" to {
                addToList(KEY_DOCK, app.packageName)
                rebuildDock()
            },
            "Add to Sidebar" to {
                addToList(KEY_SIDEBAR, app.packageName)
                rebuildSidebar()
            },
            "Add to Home (Page ${'$'}{contentIndex + 1})" to {
                addToHomePage(contentIndex, app.packageName)
                refreshHomePages()
            }
        )
        showGlassActionSheet(app.name, actions)
    }

    private fun isShowLabels(): Boolean = prefs.getBoolean(KEY_SHOW_LABELS, true)

    // Drawer: columns preference (3..7), default 5
    private fun getDrawerColumns(): Int {
        val cols = prefs.getInt("drawer_columns", 5)
        return cols.coerceIn(3, 7)
    }
    private fun setShowLabels(value: Boolean) { prefs.edit().putBoolean(KEY_SHOW_LABELS, value).apply() }
    private fun toggleLabels() {
        val newVal = !isShowLabels()
        setShowLabels(newVal)
        dockAdapter.showLabels = newVal
        sidebarAdapter.showLabels = newVal
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun addToList(key: String, pkg: String) {
        val current = loadPackageList(key).toMutableList()
        if (current.contains(pkg)) return
        current.add(pkg)
        savePackageList(key, current)
    }

    private fun removeFromList(key: String, pkg: String) {
        val current = loadPackageList(key).toMutableList()
        if (!current.remove(pkg)) return
        savePackageList(key, current)
    }

    // If user tries to edit dock/sidebar when none saved yet, initialize with currently shown items
    private fun ensureListInitialized(key: String, currentItems: List<AppInfo>) {
        val existing = loadPackageList(key)
        if (existing.isEmpty() && currentItems.isNotEmpty()) {
            val ordered = currentItems.map { it.packageName }
            savePackageList(key, ordered)
        }
    }

    private fun moveBetween(fromKey: String, toKey: String, pkg: String) {
        val from = loadPackageList(fromKey).toMutableList()
        val to = loadPackageList(toKey).toMutableList()
        if (from.remove(pkg) && !to.contains(pkg)) {
            to.add(pkg)
            savePackageList(fromKey, from)
            savePackageList(toKey, to)
        }
    }

    // ---- Folder APIs (in-memory) ----
    fun getFolders(pageIndex: Int): List<Folder> {
        if (!homeFolders.containsKey(pageIndex)) {
            homeFolders[pageIndex] = loadFoldersFromPrefs(pageIndex)
        }
        return homeFolders[pageIndex] ?: emptyList()
    }

    fun createFolder(pageIndex: Int, name: String, initialPkg: String? = null) {
        val list = homeFolders.getOrPut(pageIndex) { loadFoldersFromPrefs(pageIndex) }
        val folder = Folder(name.ifBlank { "Folder" }, mutableListOf())
        if (initialPkg != null) {
            // Find the app info for the package
            val app = appList.find { it.packageName == initialPkg }
            if (app != null) folder.apps.add(app)
        }
        list.add(folder)
        saveFoldersToPrefs(pageIndex)
    }

    fun renameFolder(pageIndex: Int, folderIndex: Int, newName: String) {
        val list = homeFolders.getOrPut(pageIndex) { loadFoldersFromPrefs(pageIndex) }
        list.getOrNull(folderIndex)?.name = newName.ifBlank { "Folder" }
        saveFoldersToPrefs(pageIndex)
    }

    fun deleteFolder(pageIndex: Int, folderIndex: Int, moveAppsBackToPage: Boolean) {
        val list = homeFolders.getOrPut(pageIndex) { loadFoldersFromPrefs(pageIndex) }
        val folder = list.getOrNull(folderIndex) ?: return
        if (moveAppsBackToPage) {
            // Add folder apps back to the page shortcuts
            for (app in folder.apps) addToHomePage(pageIndex, app.packageName)
        }
        list.removeAt(folderIndex)
        saveFoldersToPrefs(pageIndex)
    }

    fun addToFolder(pageIndex: Int, folderIndex: Int, pkg: String) {
        val list = homeFolders.getOrPut(pageIndex) { loadFoldersFromPrefs(pageIndex) }
        val folder = list.getOrNull(folderIndex) ?: return
        val app = appList.find { it.packageName == pkg }
        if (app != null && !folder.apps.contains(app)) {
            folder.apps.add(app)
        }
        saveFoldersToPrefs(pageIndex)
    }

    fun removeFromFolder(pageIndex: Int, folderIndex: Int, pkg: String) {
        val list = homeFolders.getOrPut(pageIndex) { loadFoldersFromPrefs(pageIndex) }
        val folder = list.getOrNull(folderIndex) ?: return
        val app = appList.find { it.packageName == pkg }
        if (app != null) {
            folder.apps.remove(app)
        }
        saveFoldersToPrefs(pageIndex)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun setWallpaper() {
        // Some OEMs place wallpaper in protected storage requiring READ permissions.
        // Avoid crashing: try/catch and fall back to a neutral transparent background.
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable
            if (wallpaperDrawable != null) {
                homeScreen.background = wallpaperDrawable
            } else {
                // Fallback if drawable is null
                homeScreen.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        } catch (se: SecurityException) {
            android.util.Log.w("LauncherActivity", "Wallpaper requires permission; using transparent background", se)
            homeScreen.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } catch (t: Throwable) {
            android.util.Log.w("LauncherActivity", "Failed to set wallpaper; using transparent background", t)
            homeScreen.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    // Click handler referenced by activity_launcher.xml (android:onClick="onCloseDrawerClick")
    @Suppress("UNUSED_PARAMETER")
    fun onCloseDrawerClick(v: View) {
        try {
            toggleAppDrawer(false)
        } catch (t: Throwable) {
            android.util.Log.e("LauncherActivity", "onCloseDrawerClick failed", t)
        }
    }

    private lateinit var dockAdapter: DockSidebarAdapter
    private lateinit var sidebarAdapter: DockSidebarAdapter
    private var searchJob: kotlinx.coroutines.Job? = null
    // Selection state
    // When not null, selection is targeting a folder (pageIndex to folderIndex)
    private var folderSelectTarget: Pair<Int, Int>? = null
    // When true, selection targets dock or sidebar respectively
    private var dockEditMode: Boolean = false
    private var sidebarEditMode: Boolean = false
    // Pending state for custom icon picking
    private var pendingCustomIconPkg: String? = null

    // ---- Widget persistence helpers ----
    private fun widgetKey(pageIndex: Int) = "widgets_page_$pageIndex"
    // Legacy bug: earlier builds saved under a literal key "widgets_page_$pageIndex" (no interpolation).
    // Keep a helper to migrate from that single legacy key.
    private fun legacyWidgetKey(): String = "widgets_page_${'$'}pageIndex"
    private fun loadWidgetIdsForPage(pageIndex: Int): List<Int> {
        // Read the correct per-page key first
        var raw = prefs.getString(widgetKey(pageIndex), "") ?: ""
        // If empty, try migrating from the legacy literal key
        if (raw.isBlank()) {
            val legacy = prefs.getString(legacyWidgetKey(), "") ?: ""
            if (legacy.isNotBlank()) {
                raw = legacy
                // Write migrated value to proper key and clear legacy to avoid double-loads
                prefs.edit()
                    .putString(widgetKey(pageIndex), raw)
                    .remove(legacyWidgetKey())
                    .apply()
            }
        }
        return raw.split(';').mapNotNull { t ->
            val s = t.trim(); if (s.isEmpty()) null else s.toIntOrNull()
        }
    }
    private fun saveWidgetIdsForPage(pageIndex: Int, ids: List<Int>) {
        prefs.edit().putString(widgetKey(pageIndex), ids.joinToString(";"))
            .apply()
    }

    // ---- Widget UI helpers ----
    private fun hasWidget(): Boolean {
        // Widgets are only on regular content pages (positions >= 2); TODO (0) and Stan (1) host none
        val pos = homePager.currentItem
        if (pos < 2) return false
        val contentIndex = (pos - 2).coerceAtLeast(0)
        return loadWidgetIdsForPage(contentIndex).isNotEmpty()
    }

    private fun addAppWidget(appWidgetId: Int, contentIndex: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val hostView = appWidgetHost.createView(this, appWidgetId, info)
        val frag = supportFragmentManager.fragments.firstOrNull { f ->
            (f is HomePageFragment) && f.getPageIndex() == contentIndex
        } as? HomePageFragment
        val container = frag?.getWidgetsContainer() ?: widgetsContainer
        container.addView(
            hostView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        val ids = loadWidgetIdsForPage(contentIndex).toMutableList()
        if (!ids.contains(appWidgetId)) ids.add(appWidgetId)
        saveWidgetIdsForPage(contentIndex, ids)
        try { appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listView) } catch (_: Throwable) {}
        // Ensure visibility reflects the presence of widgets on this page
        updateWidgetVisibilityForCurrentPage()
    }

    private fun removeCurrentWidget() {
        // With two special pages (0=TODO, 1=Stan), content pages start at position 2
        val contentIndex = (homePager.currentItem - 2).coerceIn(0, NORMAL_HOME_PAGES - 1)
        val ids = loadWidgetIdsForPage(contentIndex)
        // Remove views from UI
        val frag = supportFragmentManager.fragments.firstOrNull { f ->
            (f is HomePageFragment) && f.getPageIndex() == contentIndex
        } as? HomePageFragment
        val container = frag?.getWidgetsContainer() ?: widgetsContainer
        container.removeAllViews()
        // Delete widget IDs from host
        for (id in ids) {
            try { appWidgetHost.deleteAppWidgetId(id) } catch (_: Throwable) {}
        }
        saveWidgetIdsForPage(contentIndex, emptyList())
        // Update visibility now that widgets are removed
        updateWidgetVisibilityForCurrentPage()
    }

    private fun restoreWidgetIfAny() {
        // For each page, re-create any saved widget host views
        for (page in 0 until NORMAL_HOME_PAGES) {
            val ids = loadWidgetIdsForPage(page)
            if (ids.isEmpty()) continue
            val frag = supportFragmentManager.fragments.firstOrNull { f ->
                (f is HomePageFragment) && f.getPageIndex() == page
            } as? HomePageFragment
            val container = frag?.getWidgetsContainer() ?: widgetsContainer
            container.removeAllViews()
            for (id in ids) {
                val info = appWidgetManager.getAppWidgetInfo(id)
                val hostView = appWidgetHost.createView(this, id, info)
                container.addView(
                    hostView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                try { appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.listView) } catch (_: Throwable) {}
            }
        }
        updateWidgetVisibilityForCurrentPage()
    }

    private fun updateWidgetVisibilityForCurrentPage() {
        // Show a page's widget container if that page has any widgets saved; hide otherwise.
        // Stan page is position 0 and never hosts widgets.
        supportFragmentManager.fragments.forEach { f ->
            if (f is HomePageFragment) {
                val ids = loadWidgetIdsForPage(f.getPageIndex())
                val container = f.getWidgetsContainer()
                container?.visibility = if (ids.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun configureOrAddWidget(appWidgetId: Int, configure: ComponentName?, contentIndex: Int) {
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                startActivityForResult(intent, REQ_CONFIGURE_APPWIDGET)
            } catch (_: Throwable) {
                // If config activity fails, just add directly
                addAppWidget(appWidgetId, contentIndex)
            }
        } else {
            addAppWidget(appWidgetId, contentIndex)
        }
    }

    

    private fun setupDock() {
        val lm = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        dock.layoutManager = lm
        dockAdapter = DockSidebarAdapter(mutableListOf(), onClick = { app ->
            launchApp(app)
        }, onLongPress = { _ ->
            // Open multi-select editor for Dock
            startEditDockMode()
        }, showLabels = isShowLabels(), iconSizePx = getDockIconSizePx(), iconPackHelper = iconPackHelper)
        dock.adapter = dockAdapter
        
        // Apply enhanced glass effect to dock
        try {
            glassEffectHelper.applyGlassEffect(
                view = dock,
                intensity = GlassEffectHelper.GlassIntensity.NORMAL
            )
            Log.d("LauncherActivity", "Enhanced glass effect applied to dock successfully")
        } catch (e: Exception) {
            Log.e("LauncherActivity", "Failed to apply enhanced glass effect to dock", e)
            // Fallback to basic glass effect if available
            try {
                applyGlassBlurIfPossible(dock, 20f)
            } catch (fallbackException: Exception) {
                Log.e("LauncherActivity", "Fallback glass effect also failed", fallbackException)
            }
        }
        
        // Spacing between dock items
        dock.addItemDecoration(SpaceItemDecoration(dp(8), RecyclerView.HORIZONTAL))
        dock.setHasFixedSize(true)
        // Smooth scrolling tweaks
        (dock.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        dock.isNestedScrollingEnabled = false
        dock.setItemViewCacheSize(20)
        attachDragHelper(dock, dockAdapter, isDock = true)
        // Apply dimensions before first layout
        applyDockDimensions()
        rebuildDock()
    }

    private fun setupSidebar() {
        sidebar.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        sidebarAdapter = DockSidebarAdapter(mutableListOf(), onClick = { app ->
            launchApp(app)
        }, onLongPress = { app ->
            // Show context actions: remove from Sidebar/Recents or open editor
            showSidebarItemOptions(app)
        }, showLabels = isShowLabels(), iconSizePx = getSidebarIconSizePx(), iconPackHelper = iconPackHelper)
        sidebar.adapter = sidebarAdapter
        
        // Apply enhanced glass effect to sidebar
        try {
            glassEffectHelper.applyGlassEffect(
                view = sidebar,
                intensity = GlassEffectHelper.GlassIntensity.LIGHT
            )
            Log.d("LauncherActivity", "Enhanced glass effect applied to sidebar successfully")
        } catch (e: Exception) {
            Log.e("LauncherActivity", "Failed to apply enhanced glass effect to sidebar", e)
            // Fallback to basic glass effect if available
            try {
                applyGlassBlurIfPossible(sidebar, 20f)
            } catch (fallbackException: Exception) {
                Log.e("LauncherActivity", "Fallback glass effect for sidebar also failed", fallbackException)
            }
        }
        
        // Spacing between sidebar items
        sidebar.addItemDecoration(SpaceItemDecoration(dp(8), RecyclerView.VERTICAL))
        sidebar.setHasFixedSize(true)
        // Smooth scrolling tweaks
        (sidebar.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        sidebar.isNestedScrollingEnabled = true // Enable nested scrolling for better touch handling
        sidebar.setItemViewCacheSize(20)
        
        // Add touch listener to prevent sidebar scrolling from triggering app drawer
        sidebar.setOnTouchListener { _, event ->
            // Allow normal scrolling within sidebar, but prevent gesture detection outside
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Mark that we're interacting with sidebar
                    true // Consume the touch event
                }
                else -> false // Let RecyclerView handle other events normally
            }
        }
        
        attachDragHelper(sidebar, sidebarAdapter, isDock = false)
        rebuildSidebar()
    }

    private fun rebuildDock() {
        val packages = loadPackageList(KEY_DOCK)
        // For first-time setup, show a small default set; once the user selects apps, allow unlimited
        val initialCount = getDockMaxItems()
        val selected = if (packages.isEmpty()) appList.take(initialCount) else appList.filter { it.packageName in packages }
        // Apply icon size then update list efficiently
        dockAdapter.iconSizePx = getDockIconSizePx()
        dockAdapter.setItems(selected)
        // Apply alignment/padding
        applyDockPositionAndPadding()
    }

    private fun rebuildSidebar() {
        val pinnedPkgs = loadPackageList(KEY_SIDEBAR)
        val recentPkgs = loadPackageList(KEY_RECENTS)
        val pinned = if (pinnedPkgs.isEmpty()) emptyList() else appList.filter { it.packageName in pinnedPkgs }
        // Limit recent items to 4, and keep pinned items unlimited and separate
        val recentLimit = 4
        val recent = appList.filter { it.packageName in recentPkgs && it.packageName !in pinnedPkgs }
            .sortedBy { recentPkgs.indexOf(it.packageName) }
            .take(recentLimit)
        val combined = (recent + pinned).distinctBy { it.packageName }
        sidebarAdapter.setItems(combined)
    }

    private fun savePackageList(key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString(";"))
            .apply()
    }

    private fun loadPackageList(key: String): List<String> {
        val raw = prefs.getString(key, "") ?: ""
        return raw.split(';').mapNotNull { val t = it.trim(); if (t.isEmpty()) null else t }
    }

    // Persist per-page shortcuts
    private fun saveHomePageList(pageIndex: Int, packages: List<String>) {
        prefs.edit().putString("home_page_$pageIndex", packages.joinToString(";"))
            .apply()
    }

    fun loadHomePageList(pageIndex: Int): List<String> {
        val raw = prefs.getString("home_page_$pageIndex", "") ?: ""
        return raw.split(';').mapNotNull { val t = it.trim(); if (t.isEmpty()) null else t }
    }

    fun addToHomePage(pageIndex: Int, pkg: String) {
        val list = loadHomePageList(pageIndex).toMutableList()
        if (!list.contains(pkg)) {
            list.add(pkg)
            saveHomePageList(pageIndex, list)
        }
    }

    fun removeFromHomePage(pageIndex: Int, pkg: String) {
        val list = loadHomePageList(pageIndex).toMutableList()
        if (list.remove(pkg)) {
            saveHomePageList(pageIndex, list)
        }
    }

    fun moveHomeShortcut(fromPage: Int, toPage: Int, pkg: String) {
        if (toPage < 0 || toPage >= NORMAL_HOME_PAGES) return
        val from = loadHomePageList(fromPage).toMutableList()
        val to = loadHomePageList(toPage).toMutableList()
        if (from.remove(pkg)) {
            if (!to.contains(pkg)) to.add(pkg)
            saveHomePageList(fromPage, from)
            saveHomePageList(toPage, to)
        }
    }

    fun refreshHomePages() {
        // Refresh all home pages
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is HomePageFragment) {
                fragment.refreshData()
            }
        }
    }
    
    /**
     * Refreshes the app drawer with current settings
     */
    private fun refreshAppDrawer() {
        if (::drawerPagerAdapter.isInitialized) {
            // Get the current app drawer fragment if it exists
            val currentFragment = supportFragmentManager.findFragmentByTag("f${appDrawerPager.currentItem}")
            if (currentFragment is AppDrawerFragment) {
                currentFragment.refreshAppDrawer()
            } else {
                // If we can't find the current fragment, refresh the whole adapter
                drawerPagerAdapter.notifyDataSetChanged()
            }
        }
    }

    // --- Public wrappers for fragment access ---
    fun addPackageToListPublic(key: String, pkg: String) {
        addToList(key, pkg)
        when (key) {
            KEY_DOCK -> rebuildDock()
            KEY_SIDEBAR -> rebuildSidebar()
        }
    }

    fun removePackageFromListPublic(key: String, pkg: String) {
        removeFromList(key, pkg)
        when (key) {
            KEY_DOCK -> rebuildDock()
            KEY_SIDEBAR -> rebuildSidebar()
        }
    }

    fun rebuildDockPublic() { rebuildDock() }

    fun removeFromAllHomePagesPublic(pkg: String) { removeFromAllHomePages(pkg) }

    // Expose count of regular home pages for fragments
    fun getNormalHomePages(): Int = NORMAL_HOME_PAGES

    // Public methods for accessing private members from fragments
    fun getCurrentHomePage(): Int {
        return if (::homePager.isInitialized) homePager.currentItem - 1 else -1
    }

    fun rebuildSidebarPublic() {
        // Implementation for rebuilding sidebar
        try {
            rebuildSidebar()
        } catch (e: Exception) {
            // Handle error if rebuildSidebar method doesn't exist
            android.util.Log.w("LauncherActivity", "rebuildSidebar method not found: ${e.message}")
        }
    }

    private fun openAppPicker(forDock: Boolean) {
        if (appList.isEmpty()) return
        val key = if (forDock) KEY_DOCK else KEY_SIDEBAR
        val current = loadPackageList(key).toMutableSet()
        showMultiSelectAppsSheet(
            title = if (forDock) "Edit Dock" else "Edit Sidebar",
            preselected = current
        ) { selected ->
            // Save in chosen order of appList filtered by selected
            val ordered = appList.filter { it.packageName in selected }.map { it.packageName }
            savePackageList(key, ordered)
            if (forDock) rebuildDock() else rebuildSidebar()
        }
    }

    private fun showMultiSelectAppsSheet(
        title: String,
        preselected: Set<String>,
        onSave: (Set<String>) -> Unit
    ) {
        val sheet = BottomSheetDialog(this)
        val selected = preselected.toMutableSet()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        val titleView = android.widget.TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        root.addView(titleView)

        val scroll = android.widget.ScrollView(this)
        val list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        appList.forEach { app ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(R.drawable.glass_panel)
            }

            val check = android.widget.CheckBox(this).apply {
                isChecked = app.packageName in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                }
            }

            val name = android.widget.TextView(this).apply {
                text = app.name
                textSize = 14f
                setTextColor(0xFFEAF6FF.toInt())
                setPadding(dp(8), 0, 0, 0)
            }

            row.setOnClickListener { check.isChecked = !check.isChecked }
            row.addView(check)
            row.addView(name)

            val rowLp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            list.addView(row, rowLp)
        }

        scroll.addView(list)
        val scrollLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dp(320)
        ).apply { bottomMargin = dp(12) }
        root.addView(scroll, scrollLp)

        val save = android.widget.TextView(this).apply {
            text = "Save"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(this@LauncherActivity, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                sheet.dismiss()
                onSave(selected)
            }
        }
        val saveLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        root.addView(save, saveLp)

        val cancel = android.widget.TextView(this).apply {
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

    private fun attachDragHelper(rv: RecyclerView, adapter: DockSidebarAdapter, isDock: Boolean) {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            if (isDock) androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT else androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val list = adapter.items
                val item = list.removeAt(from)
                list.add(to, item)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE) {
                    // Persist order
                    val pkgs = adapter.items.map { it.packageName }
                    savePackageList(if (isDock) KEY_DOCK else KEY_SIDEBAR, pkgs)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(rv)
    }


    private fun toggleAppDrawer(show: Boolean) {
        Perf.begin("toggleAppDrawer:" + if (show) "open" else "close")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            setupAppDrawer()
            // Prepare for animation
            appDrawerContainer.clearAnimation()
            appDrawerContainer.visibility = View.VISIBLE
            if (appDrawerContainer.height == 0) {
                // Post to ensure we have dimensions
                appDrawerContainer.post { animateDrawer(true) }
            } else {
                animateDrawer(true)
            }
            // Do NOT auto-focus or auto-show keyboard. Only show on explicit tap.
            if (::searchBox.isInitialized) {
                searchBox.clearFocus()
                imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
            }
        } else {
            // Hide keyboard and selection modes then animate close
            if (::searchBox.isInitialized) {
                imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
                searchBox.clearFocus()
            }
            if (folderSelectTarget != null || dockEditMode || sidebarEditMode) endSelectionModes()
            appDrawerContainer.clearAnimation()
            if (appDrawerContainer.visibility == View.VISIBLE) animateDrawer(false) else appDrawerContainer.visibility = View.GONE
        }
        Perf.end()
    }

    private fun animateDrawer(show: Boolean) {
        Perf.begin("animateDrawer:" + if (show) "open" else "close")
        val h = appDrawerContainer.height.takeIf { it > 0 } ?: appDrawerContainer.measuredHeight
        if (h <= 0) { appDrawerContainer.visibility = if (show) View.VISIBLE else View.GONE; return }
        if (show) {
            appDrawerContainer.translationY = h.toFloat()
            appDrawerContainer.alpha = 0f
            appDrawerContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .withEndAction { appDrawerContainer.translationY = 0f }
                .start()
        } else {
            appDrawerContainer.animate()
                .translationY(h.toFloat())
                .alpha(0f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .withEndAction {
                    appDrawerContainer.visibility = View.GONE
                    appDrawerContainer.translationY = 0f
                    appDrawerContainer.alpha = 1f
                }
                .start()
        }
        Perf.end()
    }

    private var isSavingSelection: Boolean = false

    private fun setupAppDrawer() {
        // Ensure pager sections reflect current section order
        drawerPagerAdapter.setSections(getDrawerSections())
        refreshDrawerPages()
        // Selection bar controls
        selectionBar = findViewById(R.id.selectionBar)
        saveSelectionBtn = findViewById(R.id.saveSelection)
        cancelSelectionBtn = findViewById(R.id.cancelSelection)
        saveSelectionBtn.setOnClickListener {
            if (isSavingSelection) return@setOnClickListener
            isSavingSelection = true
            val adapter = try { getCurrentPageAdapter() } catch (_: Throwable) { null }
            if (adapter == null) { isSavingSelection = false; return@setOnClickListener }
            val selectedPkgs = adapter.getSelectedPackages()
            if (folderSelectTarget != null) {
                val (pIdx, fIdx) = folderSelectTarget!!
                for (pkg in selectedPkgs) {
                    addToFolder(pIdx, fIdx, pkg)
                    removeFromHomePage(pIdx, pkg)
                }
                refreshHomePages()
            } else if (dockEditMode) {
                // Save dock selection in appList order filtered by selected
                val ordered = appList.filter { it.packageName in selectedPkgs }.map { it.packageName }
                savePackageList(KEY_DOCK, ordered)
                rebuildDock()
            } else if (sidebarEditMode) {
                val ordered = appList.filter { it.packageName in selectedPkgs }.map { it.packageName }
                savePackageList(KEY_SIDEBAR, ordered)
                rebuildSidebar()
            }
            try {
                endSelectionModes()
                toggleAppDrawer(false)
            } finally {
                isSavingSelection = false
            }
        }
        cancelSelectionBtn.setOnClickListener {
            // Ensure all flags are reset and drawer is closed to avoid null states
            isSavingSelection = false
            try { endSelectionModes() } catch (_: Throwable) {}
            try { toggleAppDrawer(false) } catch (_: Throwable) {}
        }
    }

    // ---- Folder add-to selection mode ----
    fun startAddToFolderMode(pageIndex: Int, folderIndex: Int) {
        folderSelectTarget = Pair(pageIndex, folderIndex)
        getCurrentPageAdapter()?.enableSelectionMode()
        if (!::selectionBar.isInitialized) {
            selectionBar = findViewById(R.id.selectionBar)
            saveSelectionBtn = findViewById(R.id.saveSelection)
            cancelSelectionBtn = findViewById(R.id.cancelSelection)
        }
        selectionBar.visibility = View.VISIBLE
        Toast.makeText(this, "Select apps to add, then Save.", Toast.LENGTH_SHORT).show()
        toggleAppDrawer(true)
        // Ensure search does not grab focus
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (::searchBox.isInitialized) {
            searchBox.clearFocus(); imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
    }

    private fun endSelectionModes() {
        folderSelectTarget = null
        dockEditMode = false
        sidebarEditMode = false
        // Disable selection mode on all drawer pages
        supportFragmentManager.fragments.forEach { f ->
            if (f is AppDrawerPageFragment) {
                try { f.getAdapter().disableSelectionMode() } catch (_: Throwable) {}
            }
        }
        if (::selectionBar.isInitialized) selectionBar.visibility = View.GONE
    }

    fun startEditDockMode() {
        dockEditMode = true
        if (!::selectionBar.isInitialized) {
            selectionBar = findViewById(R.id.selectionBar)
            saveSelectionBtn = findViewById(R.id.saveSelection)
            cancelSelectionBtn = findViewById(R.id.cancelSelection)
        }
        selectionBar.visibility = View.VISIBLE
        // Open drawer first to ensure fragments/adapters are attached, then enable selection
        toggleAppDrawer(true)
        appDrawerPager.post {
            val adapter = getCurrentPageAdapter()
            if (adapter != null) {
                adapter.enableSelectionMode()
                val current = loadPackageList(KEY_DOCK)
                adapter.setSelectedPackages(current)
            } else {
                // Fallback: enable selection on all attached drawer fragments
                supportFragmentManager.fragments.forEach { f ->
                    if (f is AppDrawerPageFragment) {
                        try {
                            val a = f.getAdapter(); a.enableSelectionMode(); a.setSelectedPackages(loadPackageList(KEY_DOCK))
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    fun startEditSidebarMode() {
        sidebarEditMode = true
        if (!::selectionBar.isInitialized) {
            selectionBar = findViewById(R.id.selectionBar)
            saveSelectionBtn = findViewById(R.id.saveSelection)
            cancelSelectionBtn = findViewById(R.id.cancelSelection)
        }
        selectionBar.visibility = View.VISIBLE
        // Open drawer first to ensure fragments/adapters are attached, then enable selection
        toggleAppDrawer(true)
        appDrawerPager.post {
            val adapter = getCurrentPageAdapter()
            if (adapter != null) {
                adapter.enableSelectionMode()
                val current = loadPackageList(KEY_SIDEBAR)
                adapter.setSelectedPackages(current)
            } else {
                supportFragmentManager.fragments.forEach { f ->
                    if (f is AppDrawerPageFragment) {
                        try {
                            val a = f.getAdapter(); a.enableSelectionMode(); a.setSelectedPackages(loadPackageList(KEY_SIDEBAR))
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only handle gestures if not inside app drawer or sidebar
        if (!isTouchInAppDrawer(event) && !isTouchInSidebar(event)) {
            // Use the configured gesture detector instance (avoid creating new detectors per event)
            return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Only dispatch global gestures if not inside interactive areas
        if (!isTouchInAppDrawer(ev) && !isTouchInSidebar(ev)) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private fun isTouchInAppDrawer(event: MotionEvent): Boolean {
        if (!::appDrawerContainer.isInitialized || appDrawerContainer.visibility != View.VISIBLE) {
            return false
        }
        
        val location = IntArray(2)
        appDrawerContainer.getLocationOnScreen(location)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        
        return x >= location[0] && 
               x <= location[0] + appDrawerContainer.width &&
               y >= location[1] && 
               y <= location[1] + appDrawerContainer.height
    }
    
    private fun isTouchInSidebar(event: MotionEvent): Boolean {
        if (!::sidebar.isInitialized || sidebar.visibility != View.VISIBLE) {
            return false
        }
        
        val location = IntArray(2)
        sidebar.getLocationOnScreen(location)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        
        return x >= location[0] && 
               x <= location[0] + sidebar.width &&
               y >= location[1] && 
               y <= location[1] + sidebar.height
    }

    private fun startAddWidgetFlow() {
        if (!::appWidgetHost.isInitialized) {
            appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        }
        if (!::appWidgetManager.isInitialized) {
            appWidgetManager = AppWidgetManager.getInstance(this)
        }

        val appWidgetId = try {
            appWidgetHost.allocateAppWidgetId()
        } catch (t: Throwable) {
            Toast.makeText(this, "Unable to start widget picker", Toast.LENGTH_SHORT).show()
            return
        }
        val contentIndex = (homePager.currentItem - 2).coerceIn(0, NORMAL_HOME_PAGES - 1)
        prefs.edit()
            .putInt(KEY_WIDGET_ID, appWidgetId)
            .putInt("widget_page_index", contentIndex)
            .apply()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        try {
            startActivityForResult(pickIntent, REQ_PICK_APPWIDGET)
        } catch (t: Throwable) {
            try { appWidgetHost.deleteAppWidgetId(appWidgetId) } catch (_: Throwable) {}
            Toast.makeText(this, "No widget picker available", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Activity API but sufficient for this project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PICK_CUSTOM_ICON -> {
                if (resultCode == RESULT_OK) {
                    val srcUri = data?.data
                    val pkg = pendingCustomIconPkg
                    if (srcUri != null && !pkg.isNullOrEmpty()) {
                        // Compute target size (used to cap crop result)
                        val iconPref = prefs.getInt("icon_size", 1).coerceIn(0, 2)
                        val sizeRes = when (iconPref) { 0 -> R.dimen.app_icon_size_small; 2 -> R.dimen.app_icon_size_large; else -> R.dimen.app_icon_size_medium }
                        val size = resources.getDimensionPixelSize(sizeRes)
                        val outFile = File(cacheDir, "crop_${System.currentTimeMillis()}.png")
                        val destUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
                        try {
                            // Grant UCrop temporary access to URIs
                            try {
                                grantUriPermission("com.yalantis.ucrop", srcUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: Throwable) {}
                            try {
                                grantUriPermission("com.yalantis.ucrop", destUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            } catch (_: Throwable) {}

                            UCrop.of(srcUri, destUri)
                                .withAspectRatio(1f, 1f)
                                .withMaxResultSize(size, size)
                                .start(this)
                        } catch (t: Throwable) {
                            Toast.makeText(this, "Crop not available", Toast.LENGTH_SHORT).show()
                            pendingCustomIconPkg = null
                        }
                    } else {
                        pendingCustomIconPkg = null
                    }
                } else {
                    pendingCustomIconPkg = null
                }
            }
            UCrop.REQUEST_CROP -> {
                if (resultCode == RESULT_OK) {
                    val resultUri = UCrop.getOutput(data!!)
                    val pkg = pendingCustomIconPkg
                    if (resultUri != null && !pkg.isNullOrEmpty()) {
                        val iconPref = prefs.getInt("icon_size", 1).coerceIn(0, 2)
                        val sizeRes = when (iconPref) { 0 -> R.dimen.app_icon_size_small; 2 -> R.dimen.app_icon_size_large; else -> R.dimen.app_icon_size_medium }
                        val size = resources.getDimensionPixelSize(sizeRes)
                        val ok = CustomIconManager.setCustomIconFromUri(this, pkg, resultUri, size)
                        if (ok) {
                            IconCache.invalidate(pkg)
                            refreshDrawerPages()
                            rebuildDock()
                            rebuildSidebar()
                            refreshHomePages()
                            Toast.makeText(this, "Custom icon set", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to set custom icon", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingCustomIconPkg = null
                } else if (resultCode == UCrop.RESULT_ERROR) {
                    val error = UCrop.getError(data!!)
                    Toast.makeText(this, error?.localizedMessage ?: "Crop failed", Toast.LENGTH_SHORT).show()
                    pendingCustomIconPkg = null
                }
            }
            REQ_PICK_APPWIDGET -> {
                if (resultCode != RESULT_OK) {
                    val pendingId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                        ?: prefs.getInt(KEY_WIDGET_ID, -1)
                    if (pendingId != -1) {
                        try { appWidgetHost.deleteAppWidgetId(pendingId) } catch (_: Throwable) {}
                    }
                    prefs.edit().remove(KEY_WIDGET_ID).remove("widget_page_index").apply()
                    return
                }
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    ?: prefs.getInt(KEY_WIDGET_ID, -1)
                if (appWidgetId != -1) {
                    val pageIndex = prefs.getInt("widget_page_index", 0)
                    // Try to bind if allowed, otherwise request bind
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null && appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)) {
                        configureOrAddWidget(appWidgetId, info.configure, pageIndex)
                        prefs.edit().remove(KEY_WIDGET_ID).remove("widget_page_index").apply()
                    } else {
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info?.provider)
                        try {
                            startActivityForResult(bindIntent, REQ_BIND_APPWIDGET)
                        } catch (_: Throwable) {
                            try { appWidgetHost.deleteAppWidgetId(appWidgetId) } catch (_: Throwable) {}
                        }
                    }
                }
            }
            REQ_BIND_APPWIDGET -> {
                if (resultCode != RESULT_OK) {
                    val pendingId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                        ?: prefs.getInt(KEY_WIDGET_ID, -1)
                    if (pendingId != -1) {
                        try { appWidgetHost.deleteAppWidgetId(pendingId) } catch (_: Throwable) {}
                    }
                    prefs.edit().remove(KEY_WIDGET_ID).remove("widget_page_index").apply()
                    return
                }
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    ?: prefs.getInt(KEY_WIDGET_ID, -1)
                if (appWidgetId != -1) {
                    val pageIndex = prefs.getInt("widget_page_index", -1)
                    if (pageIndex != -1) {
                        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                        configureOrAddWidget(appWidgetId, info?.configure, pageIndex)
                        prefs.edit().remove(KEY_WIDGET_ID).remove("widget_page_index").apply()
                    }
                }
            }
            REQ_CONFIGURE_APPWIDGET -> {
                if (resultCode != RESULT_OK) {
                    val pendingId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                        ?: prefs.getInt(KEY_WIDGET_ID, -1)
                    if (pendingId != -1) {
                        try { appWidgetHost.deleteAppWidgetId(pendingId) } catch (_: Throwable) {}
                    }
                    prefs.edit().remove(KEY_WIDGET_ID).remove("widget_page_index").apply()
                    return
                }
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    ?: prefs.getInt(KEY_WIDGET_ID, -1)
                if (appWidgetId != -1) {
                    val pageIndex = prefs.getInt("widget_page_index", -1)
                    if (pageIndex != -1) {
                        addAppWidget(appWidgetId, pageIndex)
                        // Nudge list data to refresh after config
                        try { appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listView) } catch (_: Throwable) {}
                        prefs.edit().remove(KEY_WIDGET_ID).remove("widget_page_index").apply()
                    }
                }
            }
            REQ_PICK_BACKGROUND -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) {
                        try {
                            // Persist read/write permissions only (persistable flag is not accepted by this API)
                            val flags = data.flags and
                                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Throwable) {}
                            // Save and apply
                            prefs.edit().putString(customBackgroundKey(), uri.toString()).apply()
                            applyCustomBackgroundIfAny()
                        } catch (t: Throwable) {
                            Toast.makeText(this, "Failed to apply background", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun launchApp(app: AppInfo) {
        if (::appPrivacyManager.isInitialized && appPrivacyManager.isAppLocked(app.packageName)) {
            val timeoutMs = prefs.getLong("app_lock_timeout_ms", 0L)
            val lastUnlockTs = prefs.getLong("app_unlock_ts_${app.packageName}", 0L)
            val timeoutValid = timeoutMs > 0L && (System.currentTimeMillis() - lastUnlockTs) < timeoutMs
            val oneShotValid = oneShotUnlockedPackages.remove(app.packageName)
            if (!timeoutValid && !oneShotValid) {
                requestUnlockForApp(app)
                return
            }
        }

        try {
            // Open our settings when selecting the launcher app itself
            if (app.packageName == packageName) {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return
            }
            // Internal Calc Vault
            if (app.packageName == INTERNAL_CALC_PKG) {
                val intent = Intent(this, com.sevenk.calcvault.MainActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_CALC_PKG)
                return
            }
            // Internal 7K Study
            if (app.packageName == INTERNAL_STUDY_PKG) {
                val intent = Intent(this, StudyActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_STUDY_PKG)
                return
            }
            // Internal 7KLAWPREP PWA wrapper
            if (app.packageName == INTERNAL_LAW_PKG) {
                val intent = Intent(this, WebAppActivity::class.java)
                intent.putExtra(WebAppActivity.EXTRA_TITLE, "7KLAWPREP")
                intent.putExtra(WebAppActivity.EXTRA_URL, "https://www.7klawprep.me/")
                startActivity(intent)
                recordRecent(INTERNAL_LAW_PKG)
                return
            }
            // Internal 7K PWAs (TODO: confirm URLs)
            if (app.packageName == INTERNAL_ITIHAAS_PKG) {
                val intent = Intent(this, WebAppActivity::class.java)
                intent.putExtra(WebAppActivity.EXTRA_TITLE, "7K ITIHAAS")
                intent.putExtra(WebAppActivity.EXTRA_URL, "https://7-k-itihaas.vercel.app/")
                startActivity(intent)
                recordRecent(INTERNAL_ITIHAAS_PKG)
                return
            }
            if (app.packageName == INTERNAL_POLYGLOT_PKG) {
                val intent = Intent(this, WebAppActivity::class.java)
                intent.putExtra(WebAppActivity.EXTRA_TITLE, "7K POLYGLOT")
                intent.putExtra(WebAppActivity.EXTRA_URL, "https://polyglot.7klawprep.me/")
                startActivity(intent)
                recordRecent(INTERNAL_POLYGLOT_PKG)
                return
            }
            if (app.packageName == INTERNAL_ECO_PKG) {
                val intent = Intent(this, WebAppActivity::class.java)
                intent.putExtra(WebAppActivity.EXTRA_TITLE, "7K ECO")
                intent.putExtra(WebAppActivity.EXTRA_URL, "https://eco.7klawprep.me/")
                startActivity(intent)
                recordRecent(INTERNAL_ECO_PKG)
                return
            }
            if (app.packageName == INTERNAL_LIFE_PKG) {
                val intent = Intent(this, WebAppActivity::class.java)
                intent.putExtra(WebAppActivity.EXTRA_TITLE, "7K LIFE")
                intent.putExtra(WebAppActivity.EXTRA_URL, "https://life.7klawprep.me/")
                startActivity(intent)
                recordRecent(INTERNAL_LIFE_PKG)
                return
            }
            // Daily Essentials (synthetic)
            if (app.packageName == INTERNAL_NOTES_PKG) {
                val intent = Intent(this, com.sevenk.launcher.notes.ui.NotesActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_NOTES_PKG)
                return
            }
            if (app.packageName == INTERNAL_CALENDAR2_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.CalendarActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_CALENDAR2_PKG)
                return
            }
            if (app.packageName == INTERNAL_WEATHER_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.WeatherActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_WEATHER_PKG)
                return
            }
            if (app.packageName == INTERNAL_MUSIC_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.MusicActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_MUSIC_PKG)
                return
            }
            if (app.packageName == INTERNAL_UTILITY_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.UtilityActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_UTILITY_PKG)
                return
            }
            if (app.packageName == INTERNAL_GAMES_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.GamesActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_GAMES_PKG)
                return
            }
            if (app.packageName == INTERNAL_WIDGETS_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.WidgetsHubActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_WIDGETS_PKG)
                return
            }
            if (app.packageName == INTERNAL_APPSTORE_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.AppStoreActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_APPSTORE_PKG)
                return
            }
            if (app.packageName == INTERNAL_SMART_NOTES_PLUS_PKG) {
                val intent = Intent(this, com.sevenk.launcher.notes.ui.NotesActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_SMART_NOTES_PLUS_PKG)
                return
            }
            if (app.packageName == INTERNAL_TASKS_COMMANDER_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.TasksCommanderActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_TASKS_COMMANDER_PKG)
                return
            }
            if (app.packageName == INTERNAL_FILE_FORGE_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.FileForgeActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_FILE_FORGE_PKG)
                return
            }
            if (app.packageName == INTERNAL_BUDGET_GUARDIAN_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.BudgetGuardianActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_BUDGET_GUARDIAN_PKG)
                return
            }
            if (app.packageName == INTERNAL_PRIVACY_SHIELD_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.PrivacyShieldActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_PRIVACY_SHIELD_PKG)
                return
            }
            if (app.packageName == INTERNAL_BATTERY_DOCTOR_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.BatteryDoctorActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_BATTERY_DOCTOR_PKG)
                return
            }
            if (app.packageName == INTERNAL_STUDIO_TEMPLATES_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.StudioTemplatesActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_STUDIO_TEMPLATES_PKG)
                return
            }
            if (app.packageName == INTERNAL_OFFLINE_APPSTORE_PKG) {
                val intent = Intent(this, com.sevenk.launcher.ecosystem.OfflineFirstAppStoreActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_OFFLINE_APPSTORE_PKG)
                return
            }
            // Internal 7K Settings
            if (app.packageName == "internal.7ksettings") {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return
            }
            
            // Internal 7K Enhanced Settings
            if (app.packageName == "internal.7kenhancedsettings") {
                val intent = Intent(this, com.sevenk.launcher.settings.EnhancedSettingsActivity::class.java)
                startActivity(intent)
                return
            }
            
            // Internal 7K Browser
            if (app.packageName == "internal.7kbrowser") {
                try {
                    val intent = Intent(this, com.sevenk.browser.BrowserActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "7K Browser not available", Toast.LENGTH_SHORT).show()
                }
                return
            }
            
            // Internal 7KSTUDIO launcher
            if (app.packageName == INTERNAL_STUDIO_PKG) {
                val intent = Intent(this, com.sevenk.studio.StudioActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_STUDIO_PKG)
                return
            }
            // Default: use package manager
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                recordRecent(app.packageName)
            } else {
                Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Launch failed: ${'$'}{t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUnlockForApp(app: AppInfo) {
        val mode = prefs.getString("app_lock_auth_mode", "biometric_or_pin") ?: "biometric_or_pin"
        val requireBiometric = prefs.getBoolean("require_biometric", true)

        when (mode) {
            "pin_only" -> showPinUnlockDialog(app)
            "biometric_only" -> {
                if (requireBiometric) authenticateWithBiometricForApp(app, allowPinFallback = false)
                else showPinUnlockDialog(app)
            }
            else -> {
                if (requireBiometric) authenticateWithBiometricForApp(app, allowPinFallback = true)
                else showPinUnlockDialog(app)
            }
        }
    }

    private fun authenticateWithBiometricForApp(app: AppInfo, allowPinFallback: Boolean) {
        lifecycleScope.launch {
            val result = appPrivacyManager.authenticateForApp(this@LauncherActivity, app.packageName, app.name)
            when (result) {
                is AppPrivacyManager.AuthenticationResult.Success -> {
                    oneShotUnlockedPackages.add(app.packageName)
                    prefs.edit().putLong("app_unlock_ts_${app.packageName}", System.currentTimeMillis()).apply()
                    launchApp(app)
                }
                is AppPrivacyManager.AuthenticationResult.PinRequired -> {
                    if (allowPinFallback) showPinUnlockDialog(app)
                    else Toast.makeText(this@LauncherActivity, "Biometric authentication required", Toast.LENGTH_SHORT).show()
                }
                is AppPrivacyManager.AuthenticationResult.Failed,
                is AppPrivacyManager.AuthenticationResult.Error -> {
                    if (allowPinFallback) showPinUnlockDialog(app)
                    else Toast.makeText(this@LauncherActivity, "Unlock failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPinUnlockDialog(app: AppInfo) {
        showGlassInputSheet(
            title = "Unlock ${app.name}",
            hint = "Enter PIN",
            submitLabel = "Unlock",
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            secondaryActionLabel = "Set PIN",
            onSecondaryAction = { showSetGlobalPinDialog() }
        ) { enteredRaw ->
            val entered = enteredRaw.trim()
            if (isPinValidForApp(app.packageName, entered)) {
                oneShotUnlockedPackages.add(app.packageName)
                prefs.edit().putLong("app_unlock_ts_${app.packageName}", System.currentTimeMillis()).apply()
                launchApp(app)
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isPinValidForApp(packageName: String, enteredPin: String): Boolean {
        if (enteredPin.isBlank()) return false
        if (::appPrivacyManager.isInitialized && appPrivacyManager.verifyAppPin(packageName, enteredPin)) {
            return true
        }
        val globalPin = prefs.getString("app_lock_pin", null)
        return !globalPin.isNullOrBlank() && globalPin == enteredPin
    }

    private fun showSetGlobalPinDialog() {
        showGlassInputSheet(
            title = "Set App Lock PIN",
            hint = "New 4-8 digit PIN",
            submitLabel = "Save",
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) { pinRaw ->
            val pin = pinRaw.trim()
            if (pin.length in 4..8 && pin.all { it.isDigit() }) {
                prefs.edit().putString("app_lock_pin", pin).apply()
                Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "PIN must be 4-8 digits", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Recent apps for sidebar ----
    private fun recordRecent(pkg: String) {
        if (pkg == packageName) return
        val current = loadPackageList(KEY_RECENTS).toMutableList()
        current.remove(pkg)
        current.add(0, pkg)
        val capped = current.take(4)
        savePackageList(KEY_RECENTS, capped)
        rebuildSidebar()
    }

    // Attempt to automatically add our TODO widget (TodoWidgetProvider) programmatically
    private fun tryAutoAddTodoWidget() {
        try {
            val provider = ComponentName(this, TodoWidgetProvider::class.java)
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            // First try silent bind (if permission granted)
            val bound = try {
                @Suppress("DEPRECATION")
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider, null as AndroidBundle?)
            } catch (_: Throwable) { false }
            if (bound) {
                val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                val contentIndex = prefs.getInt("widget_page_index", 0)
                configureOrAddWidget(appWidgetId, info?.configure, contentIndex)
            } else {
                // Request bind permission from system
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                }
                startActivityForResult(bindIntent, REQ_BIND_APPWIDGET)
            }
        } catch (_: Throwable) {
            // Ignore auto-add failures
        }
    }
}
