package com.todoapp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.todoapp.data.remote.google.GmailApiClient
import com.todoapp.data.remote.google.GoogleAuthManager
import com.todoapp.data.remote.google.GoogleCalendarApiClient
import com.todoapp.data.remote.google.GoogleTasksApiClient
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoType
import com.todoapp.domain.repository.TodoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TodoRepository,
    private val authManager: GoogleAuthManager,
    private val tasksClient: GoogleTasksApiClient,
    private val calendarClient: GoogleCalendarApiClient,
    private val gmailClient: GmailApiClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!authManager.isSignedIn) return Result.success()

        return try {
            // Step 1: auto-complete items whose deadline has passed
            repository.autoCompleteExpired()

            // Step 2: pull remote items into the local DB so other devices' changes appear
            pullFromGoogle()

            // Step 3: push any locally-pending changes up to Google
            val pending = repository.getPendingSync()
            var anyFailed = false
            for (item in pending) {
                when (item.syncStatus) {
                    SyncStatus.DELETED -> handleDeletion(item)
                    else -> {
                        val success = handleUpsert(item)
                        if (!success) anyFailed = true
                    }
                }
            }

            if (anyFailed) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun pullFromGoogle() {
        val remoteItems = tasksClient.listTasks() +
                calendarClient.listEvents() +
                gmailClient.listReminderDrafts()
        for (item in remoteItems) {
            repository.upsertFromRemote(item)
        }
    }

    private suspend fun handleUpsert(item: com.todoapp.domain.model.TodoItem): Boolean {
        val isNew = item.googleId == null

        val googleId: String? = when (item.type) {
            TodoType.TASK -> {
                if (isNew) tasksClient.createTask(item)
                else if (tasksClient.updateTask(item)) item.googleId else null
            }
            TodoType.EVENT -> {
                if (isNew) calendarClient.createEvent(item)
                else if (calendarClient.updateEvent(item)) item.googleId else null
            }
            TodoType.REMINDER -> {
                if (isNew) gmailClient.createReminderDraft(item)
                else if (gmailClient.updateReminderDraft(item)) item.googleId else null
            }
        }

        return if (googleId != null) {
            repository.markSynced(item.id, googleId)
            true
        } else {
            repository.markSyncFailed(item.id)
            false
        }
    }

    private suspend fun handleDeletion(item: com.todoapp.domain.model.TodoItem) {
        if (item.googleId == null) {
            // Never reached Google — safe to remove immediately
            repository.deletePermanently(item.id)
            return
        }
        val deleted = when (item.type) {
            TodoType.TASK     -> tasksClient.deleteTask(item.googleId)
            TodoType.EVENT    -> calendarClient.deleteEvent(item.googleId)
            TodoType.REMINDER -> gmailClient.deleteReminderDraft(item.googleId)
        }
        if (deleted) repository.deletePermanently(item.id)
    }

    companion object {
        const val WORK_NAME = "TodoSyncWork"
    }
}
