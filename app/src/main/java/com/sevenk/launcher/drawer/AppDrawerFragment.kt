package com.sevenk.launcher.drawer

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.sevenk.launcher.AppInfo
import com.sevenk.launcher.LauncherActivity
import com.sevenk.launcher.R
import com.sevenk.launcher.util.Perf
import kotlinx.coroutines.launch

/**
 * Fragment for the app drawer page with enhanced search and category features.
 */
class AppDrawerFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageView
    private lateinit var emptyStateView: LinearLayout
    private lateinit var categoryTabs: TabLayout
    private lateinit var searchBarContainer: View

    private var allApps: List<AppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app_drawer, container, false)

        // Initialize views
        recyclerView = view.findViewById(R.id.appGridRecyclerView)
        searchEditText = view.findViewById(R.id.searchEditText)
        clearSearchButton = view.findViewById(R.id.clearSearchButton)
        emptyStateView = view.findViewById(R.id.emptyStateView)
        categoryTabs = view.findViewById(R.id.appCategoriesTabs)
        searchBarContainer = view.findViewById(R.id.searchBarContainer)

        // Set up the grid layout
        val columnCount = resources.getInteger(R.integer.app_drawer_columns)
        recyclerView.layoutManager = GridLayoutManager(context, columnCount)

        // Set up the adapter
        adapter = AppDrawerAdapter(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            onAppClick = { app ->
                Perf.trace("AppLaunch") {
                    (activity as? LauncherActivity)?.launchApp(app)
                }
            },
            onAppLongClick = { app, view ->
                showAppOptions(app, view)
                true
            }
        )
        recyclerView.adapter = adapter

        // Set up search functionality
        setupSearch()

        // Set up category tabs
        setupCategoryTabs()

        return view
    }

    /**
     * Updates the fragment with the provided list of apps
     */
    fun updateApps(apps: List<AppInfo>) {
        this.allApps = apps
        adapter.updateApps(apps)

        // Update category counts in tabs
        updateCategoryTabs()
    }

    /**
     * Sets up the search functionality
     */
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Show/hide clear button
                clearSearchButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE

                // Filter apps
                adapter.filter.filter(s)

                // Check if we need to show empty state
                checkEmptyState()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Set up clear button
        clearSearchButton.setOnClickListener {
            searchEditText.setText("")

            // Hide keyboard
            hideKeyboard()
        }
    }

    /**
     * Sets up the category tabs
     */
    private fun setupCategoryTabs() {
        // Add tabs for each category
        val categories = adapter.getCategories()
        categories.forEach { category ->
            categoryTabs.addTab(categoryTabs.newTab().setText(category))
        }

        // Listen for tab selection
        categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val category = tab?.text?.toString()
                adapter.setCategory(category)

                // Clear search when changing category
                if (!searchEditText.text.isNullOrEmpty()) {
                    searchEditText.setText("")
                }

                // Check if we need to show empty state
                checkEmptyState()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * Updates the category tabs with current counts
     */
    private fun updateCategoryTabs() {
        val categoryCounts = adapter.getCategoryCounts()

        for (i in 0 until categoryTabs.tabCount) {
            val tab = categoryTabs.getTabAt(i)
            val category = tab?.text?.toString() ?: continue
            val count = categoryCounts[category] ?: 0

            // Only show count if not "All" category
            if (category != "All") {
                tab.text = "$category ($count)"
            }
        }
    }

    /**
     * Checks if the empty state should be shown
     */
    private fun checkEmptyState() {
        val isEmpty = adapter.itemCount == 0
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    /**
     * Hides the keyboard
     */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    /**
     * Shows the keyboard and focuses on search
     */
    fun focusSearch() {
        searchEditText.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Shows options for the selected app
     */
    private fun showAppOptions(app: AppInfo, view: View) {
        val launcher = activity as? LauncherActivity ?: return
        val options = arrayOf("App Info", "Uninstall", "Add to Home", "Add to Dock", "Add to Sidebar")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(app.name)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // App Info
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = android.net.Uri.parse("package:${app.packageName}")
                            startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(requireContext(), "Could not open app info", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> { // Uninstall
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_UNINSTALL_PACKAGE)
                            intent.data = android.net.Uri.parse("package:${app.packageName}")
                            startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(requireContext(), "Could not uninstall app", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> { // Add to Home
                        val currentPage = launcher.getCurrentHomePage()
                        if (currentPage >= 0) {
                            launcher.addToHomePage(currentPage, app.packageName)
                            launcher.refreshHomePages()
                            android.widget.Toast.makeText(requireContext(), "Added to Home", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> { // Add to Dock
                        launcher.addPackageToListPublic("dock_packages", app.packageName)
                        launcher.rebuildDockPublic()
                        android.widget.Toast.makeText(requireContext(), "Added to Dock", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    4 -> { // Add to Sidebar
                        launcher.addPackageToListPublic("sidebar_packages", app.packageName)
                        launcher.rebuildSidebarPublic()
                        android.widget.Toast.makeText(requireContext(), "Added to Sidebar", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        fun newInstance(): AppDrawerFragment {
            return AppDrawerFragment()
        }
    }
}
