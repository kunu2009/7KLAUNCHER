package com.sevenk.browser.privacy

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.util.*

class PrivacyManager(private val context: Context) {
    private val blockedTrackers = setOf(
        // Google
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "adservice.google.com",
        "googleadservices.com",
        "googlesyndication.com",
        // Facebook
        "facebook.com",
        "facebook.net",
        "fbcdn.net",
        "connect.facebook.net",
        // Twitter
        "twitter.com",
        "twimg.com",
        // Other Ad/Analytics
        "appsflyer.com",
        "app-measurement.com",
        "criteo.com",
        "scorecardresearch.com",
        "admob.com",
        "mopub.com",
        "chartbeat.net"
    )

    private var blockTrackers = true

    fun shouldBlockRequest(request: WebResourceRequest): Boolean {
        if (!blockTrackers || request.isForMainFrame) {
            return false
        }

        val requestHost = request.url.host ?: return false

        return blockedTrackers.any { trackerHost ->
            requestHost.endsWith(trackerHost)
        }
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }

    fun setBlockTrackers(block: Boolean) {
        blockTrackers = block
    }

    fun clearAllData() {
        WebView(context).apply {
            clearCache(true)
            clearFormData()
            clearHistory()
            clearSslPreferences()
        }.destroy()

        android.webkit.CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }

        android.webkit.WebStorage.getInstance().deleteAllData()
    }
}