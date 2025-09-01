package com.sevenk.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.iconpack.IconPackHelper

// Using existing HomeItem class from HomeItem.kt

class HomePageAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongPress: ((AppInfo) -> Unit)? = null,
    private val onFolderClick: ((HomeItem.FolderItem) -> Unit)? = null,
    private val onFolderLongPress: ((HomeItem.FolderItem) -> Unit)? = null,
    private val iconPackHelper: IconPackHelper? = null
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(Diff) {

    private companion object {
        const val TYPE_APP = 0
        const val TYPE_FOLDER = 1
    }

    object Diff : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean = when {
            oldItem is HomeItem.App && newItem is HomeItem.App -> oldItem.appInfo.packageName == newItem.appInfo.packageName
            oldItem is HomeItem.FolderItem && newItem is HomeItem.FolderItem ->
                oldItem.folder.name == newItem.folder.name
            else -> false
        }

        override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean = oldItem == newItem
    }

    class AppVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)
    }

    class FolderVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.folderIcon)
        val name: TextView = v.findViewById(R.id.folderName)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HomeItem.App -> TYPE_APP
        is HomeItem.FolderItem -> TYPE_FOLDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderVH(inflater.inflate(R.layout.folder_icon, parent, false))
            else -> AppVH(inflater.inflate(R.layout.app_icon, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeItem.App -> {
                val h = holder as AppVH
                val app = item.appInfo
                val size = h.itemView.resources.getDimensionPixelSize(R.dimen.icon_size)
                val ctx = h.itemView.context
                val bmp = try {
                    // Default app icon
                    val defaultIcon = ctx.packageManager.getApplicationIcon(app.packageName)
                    // Apply icon pack if available
                    val drawable = iconPackHelper?.getAppIcon(app.packageName, app.packageName, defaultIcon) ?: defaultIcon
                    IconCache.getBitmap(ctx, "iconpack:${app.packageName}@$size", drawable, size)
                } catch (_: Throwable) {
                    val hostIcon = ctx.packageManager.getApplicationIcon(ctx.packageName)
                    IconCache.getBitmap(ctx, "host:" + ctx.packageName + "@" + size, hostIcon, size)
                }
                h.icon.setImageBitmap(bmp)
                h.name.text = app.name
                h.itemView.setOnClickListener { onAppClick(app) }
                // Long-press-then-drag vs long-press-without-move
                val vc = android.view.ViewConfiguration.get(h.itemView.context)
                val touchSlop = vc.scaledTouchSlop
                var downX = 0f
                var downY = 0f
                var longPressed = false
                var startedDrag = false
                val gd = android.view.GestureDetector(h.itemView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: android.view.MotionEvent) {
                        longPressed = true
                        downX = e.x; downY = e.y
                    }
                })
                h.itemView.setOnTouchListener { v, ev ->
                    gd.onTouchEvent(ev)
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (longPressed && !startedDrag) {
                                val dx = ev.x - downX; val dy = ev.y - downY
                                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                    val clip = android.content.ClipData.newPlainText("app", app.packageName)
                                    val shadow = View.DragShadowBuilder(v)
                                    if (android.os.Build.VERSION.SDK_INT >= 24) v.startDragAndDrop(clip, shadow, app.packageName, 0)
                                    else @Suppress("DEPRECATION") v.startDrag(clip, shadow, app.packageName, 0)
                                    startedDrag = true
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (longPressed && !startedDrag) {
                                onAppLongPress?.invoke(app)
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
            is HomeItem.FolderItem -> {
                val h = holder as FolderVH
                h.name.text = item.folder.name
                // Build a 2x2 mosaic preview from first four app icons in the folder
                val ctx = h.itemView.context
                val size = h.itemView.resources.getDimensionPixelSize(R.dimen.icon_size)
                val half = size / 2
                val padding = (h.itemView.resources.displayMetrics.density * 2).toInt() // 2dp gap
                val apps = item.folder.apps
                if (apps.isEmpty()) {
                    h.icon.setImageResource(android.R.drawable.ic_menu_agenda)
                } else {
                    val icons = apps.take(4).map { app ->
                        try {
                            val defaultIcon = ctx.packageManager.getApplicationIcon(app.packageName)
                            val drawable = iconPackHelper?.getAppIcon(app.packageName, app.packageName, defaultIcon) ?: defaultIcon
                            IconCache.getBitmap(ctx, "iconpack:${app.packageName}@${half - padding}", drawable, half - padding)
                        } catch (_: Throwable) {
                            val hostIcon = ctx.packageManager.getApplicationIcon(ctx.packageName)
                            IconCache.getBitmap(ctx, "host:" + ctx.packageName + "@" + (half - padding), hostIcon, half - padding)
                        }
                    }
                    val mosaic = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(mosaic)
                    // Positions: [0]=top-left, [1]=top-right, [2]=bottom-left, [3]=bottom-right
                    fun drawAt(ix: Int, x: Int, y: Int) {
                        if (ix >= icons.size) return
                        val bmp = icons[ix]
                        val left = x + padding
                        val top = y + padding
                        canvas.drawBitmap(bmp, left.toFloat(), top.toFloat(), null)
                    }
                    drawAt(0, 0, 0)
                    drawAt(1, half, 0)
                    drawAt(2, 0, half)
                    drawAt(3, half, half)
                    h.icon.setImageBitmap(mosaic)
                }
                h.itemView.setOnClickListener { onFolderClick?.invoke(item) }
                h.itemView.setOnLongClickListener {
                    onFolderLongPress?.invoke(item)
                    onFolderLongPress != null
                }
            }
        }
    }
}
