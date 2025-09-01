package com.sevenk.launcher.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "task_text") var task: String,
    @ColumnInfo(name = "is_completed") var isCompleted: Boolean = false
)
