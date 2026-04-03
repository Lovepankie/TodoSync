package com.todoapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.todoapp.domain.model.Priority
import com.todoapp.domain.model.Recurrence
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import java.time.LocalDateTime

@Entity(tableName = "todo_items")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val type: String,           // TodoType.name
    val priority: String,       // Priority.name
    val dueDate: Long?,         // epoch milli
    val startDate: Long?,       // epoch milli
    val endDate: Long?,         // epoch milli
    val location: String?,
    val isCompleted: Boolean,
    val googleId: String?,
    val syncStatus: String,     // SyncStatus.name
    val recurrence: String = Recurrence.NONE.name,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): TodoItem = TodoItem(
        id = id,
        title = title,
        description = description,
        type = TodoType.valueOf(type),
        priority = Priority.valueOf(priority),
        dueDate = dueDate?.toLocalDateTime(),
        startDate = startDate?.toLocalDateTime(),
        endDate = endDate?.toLocalDateTime(),
        location = location,
        isCompleted = isCompleted,
        googleId = googleId,
        syncStatus = SyncStatus.valueOf(syncStatus),
        recurrence = runCatching { Recurrence.valueOf(recurrence) }.getOrDefault(Recurrence.NONE),
        createdAt = createdAt.toLocalDateTime()!!,
        updatedAt = updatedAt.toLocalDateTime()!!
    )
}

fun TodoItem.toEntity(): TodoEntity = TodoEntity(
    id = id,
    title = title,
    description = description,
    type = type.name,
    priority = priority.name,
    dueDate = dueDate?.toEpochMilli(),
    startDate = startDate?.toEpochMilli(),
    endDate = endDate?.toEpochMilli(),
    location = location,
    isCompleted = isCompleted,
    googleId = googleId,
    syncStatus = syncStatus.name,
    recurrence = recurrence.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

private fun Long.toLocalDateTime(): LocalDateTime? {
    return try {
        LocalDateTime.ofEpochSecond(this / 1000, 0, java.time.ZoneOffset.UTC)
    } catch (e: Exception) { null }
}

private fun LocalDateTime.toEpochMilli(): Long =
    toEpochSecond(java.time.ZoneOffset.UTC) * 1000
