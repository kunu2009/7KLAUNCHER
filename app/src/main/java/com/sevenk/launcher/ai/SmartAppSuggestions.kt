package com.sevenk.launcher.ai

import android.content.Context
import android.content.SharedPreferences
import com.sevenk.launcher.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * AI-powered smart app suggestions based on usage patterns, time, location, and user behavior
 */
class SmartAppSuggestions(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_suggestions", Context.MODE_PRIVATE)
    private val usageTracker = AppUsageTracker(context)
    private val timeBasedPredictor = TimeBasedPredictor(prefs)
    private val contextAwarePredictor = ContextAwarePredictor(context)
    
    companion object {
        private const val MAX_SUGGESTIONS = 6
        private const val MIN_CONFIDENCE_THRESHOLD = 0.3f
        private const val LEARNING_RATE = 0.1f
    }
    
    /**
     * Get smart app suggestions based on current context
     */
    suspend fun getSmartSuggestions(installedApps: List<AppInfo>): List<SmartSuggestion> = withContext(Dispatchers.IO) {
        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = currentTime.get(Calendar.DAY_OF_WEEK)
        
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Time-based predictions
        val timePredictions = timeBasedPredictor.predictAppsForTime(currentHour, dayOfWeek)
        suggestions.addAll(timePredictions.map { (pkg, confidence) ->
            SmartSuggestion(pkg, confidence, SuggestionReason.TIME_PATTERN)
        })
        
        // Usage frequency predictions
        val frequencyPredictions = usageTracker.getFrequentlyUsedApps(7) // Last 7 days
        suggestions.addAll(frequencyPredictions.map { (pkg, score) ->
            SmartSuggestion(pkg, score, SuggestionReason.FREQUENCY)
        })
        
        // Context-aware predictions (location, device state, etc.)
        val contextPredictions = contextAwarePredictor.getContextualSuggestions()
        suggestions.addAll(contextPredictions)
        
        // Sequence-based predictions (apps used together)
        val sequencePredictions = getSequenceBasedSuggestions()
        suggestions.addAll(sequencePredictions)
        
        // Merge and rank suggestions
        val mergedSuggestions = mergeSuggestions(suggestions)
        
        val installedPackages = installedApps.map { it.packageName }.toHashSet()

        // Filter by confidence threshold and installed apps, then return top suggestions
        val result = mergedSuggestions
            .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD && installedPackages.contains(it.packageName) }
            .sortedByDescending { it.confidence }
            .take(MAX_SUGGESTIONS)

        saveRecentSuggestions(result)
        result
    }
    
    /**
     * Learn from user app launches to improve predictions
     */
    fun learnFromLaunch(packageName: String, context: LaunchContext) {
        val currentTime = System.currentTimeMillis()
        usageTracker.recordAppLaunch(packageName, currentTime)
        timeBasedPredictor.recordTimeBasedLaunch(packageName, context)
        contextAwarePredictor.recordContextualLaunch(packageName, context)
        recordLaunchSequence(packageName)

        // Update suggestion accuracy based on whether the launched app was suggested
        val recentSuggestions = getRecentSuggestions()
        val wasSuggested = recentSuggestions.any { it.packageName == packageName }
        updateSuggestionAccuracy(packageName, wasSuggested, context)
    }
    
    /**
     * Get apps that are likely to be used together in sequence
     */
    private suspend fun getSequenceBasedSuggestions(): List<SmartSuggestion> = withContext(Dispatchers.IO) {
        val recentLaunches = usageTracker.getRecentLaunches(10) // Last 10 launches
        val suggestions = mutableListOf<SmartSuggestion>()
        
        if (recentLaunches.isNotEmpty()) {
            val lastLaunch = recentLaunches.first()
            val sequences = getAppSequences()
            
            sequences[lastLaunch.packageName]?.forEach { (nextApp, probability) ->
                suggestions.add(SmartSuggestion(nextApp, probability, SuggestionReason.SEQUENCE))
            }
        }
        
        suggestions
    }
    
    /**
     * Merge duplicate suggestions and combine confidence scores
     */
    private fun mergeSuggestions(suggestions: List<SmartSuggestion>): List<SmartSuggestion> {
        val merged = mutableMapOf<String, SmartSuggestion>()
        
        suggestions.forEach { suggestion ->
            val existing = merged[suggestion.packageName]
            if (existing != null) {
                // Combine confidence scores using weighted average
                val combinedConfidence = (existing.confidence + suggestion.confidence * 0.5f) / 1.5f
                val combinedReasons = existing.reasons + suggestion.reasons
                merged[suggestion.packageName] = SmartSuggestion(
                    suggestion.packageName,
                    combinedConfidence.coerceAtMost(1.0f),
                    existing.primaryReason,
                    combinedReasons.toSet().toList()
                )
            } else {
                merged[suggestion.packageName] = suggestion
            }
        }
        
        return merged.values.toList()
    }
    
    private fun getAppSequences(): Map<String, Map<String, Float>> {
        val result = mutableMapOf<String, MutableMap<String, Float>>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith("seq_")) return@forEach
            val raw = key.removePrefix("seq_")
            val split = raw.split("__")
            if (split.size != 2) return@forEach

            val from = split[0]
            val to = split[1]
            val count = (value as? Int) ?: return@forEach
            if (count <= 0) return@forEach

            val fromMap = result.getOrPut(from) { mutableMapOf() }
            fromMap[to] = count.toFloat()
        }

        // Normalize counts into probabilities per source app
        return result.mapValues { (_, nextMap) ->
            val total = nextMap.values.sum().takeIf { it > 0f } ?: 1f
            nextMap.mapValues { (_, count) -> (count / total).coerceIn(0f, 1f) }
        }
    }

    private fun saveRecentSuggestions(suggestions: List<SmartSuggestion>) {
        val serialized = suggestions.joinToString("|") {
            "${it.packageName},${it.confidence},${it.primaryReason.name}"
        }
        prefs.edit().putString("recent_suggestions", serialized).apply()
    }

    private fun getRecentSuggestions(): List<SmartSuggestion> {
        val raw = prefs.getString("recent_suggestions", "") ?: ""
        if (raw.isBlank()) return emptyList()

        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 3) return@mapNotNull null

            val packageName = parts[0]
            val confidence = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val reason = runCatching { SuggestionReason.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
            SmartSuggestion(packageName, confidence, reason)
        }
    }

    private fun recordLaunchSequence(packageName: String) {
        val previousApp = prefs.getString("last_launched_app", null)
        if (!previousApp.isNullOrBlank() && previousApp != packageName) {
            val key = "seq_${previousApp}__${packageName}"
            val current = prefs.getInt(key, 0)
            prefs.edit().putInt(key, current + 1).apply()
        }
        prefs.edit().putString("last_launched_app", packageName).apply()
    }

    private fun updateSuggestionAccuracy(packageName: String, wasAccurate: Boolean, context: LaunchContext) {
        val hour = context.hour
        val dayOfWeek = context.dayOfWeek
        val key = "acc_${packageName}_${hour}_$dayOfWeek"
        val existing = prefs.getFloat(key, 0.5f)
        val target = if (wasAccurate) 1f else 0f
        val updated = (existing + LEARNING_RATE * (target - existing)).coerceIn(0f, 1f)
        prefs.edit().putFloat(key, updated).apply()
    }
}

/**
 * Tracks app usage patterns and frequencies
 */
class AppUsageTracker(private val context: Context) {
    private val prefs = context.getSharedPreferences("usage_tracking", Context.MODE_PRIVATE)
    
    fun recordAppLaunch(packageName: String, timestamp: Long) {
        val launches = getLaunchHistory(packageName).toMutableList()
        launches.add(timestamp)
        
        // Keep only last 100 launches per app
        if (launches.size > 100) {
            launches.removeAt(0)
        }
        
        saveLaunchHistory(packageName, launches)
    }
    
    fun getFrequentlyUsedApps(daysBack: Int): List<Pair<String, Float>> {
        val cutoffTime = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)
        val appFrequencies = mutableMapOf<String, Int>()
        
        // Count launches for each app in the time window
        val allKeys = prefs.all.keys.filter { it.startsWith("launches_") }
        allKeys.forEach { key ->
            val packageName = key.removePrefix("launches_")
            val launches = getLaunchHistory(packageName)
            val recentLaunches = launches.count { it > cutoffTime }
            appFrequencies[packageName] = recentLaunches
        }
        
        // Convert to normalized scores
        val maxFreq = appFrequencies.values.maxOrNull() ?: 1
        return appFrequencies.map { (pkg, freq) ->
            pkg to (freq.toFloat() / maxFreq)
        }.sortedByDescending { it.second }
    }
    
    fun getRecentLaunches(count: Int): List<AppLaunch> {
        val allLaunches = mutableListOf<AppLaunch>()
        
        val allKeys = prefs.all.keys.filter { it.startsWith("launches_") }
        allKeys.forEach { key ->
            val packageName = key.removePrefix("launches_")
            val launches = getLaunchHistory(packageName)
            launches.forEach { timestamp ->
                allLaunches.add(AppLaunch(packageName, timestamp))
            }
        }
        
        return allLaunches.sortedByDescending { it.timestamp }.take(count)
    }
    
    private fun getLaunchHistory(packageName: String): List<Long> {
        val launchesStr = prefs.getString("launches_$packageName", "")
        return if (launchesStr.isNullOrEmpty()) {
            emptyList()
        } else {
            launchesStr.split(",").mapNotNull { it.toLongOrNull() }
        }
    }
    
    private fun saveLaunchHistory(packageName: String, launches: List<Long>) {
        prefs.edit().putString("launches_$packageName", launches.joinToString(",")).apply()
    }
}

/**
 * Predicts app usage based on time patterns
 */
class TimeBasedPredictor(private val prefs: SharedPreferences) {
    
    fun predictAppsForTime(hour: Int, dayOfWeek: Int): List<Pair<String, Float>> {
        val timeKey = "time_${hour}_$dayOfWeek"
        val appsForTime = getAppsForTimeSlot(timeKey)
        
        return appsForTime.map { (pkg, count) ->
            val confidence = calculateTimeConfidence(hour, dayOfWeek, count)
            pkg to confidence
        }.sortedByDescending { it.second }
    }
    
    fun recordTimeBasedLaunch(packageName: String, context: LaunchContext) {
        val hour = context.hour
        val dayOfWeek = context.dayOfWeek
        val timeKey = "time_${hour}_$dayOfWeek"

        val currentCount = getAppCountForTimeSlot(timeKey, packageName)
        setAppCountForTimeSlot(timeKey, packageName, currentCount + 1)

        val total = getTotalLaunchesForTimeSlot(timeKey)
        setTotalLaunchesForTimeSlot(timeKey, total + 1)
    }
    
    private fun calculateTimeConfidence(hour: Int, dayOfWeek: Int, count: Int): Float {
        // Calculate confidence based on how frequently this app is used at this time
        val totalLaunchesAtTime = getTotalLaunchesForTimeSlot("time_${hour}_$dayOfWeek")
        return if (totalLaunchesAtTime > 0) {
            (count.toFloat() / totalLaunchesAtTime).coerceAtMost(1.0f)
        } else {
            0f
        }
    }
    
    private fun getAppsForTimeSlot(timeKey: String): Map<String, Int> {
        val prefix = "${timeKey}_"
        return prefs.all
            .filterKeys { it.startsWith(prefix) && !it.endsWith("_total") }
            .mapNotNull { (key, value) ->
                val packageName = key.removePrefix(prefix)
                val count = value as? Int ?: return@mapNotNull null
                packageName to count
            }
            .toMap()
    }
    
    private fun getAppCountForTimeSlot(timeKey: String, packageName: String): Int {
        return prefs.getInt("${timeKey}_$packageName", 0)
    }
    
    private fun setAppCountForTimeSlot(timeKey: String, packageName: String, count: Int) {
        prefs.edit().putInt("${timeKey}_$packageName", count).apply()
    }
    
    private fun getTotalLaunchesForTimeSlot(timeKey: String): Int {
        return prefs.getInt("${timeKey}_total", 0)
    }

    private fun setTotalLaunchesForTimeSlot(timeKey: String, count: Int) {
        prefs.edit().putInt("${timeKey}_total", count).apply()
    }
}

/**
 * Context-aware predictions based on device state, location, etc.
 */
class ContextAwarePredictor(private val context: Context) {

    private val prefs = context.getSharedPreferences("smart_suggestions_context", Context.MODE_PRIVATE)
    
    suspend fun getContextualSuggestions(): List<SmartSuggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Battery level based suggestions
        suggestions.addAll(getBatteryBasedSuggestions())
        
        // Network state based suggestions
        suggestions.addAll(getNetworkBasedSuggestions())
        
        // Device state based suggestions
        suggestions.addAll(getDeviceStateBasedSuggestions())
        
        suggestions
    }
    
    fun recordContextualLaunch(packageName: String, context: LaunchContext) {
        if (context.batteryLevel in 0..20) {
            prefs.edit().putString("context_battery_package", packageName).apply()
        }

        if (context.networkType.equals("wifi", ignoreCase = true)) {
            prefs.edit().putString("context_network_package", packageName).apply()
        }

        if (context.isHeadphonesConnected) {
            prefs.edit().putString("context_device_state_package", packageName).apply()
        }
    }
    
    private fun getBatteryBasedSuggestions(): List<SmartSuggestion> {
        val batteryModePackage = prefs.getString("context_battery_package", null) ?: return emptyList()
        return listOf(SmartSuggestion(batteryModePackage, 0.35f, SuggestionReason.CONTEXT))
    }
    
    private fun getNetworkBasedSuggestions(): List<SmartSuggestion> {
        val networkPackage = prefs.getString("context_network_package", null) ?: return emptyList()
        return listOf(SmartSuggestion(networkPackage, 0.35f, SuggestionReason.CONTEXT))
    }
    
    private fun getDeviceStateBasedSuggestions(): List<SmartSuggestion> {
        val deviceStatePackage = prefs.getString("context_device_state_package", null) ?: return emptyList()
        return listOf(SmartSuggestion(deviceStatePackage, 0.35f, SuggestionReason.CONTEXT))
    }
}

/**
 * Represents a smart app suggestion with confidence and reasoning
 */
data class SmartSuggestion(
    val packageName: String,
    val confidence: Float,
    val primaryReason: SuggestionReason,
    val reasons: List<SuggestionReason> = listOf(primaryReason)
)

/**
 * Reasons why an app might be suggested
 */
enum class SuggestionReason {
    TIME_PATTERN,     // Based on time of day/week patterns
    FREQUENCY,        // Based on usage frequency
    SEQUENCE,         // Based on app usage sequences
    CONTEXT,          // Based on device context (battery, network, etc.)
    LOCATION,         // Based on location patterns
    RECENTLY_USED     // Recently used apps
}

/**
 * Context information when an app is launched
 */
data class LaunchContext(
    val timestamp: Long = System.currentTimeMillis(),
    val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    val dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val networkType: String = "",
    val isHeadphonesConnected: Boolean = false
)

/**
 * Represents an app launch event
 */
data class AppLaunch(
    val packageName: String,
    val timestamp: Long
)