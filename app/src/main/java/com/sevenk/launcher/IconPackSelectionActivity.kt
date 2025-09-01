package com.sevenk.launcher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.sevenk.launcher.iconpack.IconPackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconPackSelectionActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var iconPackManager: IconPackManager
    private lateinit var adapter: IconPackAdapter
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var emptyView: TextView

    private val prefs by lazy { getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_pack_selection)
        
        // Initialize views
        recyclerView = findViewById(R.id.iconPackRecyclerView)
        progressIndicator = findViewById(R.id.progressIndicator)
        emptyView = findViewById(R.id.emptyView)

        // Get the icon pack manager
        iconPackManager = (application as SevenKApplication).iconPackManager

        // Set up the RecyclerView
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = IconPackAdapter(emptyList(), ::onIconPackSelected)
        recyclerView.adapter = adapter

        // Load icon packs
        loadIconPacks()

        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.select_icon_pack)
    }
    
    private fun loadIconPacks() {
        progressIndicator.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Add a default "system" icon pack option
                val defaultIconPack = IconPackManager.IconPack(
                    packageName = "",
                    name = getString(R.string.system_icons),
                    previewDrawable = getDrawable(R.drawable.ic_system_icons)
                )

                // Scan for installed icon packs
                val iconPacks = withContext(Dispatchers.IO) {
                    iconPackManager.scanForInstalledIconPacks()
                }

                // Combine the default with installed packs
                val allPacks = listOf(defaultIconPack) + iconPacks

                withContext(Dispatchers.Main) {
                    if (allPacks.isEmpty()) {
                        // Show empty state
                        emptyView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        // Update the adapter with the icon packs
                        adapter.updateIconPacks(allPacks)
                        recyclerView.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE

                        // Highlight the currently selected icon pack
                        val currentPackage = prefs.getString("selected_icon_pack", "")
                        adapter.setSelectedIconPack(currentPackage ?: "")
                    }

                    progressIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@IconPackSelectionActivity,
                        getString(R.string.error_loading_icon_packs), Toast.LENGTH_SHORT).show()
                    progressIndicator.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun onIconPackSelected(iconPack: IconPackManager.IconPack) {
        progressIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val success = iconPackManager.loadIconPack(iconPack.packageName)

                withContext(Dispatchers.Main) {
                    progressIndicator.visibility = View.GONE

                    if (success) {
                        // Save the selected icon pack
                        prefs.edit().putString("selected_icon_pack", iconPack.packageName).apply()

                        // Update selection in adapter
                        adapter.setSelectedIconPack(iconPack.packageName)

                        // Show success message
                        val message = if (iconPack.packageName.isEmpty()) {
                            getString(R.string.using_system_icons)
                        } else {
                            getString(R.string.icon_pack_applied, iconPack.name)
                        }
                        Toast.makeText(this@IconPackSelectionActivity, message, Toast.LENGTH_SHORT).show()

                        // Notify launcher that icons have changed
                        setResult(RESULT_OK)

                        // Close after brief delay to show selection
                        recyclerView.postDelayed({
                            finish()
                        }, 800)
                    } else {
                        // Show error message
                        Toast.makeText(this@IconPackSelectionActivity,
                            getString(R.string.error_applying_icon_pack), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressIndicator.visibility = View.GONE
                    Toast.makeText(this@IconPackSelectionActivity,
                        getString(R.string.error_applying_icon_pack), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * Adapter for displaying icon packs in a grid
 */
class IconPackAdapter(
    private var iconPacks: List<IconPackManager.IconPack>,
    private val onItemClick: (IconPackManager.IconPack) -> Unit
) : RecyclerView.Adapter<IconPackAdapter.ViewHolder>() {
    
    private var selectedPackageName = ""

    fun updateIconPacks(newIconPacks: List<IconPackManager.IconPack>) {
        iconPacks = newIconPacks
        notifyDataSetChanged()
    }
    
    fun setSelectedIconPack(packageName: String) {
        val oldSelected = selectedPackageName
        selectedPackageName = packageName

        // Find the positions to update
        val oldPosition = iconPacks.indexOfFirst { it.packageName == oldSelected }
        val newPosition = iconPacks.indexOfFirst { it.packageName == packageName }

        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (newPosition >= 0) notifyItemChanged(newPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_pack, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val iconPack = iconPacks[position]
        holder.bind(iconPack, iconPack.packageName == selectedPackageName)

        holder.itemView.setOnClickListener {
            onItemClick(iconPack)
        }
    }
    
    override fun getItemCount() = iconPacks.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        private val iconView: ImageView = itemView.findViewById(R.id.iconPackIcon)
        private val nameView: TextView = itemView.findViewById(R.id.iconPackName)

        fun bind(iconPack: IconPackManager.IconPack, isSelected: Boolean) {
            nameView.text = iconPack.name
            iconView.setImageDrawable(iconPack.previewDrawable)

            // Update selection state
            cardView.isChecked = isSelected
            cardView.strokeWidth = if (isSelected) {
                itemView.resources.getDimensionPixelSize(R.dimen.selected_card_stroke_width)
            } else {
                0
            }
        }
    }
}
