package com.sevenk.launcher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AppDrawerPagerAdapter(activity: FragmentActivity, sections: List<String>) : FragmentStateAdapter(activity) {
    private val fragmentRefs = mutableMapOf<Long, AppDrawerPageFragment>()
    private var sectionList: MutableList<String> = sections.toMutableList()

    fun getSectionName(position: Int): String = sectionList[position]

    fun getFragment(position: Int): AppDrawerPageFragment? {
        if (position !in 0 until itemCount) return null
        val id = getItemId(position)
        return fragmentRefs[id]
    }

    fun setSections(newSections: List<String>) {
        // Update list
        sectionList = newSections.toMutableList()
        // Remove any fragment refs whose IDs are no longer present
        val validIds = sectionList.map { it.hashCode().toLong() }.toSet()
        val toRemove = fragmentRefs.keys.filter { it !in validIds }
        toRemove.forEach { fragmentRefs.remove(it) }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sectionList.size

    override fun createFragment(position: Int): Fragment {
        val f = AppDrawerPageFragment()
        fragmentRefs[getItemId(position)] = f
        return f
    }

    override fun getItemId(position: Int): Long {
        // Stable ID based on section name
        return sectionList[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return sectionList.any { it.hashCode().toLong() == itemId }
    }
}
