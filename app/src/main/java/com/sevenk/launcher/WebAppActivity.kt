package com.sevenk.launcher

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sevenk.launcher.util.Perf

class WebAppActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Perf.begin("WebApp.onCreate")
        webView = WebView(this)
        // Use themed background to match 7K palette (light/dark aware)
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.sevenk_bg))
        setContentView(webView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val title = intent.getStringExtra(EXTRA_TITLE)
        if (!title.isNullOrBlank()) supportActionBar?.title = title

        val url = intent.getStringExtra(EXTRA_URL) ?: "https://www.google.com/"

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Enable local storage and caching for offline
        settings.databaseEnabled = true
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        // Fit-to-screen and mobile-like behavior
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        @Suppress("DEPRECATION")
        runCatching { settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING }.onFailure { }
        settings.defaultTextEncodingName = "utf-8"
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportZoom(true)
        // Choose cache mode based on connectivity
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isOnline: Boolean = try {
            val info: NetworkInfo? = cm?.activeNetworkInfo
            info != null && info.isConnected
        } catch (_: Throwable) { true }
        settings.cacheMode = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.userAgentString = settings.userAgentString + " 7KLauncherWebView"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            private var retriedFromCache = false

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Load inside
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                // Try loading from cache if network fails
                if (!retriedFromCache) {
                    retriedFromCache = true
                    settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    view?.reload()
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Match dark mode content when device is in dark theme (Android 10+)
        try {
            val nightMask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isNight = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val forceDarkMethod = WebSettings::class.java.getMethod("setForceDark", Int::class.javaPrimitiveType)
            // 2 = FORCE_DARK_ON, 0 = OFF
            forceDarkMethod.invoke(settings, if (isNight) 2 else 0)
            // Tint system bars to align with web app surface and set icon contrast
            val bg = ContextCompat.getColor(this, R.color.sevenk_bg)
            window.statusBarColor = bg
            window.navigationBarColor = bg
            // Let content draw edge-to-edge; control icon appearance for contrast
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isNight
            controller.isAppearanceLightNavigationBars = !isNight
        } catch (_: Throwable) { }

        // Best-effort enable Service Worker handling where supported (Android 7.0+)
        try {
            val controllerCls = Class.forName("android.webkit.ServiceWorkerController")
            val getInstance = controllerCls.getMethod("getInstance")
            val controller = getInstance.invoke(null)
            val clientCls = Class.forName("android.webkit.ServiceWorkerClient")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                clientCls.classLoader,
                arrayOf(clientCls)
            ) { _, _, _ -> null }
            val setClient = controllerCls.getMethod("setServiceWorkerClient", clientCls)
            setClient.invoke(controller, proxy)
        } catch (_: Throwable) { /* ignore if not supported */ }

        if (savedInstanceState == null) {
            Perf.begin("WebApp.loadUrl")
            webView.loadUrl(url)
            Perf.end()
        } else {
            webView.restoreState(savedInstanceState)
        }
        Perf.end()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
