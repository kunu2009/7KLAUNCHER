package com.sevenk.launcher.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.R

/**
 * Adapter for search results
 */
class SearchResultsAdapter(
    private val onItemClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.SearchViewHolder>() {
    
    private var results = listOf<SearchResult>()
    
    fun updateResults(newResults: List<SearchResult>) {
        results = newResults
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(results[position])
    }
    
    override fun getItemCount() = results.size
    
    inner class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.resultIcon)
        private val title: TextView = itemView.findViewById(R.id.resultTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.resultSubtitle)
        
        fun bind(result: SearchResult) {
            itemView.setOnClickListener { onItemClick(result) }
            
            when (result) {
                is SearchResult.App -> {
                    try {
                        val appIcon = itemView.context.packageManager.getApplicationIcon(result.appInfo.packageName)
                        icon.setImageDrawable(appIcon)
                    } catch (e: Exception) {
                        icon.setImageResource(R.drawable.ic_app_default)
                    }
                    title.text = result.appInfo.name
                    subtitle.text = "App"
                }
                is SearchResult.WebSearch -> {
                    icon.setImageResource(R.drawable.ic_search)
                    title.text = "Search \"${result.query}\""
                    subtitle.text = "Web search"
                }
                is SearchResult.SystemAction -> {
                    icon.setImageResource(R.drawable.ic_settings)
                    title.text = result.name
                    subtitle.text = "System setting"
                }
            }
        }
    }
}
