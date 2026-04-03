package com.todoapp.data.repository

import com.todoapp.data.local.dao.TodoDao
import com.todoapp.data.local.entity.toEntity
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import com.todoapp.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepositoryImpl @Inject constructor(
    private val dao: TodoDao
) : TodoRepository {

    override fun observeAll(): Flow<List<TodoItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByType(type: TodoType): Flow<List<TodoItem>> =
        dao.observeByType(type.name).map { list -> list.map { it.toDomain() } }

    override fun observeByCompleted(completed: Boolean): Flow<List<TodoItem>> =
        dao.observeByCompleted(completed).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): TodoItem? =
        dao.getById(id)?.toDomain()

    override suspend fun save(item: TodoItem): Long {
        val now = LocalDateTime.now()
        val toSave = if (item.id == 0L) {
            item.copy(
                syncStatus = SyncStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        } else {
            item.copy(syncStatus = SyncStatus.PENDING, updatedAt = now)
        }
        return dao.insert(toSave.toEntity())
    }

    override suspend fun toggleComplete(id: Long) {
        dao.toggleComplete(id, nowMillis())
    }

    override suspend fun delete(id: Long) {
        val item = dao.getById(id)
        if (item?.googleId != null) {
            // Has a remote counterpart — mark for deletion so sync worker can clean up
            dao.markDeleted(id, nowMillis())
        } else {
            // Never synced — safe to delete immediately
            dao.deleteById(id)
        }
    }

    override suspend fun getPendingSync(): List<TodoItem> =
        dao.getPendingSync().map { it.toDomain() }

    override suspend fun markSynced(id: Long, googleId: String) {
        dao.markSynced(id, googleId, nowMillis())
    }

    override suspend fun markSyncFailed(id: Long) {
        dao.markSyncFailed(id, nowMillis())
    }

    override suspend fun upsertFromRemote(item: TodoItem) {
        val googleId = item.googleId ?: return
        val existing = dao.getByGoogleId(googleId)
        if (existing == null) {
            // Brand-new item from another device — insert as SYNCED
            dao.insert(item.copy(syncStatus = SyncStatus.SYNCED).toEntity())
        } else if (existing.syncStatus != SyncStatus.PENDING.name &&
            existing.syncStatus != SyncStatus.DELETED.name
        ) {
            // Update local copy, but preserve the original row id and createdAt
            val updated = item.copy(
                id = existing.id,
                syncStatus = SyncStatus.SYNCED
            ).toEntity().copy(createdAt = existing.createdAt)
            dao.insert(updated)
        }
        // PENDING or DELETED → local wins, skip remote value
    }

    override suspend fun deletePermanently(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun autoCompleteExpired() {
        dao.autoCompleteExpired(nowMillis())
    }

    private fun nowMillis() = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000
}
