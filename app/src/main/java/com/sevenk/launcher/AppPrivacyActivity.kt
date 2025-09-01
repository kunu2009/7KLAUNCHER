package com.sevenk.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.privacy.AppPrivacyManager

class AppPrivacyActivity : AppCompatActivity() {
    
    private lateinit var privacyManager: AppPrivacyManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppPrivacyAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_privacy)
        
        supportActionBar?.title = "App Privacy"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        privacyManager = AppPrivacyManager(this)
        recyclerView = findViewById(R.id.appPrivacyRecyclerView)
        
        setupRecyclerView()
        loadApps()
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppPrivacyAdapter { packageName, level ->
            privacyManager.setAppPrivacyLevel(packageName, level)
        }
        recyclerView.adapter = adapter
    }
    
    private fun loadApps() {
        val pm = packageManager
        val main = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(main, 0)
            .map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                val privacyLevel = privacyManager.getAppPrivacyLevel(packageName)
                AppPrivacyItem(packageName, appName, icon, privacyLevel)
            }
            .sortedBy { it.appName }
        
        adapter.submitList(apps)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    data class AppPrivacyItem(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable,
        val privacyLevel: AppPrivacyManager.PrivacyLevel
    )
}

class AppPrivacyAdapter(
    private val onPrivacyChanged: (String, AppPrivacyManager.PrivacyLevel) -> Unit
) : RecyclerView.Adapter<AppPrivacyAdapter.ViewHolder>() {
    
    private var apps = listOf<AppPrivacyActivity.AppPrivacyItem>()
    
    fun submitList(newApps: List<AppPrivacyActivity.AppPrivacyItem>) {
        apps = newApps
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_privacy, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app, onPrivacyChanged)
    }
    
    override fun getItemCount() = apps.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val privacySpinner: Spinner = itemView.findViewById(R.id.privacySpinner)
        
        fun bind(
            app: AppPrivacyActivity.AppPrivacyItem,
            onPrivacyChanged: (String, AppPrivacyManager.PrivacyLevel) -> Unit
        ) {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName
            
            val levels = AppPrivacyManager.PrivacyLevel.values()
            val adapter = android.widget.ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, 
                levels.map { it.name.replace("_", " ").lowercase().split(" ").joinToString(" ") { word -> word.capitalize() } })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            privacySpinner.adapter = adapter
            
            val currentIndex = levels.indexOf(app.privacyLevel)
            if (currentIndex >= 0) {
                privacySpinner.setSelection(currentIndex)
            }
            
            privacySpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newLevel = levels[position]
                    if (newLevel != app.privacyLevel) {
                        onPrivacyChanged(app.packageName, newLevel)
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            })
        }
    }
}
