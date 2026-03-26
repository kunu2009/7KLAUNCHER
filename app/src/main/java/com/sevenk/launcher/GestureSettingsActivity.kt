package com.sevenk.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sevenk.launcher.gesture.GestureManager
import java.util.Locale

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

    private data class LaunchableApp(
        val label: String,
        val packageName: String
    )
    
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
        private val targetAppView: TextView = itemView.findViewById(R.id.gestureTargetApp)

        private var launchableAppsCache: List<LaunchableApp>? = null
        
        fun bind(
            config: GestureManager.GestureConfig,
            onConfigChanged: (GestureManager.GestureConfig) -> Unit
        ) {
            var currentConfig = config

            gestureNameView.text = config.type.name.replace("_", " ").lowercase(Locale.getDefault())
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
            
            val actions = GestureManager.GestureAction.values()
            val adapter = ArrayAdapter(itemView.context, R.layout.item_glass_spinner_selected,
                actions.map {
                    it.name.replace("_", " ")
                        .lowercase(Locale.getDefault())
                        .split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
                })
            adapter.setDropDownViewResource(R.layout.item_glass_spinner_dropdown)
            actionSpinner.adapter = adapter
            
            val currentIndex = actions.indexOf(config.action)
            if (currentIndex >= 0) {
                actionSpinner.setSelection(currentIndex)
            }

            fun resolveTargetLabel(targetPackage: String?): String {
                if (targetPackage.isNullOrBlank()) return "Tap to choose app"
                val app = getLaunchableApps().firstOrNull { it.packageName == targetPackage }
                return if (app != null) "Target app: ${app.label}" else "Target app: $targetPackage"
            }

            fun refreshTargetAppUi() {
                val show = currentConfig.action == GestureManager.GestureAction.LAUNCH_APP
                targetAppView.visibility = if (show) View.VISIBLE else View.GONE
                if (show) {
                    targetAppView.text = resolveTargetLabel(currentConfig.targetPackage)
                }
            }

            fun openTargetAppPicker() {
                val apps = getLaunchableApps()
                if (apps.isEmpty()) return
                val actions = apps.mapIndexed { which, app ->
                    app.label to {
                        val selected = apps.getOrNull(which) ?: return@to
                        currentConfig = currentConfig.copy(
                            action = GestureManager.GestureAction.LAUNCH_APP,
                            targetPackage = selected.packageName
                        )
                        onConfigChanged(currentConfig)
                        refreshTargetAppUi()
                    }
                }
                showGlassActionSheet("Choose app for this gesture", actions)
            }

            refreshTargetAppUi()
            targetAppView.setOnClickListener {
                openTargetAppPicker()
            }
            
            actionSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newAction = actions[position]
                    if (newAction != currentConfig.action) {
                        currentConfig = if (newAction == GestureManager.GestureAction.LAUNCH_APP) {
                            currentConfig.copy(action = newAction)
                        } else {
                            currentConfig.copy(action = newAction, targetPackage = null)
                        }
                        onConfigChanged(currentConfig)
                        refreshTargetAppUi()

                        if (newAction == GestureManager.GestureAction.LAUNCH_APP && currentConfig.targetPackage.isNullOrBlank()) {
                            openTargetAppPicker()
                        }
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            })
        }

        private fun getLaunchableApps(): List<LaunchableApp> {
            launchableAppsCache?.let { return it }

            val context = itemView.context
            val packageManager = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }

            val apps = packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) return@mapNotNull null
                    LaunchableApp(label = label, packageName = pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }

            launchableAppsCache = apps
            return apps
        }

        private fun showGlassActionSheet(title: String, actions: List<Pair<String, () -> Unit>>) {
            val context = itemView.context
            val sheet = BottomSheetDialog(context)
            val root = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = dp(16)
                setPadding(pad, pad, pad, dp(20))
                setBackgroundResource(R.drawable.dialog_background)
            }

            val titleView = TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(8), dp(4), dp(8), dp(12))
            }
            root.addView(titleView)

            actions.forEach { (label, onClick) ->
                val item = TextView(context).apply {
                    text = label
                    textSize = 15f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    setBackgroundResource(R.drawable.glass_panel)
                    foreground = AppCompatResources.getDrawable(context, android.R.drawable.list_selector_background)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        sheet.dismiss()
                        onClick.invoke()
                    }
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                root.addView(item, lp)
            }

            val cancel = TextView(context).apply {
                text = "Cancel"
                textSize = 14f
                setTextColor(0xFF80CBC4.toInt())
                setPadding(dp(16), dp(12), dp(16), dp(12))
                gravity = android.view.Gravity.CENTER
                setOnClickListener { sheet.dismiss() }
            }
            root.addView(cancel)

            sheet.setContentView(root)
            sheet.show()
        }

        private fun dp(value: Int): Int = (value * itemView.resources.displayMetrics.density).toInt()
    }
}
