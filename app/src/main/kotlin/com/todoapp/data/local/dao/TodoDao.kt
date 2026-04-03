package com.todoapp.data.local.dao

import androidx.room.*
import com.todoapp.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todo_items WHERE syncStatus != 'DELETED' AND isCompleted = 0 ORDER BY CASE WHEN COALESCE(startDate, dueDate) IS NULL THEN 1 ELSE 0 END ASC, COALESCE(startDate, dueDate) ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_items WHERE type = :type AND syncStatus != 'DELETED' AND isCompleted = 0 ORDER BY CASE WHEN COALESCE(startDate, dueDate) IS NULL THEN 1 ELSE 0 END ASC, COALESCE(startDate, dueDate) ASC, createdAt ASC")
    fun observeByType(type: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = :completed AND syncStatus != 'DELETED' ORDER BY CASE WHEN COALESCE(startDate, dueDate) IS NULL THEN 1 ELSE 0 END ASC, COALESCE(startDate, dueDate) ASC, createdAt ASC")
    fun observeByCompleted(completed: Boolean): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Query("SELECT * FROM todo_items WHERE googleId = :googleId LIMIT 1")
    suspend fun getByGoogleId(googleId: String): TodoEntity?

    @Query("SELECT * FROM todo_items WHERE syncStatus IN ('PENDING', 'FAILED')")
    suspend fun getPendingSync(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TodoEntity): Long

    @Update
    suspend fun update(entity: TodoEntity)

    @Query("UPDATE todo_items SET isCompleted = NOT isCompleted, syncStatus = 'PENDING', updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleComplete(id: Long, updatedAt: Long)

    @Query("UPDATE todo_items SET syncStatus = 'DELETED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: Long, updatedAt: Long)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todo_items SET googleId = :googleId, syncStatus = 'SYNCED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: Long, googleId: String, updatedAt: Long)

    @Query("UPDATE todo_items SET syncStatus = 'FAILED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSyncFailed(id: Long, updatedAt: Long)

    /**
     * Marks all non-completed, non-deleted items as complete when their deadline
     * has passed. Tasks/Reminders use dueDate; Events use endDate (or dueDate as
     * fallback). Marked PENDING so the completion is pushed to Google on next sync.
     */
    @Query("""
        UPDATE todo_items
        SET isCompleted = 1, syncStatus = 'PENDING', updatedAt = :now
        WHERE isCompleted = 0
          AND syncStatus != 'DELETED'
          AND (
              (type IN ('TASK', 'REMINDER') AND dueDate IS NOT NULL AND dueDate < :now)
              OR (type = 'EVENT' AND COALESCE(endDate, dueDate) IS NOT NULL AND COALESCE(endDate, dueDate) < :now)
          )
    """)
    suspend fun autoCompleteExpired(now: Long)
}
