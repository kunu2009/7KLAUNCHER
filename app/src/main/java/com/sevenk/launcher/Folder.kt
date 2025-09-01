package com.sevenk.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a folder containing multiple apps
 */
@Serializable
class Folder(
    var name: String,
    val apps: MutableList<AppInfo> = mutableListOf()
) {
    // Add an app to the folder
    fun addApp(app: AppInfo) {
        if (!apps.contains(app)) {
            apps.add(app)
        }
    }

    // Remove an app from the folder
    fun removeApp(app: AppInfo): Boolean {
        return apps.remove(app)
    }

    // Convert to JSON for persistence
    fun toJson(): String {
        val json = JSONObject()
        json.put("name", name)

        val appsArray = JSONArray()
        for (app in apps) {
            val appJson = JSONObject()
            appJson.put("packageName", app.packageName)
            appJson.put("className", app.className)
            appJson.put("name", app.name)
            appsArray.put(appJson)
        }

        json.put("apps", appsArray)
        return json.toString()
    }

    companion object {
        // Parse from JSON for persistence
        fun fromJson(jsonString: String): Folder {
            val json = JSONObject(jsonString)
            val name = json.optString("name", "Folder")
            val folder = Folder(name)

            val appsArray = json.optJSONArray("apps")
            if (appsArray != null) {
                for (i in 0 until appsArray.length()) {
                    val appJson = appsArray.getJSONObject(i)
                    val packageName = appJson.optString("packageName", "")
                    val className = appJson.optString("className", "")
                    val appName = appJson.optString("name", "")

                    if (packageName.isNotEmpty()) {
                        // Create AppInfo without icon (will be loaded later)
                        val appInfo = AppInfo(
                            name = appName,
                            packageName = packageName,
                            className = className,
                            icon = null,
                            applicationInfo = null
                        )
                        folder.addApp(appInfo)
                    }
                }
            }

            return folder
        }
    }
}
