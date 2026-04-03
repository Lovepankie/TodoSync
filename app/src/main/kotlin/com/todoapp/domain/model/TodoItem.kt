package com.todoapp.domain.model

import java.time.LocalDateTime

data class TodoItem(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val type: TodoType,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDateTime? = null,
    val startDate: LocalDateTime? = null,   // For events: start time
    val endDate: LocalDateTime? = null,     // For events: end time
    val location: String? = null,           // For events: location
    val isCompleted: Boolean = false,
    val googleId: String? = null,           // Remote ID from Google service
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val recurrence: Recurrence = Recurrence.NONE,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
