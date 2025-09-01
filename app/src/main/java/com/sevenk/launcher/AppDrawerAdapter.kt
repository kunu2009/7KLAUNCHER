package com.sevenk.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.iconpack.IconPackHelper

// Using existing SectionedItem class from SectionedItem.kt

class AppDrawerAdapter(
    private val context: Context,
    onItemClick: ((AppInfo) -> Unit)? = null,
    private val onItemLongPress: ((AppInfo) -> Unit)? = null,
    private val iconPackHelper: IconPackHelper? = null
) : ListAdapter<SectionedItem, RecyclerView.ViewHolder>(Diff) {

    var onItemClick: (AppInfo) -> Unit = onItemClick ?: {}
        private set

    fun setOnItemClick(handler: (AppInfo) -> Unit) {
        onItemClick = handler
    }

    // Note: Rely on DiffUtil for identity; do not use stable IDs to avoid rare hash collisions

    // Selection mode state
    private var selectionMode: Boolean = false
    private val selected: MutableSet<String> = mutableSetOf()

    fun enableSelectionMode() {
        selectionMode = true
        selected.clear()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    fun disableSelectionMode() {
        selectionMode = false
        selected.clear()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    fun getSelectedPackages(): Set<String> = selected.toSet()

    private fun toggleSelectionAt(position: Int, pkg: String) {
        if (selected.contains(pkg)) selected.remove(pkg) else selected.add(pkg)
        if (position in 0 until itemCount) notifyItemChanged(position, PAYLOAD_SELECTION)
    }

    fun setSelectedPackages(pkgs: Collection<String>) {
        selected.clear()
        selected.addAll(pkgs)
        if (selectionMode) notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SectionedItem.Header -> VIEW_TYPE_HEADER
        is SectionedItem.App -> VIEW_TYPE_APP
    }

    object Diff : DiffUtil.ItemCallback<SectionedItem>() {
        override fun areItemsTheSame(oldItem: SectionedItem, newItem: SectionedItem): Boolean = when {
            oldItem is SectionedItem.Header && newItem is SectionedItem.Header -> oldItem.name == newItem.name
            oldItem is SectionedItem.App && newItem is SectionedItem.App -> oldItem.app.packageName == newItem.app.packageName
            else -> false
        }

        override fun areContentsTheSame(oldItem: SectionedItem, newItem: SectionedItem): Boolean = oldItem == newItem
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val selectionCheck: ImageView? = view.findViewById(R.id.selectionCheck)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.sectionTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.app_drawer_section_header, parent, false))
            else -> AppViewHolder(inflater.inflate(R.layout.app_icon, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SectionedItem.Header -> bindHeader(holder as HeaderViewHolder, item)
            is SectionedItem.App -> {
                bindFull(holder as AppViewHolder, item.app)
                bindSelection(holder, item.app)
                bindClicks(holder, item.app)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_SELECTION)) {
            val item = getItem(position)
            if (item is SectionedItem.App) bindSelection(holder as AppViewHolder, item.app)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindHeader(holder: HeaderViewHolder, header: SectionedItem.Header) {
        holder.title.text = header.name
    }

    private fun bindFull(holder: AppViewHolder, app: AppInfo) {
        val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
        val iconPref = prefs.getInt("icon_size", 1).coerceIn(0, 2)
        val sizeRes = when (iconPref) {
            0 -> R.dimen.app_icon_size_small
            2 -> R.dimen.app_icon_size_large
            else -> R.dimen.app_icon_size_medium
        }
        val size = holder.itemView.resources.getDimensionPixelSize(sizeRes)
        val bmp = try {
            if (iconPackHelper != null) {
                // Prefer icon pack path; avoid computing default first to reduce work
                val cacheKey = "iconpack:${app.packageName}@$size"
                // Fast path: return if already cached
                val cached = try { javaClass.getDeclaredField("cache") } catch (_: Throwable) { null }
                // We don't access IconCache internals; attempt fetch via getBitmap with drawable
                val baseDrawable = context.packageManager.getApplicationIcon(app.packageName)
                val packed = iconPackHelper.getAppIcon(app.packageName, app.packageName, baseDrawable)
                IconCache.getBitmap(context, cacheKey, packed, size)
            } else {
                // No icon pack: use default/custom icon cache path
                IconCache.getBitmapForPackage(context, app.packageName, size)
            }
        } catch (_: Throwable) {
            // Fallback to host app icon for synthetic/internal entries
            val hostIcon = context.packageManager.getApplicationIcon(context.packageName)
            IconCache.getBitmap(context, "host:" + context.packageName + "@" + size, hostIcon, size)
        }
        holder.appIcon.setImageBitmap(bmp)
        holder.appName.text = app.name
        val showLabels = prefs.getBoolean("show_labels", true)
        holder.appName.visibility = if (showLabels) View.VISIBLE else View.GONE
    }

    private fun bindSelection(holder: AppViewHolder, app: AppInfo) {
        holder.selectionCheck?.visibility =
            if (selectionMode && selected.contains(app.packageName)) View.VISIBLE else View.GONE
    }

    private fun bindClicks(holder: AppViewHolder, app: AppInfo) {
        holder.itemView.setOnClickListener {
            if (selectionMode) {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) toggleSelectionAt(pos, app.packageName)
            } else {
                onItemClick(app)
            }
        }
        // Long-press-then-drag: start drag only when moved beyond touch slop
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
                        val dx = ev.x - downX
                        val dy = ev.y - downY
                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                            // Start drag with package name as ClipData
                            val clip = ClipData.newPlainText("app", app.packageName)
                            val shadow = View.DragShadowBuilder(v)
                            if (Build.VERSION.SDK_INT >= 24) v.startDragAndDrop(clip, shadow, app.packageName, 0)
                            else @Suppress("DEPRECATION") v.startDrag(clip, shadow, app.packageName, 0)
                            (context as? LauncherActivity)?.onAppDragStart()
                            startedDrag = true
                            // Consume so click doesn't fire
                            return@setOnTouchListener true
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    longPressed = false
                    startedDrag = false
                }
            }
            false
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP = 1
        private const val PAYLOAD_SELECTION = "sel"
    }
}
