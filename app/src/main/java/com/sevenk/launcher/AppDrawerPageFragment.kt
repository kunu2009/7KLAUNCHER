package com.sevenk.launcher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppDrawerPageFragment : Fragment() {
    private lateinit var pageRecycler: RecyclerView
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
            "ALL" -> allApps.sortedBy { it.name }
            else -> allApps.filter { app -> sectionMap[app.packageName] == sectionName }.sortedBy { it.name }
        }.map { SectionedItem.App(it) }
        if (::adapter.isInitialized) {
            adapter.submitList(items)
        } else {
            // Buffer until onCreateView completes
            pendingItems = items
        }
    }

    fun getAdapter(): AppDrawerAdapter = adapter

    fun isAtTop(): Boolean = !pageRecycler.canScrollVertically(-1)

    private fun getDrawerColumns(): Int {
        val cols = prefs.getInt("drawer_columns", 5)
        return cols.coerceIn(3, 7)
    }
}
