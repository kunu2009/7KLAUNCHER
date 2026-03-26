package com.sevenk.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import java.io.File
import java.text.DateFormat
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.annotation.RequiresApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sevenk.launcher.R
import com.sevenk.launcher.databinding.ActivityBrowserBinding
import com.sevenk.browser.privacy.PrivacyManager
import com.google.android.material.color.MaterialColors
import android.view.KeyEvent
import com.sevenk.browser.model.Tab
import android.widget.Toast
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sevenk.launcher.notes.data.NotesRepository
import com.sevenk.launcher.notes.ui.NoteEditorActivity
import com.sevenk.launcher.ecosystem.TasksCommanderActivity

class BrowserActivity : AppCompatActivity() {

    companion object {
        const val ACTION_OPEN_PRIVATE_TAB = "com.sevenk.browser.action.OPEN_PRIVATE_TAB"
        const val ACTION_OPEN_OFFLINE = "com.sevenk.browser.action.OPEN_OFFLINE"
        const val EXTRA_URL = "extra_url"
    }

    private data class OfflinePage(
        val id: Long,
        val title: String,
        val url: String,
        val savedAt: Long,
        val archivePath: String
    )

    private data class BookmarkEntry(
        val id: Long,
        val title: String,
        val url: String,
        val addedAt: Long
    )

    private lateinit var binding: ActivityBrowserBinding
    private var currentSnackbar: Snackbar? = null

    private val tabs = mutableListOf<Tab>()
    private var currentTabIndex = -1
    private var isIncognitoMode = false
    private var currentGroupFilter: String? = null

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }
    
    // Managers
    private lateinit var privacyManager: PrivacyManager
    private lateinit var downloadManager: DownloadManager
    
    // State
    private var isDesktopMode = false

    private val suggestions = mutableListOf<String>()
    private lateinit var suggestionsAdapter: ArrayAdapter<String>
    private val offlinePrefs by lazy { getSharedPreferences("sevenk_browser_offline", MODE_PRIVATE) }
    private val bookmarksPrefs by lazy { getSharedPreferences("sevenk_browser_bookmarks", MODE_PRIVATE) }
    
    // WebChromeClient for handling browser UI updates
    private val webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
            val tab = currentTab ?: return
            if (view == tab.webView) {
                tab.title = title ?: tab.url.ifEmpty { "New Tab" }
                updateUI()
            }
        }
        
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            fileChooserCallback = filePathCallback
            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            return try {
                fileChooserLauncher.launch(intent)
                true
            } catch (e: ActivityNotFoundException) {
                fileChooserCallback = null
                showSnackbar("No file picker found")
                false
            }
        }
    }

    // WebViewClient for handling page loading and navigation
    private val webViewClient = object : WebViewClient() {
        @RequiresApi(21)
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return if (privacyManager.shouldBlockRequest(request)) {
                privacyManager.createEmptyResponse()
            } else {
                super.shouldInterceptRequest(view, request)
            }
        }
        
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            request?.let {
                if (privacyManager.shouldBlockRequest(it)) return true
                if (it.url.scheme?.startsWith("http") == true) {
                    view?.loadUrl(it.url.toString())
                    return true
                }
            }
            return false
        }
        
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val tab = currentTab ?: return
            if (view == tab.webView) {
                tab.url = url ?: ""
                tab.lastActiveTime = System.currentTimeMillis()
                updateUI()
            }
        }
        
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val tab = currentTab ?: return
            if (view == tab.webView) {
                if (!tab.isIncognito) addSuggestion(url ?: "")
                applyReaderMode(tab.webView, tab.readerModeEnabled)
                updateUI()
            }
        }
    }

    private val currentTab: Tab?
        get() = tabs.getOrNull(currentTabIndex)

    private val currentWebView: WebView?
        get() = currentTab?.webView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up window flags for fullscreen and transparent system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } catch (_: Throwable) {}
        applyGlassUi()
        
        // Initialize managers
        privacyManager = PrivacyManager(this)
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Set up UI components
        setupUrlBar()
        setupBottomNavigation()
        setupFab()
        
        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentWebView?.canGoBack() == true) {
                    currentWebView?.goBack()
                } else if (tabs.size > 1) {
                    closeCurrentTab()
                } else {
                    finish()
                }
            }
        })
        
        // Initialize suggestions adapter
        suggestionsAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions)
        binding.urlInput.setAdapter(suggestionsAdapter)
        binding.urlInput.threshold = 1

        // Start according to incoming intent (URL, share, quick action)
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    // No override of deprecated onBackPressed; using onBackPressedDispatcher above

    private fun updateUI() {
        val tab = currentTab ?: return
        // Update URL bar
        binding.urlInput.apply {
            setText(tab.url.ifEmpty { "" }, false)
            setSelection(text?.length ?: 0)
        }
        // Update toolbar styling based on incognito
        val colorAttr = if (isIncognitoMode) com.google.android.material.R.attr.colorSurfaceInverse else com.google.android.material.R.attr.colorSurface
        binding.toolbar.setBackgroundColor(MaterialColors.getColor(binding.toolbar, colorAttr))
        if (binding.toolbar.menu.size() > 0) {
            binding.toolbar.menu.findItem(R.id.action_desktop_site)?.isChecked = isDesktopMode
            binding.toolbar.menu.findItem(R.id.action_toggle_reader_mode)?.isChecked = tab.readerModeEnabled
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Toolbar already has app:menu, but inflate defensively for ActionBar consistency
        if (menu.size() == 0) menuInflater.inflate(R.menu.browser_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                val url = currentTab?.url.orEmpty()
                if (url.isNotBlank()) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    return try { startActivity(Intent.createChooser(share, getString(R.string.share))); true } catch (_: Throwable) { false }
                }
            }
            R.id.action_find_in_page -> {
                showSnackbar("Find in page coming soon")
                return true
            }
            R.id.action_desktop_site -> {
                isDesktopMode = !isDesktopMode
                item.isChecked = isDesktopMode
                currentWebView?.settings?.userAgentString = if (isDesktopMode) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                } else null
                currentWebView?.reload()
                return true
            }
            R.id.action_add_to_home -> {
                showSnackbar("Add to Home screen coming soon")
                return true
            }
            R.id.action_add_bookmark -> {
                addCurrentPageBookmark()
                return true
            }
            R.id.action_manage_bookmarks -> {
                showBookmarksDialog()
                return true
            }
            R.id.action_save_offline -> {
                saveCurrentPageOffline()
                return true
            }
            R.id.action_offline_pages -> {
                showOfflinePagesDialog()
                return true
            }
            R.id.action_tab_groups -> {
                showTabGroupsDialog()
                return true
            }
            R.id.action_toggle_reader_mode -> {
                toggleReaderMode()
                return true
            }
            R.id.action_send_to_notes -> {
                sendCurrentPageToNotes()
                return true
            }
            R.id.action_create_task -> {
                createTaskFromCurrentPage()
                return true
            }
            R.id.action_settings -> {
                showSnackbar("Browser settings coming soon")
                return true
            }
            R.id.action_help -> {
                showSnackbar("Help & feedback coming soon")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupUrlBar() {
        // Set up URL input
        binding.urlInput.apply {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    loadUrl(text.toString())
                    clearFocus()
                    true
                } else false
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    loadUrl(text.toString())
                    clearFocus()
                    true
                } else false
            }
            setOnItemClickListener { _, _, position, _ ->
                val url = suggestionsAdapter.getItem(position) ?: return@setOnItemClickListener
                loadUrl(url)
                clearFocus()
            }
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadUrl("https://www.google.com")
                    true
                }
                R.id.nav_tabs -> {
                    showTabsDialog()
                    true
                }
                R.id.nav_bookmarks -> {
                    showBookmarksDialog()
                    true
                }
                R.id.nav_downloads -> {
                    showDownloads()
                    true
                }
                R.id.nav_menu -> {
                    showBottomSheetMenu()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupFab() {
        binding.fabNewTab.setOnClickListener { openNewTab() }
    }
    
    private fun openNewTab(initialUrl: String? = null) {
        val webView = createNewWebView()
        addNewTab(webView, initialUrl ?: "about:blank")
        updateUI()
        if (isIncognitoMode) showIncognitoSnackbar()
    }
    
    private fun addNewTab(webView: WebView, url: String) {
        val tab = Tab(
            webView = webView,
            title = "New Tab",
            url = url,
            isIncognito = isIncognitoMode,
            groupName = currentTab?.groupName ?: "General"
        )
        tabs.add(tab)
        currentTabIndex = tabs.size - 1
        
        // Add WebView to container
        binding.webContainer.removeAllViews()
        binding.webContainer.addView(webView)
        
        // Load URL if provided
        if (url != "about:blank") {
            loadUrl(url)
        }
        
        // Update UI
        updateUI()
    }
    
    fun closeTab(index: Int) {
        if (tabs.isEmpty() || index !in tabs.indices) return
        val tabToRemove = tabs[index]
        if (tabToRemove.webView.parent == binding.webContainer) {
            binding.webContainer.removeView(tabToRemove.webView)
        }
        tabs.removeAt(index)
        currentTabIndex = (index - 1).coerceAtLeast(0).coerceAtMost(tabs.lastIndex)
        if (tabs.isNotEmpty()) {
            binding.webContainer.removeAllViews()
            binding.webContainer.addView(tabs[currentTabIndex].webView)
            updateUI()
        } else finish()
    }

    private fun closeCurrentTab() {
        if (tabs.size <= 1) {
            finish()
            return
        }
        
        val tabToRemove = currentTab ?: return
        binding.webContainer.removeView(tabToRemove.webView)
        tabs.removeAt(currentTabIndex)
        
        // Switch to the most recent tab
        currentTabIndex = if (currentTabIndex >= tabs.size) tabs.size - 1 else currentTabIndex
        
        if (tabs.isNotEmpty()) {
            binding.webContainer.addView(currentTab?.webView)
            updateUI()
        } else {
            finish()
        }
    }
    
    private fun loadUrl(url: String) {
        val tab = currentTab ?: return
        val webView = tab.webView
        
        // Process the URL
        val processedUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("about:") -> url
            url.contains(".") && !url.startsWith(" ") -> "https://$url"
            else -> "https://www.google.com/search?q=${Uri.encode(url)}"
        }
        
        // Update tab URL
        tab.url = processedUrl
        
        // Load the URL
        webView.loadUrl(processedUrl)
        
        // Update UI
        updateUI()
    }
    
    private fun showIncognitoSnackbar() {
        currentSnackbar?.dismiss()
        currentSnackbar = Snackbar.make(binding.root, "Incognito mode on", Snackbar.LENGTH_SHORT)
            .setAction("EXIT") { toggleIncognito() }
        currentSnackbar?.show()
    }
    
    private fun toggleIncognito() {
        isIncognitoMode = !isIncognitoMode
        if (isIncognitoMode) {
            tabs.removeAll { !it.isIncognito }
            if (tabs.none { it.isIncognito }) openNewTab() else {
                currentTabIndex = tabs.indexOfLast { it.isIncognito }
                updateUI()
            }
            showIncognitoSnackbar()
        } else {
            tabs.removeAll { it.isIncognito }
            if (tabs.isEmpty()) openNewTab() else {
                currentTabIndex = tabs.size - 1
                updateUI()
            }
            currentSnackbar?.dismiss(); currentSnackbar = null
        }
    }
    
    private fun showTabsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tabs, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tabsRecyclerView)
        val displayPairs = tabs.mapIndexed { index, tab -> index to tab }
            .filter { (_, tab) -> currentGroupFilter == null || tab.groupName == currentGroupFilter }

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = com.sevenk.browser.adapter.TabsAdapter(displayPairs.map { it.second }) { position ->
            val actualIndex = displayPairs.getOrNull(position)?.first ?: return@TabsAdapter
            currentTabIndex = actualIndex
            binding.webContainer.removeAllViews()
            binding.webContainer.addView(tabs[actualIndex].webView)
            updateUI()
        }

        val suffix = currentGroupFilter?.let { " • Group: $it" }.orEmpty()
        MaterialAlertDialogBuilder(this)
            .setTitle("Tabs (${displayPairs.size})$suffix")
            .setView(dialogView)
            .setPositiveButton("New Tab") { _, _ -> openNewTab() }
            .setNeutralButton("Groups") { _, _ -> showTabGroupsDialog() }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun showBookmarks() {
        showBookmarksDialog()
    }

    private fun addCurrentPageBookmark() {
        val tab = currentTab ?: return
        val url = tab.url.trim()
        if (url.isBlank() || url == "about:blank") {
            showSnackbar("Open a page first")
            return
        }
        val title = tab.title.ifBlank { url }
        val now = System.currentTimeMillis()
        val updated = loadBookmarks().toMutableList().apply {
            removeAll { it.url == url }
            add(0, BookmarkEntry(now, title, url, now))
            while (size > 300) removeLast()
        }
        persistBookmarks(updated)
        showSnackbar("Bookmarked")
    }

    private fun showBookmarksDialog() {
        val bookmarks = loadBookmarks()
        if (bookmarks.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Bookmarks")
                .setMessage("No bookmarks yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = bookmarks.map { "${it.title}\n${it.url}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Bookmarks")
            .setItems(labels) { _, which -> loadUrl(bookmarks[which].url) }
            .setNeutralButton("Clear All") { _, _ ->
                persistBookmarks(emptyList())
                showSnackbar("Bookmarks cleared")
            }
            .setPositiveButton("Add Current") { _, _ -> addCurrentPageBookmark() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadBookmarks(): List<BookmarkEntry> {
        val raw = bookmarksPrefs.getString("bookmarks_json", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        BookmarkEntry(
                            id = obj.optLong("id"),
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            addedAt = obj.optLong("addedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistBookmarks(list: List<BookmarkEntry>) {
        val arr = JSONArray()
        list.forEach { bookmark ->
            arr.put(
                JSONObject()
                    .put("id", bookmark.id)
                    .put("title", bookmark.title)
                    .put("url", bookmark.url)
                    .put("addedAt", bookmark.addedAt)
            )
        }
        bookmarksPrefs.edit().putString("bookmarks_json", arr.toString()).apply()
    }

    private fun toggleReaderMode() {
        val tab = currentTab ?: return
        tab.readerModeEnabled = !tab.readerModeEnabled
        applyReaderMode(tab.webView, tab.readerModeEnabled)
        showSnackbar(if (tab.readerModeEnabled) "Reader mode enabled" else "Reader mode disabled")
        updateUI()
    }

    private fun applyReaderMode(webView: WebView, enabled: Boolean) {
        val js = if (enabled) {
            """
            (function() {
              var old = document.getElementById('sevenk-reader-style');
              if (old) old.remove();
              var style = document.createElement('style');
              style.id = 'sevenk-reader-style';
              style.innerHTML = 'body{max-width:780px!important;margin:0 auto!important;padding:16px!important;line-height:1.7!important;font-size:18px!important;background:#111!important;color:#f1f1f1!important;}header,footer,nav,aside,form,button,iframe,video,canvas,[role="banner"],[role="navigation"],[role="complementary"],.ads,.ad,.advert{display:none!important;}img{max-width:100%!important;height:auto!important;}';
              document.head.appendChild(style);
            })();
            """.trimIndent()
        } else {
            """
            (function() {
              var old = document.getElementById('sevenk-reader-style');
              if (old) old.remove();
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }

    private fun showTabGroupsDialog() {
        val tab = currentTab
        val options = arrayOf(
            "Assign current tab to group",
            "Filter tabs by group",
            "Clear group filter"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Tab Groups")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (tab == null) return@setItems
                        val input = EditText(this).apply {
                            hint = "Group name"
                            setText(tab.groupName)
                            setSelection(text?.length ?: 0)
                        }
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Set Group")
                            .setView(input)
                            .setPositiveButton("Save") { _, _ ->
                                val value = input.text?.toString()?.trim().orEmpty().ifBlank { "General" }
                                tab.groupName = value
                                currentGroupFilter = value
                                updateUI()
                                showSnackbar("Moved to group: $value")
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    1 -> {
                        val groups = tabs.map { it.groupName }.distinct().sorted()
                        if (groups.isEmpty()) {
                            showSnackbar("No groups yet")
                            return@setItems
                        }
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Choose Group")
                            .setItems(groups.toTypedArray()) { _, index ->
                                currentGroupFilter = groups[index]
                                showSnackbar("Showing group: ${groups[index]}")
                                showTabsDialog()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    2 -> {
                        currentGroupFilter = null
                        showSnackbar("Showing all tabs")
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val i = incoming ?: run {
            if (tabs.isEmpty()) openNewTab("https://www.google.com")
            return
        }

        when (i.action) {
            ACTION_OPEN_OFFLINE -> {
                if (tabs.isEmpty()) openNewTab("about:blank")
                showOfflinePagesDialog()
                return
            }
            ACTION_OPEN_PRIVATE_TAB -> {
                if (!isIncognitoMode) toggleIncognito()
                val privateUrl = i.getStringExtra(EXTRA_URL)
                if (tabs.isEmpty()) openNewTab(privateUrl ?: "about:blank")
                else openNewTab(privateUrl ?: "about:blank")
                return
            }
            Intent.ACTION_VIEW -> {
                val targetUrl = i.dataString ?: i.getStringExtra(EXTRA_URL)
                if (tabs.isEmpty()) openNewTab(targetUrl ?: "https://www.google.com")
                else if (!targetUrl.isNullOrBlank()) loadUrl(targetUrl)
                return
            }
            Intent.ACTION_SEND -> {
                val shared = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
                if (tabs.isEmpty()) openNewTab(if (shared.isNotBlank()) shared else "https://www.google.com")
                else if (shared.isNotBlank()) loadUrl(shared)
                return
            }
        }

        val explicitUrl = i.getStringExtra(EXTRA_URL)
        if (tabs.isEmpty()) {
            openNewTab(explicitUrl ?: "https://www.google.com")
        } else if (!explicitUrl.isNullOrBlank()) {
            loadUrl(explicitUrl)
        }
    }

    private fun saveCurrentPageOffline() {
        val tab = currentTab ?: return
        val webView = tab.webView
        val url = tab.url.trim()
        if (url.isBlank() || url == "about:blank") {
            showSnackbar("Open a page first")
            return
        }

        val title = tab.title.ifBlank { url }
        val dir = File(filesDir, "browser_offline")
        if (!dir.exists()) dir.mkdirs()
        val id = System.currentTimeMillis()
        val file = File(dir, "offline_$id.mht")

        webView.saveWebArchive(file.absolutePath, false) { savedPath ->
            val actual = savedPath ?: file.absolutePath
            val page = OfflinePage(
                id = id,
                title = title,
                url = url,
                savedAt = id,
                archivePath = actual
            )
            val existing = loadOfflinePages().toMutableList().apply {
                removeAll { it.url == page.url }
                add(0, page)
                while (size > 100) removeLast()
            }
            persistOfflinePages(existing)
            showSnackbar("Saved offline")
        }
    }

    private fun showOfflinePagesDialog() {
        val pages = loadOfflinePages()
        if (pages.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Offline Pages")
                .setMessage("No saved pages yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = pages.map {
            "${it.title}\n${dateFormat.format(it.savedAt)}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Offline Pages")
            .setItems(labels) { _, which ->
                val selected = pages[which]
                val archiveFile = File(selected.archivePath)
                if (archiveFile.exists()) {
                    loadUrl("file://${archiveFile.absolutePath}")
                } else {
                    loadUrl(selected.url)
                }
            }
            .setNeutralButton("Clear All") { _, _ ->
                clearOfflinePages()
                showSnackbar("Offline pages cleared")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun clearOfflinePages() {
        loadOfflinePages().forEach { page ->
            runCatching {
                val f = File(page.archivePath)
                if (f.exists()) f.delete()
            }
        }
        persistOfflinePages(emptyList())
    }

    private fun sendCurrentPageToNotes() {
        val tab = currentTab ?: return
        val url = tab.url.trim()
        if (url.isBlank() || url == "about:blank") {
            showSnackbar("Open a page first")
            return
        }
        val title = tab.title.ifBlank { "Saved from 7K Browser" }
        val content = buildString {
            append(url)
            append("\n\nSaved from 7K Browser")
        }
        val note = NotesRepository.get(this).createNew(title = title, content = content)
        NoteEditorActivity.start(this, note.id)
        showSnackbar("Sent to 7K Notes")
    }

    private fun createTaskFromCurrentPage() {
        val tab = currentTab ?: return
        val url = tab.url.trim()
        if (url.isBlank() || url == "about:blank") {
            showSnackbar("Open a page first")
            return
        }
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val taskTitle = if (host.isNotBlank()) {
            "Read: ${tab.title.ifBlank { host }}"
        } else {
            "Read: ${tab.title.ifBlank { "Saved page" }}"
        }
        startActivity(
            Intent(this, TasksCommanderActivity::class.java)
                .putExtra("prefill_task_title", taskTitle)
                .putExtra("prefill_task_url", url)
        )
        showSnackbar("Task sent to 7K Tasks Commander")
    }

    private fun loadOfflinePages(): List<OfflinePage> {
        val raw = offlinePrefs.getString("offline_pages_json", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        OfflinePage(
                            id = obj.optLong("id"),
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            savedAt = obj.optLong("savedAt"),
                            archivePath = obj.optString("archivePath")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistOfflinePages(list: List<OfflinePage>) {
        val arr = JSONArray()
        list.forEach { page ->
            arr.put(
                JSONObject()
                    .put("id", page.id)
                    .put("title", page.title)
                    .put("url", page.url)
                    .put("savedAt", page.savedAt)
                    .put("archivePath", page.archivePath)
            )
        }
        offlinePrefs.edit().putString("offline_pages_json", arr.toString()).apply()
    }
    
    private fun showDownloads() {
        // Open downloads manager
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            showSnackbar("No download manager found")
        }
    }
    
    private fun showBottomSheetMenu() {
        val menuView = layoutInflater.inflate(R.layout.bottom_sheet_menu, binding.root as ViewGroup, false)
        
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(menuView)
        
        menuView.findViewById<View>(R.id.menu_incognito).setOnClickListener {
            toggleIncognito()
            dialog.dismiss()
        }
        
        menuView.findViewById<View>(R.id.menu_desktop_site).setOnClickListener {
            isDesktopMode = !isDesktopMode
            currentTab?.webView?.settings?.apply {
                userAgentString = if (isDesktopMode) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                } else {
                    null // Reset to default
                }
                currentTab?.webView?.reload()
            }
            dialog.dismiss()
        }

        menuView.findViewById<View>(R.id.menu_reader_mode).setOnClickListener {
            toggleReaderMode()
            dialog.dismiss()
        }

        menuView.findViewById<View>(R.id.menu_bookmarks).setOnClickListener {
            showBookmarksDialog()
            dialog.dismiss()
        }

        menuView.findViewById<View>(R.id.menu_tab_groups).setOnClickListener {
            showTabGroupsDialog()
            dialog.dismiss()
        }

        menuView.findViewById<View>(R.id.menu_save_offline).setOnClickListener {
            saveCurrentPageOffline()
            dialog.dismiss()
        }

        menuView.findViewById<View>(R.id.menu_send_notes).setOnClickListener {
            sendCurrentPageToNotes()
            dialog.dismiss()
        }

        menuView.findViewById<View>(R.id.menu_create_task).setOnClickListener {
            createTaskFromCurrentPage()
            dialog.dismiss()
        }
        
        menuView.findViewById<View>(R.id.menu_settings).setOnClickListener {
            // TODO: Open settings
            showSnackbar("Settings will be implemented in a future update")
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, null, "downloads/" + URLUtil.guessFileName(url, contentDisposition, mimeType))
        
        downloadManager.enqueue(request)
        showSnackbar("Download started")
    }
    
    override fun onPause() {
        super.onPause()
        currentTab?.lastActiveTime = System.currentTimeMillis()
    }
    
    override fun onResume() {
        super.onResume()
        currentTab?.webView?.onResume()
    }
    
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewWebView(): WebView {
        return WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                // Defaults tuned for usability (with planned settings toggle)
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                userAgentString = if (isDesktopMode) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                } else null
                setSupportMultipleWindows(false)
            }
            webViewClient = this@BrowserActivity.webViewClient
            webChromeClient = this@BrowserActivity.webChromeClient
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                handleDownload(url, userAgent, contentDisposition, mimeType)
            }
            try {
                // Restrictive cookies by default
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                CookieManager.getInstance().setAcceptCookie(!isIncognitoMode)
            } catch (_: Throwable) {}
            // Extra safety: do not retain form data/history by default
            try { clearFormData() } catch (_: Throwable) {}
            try { clearHistory() } catch (_: Throwable) {}
        }
    }

    // Suggestions
    private fun addSuggestion(url: String) {
        if (url.isBlank() || url == "about:blank") return
        if (!suggestions.contains(url)) {
            suggestions.add(0, url)
            if (suggestions.size > 10) suggestions.removeAt(suggestions.lastIndex)
            suggestionsAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentSnackbar?.dismiss()
        tabs.forEach { it.webView.destroy() }
        if (isIncognitoMode) {
            try { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() } catch (_: Throwable) {}
            try { WebStorage.getInstance().deleteAllData() } catch (_: Throwable) {}
        }
    }

    // --- Glass UI helpers ---
    private fun applyGlassUi() {
        try {
            val toolbar = binding.toolbar
            val bottom = binding.bottomNavigation
            val surface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorSurface)
            val onSurface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
            // Translucent backgrounds
            val translucent = with(Color.valueOf(surface)) {
                Color.argb((0.75f * 255).toInt(), (red() * 255).toInt(), (green() * 255).toInt(), (blue() * 255).toInt())
            }
            toolbar.setBackgroundColor(translucent)
            bottom.setBackgroundColor(translucent)

            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    val blur = android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP)
                    toolbar.setRenderEffect(blur)
                    bottom.setRenderEffect(blur)
                } catch (_: Throwable) {}
            }

            // Handle insets so content isn't obscured
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                toolbar.setPadding(toolbar.paddingLeft, sys.top, toolbar.paddingRight, toolbar.paddingBottom)
                bottom.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight, sys.bottom)
                WindowInsetsCompat.CONSUMED
            }
        } catch (_: Throwable) { /* never crash UI theming */ }
    }

    
}
