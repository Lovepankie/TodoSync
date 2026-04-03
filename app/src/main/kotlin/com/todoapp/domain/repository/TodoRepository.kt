package com.todoapp.domain.repository

import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import kotlinx.coroutines.flow.Flow

interface TodoRepository {

    /** Stream of all items, ordered by due date then created date. */
    fun observeAll(): Flow<List<TodoItem>>

    /** Stream filtered by type. */
    fun observeByType(type: TodoType): Flow<List<TodoItem>>

    /** Stream filtered by completion state. */
    fun observeByCompleted(completed: Boolean): Flow<List<TodoItem>>

    suspend fun getById(id: Long): TodoItem?

    /** Inserts or updates a local item and queues it for sync. Returns the row id. */
    suspend fun save(item: TodoItem): Long

    /** Marks an item complete / incomplete locally and queues sync. */
    suspend fun toggleComplete(id: Long)

    /** Deletes locally and marks for remote deletion. */
    suspend fun delete(id: Long)

    /** Returns items that have not yet been synced to Google. */
    suspend fun getPendingSync(): List<TodoItem>

    /** Called by the sync layer to record a successful push to Google. */
    suspend fun markSynced(id: Long, googleId: String)

    /** Called by the sync layer when sync failed for an item. */
    suspend fun markSyncFailed(id: Long)

    /**
     * Inserts or updates an item that came from Google (no local changes).
     * Skips items that have un-pushed local changes (PENDING/DELETED) so
     * local edits are never overwritten by a stale remote value.
     */
    suspend fun upsertFromRemote(item: TodoItem)

    /** Permanently removes the local row (used after a successful remote deletion). */
    suspend fun deletePermanently(id: Long)

    /** Marks all items whose deadline has passed as complete and queues sync. */
    suspend fun autoCompleteExpired()
}
