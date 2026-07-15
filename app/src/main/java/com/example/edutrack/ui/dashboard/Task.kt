package com.example.edutrack.ui.dashboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_table")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val subject: String,
    val priority: String,
    val deadline: String,
    val isDone: Boolean = false,
    val description: String = "",
    val category: String = "Lainnya"
)