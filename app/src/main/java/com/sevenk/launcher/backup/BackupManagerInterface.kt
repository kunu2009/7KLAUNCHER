package com.sevenk.launcher.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple implementation of backup contract backed by the real BackupManager
 */
class SimpleBackupManager(private val context: Context) : BackupManagerContract {
    private val impl = BackupManager(context)

    /**
     * Generates a filename for a new backup
     * Format: 7klauncher_backup_yyyyMMdd_HHmmss.zip
     */
    override fun generateBackupFilename(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        return "7klauncher_backup_$timestamp.7kbackup"
    }

    /**
     * Creates a backup of launcher settings
     * @return Uri of the created backup or null if failed
     */
    override suspend fun createBackup(): Uri? = withContext(Dispatchers.IO) {
        impl.createBackup(null)
    }

    /**
     * Exports a backup to the specified output stream
     * @param backupUri URI of the backup to export
     * @param outputStream Stream to write the backup to
     * @return True if successful
     */
    override suspend fun exportBackup(backupUri: Uri?, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        if (backupUri == null) return@withContext false
        try {
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                input.copyTo(outputStream)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Imports a backup from the specified input stream
     * @param inputStream Stream to read the backup from
     * @return Backup object or null if import failed
     */
    override suspend fun importBackup(inputStream: InputStream): Any? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "imported_backup.7kbackup")
            FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Restores a backup
     * @param backup Backup object to restore
     * @return Result of the restore operation
     */
    override suspend fun restoreBackup(backup: Any): BackupManagerContract.BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            val uri = when (backup) {
                is Uri -> backup
                is File -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
                else -> return@withContext BackupManagerContract.BackupRestoreResult.Error("Unsupported backup type: ${backup::class.java.simpleName}")
            }
            val ok = impl.restoreBackup(uri, RestoreOptions())
            if (ok) BackupManagerContract.BackupRestoreResult.Success
            else BackupManagerContract.BackupRestoreResult.Error("Restore failed")
        } catch (e: Exception) {
            BackupManagerContract.BackupRestoreResult.Error("Restore error: ${e.message}")
        }
    }
}

/**
 * Contract for backup and restore operations
 */
interface BackupManagerContract {
    /**
     * Generates a filename for a new backup
     * Format: 7klauncher_backup_yyyyMMdd_HHmmss.zip
     */
    fun generateBackupFilename(): String

    /**
     * Creates a backup of launcher settings
     * @return Uri of the created backup or null if failed
     */
    suspend fun createBackup(): Uri?

    /**
     * Exports a backup to the specified output stream
     * @param backupUri URI of the backup to export
     * @param outputStream Stream to write the backup to
     * @return True if successful
     */
    suspend fun exportBackup(backupUri: Uri?, outputStream: OutputStream): Boolean

    /**
     * Imports a backup from the specified input stream
     * @param inputStream Stream to read the backup from
     * @return Backup object or null if import failed
     */
    suspend fun importBackup(inputStream: InputStream): Any?

    /**
     * Restores a backup
     * @param backup Backup object to restore
     * @return Result of the restore operation
     */
    suspend fun restoreBackup(backup: Any): BackupRestoreResult

    /**
     * Result of a restore operation
     */
    sealed class BackupRestoreResult {
        object Success : BackupRestoreResult()
        data class Error(val message: String) : BackupRestoreResult()
    }
}
