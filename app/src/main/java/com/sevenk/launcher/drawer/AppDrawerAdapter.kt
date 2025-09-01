package com.sevenk.launcher.drawer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.AppInfo
import com.sevenk.launcher.R
import com.sevenk.launcher.util.Perf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Adapter for the app drawer grid with enhanced features:
 * - Category support
 * - Efficient filtering
 * - Glass effect for app icons
 */
class AppDrawerAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Boolean
) : RecyclerView.Adapter<AppDrawerAdapter.AppViewHolder>(), Filterable {

    // All apps
    private var allApps: List<AppInfo> = emptyList()
    // Filtered apps (what's currently displayed)
    private var filteredApps: List<AppInfo> = emptyList()
    // Apps grouped by category
    private val categoryMap = mutableMapOf<String, MutableList<AppInfo>>()
    // Currently selected category (null means "All")
    private var currentCategory: String? = null

    // Standard categories
    private val allCategories = listOf("All", "Work", "Games", "Social", "Utilities")

    // Initialize with default categories
    init {
        allCategories.forEach { category ->
            categoryMap[category] = mutableListOf()
        }
    }

    /**
     * Updates the adapter with a new list of apps and categorizes them
     */
    fun updateApps(apps: List<AppInfo>) {
        scope.launch {
            val start = System.currentTimeMillis()

            // Categorize apps in background
            val categorized = withContext(Dispatchers.Default) {
                Perf.trace("AppCategorization") {
                    categorizeApps(apps)
                }
            }

            // Update on main thread
            withContext(Dispatchers.Main) {
                allApps = apps
                filteredApps = if (currentCategory == null || currentCategory == "All") {
                    apps
                } else {
                    categoryMap[currentCategory].orEmpty()
                }
                notifyDataSetChanged()

                val duration = System.currentTimeMillis() - start
                Perf.recordMetric("AppDrawerUpdate", duration)
            }
        }
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.bind(app)
    }

    override fun getItemCount(): Int = filteredApps.size

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""

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

            val results = FilterResults()
            results.values = filteredList
            results.count = filteredList.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredApps = results?.values as? List<AppInfo> ?: emptyList()
            notifyDataSetChanged()
        }
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameView: TextView = itemView.findViewById(R.id.appName)

        fun bind(app: AppInfo) {
            // Set the app icon
            iconView.setImageDrawable(app.icon)

            // Set the app name
            nameView.text = app.name

            // Set click listener
            itemView.setOnClickListener { onAppClick(app) }

            // Set long click listener
            itemView.setOnLongClickListener { view -> onAppLongClick(app, view) }
        }
    }
}
