package com.sevenk.launcher

import kotlinx.serialization.Serializable

@Serializable
data class UsageStats(
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
    val totalVisibleTime: Long,
    val lastTimeVisible: Long
)
