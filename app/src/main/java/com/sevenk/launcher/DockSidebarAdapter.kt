package com.sevenk.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.iconpack.IconPackHelper

class DockSidebarAdapter(
    val items: MutableList<AppInfo>,
    private val onClick: (AppInfo) -> Unit,
    private val onLongPress: ((AppInfo) -> Unit)? = null,
    showLabels: Boolean = true,
    iconSizePx: Int? = null,
    private val iconPackHelper: IconPackHelper? = null
) : RecyclerView.Adapter<DockSidebarAdapter.VH>() {

    var showLabels: Boolean = showLabels
        set(value) {
            field = value
            if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, "appearance")
        }

    var iconSizePx: Int? = iconSizePx
        set(value) {
            field = value
            if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, "appearance")
        }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.app_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val size = iconSizePx ?: holder.itemView.resources.getDimensionPixelSize(R.dimen.icon_size)
        val ctx = holder.itemView.context
        val bmp = try {
            val defaultIcon = IconCache.getBitmapForPackage(ctx, app.packageName, size)
            // Apply icon pack if available
            iconPackHelper?.let { helper ->
                val iconPackIcon = helper.getAppIcon(app.packageName, app.packageName, 
                    ctx.packageManager.getApplicationIcon(app.packageName))
                IconCache.getBitmap(ctx, "iconpack:${app.packageName}@$size", iconPackIcon, size)
            } ?: defaultIcon
        } catch (_: Throwable) {
            // Fallback to host app icon for synthetic/internal entries
            val hostIcon = ctx.packageManager.getApplicationIcon(ctx.packageName)
            IconCache.getBitmap(ctx, "host:" + ctx.packageName + "@" + size, hostIcon, size)
        }
        holder.icon.setImageBitmap(bmp)
        holder.name.text = app.name
        holder.name.visibility = if (showLabels) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(app) }
        // Long-press-then-drag vs long-press-without-move
        val vc = android.view.ViewConfiguration.get(holder.itemView.context)
        val touchSlop = vc.scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var longPressed = false
        var startedDrag = false
        val gd = android.view.GestureDetector(holder.itemView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: android.view.MotionEvent) {
                longPressed = true
                downX = e.x; downY = e.y
            }
        })
        holder.itemView.setOnTouchListener { v, ev ->
            gd.onTouchEvent(ev)
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (longPressed && !startedDrag) {
                        val dx = ev.x - downX; val dy = ev.y - downY
                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                            val clip = android.content.ClipData.newPlainText("app", app.packageName)
                            val shadow = android.view.View.DragShadowBuilder(v)
                            if (android.os.Build.VERSION.SDK_INT >= 24) v.startDragAndDrop(clip, shadow, app.packageName, 0)
                            else @Suppress("DEPRECATION") v.startDrag(clip, shadow, app.packageName, 0)
                            startedDrag = true
                            return@setOnTouchListener true
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (longPressed && !startedDrag) {
                        onLongPress?.invoke(app)
                        longPressed = false
                        return@setOnTouchListener true
                    }
                    longPressed = false; startedDrag = false
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    longPressed = false; startedDrag = false
                }
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size

    // Efficient list update
    fun setItems(newItems: List<AppInfo>) {
        val old = items.toList()
        val cb = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition].packageName == newItems[newItemPosition].packageName
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = old[oldItemPosition]
                val n = newItems[newItemPosition]
                return o.name == n.name
            }
        }
        val diff = DiffUtil.calculateDiff(cb, false)
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}
