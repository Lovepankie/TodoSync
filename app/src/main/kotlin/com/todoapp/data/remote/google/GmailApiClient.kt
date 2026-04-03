package com.todoapp.data.remote.google

import android.content.Context
import android.util.Base64
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.todoapp.domain.model.Priority
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GmailApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager
) {

    private val ME = "me"

    private fun buildService(): Gmail? {
        val account = authManager.getAccount() ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(GmailScopes.GMAIL_COMPOSE)
        ).apply { selectedAccount = account.account }

        return Gmail.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("TodoSync").build()
    }

    /**
     * Creates a Gmail draft representing this reminder.
     * Returns the draft ID (used as googleId).
     */
    suspend fun createReminderDraft(item: TodoItem): String? = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext null
        val email = authManager.getAccount()?.email ?: return@withContext null

        val subject = buildSubject(item)
        val body = buildBody(item)
        val rawMessage = buildMimeMessage(email, subject, body)

        val message = Message().apply { raw = rawMessage }
        val draft = com.google.api.services.gmail.model.Draft().apply { this.message = message }

        try {
            service.users().drafts().create(ME, draft).execute().id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Updates the draft content. Returns true on success.
     */
    suspend fun updateReminderDraft(item: TodoItem): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        val draftId = item.googleId ?: return@withContext false
        val email = authManager.getAccount()?.email ?: return@withContext false

        val subject = buildSubject(item)
        val body = buildBody(item)
        val rawMessage = buildMimeMessage(email, subject, body)

        val message = Message().apply { raw = rawMessage }
        val draft = com.google.api.services.gmail.model.Draft().apply {
            id = draftId
            this.message = message
        }

        try {
            service.users().drafts().update(ME, draftId, draft).execute()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes the draft. Returns true on success. */
    suspend fun deleteReminderDraft(draftId: String): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        try {
            service.users().drafts().delete(ME, draftId).execute()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Lists all Gmail drafts created by this app (identified by the
     * "Reminder: " subject prefix) and returns them as domain objects.
     * Each draft is fetched with metadata-only format to minimise data usage.
     */
    suspend fun listReminderDrafts(): List<TodoItem> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext emptyList()
        try {
            val drafts = service.users().drafts().list(ME).execute().drafts
                ?: return@withContext emptyList()
            drafts.mapNotNull { draftRef ->
                try {
                    val draft = service.users().drafts().get(ME, draftRef.id).execute()
                    val subject = draft.message?.payload?.headers
                        ?.firstOrNull { it.name == "Subject" }?.value
                        ?: return@mapNotNull null
                    if (!subject.startsWith("Reminder: ")) return@mapNotNull null
                    val rawTitle = subject.removePrefix("Reminder: ")
                    val (priority, cleanTitle) = parsePriorityFromSubject(rawTitle)
                    TodoItem(
                        title = cleanTitle,
                        description = "",
                        type = TodoType.REMINDER,
                        priority = priority,
                        googleId = draftRef.id,
                        syncStatus = SyncStatus.SYNCED
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parsePriorityFromSubject(title: String): Pair<Priority, String> = when {
        title.startsWith("[HIGH] ") -> Pair(Priority.HIGH, title.removePrefix("[HIGH] "))
        title.startsWith("[LOW] ")  -> Pair(Priority.LOW,  title.removePrefix("[LOW] "))
        else                        -> Pair(Priority.MEDIUM, title)
    }

    private fun buildSubject(item: TodoItem): String {
        val priority = when (item.priority) {
            Priority.HIGH -> "[HIGH] "
            Priority.LOW -> "[LOW] "
            else -> ""
        }
        return "Reminder: $priority${item.title}"
    }

    private fun buildBody(item: TodoItem): String {
        val sb = StringBuilder()
        sb.appendLine("=== TodoSync Reminder ===")
        sb.appendLine()
        sb.appendLine("Title: ${item.title}")
        if (item.description.isNotBlank()) sb.appendLine("Notes: ${item.description}")
        item.dueDate?.let {
            sb.appendLine("Due: ${it.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))}")
        }
        sb.appendLine("Priority: ${item.priority.displayName}")
        sb.appendLine()
        sb.appendLine("This reminder was created by TodoSync.")
        return sb.toString()
    }

    /**
     * Sends an actual email to the signed-in user's own address as a due-date reminder.
     * Called by AlarmReceiver when an item's alarm fires.
     */
    suspend fun sendReminderEmail(title: String, description: String, type: String): Boolean =
        withContext(Dispatchers.IO) {
            val service = buildService() ?: return@withContext false
            val email = authManager.getAccount()?.email ?: return@withContext false

            val subject = "TodoSync Reminder: $title"
            val body = buildString {
                appendLine("=== TodoSync Reminder ===")
                appendLine()
                appendLine("Your $type is due now.")
                appendLine()
                appendLine("Title: $title")
                if (description.isNotBlank()) appendLine("Notes: $description")
                appendLine()
                appendLine("Open the TodoSync app to view details.")
            }
            val raw = buildMimeMessage(email, subject, body)
            try {
                val message = Message().apply { this.raw = raw }
                service.users().messages().send(ME, message).execute()
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun buildMimeMessage(to: String, subject: String, body: String): String {
        val rawEmail = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
        // Gmail API requires URL-safe base64 with no line wraps and no padding characters
        return Base64.encodeToString(rawEmail.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
