package com.sevenk.launcher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppDrawerPageFragment : Fragment() {
    private lateinit var pageRecycler: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: AppDrawerAdapter
    private var pendingItems: List<SectionedItem>? = null

    private val prefs by lazy { requireContext().getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_app_drawer_page, container, false)
        pageRecycler = view.findViewById(R.id.pageRecycler)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        val cols = getDrawerColumns()
        val glm = GridLayoutManager(requireContext(), cols)
        // Prefetch a couple rows to make scrolls feel instant
        glm.initialPrefetchItemCount = (cols * 2).coerceAtLeast(cols)
        pageRecycler.layoutManager = glm
        pageRecycler.setHasFixedSize(true)
        // Reduce jank on updates
        (pageRecycler.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        // Cache more views to minimize rebinds during fast scrolls
        pageRecycler.setItemViewCacheSize(80)
        adapter = AppDrawerAdapter(requireContext(), onItemClick = { app ->
            (activity as? LauncherActivity)?.launchApp(app)
        }, onItemLongPress = { app ->
            (activity as? LauncherActivity)?.let { it2 ->
                it2.runOnUiThread { it2.showIconOptions(app) }
            }
        })
        pageRecycler.adapter = adapter
        // Apply any items requested before adapter was initialized
        pendingItems?.let { buffered ->
            adapter.submitList(buffered)
            pendingItems = null
        }
        return view
    }

    fun updateAppsForPage(allApps: List<AppInfo>, sectionName: String, sectionMap: Map<String, String>) {
        // Build items for this page: no headers in paged UI
        val items = when (sectionName) {
            "7K Apps" -> allApps
                .filter { isSevenKApp(it) }
                .sortedBy { it.name }
            "All Apps", "ALL" -> allApps
                .filterNot { isSevenKApp(it) }
                .sortedBy { it.name }
            else -> allApps.filter { app -> sectionMap[app.packageName] == sectionName }.sortedBy { it.name }
        }.map { SectionedItem.App(it) }
        if (::adapter.isInitialized) {
            adapter.submitList(items)
            updateEmptyState(sectionName, items.isEmpty())
        } else {
            // Buffer until onCreateView completes
            pendingItems = items
        }
    }

    private fun isSevenKApp(app: AppInfo): Boolean {
        val pkg = app.packageName.lowercase()
        return pkg.startsWith("internal.7k") || pkg.startsWith("com.sevenk")
    }

    private fun updateEmptyState(sectionName: String, isEmpty: Boolean) {
        if (!::emptyStateText.isInitialized || !::pageRecycler.isInitialized) return
        val show = isEmpty
        emptyStateText.visibility = if (show) View.VISIBLE else View.GONE
        pageRecycler.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            emptyStateText.text = when (sectionName) {
                "7K Apps" -> "No 7K apps found yet."
                else -> "No apps in this section yet."
            }
        }
    }

    fun getAdapter(): AppDrawerAdapter = adapter

    fun isAtTop(): Boolean = !pageRecycler.canScrollVertically(-1)

    private fun getDrawerColumns(): Int {
        val cols = prefs.getInt("drawer_columns", 5)
        return cols.coerceIn(3, 7)
    }
}
