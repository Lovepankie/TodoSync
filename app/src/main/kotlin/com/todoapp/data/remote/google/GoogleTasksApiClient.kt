package com.todoapp.data.remote.google

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import com.todoapp.domain.model.Priority
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import java.time.LocalDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleTasksApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager
) {

    private val MY_TASKS_LIST = "@default"
    private val RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    private fun buildService(): Tasks? {
        val account = authManager.getAccount() ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(TasksScopes.TASKS)
        ).apply { selectedAccount = account.account }

        return Tasks.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("TodoSync").build()
    }

    /** Creates a Google Task and returns its remote ID. */
    suspend fun createTask(item: TodoItem): String? = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext null
        val task = Task().apply {
            title = buildTitle(item)
            notes = item.description
            item.dueDate?.let {
                due = it.atOffset(ZoneOffset.UTC).format(RFC3339)
            }
            if (item.isCompleted) status = "completed" else status = "needsAction"
        }
        try {
            service.tasks().insert(MY_TASKS_LIST, task).execute().id
        } catch (e: Exception) {
            null
        }
    }

    /** Updates an existing Google Task. Returns true on success. */
    suspend fun updateTask(item: TodoItem): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        val googleId = item.googleId ?: return@withContext false
        val task = Task().apply {
            id = googleId
            title = buildTitle(item)
            notes = item.description
            item.dueDate?.let {
                due = it.atOffset(ZoneOffset.UTC).format(RFC3339)
            }
            status = if (item.isCompleted) "completed" else "needsAction"
        }
        try {
            service.tasks().update(MY_TASKS_LIST, googleId, task).execute()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes a Google Task. Returns true on success. */
    suspend fun deleteTask(googleId: String): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        try {
            service.tasks().delete(MY_TASKS_LIST, googleId).execute()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Fetches all tasks from Google and returns them as domain objects. */
    suspend fun listTasks(): List<TodoItem> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext emptyList()
        try {
            val result = service.tasks().list(MY_TASKS_LIST)
                .setShowCompleted(true)
                .setShowHidden(true)
                .execute()
            result.items?.mapNotNull { it.toTodoItem() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Task.toTodoItem(): TodoItem? {
        val rawTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val (priority, cleanTitle) = parsePriority(rawTitle)
        val dueDate = due?.let {
            try { LocalDateTime.parse(it, RFC3339) } catch (e: Exception) { null }
        }
        return TodoItem(
            title = cleanTitle,
            description = notes ?: "",
            type = TodoType.TASK,
            priority = priority,
            dueDate = dueDate,
            isCompleted = status == "completed",
            googleId = id,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun parsePriority(title: String): Pair<Priority, String> = when {
        title.startsWith("[HIGH] ") -> Pair(Priority.HIGH, title.removePrefix("[HIGH] "))
        title.startsWith("[LOW] ")  -> Pair(Priority.LOW,  title.removePrefix("[LOW] "))
        else                        -> Pair(Priority.MEDIUM, title)
    }

    private fun buildTitle(item: TodoItem): String {
        val prefix = when (item.priority) {
            Priority.HIGH -> "[HIGH] "
            Priority.MEDIUM -> ""
            Priority.LOW -> "[LOW] "
        }
        return "$prefix${item.title}"
    }
}
