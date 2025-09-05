package com.sevenk.launcher.notes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    @SerialName("id") val id: String,
    @SerialName("title") var title: String = "",
    @SerialName("content") var content: String = "",
    @SerialName("pinned") var pinned: Boolean = false,
    @SerialName("color") var color: String = "#FFFFFF",
    @SerialName("updatedAt") var updatedAt: Long = System.currentTimeMillis()
) {
    fun matchesQuery(q: String): Boolean {
        if (q.isBlank()) return true
        val s = q.lowercase()
        return title.lowercase().contains(s) || content.lowercase().contains(s)
    }
}
