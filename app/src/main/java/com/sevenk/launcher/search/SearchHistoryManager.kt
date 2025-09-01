package com.sevenk.launcher.search

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Search history manager for faster repeat access
 */
class SearchHistoryManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val MAX_HISTORY_SIZE = 50
        private const val MAX_SUGGESTIONS = 8
        private const val HISTORY_KEY = "search_history_data"
    }
    
    data class SearchEntry(
        val query: String,
        val timestamp: Long,
        val resultType: String, // "app", "web", "system"
        val resultData: String? = null, // package name for apps, url for web
        val frequency: Int = 1
    )
    
    private var searchHistory = mutableListOf<SearchEntry>()
    private var isLoaded = false
    
    /**
     * Initialize and load history
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            loadHistory()
            isLoaded = true
        }
    }
    
    /**
     * Add search query to history
     */
    fun addSearch(query: String, resultType: String, resultData: String? = null) {
        if (query.isBlank()) return
        
        scope.launch {
            val normalizedQuery = query.trim().lowercase()
            
            // Find existing entry
            val existingIndex = searchHistory.indexOfFirst { 
                it.query.lowercase() == normalizedQuery && it.resultType == resultType 
            }
            
            if (existingIndex != -1) {
                // Update existing entry
                val existing = searchHistory[existingIndex]
                searchHistory[existingIndex] = existing.copy(
                    timestamp = System.currentTimeMillis(),
                    frequency = existing.frequency + 1,
                    resultData = resultData ?: existing.resultData
                )
            } else {
                // Add new entry
                searchHistory.add(0, SearchEntry(
                    query = query.trim(),
                    timestamp = System.currentTimeMillis(),
                    resultType = resultType,
                    resultData = resultData
                ))
            }
            
            // Trim history size
            if (searchHistory.size > MAX_HISTORY_SIZE) {
                searchHistory = searchHistory.take(MAX_HISTORY_SIZE).toMutableList()
            }
            
            saveHistory()
        }
    }
    
    /**
     * Get search suggestions based on input
     */
    suspend fun getSuggestions(input: String): List<SearchEntry> {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) initialize()
            
            if (input.isBlank()) {
                // Return recent searches
                searchHistory
                    .sortedWith(compareByDescending<SearchEntry> { it.frequency }.thenByDescending { it.timestamp })
                    .take(MAX_SUGGESTIONS)
            } else {
                val normalizedInput = input.lowercase()
                
                // Find matching queries
                searchHistory
                    .filter { it.query.lowercase().contains(normalizedInput) }
                    .sortedWith(
                        compareBy<SearchEntry> { !it.query.lowercase().startsWith(normalizedInput) }
                            .thenByDescending { it.frequency }
                            .thenByDescending { it.timestamp }
                    )
                    .take(MAX_SUGGESTIONS)
            }
        }
    }
    
    /**
     * Get recent searches
     */
    suspend fun getRecentSearches(limit: Int = 10): List<SearchEntry> {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) initialize()
            
            searchHistory
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }
    
    /**
     * Get popular searches
     */
    suspend fun getPopularSearches(limit: Int = 10): List<SearchEntry> {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) initialize()
            
            searchHistory
                .sortedWith(compareByDescending<SearchEntry> { it.frequency }.thenByDescending { it.timestamp })
                .take(limit)
        }
    }
    
    /**
     * Clear search history
     */
    fun clearHistory() {
        scope.launch {
            searchHistory.clear()
            saveHistory()
        }
    }
    
    /**
     * Remove specific search entry
     */
    fun removeSearch(query: String, resultType: String) {
        scope.launch {
            searchHistory.removeAll { 
                it.query.equals(query, ignoreCase = true) && it.resultType == resultType 
            }
            saveHistory()
        }
    }
    
    /**
     * Get search statistics
     */
    suspend fun getSearchStats(): SearchStats {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) initialize()
            
            val totalSearches = searchHistory.sumOf { it.frequency }
            val uniqueQueries = searchHistory.size
            val appSearches = searchHistory.filter { it.resultType == "app" }.sumOf { it.frequency }
            val webSearches = searchHistory.filter { it.resultType == "web" }.sumOf { it.frequency }
            val systemSearches = searchHistory.filter { it.resultType == "system" }.sumOf { it.frequency }
            
            val mostSearched = searchHistory.maxByOrNull { it.frequency }
            val oldestSearch = searchHistory.minByOrNull { it.timestamp }
            val newestSearch = searchHistory.maxByOrNull { it.timestamp }
            
            SearchStats(
                totalSearches = totalSearches,
                uniqueQueries = uniqueQueries,
                appSearches = appSearches,
                webSearches = webSearches,
                systemSearches = systemSearches,
                mostSearchedQuery = mostSearched?.query,
                oldestSearchDate = oldestSearch?.timestamp,
                newestSearchDate = newestSearch?.timestamp
            )
        }
    }
    
    /**
     * Load history from preferences
     */
    private fun loadHistory() {
        try {
            val historyJson = prefs.getString(HISTORY_KEY, "[]") ?: "[]"
            val jsonArray = JSONArray(historyJson)
            
            searchHistory.clear()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val entry = SearchEntry(
                    query = jsonObject.getString("query"),
                    timestamp = jsonObject.getLong("timestamp"),
                    resultType = jsonObject.getString("resultType"),
                    resultData = jsonObject.optString("resultData", null),
                    frequency = jsonObject.optInt("frequency", 1)
                )
                searchHistory.add(entry)
            }
        } catch (e: Exception) {
            // If loading fails, start with empty history
            searchHistory.clear()
        }
    }
    
    /**
     * Save history to preferences
     */
    private fun saveHistory() {
        try {
            val jsonArray = JSONArray()
            
            searchHistory.forEach { entry ->
                val jsonObject = JSONObject().apply {
                    put("query", entry.query)
                    put("timestamp", entry.timestamp)
                    put("resultType", entry.resultType)
                    put("resultData", entry.resultData ?: "")
                    put("frequency", entry.frequency)
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit()
                .putString(HISTORY_KEY, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            // Ignore save errors
        }
    }
    
    /**
     * Cleanup old entries
     */
    fun cleanupOldEntries(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000) { // 30 days
        scope.launch {
            val cutoffTime = System.currentTimeMillis() - maxAgeMs
            val sizeBefore = searchHistory.size
            
            searchHistory.removeAll { it.timestamp < cutoffTime }
            
            if (searchHistory.size != sizeBefore) {
                saveHistory()
            }
        }
    }
    
    /**
     * Destroy manager and cleanup resources
     */
    fun destroy() {
        scope.cancel()
    }
}

data class SearchStats(
    val totalSearches: Int,
    val uniqueQueries: Int,
    val appSearches: Int,
    val webSearches: Int,
    val systemSearches: Int,
    val mostSearchedQuery: String?,
    val oldestSearchDate: Long?,
    val newestSearchDate: Long?
)
