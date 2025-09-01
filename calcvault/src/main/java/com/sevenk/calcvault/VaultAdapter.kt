package com.sevenk.calcvault

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.DecimalFormat

data class VaultItem(val file: File)

class VaultAdapter(private val items: MutableList<VaultItem>, private val onItemClick: (File) -> Unit) : RecyclerView.Adapter<VaultAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vault, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.file.name
        holder.subtitle.text = readableSize(item.file.length())
        // Try load private thumbnail from sibling .thumbs dir
        val thumbs = File(item.file.parentFile, ".thumbs")
        val thumbFile = File(thumbs, item.file.nameWithoutExtension + ".jpg")
        if (thumbFile.exists()) {
            val bmp = BitmapFactory.decodeFile(thumbFile.absolutePath)
            if (bmp != null) holder.thumb.setImageBitmap(bmp)
        } else {
            // Keep placeholder
        }
        holder.itemView.setOnClickListener { onItemClick(item.file) }
    }

    override fun getItemCount(): Int = items.size

    fun submitFiles(files: List<File>) {
        items.clear()
        // sort newest first
        items.addAll(files.sortedByDescending { it.lastModified() }.map { VaultItem(it) })
        notifyDataSetChanged()
    }

    private fun readableSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
        val df = DecimalFormat("#.##")
        return df.format(bytes / Math.pow(1024.0, z.toDouble())) + " " + (" KMGTPE"[z]) + "B"
    }
}
