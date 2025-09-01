package com.sevenk.launcher.folders

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BlurMaskFilter
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.AppInfo
import com.sevenk.launcher.Folder
import com.sevenk.launcher.FolderGridAdapter
import com.sevenk.launcher.R
import com.sevenk.launcher.ui.glass.GlassManager
import com.sevenk.launcher.util.Perf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Comprehensive folder management for the launcher
 */
class FolderManager(private val context: Context) {

    private val customizationManager = FolderCustomizationManager(context)
    private val prefs = context.getSharedPreferences("folder_prefs", Context.MODE_PRIVATE)
    private val glassManager = GlassManager(context)

    // Maps folder IDs to Folder objects
    private val folders = mutableMapOf<String, Folder>()

    // Currently open folder (if any)
    private var openFolderId: String? = null
    private var folderContainer: ViewGroup? = null
    private var folderRecyclerView: RecyclerView? = null
    private var folderBackgroundView: View? = null
    private var folderOpenListener: ((String) -> Unit)? = null
    private var folderCloseListener: ((String) -> Unit)? = null

    // Animation duration for folder open/close
    private val animationDuration = 300L

    init {
        // Load saved folders
        loadFolders()
    }

    /**
     * Load saved folders from preferences
     */
    private fun loadFolders() {
        folders.clear()

        // Get all folder IDs
        val folderIds = prefs.getStringSet("folder_ids", emptySet()) ?: emptySet()

        // Load each folder
        for (id in folderIds) {
            val folderJson = prefs.getString("folder_$id", null)
            if (folderJson != null) {
                try {
                    // Parse folder data from JSON
                    val folder = Folder.fromJson(folderJson)
                    folders[id] = folder
                } catch (e: Exception) {
                    // Handle parsing errors
                }
            }
        }
    }

    /**
     * Save folders to preferences
     */
    private fun saveFolders() {
        val editor = prefs.edit()

        // Save folder IDs
        editor.putStringSet("folder_ids", folders.keys)

        // Save each folder
        for ((id, folder) in folders) {
            val folderJson = folder.toJson()
            editor.putString("folder_$id", folderJson)
        }

        editor.apply()
    }

    /**
     * Create a new folder with the given apps
     * @param name Folder name
     * @param apps List of apps to include in the folder
     * @return The newly created folder
     */
    fun createFolder(name: String, apps: List<AppInfo>): Folder {
        // Generate a unique ID for the folder
        val id = UUID.randomUUID().toString()

        // Create the folder
        val folder = Folder(
            name = name,
            apps = apps.toMutableList()
        )

        // Save the folder
        folders[name] = folder
        saveFolders()

        // Create default customization
        val customization = FolderCustomizationManager.FolderCustomization(
            backgroundColor = Color.parseColor("#33000000"),
            backgroundAlpha = 0.85f,
            textColor = Color.WHITE,
            iconLayout = FolderCustomizationManager.FolderIconLayout.GRID_2x2,
            rounded = true,
            blurEnabled = true,
            titleEnabled = true,
            useCustomColors = false,
            style = FolderCustomizationManager.FolderStyle.GLASS_PANEL
        )
        customizationManager.saveFolderCustomization(id, customization)

        return folder
    }

    /**
     * Add an app to a folder
     * @param folderId Folder ID
     * @param app App to add
     * @return True if the app was added, false if the folder doesn't exist
     */
    fun addAppToFolder(folderId: String, app: AppInfo): Boolean {
        val folder = folders[folderId] ?: return false

        // Add the app to the folder
        folder.apps.add(app)

        // Save the folder
        saveFolders()

        return true
    }

    /**
     * Remove an app from a folder
     * @param folderId Folder ID
     * @param packageName Package name of the app to remove
     * @return True if the app was removed, false if the folder doesn't exist or the app is not in the folder
     */
    fun removeAppFromFolder(folderId: String, packageName: String): Boolean {
        val folder = folders[folderId] ?: return false

        // Find the app in the folder
        val app = folder.apps.find { it.packageName == packageName }

        if (app != null) {
            // Remove the app from the folder
            folder.apps.remove(app)

            // Save the folder
            saveFolders()

            return true
        }

        return false
    }

    /**
     * Delete a folder
     * @param folderId Folder ID
     * @return True if the folder was deleted, false if it doesn't exist
     */
    fun deleteFolder(folderId: String): Boolean {
        if (folders.containsKey(folderId)) {
            // Close the folder if it's open
            if (openFolderId == folderId) {
                closeFolder()
            }

            // Remove the folder
            folders.remove(folderId)

            // Save the folders
            saveFolders()

            // Remove customization
            customizationManager.deleteFolderCustomization(folderId)

            return true
        }

        return false
    }

    /**
     * Rename a folder
     * @param folderId Folder ID
     * @param newName New folder name
     * @return True if the folder was renamed, false if it doesn't exist
     */
    fun renameFolder(folderId: String, newName: String): Boolean {
        val folder = folders[folderId] ?: return false

        // Rename the folder
        folder.name = newName

        // Save the folder
        saveFolders()

        // Update customization
        val customization = customizationManager.getFolderCustomization(folderId)
        if (customization != null) {
            customizationManager.saveFolderCustomization(folderId, customization)
        }

        return true
    }

    /**
     * Get a folder by ID
     * @param folderId Folder ID
     * @return The folder or null if it doesn't exist
     */
    fun getFolder(folderId: String): Folder? {
        return folders[folderId]
    }

    /**
     * Get all folders
     * @return Map of folder IDs to folders
     */
    fun getAllFolders(): Map<String, Folder> {
        return folders.toMap()
    }

    /**
     * Get apps in a folder
     * @param folderId Folder ID
     * @return List of apps in the folder or empty list if the folder doesn't exist
     */
    fun getFolderApps(folderId: String): List<AppInfo> {
        return folders[folderId]?.apps ?: emptyList()
    }

    /**
     * Check if a folder is open
     * @return True if a folder is open
     */
    fun isFolderOpen(): Boolean {
        return openFolderId != null
    }

    /**
     * Set the container view for folders
     * @param container ViewGroup to use as the container for folder content
     * @param recyclerView RecyclerView to use for folder items
     * @param backgroundView View to use for the folder background
     */
    fun setFolderContainer(
        container: ViewGroup,
        recyclerView: RecyclerView,
        backgroundView: View
    ) {
        folderContainer = container
        folderRecyclerView = recyclerView
        folderBackgroundView = backgroundView

        // Set up the recycler view
        recyclerView.layoutManager = GridLayoutManager(context, 4)
    }

    /**
     * Set listeners for folder open/close events
     * @param openListener Called when a folder is opened
     * @param closeListener Called when a folder is closed
     */
    fun setFolderListeners(
        openListener: (String) -> Unit,
        closeListener: (String) -> Unit
    ) {
        folderOpenListener = openListener
        folderCloseListener = closeListener
    }

    /**
     * Open a folder with animation
     * @param folderId Folder ID
     * @param sourceView The view that was clicked to open the folder
     * @return True if the folder was opened, false if it doesn't exist or another folder is already open
     */
    fun openFolder(folderId: String, sourceView: View): Boolean {
        Perf.trace("OpenFolder") {
            // Check if the folder exists
            val folder = folders[folderId] ?: return@trace false

            // Check if we have the necessary views
            if (folderContainer == null || folderRecyclerView == null || folderBackgroundView == null) {
                return@trace false
            }

            // Check if another folder is already open
            if (openFolderId != null) {
                closeFolder()
            }

            // Set the open folder ID
            openFolderId = folderId

            // Show the folder container
            folderContainer?.visibility = View.VISIBLE

            // Apply glass effect to the background
            glassManager.applyGlassEffect(folderBackgroundView!!)

            // Set up the recycler view
            val adapter = FolderGridAdapter(folder.apps) { app ->
                // App clicked
                // Close the folder
                closeFolder()

                // Launch the app (this would be handled by the callback)
            }
            folderRecyclerView?.adapter = adapter

            // Get customization
            val customization = customizationManager.getFolderCustomization(folderId)

            // Set grid columns based on customization
            val spanCount = when (customization?.iconLayout) {
                FolderCustomizationManager.FolderIconLayout.GRID_2x2 -> 2
                FolderCustomizationManager.FolderIconLayout.GRID_3x3 -> 3
                else -> 4
            }
            (folderRecyclerView?.layoutManager as? GridLayoutManager)?.spanCount = spanCount

            // Get the source view location
            val sourceLocation = IntArray(2)
            sourceView.getLocationOnScreen(sourceLocation)

            val sourceX = sourceLocation[0] + sourceView.width / 2
            val sourceY = sourceLocation[1] + sourceView.height / 2

            // Get the center of the folder container
            val containerLocation = IntArray(2)
            folderContainer?.getLocationOnScreen(containerLocation)

            val containerCenterX = containerLocation[0] + (folderContainer?.width ?: 0) / 2
            val containerCenterY = containerLocation[1] + (folderContainer?.height ?: 0) / 2

            // Animate the folder background from the source view
            folderBackgroundView?.alpha = 0f
            folderBackgroundView?.scaleX = 0.2f
            folderBackgroundView?.scaleY = 0.2f
            folderBackgroundView?.pivotX = sourceX - containerLocation[0].toFloat()
            folderBackgroundView?.pivotY = sourceY - containerLocation[1].toFloat()

            folderBackgroundView?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(animationDuration)
                ?.setInterpolator(DecelerateInterpolator())
                ?.start()

            // Animate the recycler view
            folderRecyclerView?.alpha = 0f
            folderRecyclerView?.translationY = 50f

            folderRecyclerView?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setStartDelay(animationDuration / 2)
                ?.setDuration(animationDuration / 2)
                ?.setInterpolator(DecelerateInterpolator())
                ?.start()

            // Notify listener
            folderOpenListener?.invoke(folderId)

            return@trace true
        }

        return false
    }

    /**
     * Close the currently open folder with animation
     * @return True if a folder was closed, false if no folder was open
     */
    fun closeFolder(): Boolean {
        Perf.trace("CloseFolder") {
            // Check if a folder is open
            val folderId = openFolderId ?: return@trace false

            // Animate the folder background
            folderBackgroundView?.animate()
                ?.alpha(0f)
                ?.scaleX(0.2f)
                ?.scaleY(0.2f)
                ?.setDuration(animationDuration)
                ?.setInterpolator(DecelerateInterpolator())
                ?.start()

            // Animate the recycler view
            folderRecyclerView?.animate()
                ?.alpha(0f)
                ?.translationY(50f)
                ?.setDuration(animationDuration / 2)
                ?.setInterpolator(DecelerateInterpolator())
                ?.withEndAction {
                    // Hide the folder container
                    folderContainer?.visibility = View.GONE

                    // Clear the open folder ID
                    openFolderId = null
                }
                ?.start()

            // Notify listener
            folderCloseListener?.invoke(folderId)

            return@trace true
        }

        return false
    }

    /**
     * Create a preview drawable for a folder
     * @param folderId Folder ID
     * @param size Size of the preview
     * @return Drawable for the folder preview
     */
    fun createFolderPreview(folderId: String, size: Int): Drawable {
        return Perf.trace("CreateFolderPreview") {
            // Get the folder
            val folder = folders[folderId]

            // Get customization
            val customization = customizationManager.getFolderCustomization(folderId)

            // Create a bitmap for the preview
            val preview = createFolderPreviewBitmap(folder, customization, size)

            // Convert to drawable
            return@trace android.graphics.drawable.BitmapDrawable(context.resources, preview)
        }
    }

    /**
     * Create a preview bitmap for a folder
     * @param folder Folder to create preview for
     * @param customization Customization for the folder
     * @param size Size of the preview
     * @return Bitmap for the folder preview
     */
    private fun createFolderPreviewBitmap(
        folder: Folder?,
        customization: FolderCustomizationManager.FolderCustomization?,
        size: Int
    ): Bitmap {
        // Default preview
        if (folder == null) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            return bitmap
        }

        // Create a bitmap for the preview
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background based on style
        val style = customization?.style ?: FolderCustomizationManager.FolderStyle.GLASS_PANEL
        val backgroundColor = customization?.backgroundColor ?: Color.parseColor("#80000000")

        when (style) {
            FolderCustomizationManager.FolderStyle.CIRCLE -> {
                val paint = Paint().apply {
                    color = backgroundColor
                    isAntiAlias = true
                }
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            }
            FolderCustomizationManager.FolderStyle.SQUARE -> {
                val paint = Paint().apply {
                    color = backgroundColor
                    isAntiAlias = true
                }
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            }
            FolderCustomizationManager.FolderStyle.ROUNDED_SQUARE -> {
                val paint = Paint().apply {
                    color = backgroundColor
                    isAntiAlias = true
                }
                val radius = size / 8f
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, paint)
            }
            FolderCustomizationManager.FolderStyle.GLASS_PANEL -> {
                val paint = Paint().apply {
                    color = backgroundColor
                    isAntiAlias = true
                }
                val radius = size / 8f
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, paint)

                // Add subtle inner shadow
                val shadowPaint = Paint().apply {
                    color = Color.BLACK
                    isAntiAlias = true
                    alpha = 40
                    maskFilter = android.graphics.BlurMaskFilter(radius / 2, android.graphics.BlurMaskFilter.Blur.INNER)
                }
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, shadowPaint)

                // Add subtle highlight
                val highlightPaint = Paint().apply {
                    color = Color.WHITE
                    isAntiAlias = true
                    alpha = 40
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(2f, 2f, size - 2f, size - 2f, radius - 2, radius - 2, highlightPaint)
            }
        }

        // Draw app icons
        val iconLayout = customization?.iconLayout ?: FolderCustomizationManager.FolderIconLayout.GRID_2x2

        // Get the first 4 apps (or fewer if there aren't 4)
        val appsToShow = folder.apps.take(4)

        when (iconLayout) {
            FolderCustomizationManager.FolderIconLayout.GRID_2x2 -> {
                // Draw up to 4 app icons in a 2x2 grid
                val iconSize = size / 2
                val iconPadding = iconSize / 8

                for (i in appsToShow.indices) {
                    val app = appsToShow[i]
                    val icon = app.icon?.toBitmap(iconSize - iconPadding * 2, iconSize - iconPadding * 2)

                    if (icon != null) {
                        // Calculate position
                        val row = i / 2
                        val col = i % 2

                        val left = col * iconSize + iconPadding
                        val top = row * iconSize + iconPadding

                        canvas.drawBitmap(icon, left.toFloat(), top.toFloat(), null)
                    }
                }
            }
            FolderCustomizationManager.FolderIconLayout.GRID_3x3 -> {
                // Draw up to 9 app icons in a 3x3 grid
                val iconSize = size / 3
                val iconPadding = iconSize / 8

                for (i in appsToShow.indices) {
                    val app = appsToShow[i]
                    val icon = app.icon?.toBitmap(iconSize - iconPadding * 2, iconSize - iconPadding * 2)

                    if (icon != null) {
                        // Calculate position
                        val row = i / 3
                        val col = i % 3

                        val left = col * iconSize + iconPadding
                        val top = row * iconSize + iconPadding

                        canvas.drawBitmap(icon, left.toFloat(), top.toFloat(), null)
                    }
                }
            }
            FolderCustomizationManager.FolderIconLayout.STACK -> {
                // Draw up to 4 app icons stacked
                val iconSize = (size * 0.6f).toInt()
                val iconPadding = (size * 0.05f).toInt()

                for (i in appsToShow.indices.reversed()) { // Reversed to draw first apps on top
                    val app = appsToShow[i]
                    val icon = app.icon?.toBitmap(iconSize, iconSize)

                    if (icon != null) {
                        // Calculate position with offset for stacking effect
                        val offsetX = i * iconPadding
                        val offsetY = i * iconPadding

                        val left = (size - iconSize) / 2 + offsetX
                        val top = (size - iconSize) / 2 + offsetY

                        canvas.drawBitmap(icon, left.toFloat(), top.toFloat(), null)
                    }
                }
            }
            FolderCustomizationManager.FolderIconLayout.PREVIEW_4 -> {
                // Draw first app large, next 3 small
                if (appsToShow.isNotEmpty()) {
                    // Draw first app large
                    val mainIconSize = (size * 0.65f).toInt()
                    val mainIcon = appsToShow[0].icon?.toBitmap(mainIconSize, mainIconSize)

                    if (mainIcon != null) {
                        val left = (size - mainIconSize) / 2
                        val top = (size - mainIconSize) / 2

                        canvas.drawBitmap(mainIcon, left.toFloat(), top.toFloat(), null)
                    }

                    // Draw next 3 apps small
                    val smallIconSize = (size * 0.25f).toInt()

                    for (i in 1 until minOf(4, appsToShow.size)) {
                        val app = appsToShow[i]
                        val icon = app.icon?.toBitmap(smallIconSize, smallIconSize)

                        if (icon != null) {
                            // Calculate position
                            var left = 0
                            var top = 0

                            when (i) {
                                1 -> { // Top right
                                    left = size - smallIconSize
                                    top = 0
                                }
                                2 -> { // Bottom right
                                    left = size - smallIconSize
                                    top = size - smallIconSize
                                }
                                3 -> { // Bottom left
                                    left = 0
                                    top = size - smallIconSize
                                }
                            }

                            canvas.drawBitmap(icon, left.toFloat(), top.toFloat(), null)
                        }
                    }
                }
            }
            FolderCustomizationManager.FolderIconLayout.GRID,
            FolderCustomizationManager.FolderIconLayout.SCATTER,
            FolderCustomizationManager.FolderIconLayout.MINIMAL -> {
                // Fallback: draw nothing or default
            }
        }

        return bitmap
    }
}
