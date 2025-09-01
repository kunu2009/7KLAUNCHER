package com.sevenk.launcher.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sevenk.launcher.util.Perf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * BackupManager handles backup and restore functionality for the launcher settings.
 * This includes home screen layout, dock settings, icon packs, and other preferences.
 */
class BackupManager(private val context: Context) {
    private val TAG = "BackupManager"
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Directory for backup files
    private val backupDir: File by lazy {
        File(context.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }
    }

    // File provider authority for sharing backups
    private val fileProviderAuthority = "${context.packageName}.fileprovider"

    // Current schema version for backup compatibility
    private val currentSchemaVersion = 1

    /**
     * Creates a full backup of all launcher settings
     * @param name Optional name for the backup, defaults to timestamp
     * @return URI of the created backup file or null if failed
     */
    suspend fun createBackup(name: String? = null): Uri? = withContext(Dispatchers.IO) {
        return@withContext Perf.trace("CreateBackup") {
            try {
                // Generate backup filename
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupName = name?.replace("[^a-zA-Z0-9_-]".toRegex(), "_") ?: "backup_$timestamp"
                val backupFile = File(backupDir, "$backupName.7kbackup")

                // Create backup data model
                val backupData = createBackupData()

                // Create ZIP file with JSON and preference files
                ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                    // Add metadata JSON
                    val metadataJson = json.encodeToString(backupData)
                    zipOut.putNextEntry(ZipEntry("metadata.json"))
                    zipOut.write(metadataJson.toByteArray())
                    zipOut.closeEntry()

                    // Add preference files
                    addPreferencesToZip(zipOut, "launcher_prefs")
                    addPreferencesToZip(zipOut, "gesture_prefs")
                    addPreferencesToZip(zipOut, "icon_pack_prefs")
                    addPreferencesToZip(zipOut, "widget_prefs")
                    addPreferencesToZip(zipOut, "folder_prefs")

                    // Add custom data (like home screen layout)
                    addHomeScreenLayoutToZip(zipOut)
                }

                // Return URI for the backup file
                FileProvider.getUriForFile(context, fileProviderAuthority, backupFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating backup", e)
                null
            }
        }
    }

    /**
     * Adds a preferences file to the backup ZIP
     */
    private fun addPreferencesToZip(zipOut: ZipOutputStream, prefsName: String) {
        val prefsFile = context.getSharedPrefsFile(prefsName)
        if (prefsFile.exists()) {
            zipOut.putNextEntry(ZipEntry("prefs/$prefsName.xml"))
            FileInputStream(prefsFile).use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()
        }
    }

    /**
     * Gets the shared preferences file
     */
    private fun Context.getSharedPrefsFile(name: String): File {
        return File(File(applicationInfo.dataDir, "shared_prefs"), "$name.xml")
    }

    /**
     * Adds home screen layout data to the backup ZIP
     */
    private fun addHomeScreenLayoutToZip(zipOut: ZipOutputStream) {
        // Get home screen layout data
        val homeScreenLayout = getHomeScreenLayout()

        // Add to ZIP
        zipOut.putNextEntry(ZipEntry("layout/home_screen.json"))
        zipOut.write(json.encodeToString(homeScreenLayout).toByteArray())
        zipOut.closeEntry()
    }

    /**
     * Gets the home screen layout data
     */
    private fun getHomeScreenLayout(): HomeScreenLayout {
        // Get home screen pages
        val pages = mutableListOf<HomeScreenPage>()

        // Get saved page count from preferences
        val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val pageCount = prefs.getInt("home_screen_page_count", 1)

        // For each page, get items
        for (i in 0 until pageCount) {
            val pageItems = getPageItems(i)
            pages.add(HomeScreenPage(index = i, items = pageItems))
        }

        // Get dock items
        val dockItems = getDockItems()

        return HomeScreenLayout(
            pages = pages,
            dockItems = dockItems
        )
    }

    /**
     * Gets items for a specific home screen page
     */
    private fun getPageItems(pageIndex: Int): List<HomeScreenItem> {
        val items = mutableListOf<HomeScreenItem>()

        // Get items from preferences
        val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val itemsJson = prefs.getString("home_screen_page_${pageIndex}_items", null)

        if (!itemsJson.isNullOrEmpty()) {
            try {
                val loadedItems = json.decodeFromString<List<HomeScreenItem>>(itemsJson)
                items.addAll(loadedItems)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing home screen page items", e)
            }
        }

        return items
    }

    /**
     * Gets dock items
     */
    private fun getDockItems(): List<HomeScreenItem> {
        val items = mutableListOf<HomeScreenItem>()

        // Get items from preferences
        val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val itemsJson = prefs.getString("dock_items", null)

        if (!itemsJson.isNullOrEmpty()) {
            try {
                val loadedItems = json.decodeFromString<List<HomeScreenItem>>(itemsJson)
                items.addAll(loadedItems)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing dock items", e)
            }
        }

        return items
    }

    /**
     * Creates backup data model
     */
    private fun createBackupData(): BackupMetadata {
        return BackupMetadata(
            schemaVersion = currentSchemaVersion,
            timestamp = System.currentTimeMillis(),
            deviceModel = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            launcherVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName,
            items = mapOf(
                "preferences" to true,
                "layout" to true,
                "widgets" to true,
                "folders" to true,
                "iconPack" to true
            )
        )
    }

    /**
     * Restores launcher settings from a backup
     * @param backupUri URI of the backup file
     * @param options Restore options to control what gets restored
     * @return True if restore was successful
     */
    suspend fun restoreBackup(backupUri: Uri, options: RestoreOptions = RestoreOptions()): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext Perf.trace("RestoreBackup") {
                try {
                    // Create temporary file to extract the backup
                    val tempFile = File(context.cacheDir, "temp_backup.7kbackup")

                    // Copy backup file to temp location
                    context.contentResolver.openInputStream(backupUri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Could not open backup file")

                    // Extract and process backup
                    var metadata: BackupMetadata? = null

                    ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            when {
                                entry.name == "metadata.json" -> {
                                    // Read metadata
                                    val metadataBytes = zipIn.readBytes()
                                    metadata = json.decodeFromString<BackupMetadata>(
                                        metadataBytes.toString(Charsets.UTF_8)
                                    )

                                    // Verify schema version compatibility
                                    if (metadata!!.schemaVersion > currentSchemaVersion) {
                                        Log.w(TAG, "Backup schema version ${metadata!!.schemaVersion} is newer than current $currentSchemaVersion")
                                    }
                                }

                                // Restore preferences if enabled
                                entry.name.startsWith("prefs/") && options.restorePreferences -> {
                                    val prefsName = entry.name.removePrefix("prefs/").removeSuffix(".xml")
                                    restorePreferences(zipIn, prefsName)
                                }

                                // Restore layout if enabled
                                entry.name == "layout/home_screen.json" && options.restoreLayout -> {
                                    val layoutBytes = zipIn.readBytes()
                                    val layoutData = json.decodeFromString<HomeScreenLayout>(
                                        layoutBytes.toString(Charsets.UTF_8)
                                    )
                                    restoreHomeScreenLayout(layoutData)
                                }
                            }

                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }

                    // Clean up temp file
                    tempFile.delete()

                    // Return success if we at least got metadata
                    metadata != null
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring backup", e)
                    false
                }
            }
        }

    /**
     * Restores a preferences file
     */
    private fun restorePreferences(zipIn: ZipInputStream, prefsName: String) {
        // Create temp file for the preferences
        val tempPrefsFile = File(context.cacheDir, "$prefsName.xml")

        // Write preferences to temp file
        FileOutputStream(tempPrefsFile).use { output ->
            zipIn.copyTo(output)
        }

        // Get the destination preferences file
        val prefsFile = context.getSharedPrefsFile(prefsName)

        // Ensure the shared_prefs directory exists
        prefsFile.parentFile?.mkdirs()

        // Copy the temp file to the actual preferences file
        FileInputStream(tempPrefsFile).use { input ->
            FileOutputStream(prefsFile).use { output ->
                input.copyTo(output)
            }
        }

        // Clean up temp file
        tempPrefsFile.delete()
    }

    /**
     * Restores home screen layout
     */
    private fun restoreHomeScreenLayout(layout: HomeScreenLayout) {
        val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Save page count
        editor.putInt("home_screen_page_count", layout.pages.size)

        // Save each page's items
        for (page in layout.pages) {
            val itemsJson = json.encodeToString(page.items)
            editor.putString("home_screen_page_${page.index}_items", itemsJson)
        }

        // Save dock items
        val dockItemsJson = json.encodeToString(layout.dockItems)
        editor.putString("dock_items", dockItemsJson)

        // Apply changes
        editor.apply()
    }

    /**
     * Gets all available backups
     * @return List of backup files with metadata
     */
    suspend fun getAvailableBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        val backups = mutableListOf<BackupInfo>()

        backupDir.listFiles()?.filter { it.extension == "7kbackup" }?.forEach { file ->
            try {
                // Extract metadata from backup
                var metadata: BackupMetadata? = null

                ZipInputStream(FileInputStream(file)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "metadata.json") {
                            val metadataBytes = zipIn.readBytes()
                            metadata = json.decodeFromString<BackupMetadata>(
                                metadataBytes.toString(Charsets.UTF_8)
                            )
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                // Add to list if metadata was found
                if (metadata != null) {
                    backups.add(
                        BackupInfo(
                            filename = file.name,
                            uri = FileProvider.getUriForFile(context, fileProviderAuthority, file),
                            metadata = metadata!!,
                            size = file.length(),
                            date = Date(file.lastModified())
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading backup metadata from ${file.name}", e)
            }
        }

        // Sort by date (newest first)
        backups.sortByDescending { it.date }

        backups
    }

    /**
     * Deletes a backup file
     * @param filename Name of the backup file to delete
     * @return True if deleted successfully
     */
    fun deleteBackup(filename: String): Boolean {
        val file = File(backupDir, filename)
        return file.exists() && file.delete()
    }

    /**
     * Schedules automatic backups
     * @param frequency Frequency in days
     * @param requireWifi Whether to require WiFi connection
     */
    fun scheduleAutomaticBackups(frequency: Int, requireWifi: Boolean = false) {
        // Build work constraints
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresCharging(true)
            .apply {
                if (requireWifi) {
                    setRequiredNetworkType(NetworkType.UNMETERED)
                }
            }
            .build()

        // Create work request
        val backupWorkRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            frequency.toLong(), TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        // Schedule the work
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            backupWorkRequest
        )
    }

    /**
     * Cancels automatic backups
     */
    fun cancelAutomaticBackups() {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
    }

    companion object {
        private const val AUTO_BACKUP_WORK_NAME = "7kLauncherAutoBackup"
    }

    /**
     * Worker class for automatic backups
     */
    class AutoBackupWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
        override fun doWork(): Result {
            val backupManager = BackupManager(applicationContext)

            // Create auto-backup
            val timestamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val backupName = "auto_$timestamp"

            // Run backup in blocking manner for Worker
            val backupUri = kotlinx.coroutines.runBlocking {
                backupManager.createBackup(backupName)
            }

            // Return success if backup was created
            return if (backupUri != null) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}

/**
 * Options for restoring a backup
 */
data class RestoreOptions(
    val restorePreferences: Boolean = true,
    val restoreLayout: Boolean = true,
    val restoreWidgets: Boolean = true,
    val restoreFolders: Boolean = true,
    val restoreIconPack: Boolean = true
)

/**
 * Information about a backup file
 */
data class BackupInfo(
    val filename: String,
    val uri: Uri,
    val metadata: BackupMetadata,
    val size: Long,
    val date: Date
)

/**
 * Metadata for a backup
 */
@Serializable
data class BackupMetadata(
    val schemaVersion: Int,
    val timestamp: Long,
    val deviceModel: String,
    val androidVersion: String,
    val launcherVersion: String,
    val items: Map<String, Boolean>
)

/**
 * Home screen layout data
 */
@Serializable
data class HomeScreenLayout(
    val pages: List<HomeScreenPage>,
    val dockItems: List<HomeScreenItem>
)

/**
 * Home screen page data
 */
@Serializable
data class HomeScreenPage(
    val index: Int,
    val items: List<HomeScreenItem>
)

/**
 * Home screen item data
 */
@Serializable
data class HomeScreenItem(
    val type: String, // "app", "folder", "widget", etc.
    val x: Int,
    val y: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val packageName: String? = null,
    val className: String? = null,
    val title: String? = null,
    val id: String? = null,
    val widgetId: Int? = null,
    val folderId: String? = null
)
