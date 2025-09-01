package com.sevenk.launcher.ecosystem

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.sevenk.launcher.haptics.HapticFeedbackManager

/**
 * Quick launcher for 7K ecosystem apps
 */
class QuickLauncher(private val context: Context) {
    
    private val hapticManager = HapticFeedbackManager(context)
    private val packageManager = context.packageManager
    
    data class EcosystemApp(
        val name: String,
        val packageName: String,
        val description: String,
        val color: Int,
        val fallbackUrl: String? = null
    )
    
    private val ecosystemApps = listOf(
        EcosystemApp(
            name = "7K Chat",
            packageName = "com.sevenk.chat",
            description = "Secure messaging platform",
            color = Color.parseColor("#1976D2"),
            fallbackUrl = "https://chat.7k.com"
        ),
        EcosystemApp(
            name = "7K Notes",
            packageName = "com.sevenk.notes",
            description = "Smart note-taking app",
            color = Color.parseColor("#4CAF50"),
            fallbackUrl = "https://notes.7k.com"
        ),
        EcosystemApp(
            name = "7K Files",
            packageName = "com.sevenk.files",
            description = "Cloud file manager",
            color = Color.parseColor("#FF9800"),
            fallbackUrl = "https://files.7k.com"
        ),
        EcosystemApp(
            name = "7K Calendar",
            packageName = "com.sevenk.calendar",
            description = "Smart calendar & scheduling",
            color = Color.parseColor("#9C27B0"),
            fallbackUrl = "https://calendar.7k.com"
        ),
        EcosystemApp(
            name = "7K Tasks",
            packageName = "com.sevenk.tasks",
            description = "Project management tool",
            color = Color.parseColor("#F44336"),
            fallbackUrl = "https://tasks.7k.com"
        ),
        EcosystemApp(
            name = "7K Wallet",
            packageName = "com.sevenk.wallet",
            description = "Digital wallet & payments",
            color = Color.parseColor("#00BCD4"),
            fallbackUrl = "https://wallet.7k.com"
        ),
        EcosystemApp(
            name = "7K Health",
            packageName = "com.sevenk.health",
            description = "Health & fitness tracker",
            color = Color.parseColor("#8BC34A"),
            fallbackUrl = "https://health.7k.com"
        ),
        EcosystemApp(
            name = "7K Music",
            packageName = "com.sevenk.music",
            description = "Music streaming service",
            color = Color.parseColor("#E91E63"),
            fallbackUrl = "https://music.7k.com"
        )
    )
    
    /**
     * Create quick launcher widget
     */
    fun createQuickLauncherWidget(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        
        // Title
        val titleText = TextView(context).apply {
            text = "7K Ecosystem"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        container.addView(titleText)
        
        // Create app buttons in grid
        val installedApps = getInstalledEcosystemApps()
        val availableApps = ecosystemApps.filter { app ->
            installedApps.contains(app.packageName) || app.fallbackUrl != null
        }
        
        // Create rows of 2 apps each
        for (i in availableApps.indices step 2) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 4)
            }
            
            // First app in row
            val firstApp = availableApps[i]
            val firstButton = createAppButton(firstApp, installedApps.contains(firstApp.packageName))
            rowLayout.addView(firstButton)
            
            // Second app in row (if exists)
            if (i + 1 < availableApps.size) {
                val secondApp = availableApps[i + 1]
                val secondButton = createAppButton(secondApp, installedApps.contains(secondApp.packageName))
                rowLayout.addView(secondButton)
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }
    
    /**
     * Create app button
     */
    private fun createAppButton(app: EcosystemApp, isInstalled: Boolean): View {
        val cardView = CardView(context).apply {
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(if (isInstalled) app.color else Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                hapticManager.performHaptic(HapticFeedbackManager.HapticType.LIGHT_TAP)
                launchApp(app, isInstalled)
            }
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 16, 12, 16)
        }
        
        // App name
        val nameText = TextView(context).apply {
            text = app.name
            textSize = 14f
            setTextColor(if (isInstalled) Color.WHITE else Color.parseColor("#333333"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        
        // App description
        val descText = TextView(context).apply {
            text = app.description
            textSize = 11f
            setTextColor(if (isInstalled) Color.parseColor("#E0E0E0") else Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }
        
        // Status indicator
        val statusText = TextView(context).apply {
            text = if (isInstalled) "INSTALLED" else "WEB APP"
            textSize = 9f
            setTextColor(if (isInstalled) Color.parseColor("#C8E6C9") else Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        
        container.addView(nameText)
        container.addView(descText)
        container.addView(statusText)
        
        cardView.addView(container)
        return cardView
    }
    
    /**
     * Launch ecosystem app
     */
    private fun launchApp(app: EcosystemApp, isInstalled: Boolean) {
        try {
            if (isInstalled) {
                // Launch installed app
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    // Fallback to web if launch intent fails
                    launchWebApp(app)
                }
            } else {
                // Launch web app
                launchWebApp(app)
            }
        } catch (e: Exception) {
            // Fallback to web app on any error
            launchWebApp(app)
        }
    }
    
    /**
     * Launch web app
     */
    private fun launchWebApp(app: EcosystemApp) {
        app.fallbackUrl?.let { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                // Could not launch web app
            }
        }
    }
    
    /**
     * Get installed ecosystem apps
     */
    private fun getInstalledEcosystemApps(): Set<String> {
        val installedApps = mutableSetOf<String>()
        
        ecosystemApps.forEach { app ->
            try {
                packageManager.getPackageInfo(app.packageName, 0)
                installedApps.add(app.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed
            }
        }
        
        return installedApps
    }
    
    /**
     * Get ecosystem app statistics
     */
    fun getEcosystemStats(): EcosystemStats {
        val installedApps = getInstalledEcosystemApps()
        val totalApps = ecosystemApps.size
        val installedCount = installedApps.size
        val webAppCount = totalApps - installedCount
        
        return EcosystemStats(
            totalApps = totalApps,
            installedApps = installedCount,
            webApps = webAppCount,
            completionPercentage = (installedCount * 100) / totalApps,
            installedAppNames = installedApps.mapNotNull { packageName ->
                ecosystemApps.find { it.packageName == packageName }?.name
            }
        )
    }
    
    /**
     * Check for ecosystem app updates
     */
    fun checkForUpdates(): List<String> {
        val updatesAvailable = mutableListOf<String>()
        val installedApps = getInstalledEcosystemApps()
        
        installedApps.forEach { packageName ->
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                // In a real implementation, you would check against a server for updates
                // For now, we'll simulate by checking if the app was installed more than 30 days ago
                val installTime = packageInfo.firstInstallTime
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                
                if (installTime < thirtyDaysAgo) {
                    ecosystemApps.find { it.packageName == packageName }?.name?.let { appName ->
                        updatesAvailable.add(appName)
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
        
        return updatesAvailable
    }
    
    /**
     * Get recommended apps based on installed ones
     */
    fun getRecommendedApps(): List<EcosystemApp> {
        val installedApps = getInstalledEcosystemApps()
        val notInstalled = ecosystemApps.filter { !installedApps.contains(it.packageName) }
        
        // Simple recommendation logic based on installed apps
        return when {
            installedApps.contains("com.sevenk.chat") -> {
                notInstalled.filter { it.packageName in listOf("com.sevenk.files", "com.sevenk.calendar") }
            }
            installedApps.contains("com.sevenk.notes") -> {
                notInstalled.filter { it.packageName in listOf("com.sevenk.tasks", "com.sevenk.calendar") }
            }
            installedApps.contains("com.sevenk.music") -> {
                notInstalled.filter { it.packageName in listOf("com.sevenk.health", "com.sevenk.files") }
            }
            else -> notInstalled.take(3) // Show first 3 if no specific recommendations
        }
    }
}

data class EcosystemStats(
    val totalApps: Int,
    val installedApps: Int,
    val webApps: Int,
    val completionPercentage: Int,
    val installedAppNames: List<String>
)
