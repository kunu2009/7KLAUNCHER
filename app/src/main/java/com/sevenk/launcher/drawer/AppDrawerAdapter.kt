package com.sevenk.launcher.drawer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.AppInfo
import com.sevenk.launcher.R
import com.sevenk.launcher.util.Perf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Sealed class representing items in the app drawer
 */
sealed class DrawerItem {
    data class AppItem(val app: AppInfo) : DrawerItem()
    data class Header(val title: String) : DrawerItem()
}

/**
 * Adapter for the app drawer grid with enhanced features:
 * - Category support
 * - Sorting (A-Z, Most Used, Recently Used)
 * - Alphabet section headers
 * - Efficient filtering
 */
class AppDrawerAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP = 1
    }

    // All apps
    private var allApps: List<AppInfo> = emptyList()
    
    // Current display items (apps + headers)
    private var displayItems: List<DrawerItem> = emptyList()
    
    // Filtered apps (for search)
    private var filteredApps: List<AppInfo> = emptyList()
    
    // Apps grouped by category
    private val categoryMap = mutableMapOf<String, MutableList<AppInfo>>()
    
    // Current category (null means "All")
    private var currentCategory: String? = null
    
    // Current sort order (az, most_used, recent)
    private var sortOrder: String = "az"
    
    // Show alphabet headers
    private var showAlphabetHeaders: Boolean = true
    
    // Current search query to preserve when updating display
    private var currentSearchQuery: String = ""

    // Standard categories
    private val allCategories = listOf("All", "Work", "Games", "Social", "Utilities")

    // Initialize with default categories
    init {
        allCategories.forEach { category ->
            categoryMap[category] = mutableListOf()
        }
        loadPreferences()
    }

    private fun loadPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        sortOrder = prefs.getString("drawer_sort_order", "az") ?: "az"
        showAlphabetHeaders = prefs.getBoolean("drawer_alphabet_headers", true)
    }

    /**
     * Updates the adapter with a new list of apps and categorizes them
     */
    fun updateApps(apps: List<AppInfo>) {
        scope.launch {
            val start = System.currentTimeMillis()

            // Process in background
            val processed = withContext(Dispatchers.Default) {
                Perf.trace("AppCategorization") {
                    // Update all apps
                    allApps = apps
                    
                    // Categorize apps
                    categorizeApps(apps)
                    
                    // Apply current category filter
                    filteredApps = if (currentCategory == null || currentCategory == "All") {
                        sortApps(apps)
                    } else {
                        sortApps(categoryMap[currentCategory].orEmpty())
                    }
                    
                    // Generate display items (with headers if needed)
                    generateDisplayItems()
                }
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                displayItems = processed
                notifyDataSetChanged()
                val duration = System.currentTimeMillis() - start
                Perf.recordMetric("AppDrawerUpdate", duration)
            }
        }
    }
    
    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        return when (sortOrder) {
            "most" -> apps.sortedWith(compareByDescending<AppInfo> { it.usageStats?.totalTimeInForeground ?: 0 }
                .thenBy { it.name.lowercase(Locale.getDefault()) })
            "recent" -> apps.sortedWith(compareByDescending<AppInfo> { it.usageStats?.lastTimeUsed ?: 0 }
                .thenBy { it.name.lowercase(Locale.getDefault()) })
            else -> apps.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }
    
    private fun generateDisplayItems(): List<DrawerItem> {
        if (filteredApps.isEmpty()) return emptyList()
        
        val items = mutableListOf<DrawerItem>()
        
        if (showAlphabetHeaders) {
            // Group apps by first letter
            val groups = filteredApps.groupBy { 
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" 
            }.toSortedMap()
            
            // Add headers and apps
            groups.forEach { (letter, apps) ->
                items.add(DrawerItem.Header(letter))
                items.addAll(apps.map { DrawerItem.AppItem(it) })
            }
        } else {
            // Just add apps without headers
            items.addAll(filteredApps.map { DrawerItem.AppItem(it) })
        }
        
        return items
    }

    /**
     * Categorizes apps into predefined categories
     */
    private fun categorizeApps(apps: List<AppInfo>) {
        // Clear existing categories
        categoryMap.forEach { (_, apps) -> apps.clear() }

        // Add all apps to "All" category
        categoryMap["All"] = apps.toMutableList()

        // Categorize apps based on package name and app name
        apps.forEach { app ->
            val packageName = app.packageName.lowercase(Locale.getDefault())
            val appName = app.name.lowercase(Locale.getDefault())

            // Work apps
            if (packageName.contains("office") ||
                packageName.contains("docs") ||
                packageName.contains("sheets") ||
                packageName.contains("slides") ||
                packageName.contains("word") ||
                packageName.contains("excel") ||
                packageName.contains("powerpoint") ||
                packageName.contains("outlook") ||
                packageName.contains("teams") ||
                packageName.contains("slack") ||
                packageName.contains("trello") ||
                packageName.contains("drive") ||
                packageName.contains("dropbox") ||
                appName.contains("office") ||
                appName.contains("work") ||
                appName.contains("pdf") ||
                appName.contains("document")) {
                categoryMap["Work"]?.add(app)
            }

            // Games
            if (packageName.contains("game") ||
                packageName.contains("play.games") ||
                packageName.contains("unity") ||
                packageName.contains("ea.") ||
                packageName.contains("gameloft") ||
                packageName.contains("zynga") ||
                packageName.contains("king.com") ||
                packageName.contains("supercell") ||
                appName.contains("game") ||
                appName.contains("battle") ||
                appName.contains("clash") ||
                appName.contains("craft")) {
                categoryMap["Games"]?.add(app)
            }

            // Social
            if (packageName.contains("facebook") ||
                packageName.contains("twitter") ||
                packageName.contains("instagram") ||
                packageName.contains("snapchat") ||
                packageName.contains("tiktok") ||
                packageName.contains("whatsapp") ||
                packageName.contains("telegram") ||
                packageName.contains("signal") ||
                packageName.contains("messenger") ||
                packageName.contains("social") ||
                packageName.contains("chat") ||
                appName.contains("social") ||
                appName.contains("chat") ||
                appName.contains("message")) {
                categoryMap["Social"]?.add(app)
            }

            // Utilities
            if (packageName.contains("calculator") ||
                packageName.contains("calendar") ||
                packageName.contains("clock") ||
                packageName.contains("weather") ||
                packageName.contains("tools") ||
                packageName.contains("util") ||
                packageName.contains("settings") ||
                packageName.contains("file") ||
                packageName.contains("manager") ||
                appName.contains("tool") ||
                appName.contains("util") ||
                appName.contains("file") ||
                appName.contains("manager") ||
                appName.contains("weather") ||
                appName.contains("clock") ||
                appName.contains("calculator")) {
                categoryMap["Utilities"]?.add(app)
            }

            // If app wasn't categorized and isn't in "All" only, add to "Utilities"
            val isInSomeCategory = categoryMap.any { (key, apps) ->
                key != "All" && apps.contains(app)
            }

            if (!isInSomeCategory) {
                categoryMap["Utilities"]?.add(app)
            }
        }
    }

    /**
     * Sets the current category filter
     */
    fun setCategory(category: String?) {
        if (currentCategory == category) return

        currentCategory = category

        // Update filtered apps based on category
        filteredApps = if (category == null || category == "All") {
            allApps
        } else {
            categoryMap[category].orEmpty()
        }

        notifyDataSetChanged()
    }

    /**
     * Gets all available categories
     */
    fun getCategories(): List<String> = allCategories

    /**
     * Gets the count of apps in each category
     */
    fun getCategoryCounts(): Map<String, Int> {
        return categoryMap.mapValues { (_, apps) -> apps.size }
    }


    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is DrawerItem.Header -> VIEW_TYPE_HEADER
            is DrawerItem.AppItem -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_drawer_header, parent, false)
            )
            else -> AppViewHolder(
                inflater.inflate(R.layout.item_app_icon, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is DrawerItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is DrawerItem.AppItem -> (holder as AppViewHolder).bind(item.app)
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
            currentSearchQuery = query

            val filteredList = if (query.isEmpty()) {
                // No filter, use current category
                if (currentCategory == null || currentCategory == "All") {
                    allApps
                } else {
                    categoryMap[currentCategory].orEmpty()
                }
            } else {
                // Apply search filter within current category
                val baseList = if (currentCategory == null || currentCategory == "All") {
                    allApps
                } else {
                    categoryMap[currentCategory].orEmpty()
                }

                baseList.filter { app ->
                    app.name.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(query)
                }
            }
            
            // Sort the filtered list and convert to ArrayList
            val sortedList = sortApps(filteredList)
            
            val results = FilterResults()
            results.values = ArrayList(sortedList)
            results.count = sortedList.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val filtered = results?.values as? List<AppInfo> ?: emptyList()
            filteredApps = filtered
            displayItems = if (filtered.isEmpty()) {
                emptyList()
            } else {
                generateDisplayItems()
            }
            notifyDataSetChanged()
        }
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameView: TextView = itemView.findViewById(R.id.appName)
        
        init {
            // Set click listeners once in the constructor
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = displayItems[position]
                    if (item is DrawerItem.AppItem) {
                        onAppClick(item.app)
                    }
                }
            }
            
            itemView.setOnLongClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = displayItems[position]
                    if (item is DrawerItem.AppItem) {
                        onAppLongClick(item.app, view)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }

        fun bind(app: AppInfo) {
            // Set the app icon
            iconView.setImageDrawable(app.icon)

            // Set the app name
            nameView.text = app.name
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.headerText)
        
        fun bind(letter: String) {
            titleView.text = letter
        }
    }
    
    /**
     * Updates the sort order and refreshes the display
     */
    fun setSortOrder(order: String) {
        if (sortOrder != order) {
            sortOrder = order
            updateDisplay()
        }
    }
    
    /**
     * Toggles alphabet section headers
     */
    fun setShowAlphabetHeaders(show: Boolean) {
        if (showAlphabetHeaders != show) {
            showAlphabetHeaders = show
            updateDisplay()
        }
    }
    
    /**
     * Clears the current search query (used when search is cleared)
     */
    fun clearSearch() {
        currentSearchQuery = ""
        updateDisplay()
    }
    
    private fun updateDisplay() {
        // If there's an active search, reapply the filter instead of showing all apps
        if (currentSearchQuery.isNotEmpty()) {
            // Reapply the current search filter
            filter.filter(currentSearchQuery)
        } else {
            // No search active, show apps based on current category
            filteredApps = if (currentCategory == null || currentCategory == "All") {
                sortApps(allApps)
            } else {
                sortApps(categoryMap[currentCategory].orEmpty())
            }
            displayItems = generateDisplayItems()
            notifyDataSetChanged()
        }
    }
}
