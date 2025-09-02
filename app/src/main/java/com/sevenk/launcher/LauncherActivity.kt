package com.sevenk.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import android.view.inputmethod.InputMethodManager
import android.app.ActivityManager
import android.widget.FrameLayout
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
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
import kotlin.math.roundToInt
import android.widget.Toast
import java.io.File
import com.yalantis.ucrop.UCrop
import android.view.DragEvent
import android.content.ClipDescription
import android.view.animation.DecelerateInterpolator
import com.sevenk.launcher.util.Perf
import com.sevenk.launcher.iconpack.IconPackHelper

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
    private var appList: MutableList<AppInfo> = mutableListOf()
    private lateinit var widgetsContainer: FrameLayout
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private val prefs by lazy { getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE) }

    // Add a getter method to expose the app list to fragments/adapters
    fun getAppList(): List<AppInfo> = appList

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
                // Try to find again (could be present in some layouts)
                val found: EditText? = try { findViewById(R.id.searchBox) } catch (_: Throwable) { null }
                if (found != null) {
                    searchBox = found
                    return
                }
                android.util.Log.w("LauncherActivity", "searchBox not found in layout; creating placeholder")
                searchBox = EditText(this).apply {
                    id = R.id.searchBox
                    visibility = View.GONE
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
                // Ensure placeholder is attached to the view hierarchy so it has a window token if needed
                try {
                    val root = findViewById<ViewGroup>(android.R.id.content)
                    if (searchBox.parent == null) root.addView(searchBox, ViewGroup.LayoutParams(1, 1))
                } catch (_: Throwable) { /* non-fatal */ }
            }
        } catch (_: Throwable) { /* never crash from here */ }
    }
    private var sidebarOverlayEnabled = false
    private var sidebarOverlayOpen = false
    private lateinit var iconPackHelper: IconPackHelper
    private val REQ_PICK_BACKGROUND = 5012

    private fun applyGlassBlurIfPossible(view: View?, radius: Float) {
        if (view == null) return
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val renderEffect = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
            view.setRenderEffect(renderEffect)
        } catch (_: Throwable) {}
    }

    // Expose icon pack helper to fragments/adapters
    fun getIconPackHelper(): IconPackHelper? = try {
        if (::iconPackHelper.isInitialized) iconPackHelper else null
    } catch (_: Throwable) { null }

    // ---- In-app wallpaper background (SAF) ----
    private fun customBackgroundKey() = "custom_bg_uri"

    private fun applyCustomBackgroundIfAny() {
        val uriStr = prefs.getString(customBackgroundKey(), null)
        if (uriStr.isNullOrBlank()) {
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

    AlertDialog.Builder(this)
        .setTitle(app.name)
        .setItems(actions.map { it.first }.toTypedArray()) { d, which ->
            actions.getOrNull(which)?.second?.invoke()
            d.dismiss()
        }
        .setNegativeButton("Cancel", null)
        .show()
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
    // New synthetic PWAs
    private val INTERNAL_ITIHAAS_PKG = "internal.7kitihaas"
    private val INTERNAL_POLYGLOT_PKG = "internal.7kpolyglot"
    private val INTERNAL_ECO_PKG = "internal.7keco"
    private val INTERNAL_LIFE_PKG = "internal.7klife"

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
        dynamic.add("Uninstall")
        dynamic.add("App Info")
        val options = dynamic.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(options) { d, which ->
                when (options[which]) {
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
                            if (app.packageName != packageName && app.packageName != INTERNAL_CALC_PKG && app.packageName != INTERNAL_STUDIO_PKG) {
                                val uri = Uri.parse("package:${app.packageName}")
                                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri).apply {
                                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                }
                                startActivity(intent)
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
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        val options = arrayOf("Add Section", "Reset Defaults")
        AlertDialog.Builder(this)
            .setTitle("Edit Sections")
            .setItems(options) { d, which ->
                when (which) {
                    0 -> promptAddSection()
                    1 -> {
                        sectionOrder = mutableListOf("Communication", "Games", "Media", "Tools", "Social", "Shopping", "Uncategorized")
                        saveSections()
                        try {
                            if (::drawerPagerAdapter.isInitialized) drawerPagerAdapter.setSections(getDrawerSections())
                        } catch (_: Throwable) {}
                        refreshDrawerPages()
                    }
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getDrawerSections(): List<String> {
        // First page is ALL, then user-defined sections in saved order
        return listOf("ALL") + sectionOrder
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
        // Ensure adapter sections are current
        val sections = getDrawerSections()
        drawerPagerAdapter.setSections(sections)
        // Update any attached fragments with their filtered data
        sections.forEachIndexed { index, sec ->
            drawerPagerAdapter.getFragment(index)?.updateAppsForPage(sourceList, sec, sectionMap)
        }
    }

    // Expose Home Options dialog so fragments can trigger it on long-press
    fun showHomeOptions() {
        AlertDialog.Builder(this)
            .setTitle("Home Options")
            .setItems(
                buildList {
                    add(if (isShowLabels()) "Hide Labels" else "Show Labels")
                    add("Add Widget")
                    if (hasWidget()) add("Remove Widget")
                    add("Set Background Image")
                    add("Clear Background Image")
                }.toTypedArray()
            ) { d, which ->
                val hasW = hasWidget()
                // Indices:
                // 0: toggle labels
                // 1: add widget
                // 2: remove widget (if present) OR set background
                // 3: set background (if remove exists) OR clear background
                // 4: clear background (only when remove exists)
                when (which) {
                    0 -> toggleLabels()
                    1 -> startAddWidgetFlow()
                    2 -> if (hasW) removeCurrentWidget() else startPickBackgroundImage()
                    3 -> if (hasW) startPickBackgroundImage() else clearCustomBackground()
                    4 -> if (hasW) clearCustomBackground() else Unit
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptAddSection() {
        val input = EditText(this)
        input.hint = "Section name"
        AlertDialog.Builder(this)
            .setTitle("New Section")
            .setView(input)
            .setPositiveButton("Add") { d, _ ->
                val name = input.text.toString().trim().ifBlank { return@setPositiveButton }
                if (!sectionOrder.contains(name)) sectionOrder.add(0, name)
                saveSections()
                try {
                    if (::drawerPagerAdapter.isInitialized) drawerPagerAdapter.setSections(getDrawerSections())
                } catch (_: Throwable) {}
                refreshDrawerPages()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            
            // Apply any saved custom launcher background (fallback to system wallpaper)
            try { applyCustomBackgroundIfAny() } catch (_: Throwable) {}
            // Apply glass blur on Android 12+ (optional, skip if fails)
            applyGlassBlurIfPossible(homeScreen, 18f)
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    applyGlassBlurIfPossible(dock, 20f)
                    applyGlassBlurIfPossible(sidebar, 20f)
                    val searchLocal: View? = findViewById(R.id.searchBox)
                    if (searchLocal != null) applyGlassBlurIfPossible(searchLocal, 16f)
                    val selectionLocal: View? = findViewById(R.id.selectionBar)
                    if (selectionLocal != null) applyGlassBlurIfPossible(selectionLocal, 16f)
                    android.util.Log.d("LauncherActivity", "Glass blur applied")
                } catch (e: Exception) {
                    android.util.Log.e("LauncherActivity", "Glass blur failed", e)
                }
            }
            
            // Ensure sections are ready before wiring the drawer
            try {
                ensureSectionsInitialized()
                android.util.Log.d("LauncherActivity", "Sections initialized")
            } catch (e: Exception) {
                android.util.Log.e("LauncherActivity", "Sections initialization failed", e)
            }
            
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
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (e1 == null) return false
                        val diffY = e2.y - e1.y
                        val diffX = e2.x - e1.x
                        val isVertical = kotlin.math.abs(diffY) > kotlin.math.abs(diffX)
                        val fastEnough = kotlin.math.abs(velocityY) > 800
                        if (isVertical && fastEnough) {
                            if (diffY < -100) { // Swipe up
                                try {
                                    toggleAppDrawer(true)
                                } catch (e: Exception) {
                                    android.util.Log.e("LauncherActivity", "Toggle app drawer failed", e)
                                }
                                return true
                            } else if (diffY > 100) { // Swipe down
                                try {
                                    toggleAppDrawer(false)
                                } catch (e: Exception) {
                                    android.util.Log.e("LauncherActivity", "Toggle app drawer failed", e)
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
                        if (e1 == null) return false
                        val dy = e2.y - e1.y
                        val absDx = kotlin.math.abs(e2.x - e1.x)
                        val absDy = kotlin.math.abs(dy)
                        if (absDy > absDx && absDy > 150) {
                            try {
                                if (dy < 0) {
                                    toggleAppDrawer(true)
                                } else {
                                    toggleAppDrawer(false)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LauncherActivity", "Scroll gesture failed", e)
                            }
                            return true
                        }
                        return false
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
                    AlertDialog.Builder(this)
                        .setTitle("Home Options")
                        .setItems(
                            buildList {
                                add(if (isShowLabels()) "Hide Labels" else "Show Labels")
                                add("Add Widget")
                                if (hasWidget()) add("Remove Widget")
                            }.toTypedArray()
                        ) { d, which ->
                            val hasWidget = hasWidget()
                            when (which) {
                                0 -> toggleLabels()
                                1 -> startAddWidgetFlow()
                                2 -> if (hasWidget) removeCurrentWidget()
                            }
                            d.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }

                // Also support long-press directly on the pager area (pages can consume touches)
                homePager.setOnLongClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Home Options")
                        .setItems(
                            buildList {
                                add(if (isShowLabels()) "Hide Labels" else "Show Labels")
                                add("Add Widget")
                                if (hasWidget()) add("Remove Widget")
                            }.toTypedArray()
                        ) { d, which ->
                            val hasWidget = hasWidget()
                            when (which) {
                                0 -> toggleLabels()
                                1 -> startAddWidgetFlow()
                                2 -> if (hasWidget) removeCurrentWidget()
                            }
                            d.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
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
        // Re-ensure searchBox exists in case of configuration or fallback layouts
        ensureSearchBoxReady()
        applyUserSettings()
        // Ensure fragments are attached, then restore widgets once
        if (!widgetsRestoredOnce) {
            homePager.post {
                restoreWidgetIfAny()
                widgetsRestoredOnce = true
            }
        }
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
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
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
        val iconMode = prefs.getInt("dock_icon_size", 1).coerceIn(0, 2)
        val targetDp = when (iconMode) { 0 -> 64; 2 -> 84; else -> 72 }
        lp.height = dp(targetDp)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blur = RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)
                dock.setRenderEffect(blur)
                sidebar.setRenderEffect(blur)
            } catch (_: Throwable) {
                // Fallback: keep dim rounded panels only
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
                    // Move semantics: remove from all other places first to avoid duplicates
                    removeFromList(KEY_DOCK, pkg)
                    removeFromList(KEY_SIDEBAR, pkg)
                    removeFromAllHomePages(pkg)
                    when (v) {
                        dock -> {
                            addToList(KEY_DOCK, pkg)
                            rebuildDock()
                            true
                        }
                        sidebar -> {
                            addToList(KEY_SIDEBAR, pkg)
                            rebuildSidebar()
                            true
                        }
                        homeScreen, homePager -> {
                            val contentIndex = (homePager.currentItem - 1).coerceIn(0, NORMAL_HOME_PAGES - 1)
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
        val options = arrayOf("Add to Dock", "Add to Sidebar", "Add to Home (Page ${'$'}{contentIndex + 1})")
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(options) { d, which ->
                when (which) {
                    0 -> { addToList(KEY_DOCK, app.packageName); rebuildDock() }
                    1 -> { addToList(KEY_SIDEBAR, app.packageName); rebuildSidebar() }
                    2 -> { addToHomePage(contentIndex, app.packageName); refreshHomePages() }
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun loadAppsAsync() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val packages = pm.queryIntentActivities(intent, 0)
                val list = mutableListOf<AppInfo>()
                for (resolveInfo in packages) {
                    val ai = resolveInfo.activityInfo
                    list.add(
                        AppInfo(
                            name = resolveInfo.loadLabel(pm).toString(),
                            packageName = ai.packageName,
                            className = ai.name,
                            icon = try { resolveInfo.loadIcon(pm) } catch (_: Throwable) { null },
                            applicationInfo = ai.applicationInfo
                        )
                    )
                }
                // Prepare placeholder info for synthetic entries using our own app's ApplicationInfo
                val selfAppInfo = try { packageManager.getApplicationInfo(packageName, 0) } catch (_: Throwable) { null }
                val placeholderIcon = try { getDrawable(R.mipmap.ic_launcher) } catch (_: Throwable) { null }
                // Inject synthetic internal apps
                list.add(AppInfo(name = "Calculator", packageName = INTERNAL_CALC_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.add(AppInfo(name = "7KSTUDIO", packageName = INTERNAL_STUDIO_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.add(AppInfo(name = "7K Browser", packageName = "com.sevenk.browser", className = "com.sevenk.browser.BrowserActivity", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.add(AppInfo(name = "7KLAWPREP", packageName = INTERNAL_LAW_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                // New synthetic PWA entries
                list.add(AppInfo(name = "7K ITIHAAS", packageName = INTERNAL_ITIHAAS_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.add(AppInfo(name = "7K POLYGLOT", packageName = INTERNAL_POLYGLOT_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.add(AppInfo(name = "7K ECO", packageName = INTERNAL_ECO_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.add(AppInfo(name = "7K LIFE", packageName = INTERNAL_LIFE_PKG, className = "", icon = placeholderIcon, applicationInfo = selfAppInfo ?: applicationInfo))
                list.sortedBy { it.name }
            }
            appList.clear()
            appList.addAll(loaded)
            // Initialize sections (defaults + auto-categorization if first time)
            ensureSectionsInitialized()
            // Initialize pager sections and populate pages
            drawerPagerAdapter.setSections(getDrawerSections())
            refreshDrawerPages()
            // Skip heavy prewarm here; we'll defer it to a background coroutine after UI is shown
            // to improve first-render time and reduce CPU spikes.
        }
        // Rebuild dock/sidebar with saved selections
        rebuildDock()
        rebuildSidebar()

        // Hook up search now that data is ready
        setupSearch()

        // Refresh home pages to reflect loaded app labels/icons
        refreshHomePages()

        // Defer icon prewarm: prioritize pinned/recent, then others in small batches
        prewarmIconsDeferred()
    }

    // Warm up icon bitmaps after initial UI is shown, prioritizing pinned/recent and batching others
    private fun prewarmIconsDeferred() {
        try {
            val pinned = loadPackageList(KEY_DOCK) + loadPackageList(KEY_SIDEBAR)
            val recents = loadPackageList(KEY_RECENTS)
            val prioritized = (pinned + recents).distinct()
            val rest = appList.map { it.packageName }.filter { it !in prioritized }
            val order = prioritized + rest
            lifecycleScope.launch(Dispatchers.IO) {
                val size = try { getGeneralIconSizePx() } catch (_: Throwable) { dp(48) }
                var count = 0
                for (pkg in order) {
                    try { IconCache.getBitmapForPackage(this@LauncherActivity, pkg, size) } catch (_: Throwable) {}
                    // Yield and lightly pace to avoid bursts
                    if (++count % 8 == 0) {
                        kotlinx.coroutines.yield()
                    }
                    if (count % 32 == 0) {
                        // small pause to keep CPU/battery low during long prewarm sessions
                        kotlinx.coroutines.delay(12)
                    }
                }
            }
        } catch (_: Throwable) { }
    }

    private fun setupSearch() {
        if (!::searchBox.isInitialized) return
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                // Debounce to avoid filtering on every keystroke
                searchJob?.cancel()
                searchJob = lifecycleScope.launch(Dispatchers.Default) {
                    kotlinx.coroutines.delay(250)
                    val filtered = if (q.isEmpty()) appList else appList.filter { it.name.lowercase().contains(q) }
                    withContext(Dispatchers.Main) {
                        refreshDrawerPages(filtered)
                    }
                }
            }
        })
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

    private fun addAppWidget(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val hostView = appWidgetHost.createView(this, appWidgetId, info)
        // Map current pager position to content page index (Stan at 0; clamp to content pages only)
        val contentIndex = (homePager.currentItem - 2).coerceIn(0, NORMAL_HOME_PAGES - 1)
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

    private fun configureOrAddWidget(appWidgetId: Int, configure: ComponentName?) {
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                startActivityForResult(intent, REQ_CONFIGURE_APPWIDGET)
            } catch (_: Throwable) {
                // If config activity fails, just add directly
                addAppWidget(appWidgetId)
            }
        } else {
            addAppWidget(appWidgetId)
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
        // Spacing between sidebar items
        sidebar.addItemDecoration(SpaceItemDecoration(dp(8), RecyclerView.VERTICAL))
        sidebar.setHasFixedSize(true)
        // Smooth scrolling tweaks
        (sidebar.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        sidebar.isNestedScrollingEnabled = false
        sidebar.setItemViewCacheSize(20)
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
        supportFragmentManager.fragments.forEach { f ->
            if (f is HomePageFragment) {
                f.refreshData()
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
        val names = appList.map { it.name }.toTypedArray()
        val checked = appList.map { it.packageName in current }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(if (forDock) "Edit Dock" else "Edit Sidebar")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val pkg = appList[which].packageName
                if (isChecked) current.add(pkg) else current.remove(pkg)
            }
            .setPositiveButton("Save") { d, _ ->
                // Save in chosen order of appList filtered by selected
                val ordered = appList.filter { it.packageName in current }.map { it.packageName }
                savePackageList(key, ordered)
                if (forDock) rebuildDock() else rebuildSidebar()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { appDrawerContainer.translationY = 0f }
                .start()
        } else {
            appDrawerContainer.animate()
                .translationY(h.toFloat())
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
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
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Ensure the detector always has a chance to analyze the gesture,
        // even if child views handle the touch events.
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun startAddWidgetFlow() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        startActivityForResult(pickIntent, REQ_PICK_APPWIDGET)
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
                        val outFile = File(cacheDir, "crop_${'$'}{System.currentTimeMillis()}.png")
                        val destUri = FileProvider.getUriForFile(this, "${'$'}packageName.fileprovider", outFile)
                        try {
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
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (appWidgetId != -1) {
                    // Try to bind if allowed, otherwise request bind
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null && appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)) {
                        configureOrAddWidget(appWidgetId, info.configure)
                    } else {
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info?.provider)
                        startActivityForResult(bindIntent, REQ_BIND_APPWIDGET)
                    }
                }
            }
            REQ_BIND_APPWIDGET -> {
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (appWidgetId != -1) {
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    configureOrAddWidget(appWidgetId, info?.configure)
                }
            }
            REQ_CONFIGURE_APPWIDGET -> {
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (appWidgetId != -1) {
                    addAppWidget(appWidgetId)
                    // Nudge list data to refresh after config
                    try { appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listView) } catch (_: Throwable) {}
                }
            }
            REQ_PICK_BACKGROUND -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) {
                        try {
                            // Persist permission so we can read after reboot
                            val flags = (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
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
            // Internal 7KSTUDIO launcher
            if (app.packageName == INTERNAL_STUDIO_PKG) {
                val intent = Intent(this, com.sevenk.studio.StudioActivity::class.java)
                startActivity(intent)
                recordRecent(INTERNAL_STUDIO_PKG)
                return
            }
            
            // 7K Browser
            if (app.packageName == "com.sevenk.browser") {
                try {
                    // Use direct reference to avoid reflection issues with minify/ProGuard
                    val intent = Intent(this, com.sevenk.browser.BrowserActivity::class.java)
                    startActivity(intent)
                    recordRecent("com.sevenk.browser")
                } catch (e: Exception) {
                    Toast.makeText(this, "7K Browser not available", Toast.LENGTH_SHORT).show()
                }
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
                configureOrAddWidget(appWidgetId, info?.configure)
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
