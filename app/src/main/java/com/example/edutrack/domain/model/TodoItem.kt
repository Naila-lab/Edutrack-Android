package com.example.edutrack.domain.model

import java.util.UUID

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
