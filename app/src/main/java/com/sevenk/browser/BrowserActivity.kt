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
import android.widget.FrameLayout
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

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private var currentSnackbar: Snackbar? = null

    private val tabs = mutableListOf<Tab>()
    private var currentTabIndex = -1
    private var isIncognitoMode = false

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

        // Start with one tab
        openNewTab("https://www.google.com")
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
                    showBookmarks()
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
        val tab = Tab(webView = webView, title = "New Tab", url = url, isIncognito = isIncognitoMode)
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
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = com.sevenk.browser.adapter.TabsAdapter(tabs) { position ->
            currentTabIndex = position
            binding.webContainer.removeAllViews()
            binding.webContainer.addView(tabs[position].webView)
            updateUI()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Tabs (${tabs.size})")
            .setView(dialogView)
            .setPositiveButton("New Tab") { _, _ -> openNewTab() }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun showBookmarks() {
        // TODO: Implement bookmarks functionality
        showSnackbar("Bookmarks will be implemented in a future update")
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
        val menuView = layoutInflater.inflate(R.layout.bottom_sheet_menu, null)
        
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
                // Privacy-first defaults
                javaScriptEnabled = false // Off by default; can be enabled later via settings
                domStorageEnabled = false
                databaseEnabled = false
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
