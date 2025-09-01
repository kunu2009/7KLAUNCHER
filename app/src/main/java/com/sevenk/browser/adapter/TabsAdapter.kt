package com.sevenk.browser.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sevenk.browser.BrowserActivity
import com.sevenk.launcher.R
import com.sevenk.browser.model.Tab
import com.google.android.material.color.MaterialColors

class TabsAdapter(
    private val tabs: List<Tab>,
    private val onTabClick: (Int) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab)
        
        holder.itemView.setOnClickListener {
            onTabClick(position)
        }
        
        holder.closeButton.setOnClickListener {
            (holder.itemView.context as? BrowserActivity)?.closeTab(position)
        }
    }

    override fun getItemCount() = tabs.size

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tabTitle)
        private val url: TextView = itemView.findViewById(R.id.tabUrl)
        private val favicon: ImageView = itemView.findViewById(R.id.tabFavicon)
        val closeButton: ImageView = itemView.findViewById(R.id.tabClose)
        val cardView: MaterialCardView = itemView.findViewById(R.id.tabCard)
        
        fun bind(tab: Tab) {
            title.text = tab.title.ifEmpty { "New Tab" }
            url.text = tab.url.ifEmpty { "" }
            
            // Load favicon
            tab.webView.favicon?.let { icon ->
                favicon.setImageBitmap(icon)
                favicon.visibility = View.VISIBLE
            } ?: run {
                favicon.setImageResource(R.drawable.ic_web)
                favicon.visibility = View.VISIBLE
            }
            
            // Set incognito state
            if (tab.isIncognito) {
                cardView.strokeColor = itemView.context.getColor(R.color.incognito_primary)
                cardView.setCardBackgroundColor(
                    itemView.context.getColor(R.color.incognito_background)
                )
            } else {
                cardView.strokeColor = itemView.context.getColor(android.R.color.transparent)
                val surface = MaterialColors.getColor(cardView, com.google.android.material.R.attr.colorSurface)
                cardView.setCardBackgroundColor(surface)
            }
        }
    }
}
