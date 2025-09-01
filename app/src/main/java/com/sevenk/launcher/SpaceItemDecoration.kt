package com.sevenk.launcher

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpaceItemDecoration(private val spacePx: Int, private val orientation: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        if (orientation == RecyclerView.HORIZONTAL) {
            outRect.left = if (pos == 0) 0 else spacePx
            outRect.right = 0
        } else {
            outRect.top = if (pos == 0) 0 else spacePx
            outRect.bottom = 0
        }
    }
}
