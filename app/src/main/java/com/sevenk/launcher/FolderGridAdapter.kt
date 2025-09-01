package com.sevenk.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FolderGridAdapter(
    private val items: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<FolderGridAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.app_icon, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val size = holder.itemView.resources.getDimensionPixelSize(R.dimen.icon_size)
        val ctx = holder.itemView.context
        val bmp = try {
            IconCache.getBitmapForPackage(ctx, app.packageName, size)
        } catch (_: Throwable) {
            val hostIcon = ctx.packageManager.getApplicationIcon(ctx.packageName)
            IconCache.getBitmap(ctx, "host:" + ctx.packageName + "@" + size, hostIcon, size)
        }
        holder.icon.setImageBitmap(bmp)
        holder.name.text = app.name
        holder.itemView.setOnClickListener { onClick(app) }
    }
}
