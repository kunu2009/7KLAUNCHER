package com.sevenk.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.gesture.GestureManager

class GestureSettingsActivity : AppCompatActivity() {
    
    private lateinit var gestureManager: GestureManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GestureAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture_settings)
        
        supportActionBar?.title = "Gesture Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        gestureManager = GestureManager(this)
        recyclerView = findViewById(R.id.gestureRecyclerView)
        
        setupRecyclerView()
        loadGestures()
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GestureAdapter { config ->
            gestureManager.saveGestureConfig(config)
        }
        recyclerView.adapter = adapter
    }
    
    private fun loadGestures() {
        val gestures = gestureManager.getAllGestureConfigs()
        adapter.submitList(gestures)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class GestureAdapter(
    private val onConfigChanged: (GestureManager.GestureConfig) -> Unit
) : RecyclerView.Adapter<GestureAdapter.ViewHolder>() {
    
    private var gestures = listOf<GestureManager.GestureConfig>()
    
    fun submitList(newGestures: List<GestureManager.GestureConfig>) {
        gestures = newGestures
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gesture_setting, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val gesture = gestures[position]
        holder.bind(gesture, onConfigChanged)
    }
    
    override fun getItemCount() = gestures.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gestureNameView: TextView = itemView.findViewById(R.id.gestureName)
        private val actionSpinner: Spinner = itemView.findViewById(R.id.actionSpinner)
        
        fun bind(
            config: GestureManager.GestureConfig,
            onConfigChanged: (GestureManager.GestureConfig) -> Unit
        ) {
            gestureNameView.text = config.type.name.replace("_", " ").lowercase()
                .split(" ").joinToString(" ") { it.capitalize() }
            
            val actions = GestureManager.GestureAction.values()
            val adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, 
                actions.map { it.name.replace("_", " ").lowercase().split(" ").joinToString(" ") { word -> word.capitalize() } })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            actionSpinner.adapter = adapter
            
            val currentIndex = actions.indexOf(config.action)
            if (currentIndex >= 0) {
                actionSpinner.setSelection(currentIndex)
            }
            
            actionSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newAction = actions[position]
                    if (newAction != config.action) {
                        val newConfig = config.copy(action = newAction)
                        onConfigChanged(newConfig)
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            })
        }
    }
}
