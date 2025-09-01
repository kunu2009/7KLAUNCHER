package com.sevenk.launcher.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.AppInfo
import com.sevenk.launcher.R
import com.sevenk.launcher.haptics.HapticFeedbackManager
import kotlinx.coroutines.*

/**
 * Global search activity with app and web search
 */
class GlobalSearchActivity : AppCompatActivity() {
    
    private lateinit var searchInput: EditText
    private lateinit var searchResults: RecyclerView
    private lateinit var clearButton: ImageView
    private lateinit var searchAdapter: SearchResultsAdapter
    private lateinit var hapticManager: HapticFeedbackManager
    
    private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_search)
        
        hapticManager = HapticFeedbackManager(this)
        
        initViews()
        setupSearch()
        
        // Focus search input
        searchInput.requestFocus()
    }
    
    private fun initViews() {
        searchInput = findViewById(R.id.searchInput)
        searchResults = findViewById(R.id.searchResults)
        clearButton = findViewById(R.id.clearButton)
        
        searchAdapter = SearchResultsAdapter { result ->
            hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
            handleSearchResult(result)
        }
        
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = searchAdapter
        
        clearButton.setOnClickListener {
            hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
            searchInput.text.clear()
        }
    }
    
    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                
                searchJob?.cancel()
                if (query.isNotEmpty()) {
                    searchJob = searchScope.launch {
                        delay(300) // Debounce
                        performSearch(query)
                    }
                } else {
                    searchAdapter.updateResults(emptyList())
                }
            }
        })
    }
    
    private suspend fun performSearch(query: String) {
        withContext(Dispatchers.IO) {
            val results = mutableListOf<SearchResult>()
            
            // Search installed apps
            val apps = searchApps(query)
            results.addAll(apps.map { SearchResult.App(it) })
            
            // Add web search option
            if (query.length > 2) {
                results.add(SearchResult.WebSearch(query))
            }
            
            // Add system actions
            val systemActions = searchSystemActions(query)
            results.addAll(systemActions)
            
            withContext(Dispatchers.Main) {
                searchAdapter.updateResults(results)
            }
        }
    }
    
    private fun searchApps(query: String): List<AppInfo> {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return installedApps.mapNotNull { appInfo ->
            try {
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                if (appName.contains(query, ignoreCase = true)) {
                    // We don't know the main launchable activity/class here; leave className blank
                    // and icon null. This object is only used for display in search.
                    AppInfo(
                        name = appName,
                        packageName = appInfo.packageName,
                        className = "",
                        icon = null,
                        applicationInfo = appInfo
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name }
    }
    
    private fun searchSystemActions(query: String): List<SearchResult.SystemAction> {
        val actions = mutableListOf<SearchResult.SystemAction>()
        
        when {
            "settings".contains(query, ignoreCase = true) -> {
                actions.add(SearchResult.SystemAction("Settings", "android.settings.SETTINGS"))
            }
            "wifi".contains(query, ignoreCase = true) -> {
                actions.add(SearchResult.SystemAction("Wi-Fi Settings", "android.settings.WIFI_SETTINGS"))
            }
            "bluetooth".contains(query, ignoreCase = true) -> {
                actions.add(SearchResult.SystemAction("Bluetooth Settings", "android.settings.BLUETOOTH_SETTINGS"))
            }
            "battery".contains(query, ignoreCase = true) -> {
                actions.add(SearchResult.SystemAction("Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS"))
            }
        }
        
        return actions
    }
    
    private fun handleSearchResult(result: SearchResult) {
        when (result) {
            is SearchResult.App -> {
                launchApp(result.appInfo.packageName)
            }
            is SearchResult.WebSearch -> {
                performWebSearch(result.query)
            }
            is SearchResult.SystemAction -> {
                launchSystemAction(result.action)
            }
        }
        finish()
    }
    
    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.SUCCESS)
            }
        } catch (e: Exception) {
            hapticManager.performHaptic(HapticFeedbackManager.HapticType.ERROR)
        }
    }
    
    private fun performWebSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            startActivity(intent)
            hapticManager.performHaptic(HapticFeedbackManager.HapticType.SUCCESS)
        } catch (e: Exception) {
            // Fallback to browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, 
                    android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(query)}"))
                startActivity(browserIntent)
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.SUCCESS)
            } catch (ex: Exception) {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.ERROR)
            }
        }
    }
    
    private fun launchSystemAction(action: String) {
        try {
            val intent = Intent(action)
            startActivity(intent)
            hapticManager.performHaptic(HapticFeedbackManager.HapticType.SUCCESS)
        } catch (e: Exception) {
            hapticManager.performHaptic(HapticFeedbackManager.HapticType.ERROR)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        searchScope.cancel()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
    }
}

sealed class SearchResult {
    data class App(val appInfo: AppInfo) : SearchResult()
    data class WebSearch(val query: String) : SearchResult()
    data class SystemAction(val name: String, val action: String) : SearchResult()
}
