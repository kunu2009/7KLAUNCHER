package com.sevenk.launcher.monitoring

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

/**
 * System monitoring utilities for launcher performance tracking
 */
class SystemMonitor(private val context: Context) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    /**
     * Get battery information
     */
    fun getBatteryInfo(): BatteryInfo {
        val batteryIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryIntentFilter)
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPercentage = (level * 100) / scale
        
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        
        return BatteryInfo(
            percentage = batteryPercentage,
            temperature = temperature / 10.0, // Convert to Celsius
            voltage = voltage / 1000.0, // Convert to Volts
            health = getHealthString(health),
            isCharging = isCharging
        )
    }
    
    /**
     * Get RAM information for the launcher process
     */
    fun getRAMInfo(): RAMInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Get launcher process memory
        val myPid = Process.myPid()
        val processMemoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(myPid))
        val launcherMemoryUsage = if (processMemoryInfo.isNotEmpty()) {
            processMemoryInfo[0].totalPss * 1024L // Convert KB to bytes
        } else {
            0L
        }
        
        // Get heap memory
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapMax = runtime.maxMemory()
        
        return RAMInfo(
            totalRAM = memoryInfo.totalMem,
            availableRAM = memoryInfo.availMem,
            usedRAM = memoryInfo.totalMem - memoryInfo.availMem,
            launcherRAMUsage = launcherMemoryUsage,
            heapUsed = heapUsed,
            heapMax = heapMax,
            isLowMemory = memoryInfo.lowMemory
        )
    }
    
    /**
     * Get CPU usage information
     */
    fun getCPUInfo(): CPUInfo {
        return try {
            val cpuUsage = getCurrentCPUUsage()
            val cpuTemp = getCPUTemperature()
            val cores = Runtime.getRuntime().availableProcessors()
            
            CPUInfo(
                usage = cpuUsage,
                temperature = cpuTemp,
                cores = cores,
                frequency = getCPUFrequency()
            )
        } catch (e: Exception) {
            CPUInfo(0.0, 0.0, Runtime.getRuntime().availableProcessors(), 0L)
        }
    }
    
    /**
     * Get storage information
     */
    fun getStorageInfo(): StorageInfo {
        return try {
            val internalStats = StatFs(context.filesDir.absolutePath)
            val totalBytes = internalStats.blockSizeLong * internalStats.blockCountLong
            val availableBytes = internalStats.blockSizeLong * internalStats.availableBlocksLong
            val usedBytes = totalBytes - availableBytes
            
            StorageInfo(
                totalStorage = totalBytes,
                usedStorage = usedBytes,
                availableStorage = availableBytes
            )
        } catch (e: Exception) {
            StorageInfo(0L, 0L, 0L)
        }
    }
    
    private fun getCurrentCPUUsage(): Double {
        return try {
            val file = RandomAccessFile("/proc/stat", "r")
            val cpuLine = file.readLine()
            file.close()
            
            val times = cpuLine.split(" ").drop(2).take(7).map { it.toLong() }
            val idleTime = times[3]
            val totalTime = times.sum()
            val workTime = totalTime - idleTime
            
            (workTime.toDouble() / totalTime.toDouble()) * 100.0
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun getCPUTemperature(): Double {
        return try {
            val tempFiles = listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
                "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp"
            )
            
            for (tempFile in tempFiles) {
                try {
                    val file = File(tempFile)
                    if (file.exists()) {
                        val temp = file.readText().trim().toDouble()
                        return if (temp > 1000) temp / 1000.0 else temp
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            0.0
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun getCPUFrequency(): Long {
        return try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (file.exists()) {
                file.readText().trim().toLong()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getHealthString(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }
    
    data class BatteryInfo(
        val percentage: Int,
        val temperature: Double,
        val voltage: Double,
        val health: String,
        val isCharging: Boolean
    )
    
    data class RAMInfo(
        val totalRAM: Long,
        val availableRAM: Long,
        val usedRAM: Long,
        val launcherRAMUsage: Long,
        val heapUsed: Long,
        val heapMax: Long,
        val isLowMemory: Boolean
    )
    
    data class CPUInfo(
        val usage: Double,
        val temperature: Double,
        val cores: Int,
        val frequency: Long
    )
    
    data class StorageInfo(
        val totalStorage: Long,
        val usedStorage: Long,
        val availableStorage: Long
    )
}