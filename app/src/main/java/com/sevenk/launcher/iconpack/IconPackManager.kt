package com.sevenk.launcher.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sevenk.launcher.util.Perf
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import java.util.concurrent.ConcurrentHashMap
import java.util.HashMap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Comprehensive helper class for managing icon packs in the 7K Launcher.
 * Supports multiple icon pack formats (Nova, ADW, Apex, LauncherPro, Go, Holo).
 */
class IconPackManager(private val context: Context) {

    // LRU cache for drawables to improve performance
    private val drawableCache = LruCache<String, Drawable>(500)

    // Memory cache for component name to drawable name mapping
    private val componentToDrawableMap = HashMap<String, String>()

    // Memory cache for back/mask/upon drawables
    private var maskDrawable: Drawable? = null
    private var backDrawable: Drawable? = null
    private var uponDrawable: Drawable? = null
    private var scaleFactor = 1.0f
    private var iconMask: Bitmap? = null

    // Map of icon pack package names to their metadata
    private val iconPacks = ConcurrentHashMap<String, IconPack>()

    // Current active icon pack
    private var currentIconPack: IconPack? = null

    // Known icon pack filter intents
    private val iconPackFilters = listOf(
        "com.novalauncher.THEME",
        "com.adw.launcher.THEMES",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.dlto.atom.launcher.THEME",
        "net.oneplus.launcher.icons.ACTION_PICK_ICON"
    )

    /**
     * Scans the device for installed icon packs.
     * @return List of icon packs found on the device
     */
    suspend fun scanForInstalledIconPacks(): List<IconPack> = withContext(Dispatchers.IO) {
        return@withContext Perf.trace("IconPackScan") {
            val packageManager = context.packageManager
            val iconPacks = mutableListOf<IconPack>()

            // Check for icon packs compatible with various launchers
            for (action in iconPackFilters) {
                val intent = Intent(action)
                for (resolveInfo in packageManager.queryIntentActivities(intent, 0)) {
                    val packageName = resolveInfo.activityInfo.packageName
                    try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val iconDrawable = packageManager.getApplicationIcon(appInfo)

                        val iconPack = IconPack(
                            packageName = packageName,
                            name = appName,
                            previewDrawable = iconDrawable
                        )

                        // Only add if not already in the list
                        if (iconPacks.none { it.packageName == packageName }) {
                            iconPacks.add(iconPack)
                        }
                    } catch (e: Exception) {
                        // Skip this icon pack if there was an error
                    }
                }
            }

            // Cache discovered icon packs
            iconPacks.forEach { this@IconPackManager.iconPacks[it.packageName] = it }

            iconPacks
        }
    }

    /**
     * Loads an icon pack from the given package name.
     * @param packageName The package name of the icon pack to load
     * @return True if the icon pack was loaded successfully, false otherwise
     */
    suspend fun loadIconPack(packageName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext Perf.trace("IconPackLoad") {
            try {
                // Reset any existing icon pack data
                resetIconPack()

                if (packageName.isEmpty()) {
                    return@trace true // Empty string means use default system icons
                }

                val iconPack = iconPacks[packageName] ?: IconPack(
                    packageName = packageName,
                    name = try {
                        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                        context.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        packageName
                    },
                    previewDrawable = try {
                        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                        context.packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }
                )

                // Load icon pack resources
                val packageResources = context.packageManager.getResourcesForApplication(packageName)

                // Load appfilter.xml
                loadAppFilter(packageName, packageResources)

                // Load drawable resources for background, mask, and front
                loadDrawableResources(packageName, packageResources)

                // Mark this icon pack as current
                currentIconPack = iconPack

                true
            } catch (e: Exception) {
                android.util.Log.e("IconPackManager", "Failed to load icon pack: $packageName", e)
                false
            }
        }
    }

    /**
     * Loads the appfilter.xml file from the icon pack to map component names to drawable names
     */
    private fun loadAppFilter(packageName: String, resources: Resources) {
        // Try different resource names used by various icon packs
        val appFilterResourceIds = listOf(
            resources.getIdentifier("appfilter", "xml", packageName),
            resources.getIdentifier("appfilter", "raw", packageName),
            resources.getIdentifier("drawable", "xml", packageName),
            resources.getIdentifier("app_filter", "xml", packageName)
        ).filter { it != 0 }

        for (resourceId in appFilterResourceIds) {
            try {
                // Parse XML to extract component name to drawable name mappings
                val parser = resources.getXml(resourceId)

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (parser.name == "item") {
                            // Get component and drawable attributes
                            var component: String? = null
                            var drawable: String? = null

                            for (i in 0 until parser.attributeCount) {
                                when (parser.getAttributeName(i)) {
                                    "component" -> component = parser.getAttributeValue(i)
                                    "drawable" -> drawable = parser.getAttributeValue(i)
                                }
                            }

                            // Store in the mapping if both attributes found
                            if (component != null && drawable != null) {
                                // Clean up component name
                                component = component.replace("ComponentInfo{", "")
                                    .replace("}", "")
                                componentToDrawableMap[component] = drawable
                            }
                        } else if (parser.name == "scale" || parser.name == "scale_all_icons") {
                            // Some icon packs specify scaling factor
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i) == "factor") {
                                    try {
                                        scaleFactor = parser.getAttributeValue(i).toFloat()
                                    } catch (e: NumberFormatException) {
                                        // Use default if not parsable
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }

                // If we successfully loaded a resource, break the loop
                return
            } catch (e: Exception) {
                // Try the next resource ID
            }
        }

        // Fallback to assets/appfilter.xml if no resources found
        try {
            val assetManager = context.packageManager.getResourcesForApplication(packageName).assets
            val inputStream = assetManager.open("appfilter.xml")

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "item") {
                        // Get component and drawable attributes
                        var component: String? = null
                        var drawable: String? = null

                        for (i in 0 until parser.attributeCount) {
                            when (parser.getAttributeName(i)) {
                                "component" -> component = parser.getAttributeValue(i)
                                "drawable" -> drawable = parser.getAttributeValue(i)
                            }
                        }

                        // Store in the mapping if both attributes found
                        if (component != null && drawable != null) {
                            // Clean up component name
                            component = component.replace("ComponentInfo{", "")
                                .replace("}", "")
                            componentToDrawableMap[component] = drawable
                        }
                    }
                }
                eventType = parser.next()
            }

            inputStream.close()
        } catch (e: Exception) {
            // Could not load from assets either
        }
    }

    /**
     * Loads drawable resources for icon masking (background, mask, foreground)
     */
    private fun loadDrawableResources(packageName: String, resources: Resources) {
        // Load the background drawable (used behind icons without backgrounds)
        try {
            val backResId = resources.getIdentifier("iconback", "drawable", packageName)
            if (backResId != 0) {
                backDrawable = ResourcesCompat.getDrawable(resources, backResId, null)
            }
        } catch (e: Exception) {}

        // Load the mask drawable (used to mask icon shape)
        try {
            val maskResId = resources.getIdentifier("iconmask", "drawable", packageName)
            if (maskResId != 0) {
                maskDrawable = ResourcesCompat.getDrawable(resources, maskResId, null)
                // Create bitmap for faster processing
                if (maskDrawable != null) {
                    val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    maskDrawable!!.setBounds(0, 0, 192, 192)
                    maskDrawable!!.draw(canvas)
                    iconMask = bitmap
                }
            }
        } catch (e: Exception) {}

        // Load the foreground drawable (placed on top of icons)
        try {
            val uponResId = resources.getIdentifier("iconupon", "drawable", packageName)
            if (uponResId != 0) {
                uponDrawable = ResourcesCompat.getDrawable(resources, uponResId, null)
            }
        } catch (e: Exception) {}
    }

    /**
     * Resets all icon pack related data
     */
    private fun resetIconPack() {
        componentToDrawableMap.clear()
        drawableCache.evictAll()

        maskDrawable = null
        backDrawable = null
        uponDrawable = null
        iconMask = null
        scaleFactor = 1.0f

        currentIconPack = null
    }

    /**
     * Gets the icon for a specific app component, applying the current icon pack if available
     * @param componentName The component name of the app
     * @return The icon drawable or null if not found
     */
    fun getIconForComponent(componentName: ComponentName): Drawable? {
        val key = componentName.toString()

        // Check if we have this drawable cached
        val cachedDrawable = drawableCache.get(key)
        if (cachedDrawable != null) {
            return cachedDrawable
        }

        // If no icon pack is loaded, return null to use default
        if (currentIconPack == null) {
            return null
        }

        try {
            // Check if this component exists in our mapping
            val drawableName = componentToDrawableMap[componentName.flattenToString()]

            if (drawableName != null) {
                // Get icon pack resources
                val resources = context.packageManager.getResourcesForApplication(currentIconPack!!.packageName)

                // Look up the drawable resource ID
                val drawableResId = resources.getIdentifier(drawableName, "drawable", currentIconPack!!.packageName)

                if (drawableResId != 0) {
                    // Load the drawable
                    val drawable = ResourcesCompat.getDrawable(resources, drawableResId, null)

                    if (drawable != null) {
                        // Cache and return the drawable
                        drawableCache.put(key, drawable)
                        return drawable
                    }
                }
            }

            // If we don't have a specific icon for this component, use the default app icon and apply masking
            val packageManager = context.packageManager
            val defaultIcon = packageManager.getApplicationIcon(componentName.packageName)

            // Apply icon pack masking to the default icon
            val maskedIcon = applyIconPackMask(defaultIcon)
            if (maskedIcon != null) {
                drawableCache.put(key, maskedIcon)
                return maskedIcon
            }
        } catch (e: Exception) {
            // If any error occurs, return null to use default icon
        }

        return null
    }

    /**
     * Applies the icon pack's masking to a default icon
     * @param icon The default icon to apply masking to
     * @return The masked icon drawable or null if masking failed
     */
    private fun applyIconPackMask(icon: Drawable): Drawable? {
        // If no masking components are available, return the original icon
        if (backDrawable == null && maskDrawable == null && uponDrawable == null) {
            return icon
        }

        try {
            // Create bitmap for the icon at a standard size (192x192)
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw background if available
            backDrawable?.let {
                it.setBounds(0, 0, 192, 192)
                it.draw(canvas)
            }

            // Scale icon according to the scale factor
            val scaledIconSize = (192 * scaleFactor).toInt()
            val left = (192 - scaledIconSize) / 2
            val top = (192 - scaledIconSize) / 2

            // Draw the icon
            icon.setBounds(left, top, left + scaledIconSize, top + scaledIconSize)
            icon.draw(canvas)

            // Apply mask if available
            iconMask?.let {
                val paint = Paint()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                canvas.drawBitmap(it, 0f, 0f, paint)
            }

            // Draw foreground if available
            uponDrawable?.let {
                it.setBounds(0, 0, 192, 192)
                it.draw(canvas)
            }

            // Create and return the final drawable
            return BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            return icon
        }
    }

    /**
     * Returns the list of installed icon packs (cached)
     */
    fun getInstalledIconPacks(): List<IconPack> {
        return iconPacks.values.toList()
    }

    /**
     * Clears all icon pack caches
     */
    fun clearCache() {
        drawableCache.evictAll()
        componentToDrawableMap.clear()
        iconPacks.clear()
        currentIconPack = null
        maskDrawable = null
        backDrawable = null
        uponDrawable = null
        iconMask = null
        scaleFactor = 1.0f
    }

    /**
     * Data class representing an icon pack
     */
    data class IconPack(
        val packageName: String,
        val name: String,
        val previewDrawable: Drawable?
    )
}
