package com.sevenk.launcher.drawer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sevenk.launcher.AppInfo

/**
 * Pager adapter for the app drawer that handles different sections/pages.
 */
class AppDrawerPagerAdapter(
    activity: FragmentActivity
) : FragmentStateAdapter(activity) {

    private val fragments = mutableListOf<AppDrawerFragment>()

    init {
        // Create the default app drawer fragment
        fragments.add(AppDrawerFragment.newInstance())
    }

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

    /**
     * Updates all fragments with the provided list of apps
     */
    fun updateApps(apps: List<AppInfo>) {
        fragments.forEach { fragment ->
            fragment.updateApps(apps)
        }
    }

    /**
     * Gets the fragment at the specified position
     */
    fun getFragmentAt(position: Int): AppDrawerFragment? {
        return if (position in fragments.indices) fragments[position] else null
    }

    /**
     * Focuses the search field in the current fragment
     */
    fun focusSearch(position: Int) {
        getFragmentAt(position)?.focusSearch()
    }
}
