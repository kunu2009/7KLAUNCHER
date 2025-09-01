package com.sevenk.launcher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomePagerAdapter(activity: FragmentActivity, private val normalPageCount: Int) : FragmentStateAdapter(activity) {
    // +2 pages: position 0 = TODO, position 1 = Stan AI, rest are normal pages shifted by 2
    override fun getItemCount(): Int = normalPageCount + 2
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TodoPageFragment()
            1 -> StanHomeFragment()
            else -> HomePageFragment.newInstance(position - 2)
        }
    }
}
