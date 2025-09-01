package com.sevenk.launcher.errors

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive error reporting and logging system
 */
class ErrorReporter private constructor(private val context: Context) {
    
    private val errorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val logFile = File(context.filesDir, "launcher_errors.log")
    
    companion object {
        @Volatile
        private var INSTANCE: ErrorReporter? = null
        
        fun getInstance(context: Context): ErrorReporter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ErrorReporter(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
        private const val TAG = "LauncherErrorReporter"
    }
    
    init {
        setupUncaughtExceptionHandler()
    }
    
    /**
     * Setup global exception handler
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            reportCrash(exception, thread.name)
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * Report a non-fatal error
     */
    fun reportError(error: Throwable, context: String = "", severity: ErrorSeverity = ErrorSeverity.MEDIUM) {
        errorScope.launch {
            val errorReport = createErrorReport(error, context, severity, false)
            writeToLog(errorReport)
            
            when (severity) {
                ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> {
                    Log.e(TAG, "High severity error: $context", error)
                }
                ErrorSeverity.MEDIUM -> {
                    Log.w(TAG, "Medium severity error: $context", error)
                }
                ErrorSeverity.LOW -> {
                    Log.i(TAG, "Low severity error: $context", error)
                }
            }
        }
    }
    
    /**
     * Report a crash
     */
    private fun reportCrash(error: Throwable, threadName: String) {
        errorScope.launch {
            val errorReport = createErrorReport(error, "CRASH on thread: $threadName", ErrorSeverity.CRITICAL, true)
            writeToLog(errorReport)
            Log.e(TAG, "CRASH REPORTED", error)
        }
    }
    
    /**
     * Report performance issue
     */
    fun reportPerformanceIssue(operation: String, duration: Long, threshold: Long = 1000) {
        if (duration > threshold) {
            errorScope.launch {
                val message = "Performance issue: $operation took ${duration}ms (threshold: ${threshold}ms)"
                val errorReport = ErrorReport(
                    timestamp = dateFormat.format(Date()),
                    severity = ErrorSeverity.MEDIUM,
                    context = "PERFORMANCE",
                    message = message,
                    stackTrace = "",
                    deviceInfo = getDeviceInfo(),
                    appInfo = getAppInfo(),
                    isCrash = false
                )
                writeToLog(errorReport)
                Log.w(TAG, message)
            }
        }
    }
    
    /**
     * Report memory issue
     */
    fun reportMemoryIssue(availableMemory: Long, usedMemory: Long) {
        errorScope.launch {
            val message = "Memory issue: Used ${usedMemory}MB, Available ${availableMemory}MB"
            val errorReport = ErrorReport(
                timestamp = dateFormat.format(Date()),
                severity = ErrorSeverity.HIGH,
                context = "MEMORY",
                message = message,
                stackTrace = "",
                deviceInfo = getDeviceInfo(),
                appInfo = getAppInfo(),
                isCrash = false
            )
            writeToLog(errorReport)
            Log.e(TAG, message)
        }
    }
    
    /**
     * Create detailed error report
     */
    private fun createErrorReport(
        error: Throwable,
        context: String,
        severity: ErrorSeverity,
        isCrash: Boolean
    ): ErrorReport {
        val stackTrace = StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                error.printStackTrace(pw)
                sw.toString()
            }
        }
        
        return ErrorReport(
            timestamp = dateFormat.format(Date()),
            severity = severity,
            context = context,
            message = error.message ?: "Unknown error",
            stackTrace = stackTrace,
            deviceInfo = getDeviceInfo(),
            appInfo = getAppInfo(),
            isCrash = isCrash
        )
    }
    
    /**
     * Get device information
     */
    private fun getDeviceInfo(): DeviceInfo {
        val runtime = Runtime.getRuntime()
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            totalMemory = memoryInfo.totalMem,
            availableMemory = memoryInfo.availMem,
            isLowMemory = memoryInfo.lowMemory,
            maxHeapSize = runtime.maxMemory(),
            usedHeapSize = runtime.totalMemory() - runtime.freeMemory()
        )
    }
    
    /**
     * Get app information
     */
    private fun getAppInfo(): AppVersionInfo {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersionInfo(
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
                packageName = context.packageName
            )
        } catch (e: Exception) {
            AppVersionInfo("Unknown", 0, context.packageName)
        }
    }
    
    /**
     * Write error report to log file
     */
    private suspend fun writeToLog(errorReport: ErrorReport) {
        try {
            // Check file size and rotate if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                rotateLogFile()
            }
            
            val logEntry = formatLogEntry(errorReport)
            logFile.appendText(logEntry + "\n")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
    
    /**
     * Format error report for logging
     */
    private fun formatLogEntry(report: ErrorReport): String {
        return buildString {
            appendLine("=== ${if (report.isCrash) "CRASH" else "ERROR"} REPORT ===")
            appendLine("Timestamp: ${report.timestamp}")
            appendLine("Severity: ${report.severity}")
            appendLine("Context: ${report.context}")
            appendLine("Message: ${report.message}")
            appendLine("Device: ${report.deviceInfo.manufacturer} ${report.deviceInfo.model}")
            appendLine("Android: ${report.deviceInfo.androidVersion} (API ${report.deviceInfo.apiLevel})")
            appendLine("App: ${report.appInfo.versionName} (${report.appInfo.versionCode})")
            appendLine("Memory: ${report.deviceInfo.usedHeapSize / 1024 / 1024}MB used, ${report.deviceInfo.availableMemory / 1024 / 1024}MB available")
            if (report.stackTrace.isNotEmpty()) {
                appendLine("Stack Trace:")
                appendLine(report.stackTrace)
            }
            appendLine("=== END REPORT ===")
        }
    }
    
    /**
     * Rotate log file when it gets too large
     */
    private fun rotateLogFile() {
        try {
            val backupFile = File(context.filesDir, "launcher_errors_backup.log")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log file", e)
        }
    }
    
    /**
     * Get recent error reports
     */
    fun getRecentErrors(limit: Int = 50): List<String> {
        return try {
            if (!logFile.exists()) return emptyList()
            
            logFile.readLines().takeLast(limit * 15) // Approximate lines per report
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read error log", e)
            emptyList()
        }
    }
    
    /**
     * Clear error logs
     */
    fun clearLogs() {
        errorScope.launch {
            try {
                logFile.delete()
                Log.i(TAG, "Error logs cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs", e)
            }
        }
    }
    
    /**
     * Get log file for sharing
     */
    fun getLogFile(): File? {
        return if (logFile.exists()) logFile else null
    }
}

data class ErrorReport(
    val timestamp: String,
    val severity: ErrorSeverity,
    val context: String,
    val message: String,
    val stackTrace: String,
    val deviceInfo: DeviceInfo,
    val appInfo: AppVersionInfo,
    val isCrash: Boolean
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalMemory: Long,
    val availableMemory: Long,
    val isLowMemory: Boolean,
    val maxHeapSize: Long,
    val usedHeapSize: Long
)

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long,
    val packageName: String
)

enum class ErrorSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}
