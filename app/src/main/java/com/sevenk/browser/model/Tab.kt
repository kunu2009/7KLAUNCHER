package com.sevenk.browser.model

import android.webkit.WebView

/**
 * Simple tab model shared by BrowserActivity and TabsAdapter.
 */
data class Tab(
    val webView: WebView,
    var title: String = "New Tab",
    var url: String = "",
    var isIncognito: Boolean = false,
    var lastActiveTime: Long = System.currentTimeMillis()
)
