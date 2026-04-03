package com.todoapp.data.remote.google

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import java.time.LocalDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager
) {

    private val PRIMARY_CALENDAR = "primary"

    private fun buildService(): Calendar? {
        val account = authManager.getAccount() ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(CalendarScopes.CALENDAR)
        ).apply { selectedAccount = account.account }

        return Calendar.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("TodoSync").build()
    }

    /** Creates a Google Calendar event and returns its remote ID. */
    suspend fun createEvent(item: TodoItem): String? = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext null
        val event = buildCalendarEvent(item)
        try {
            service.events().insert(PRIMARY_CALENDAR, event).execute().id
        } catch (e: Exception) {
            null
        }
    }

    /** Updates an existing Google Calendar event. Returns true on success. */
    suspend fun updateEvent(item: TodoItem): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        val googleId = item.googleId ?: return@withContext false
        val event = buildCalendarEvent(item).apply { id = googleId }
        try {
            service.events().update(PRIMARY_CALENDAR, googleId, event).execute()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes a Google Calendar event. Returns true on success. */
    suspend fun deleteEvent(googleId: String): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        try {
            service.events().delete(PRIMARY_CALENDAR, googleId).execute()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches up to 250 non-cancelled events from the past year onwards and
     * returns them as domain objects.
     */
    suspend fun listEvents(): List<TodoItem> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext emptyList()
        try {
            val oneYearAgo = DateTime(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000)
            val result = service.events().list(PRIMARY_CALENDAR)
                .setTimeMin(oneYearAgo)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setMaxResults(250)
                .execute()
            result.items
                ?.filter { it.status != "cancelled" }
                ?.mapNotNull { it.toTodoItem() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Event.toTodoItem(): TodoItem? {
        val title = summary?.takeIf { it.isNotBlank() } ?: return null
        val startMs = start?.dateTime?.value ?: start?.date?.value ?: return null
        val endMs   = end?.dateTime?.value   ?: end?.date?.value
        val startLdt = LocalDateTime.ofEpochSecond(startMs / 1000, 0, ZoneOffset.UTC)
        val endLdt   = endMs?.let { LocalDateTime.ofEpochSecond(it / 1000, 0, ZoneOffset.UTC) }
        return TodoItem(
            title = title,
            description = description ?: "",
            type = TodoType.EVENT,
            startDate = startLdt,
            endDate = endLdt,
            dueDate = startLdt,
            location = location,
            isCompleted = false,
            googleId = id,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun buildCalendarEvent(item: TodoItem): Event {
        // Use startDate/endDate for events, fall back to dueDate for all-day
        val start = item.startDate ?: item.dueDate
        val end = item.endDate ?: item.startDate?.plusHours(1) ?: item.dueDate

        val startDt = if (start != null) {
            EventDateTime().setDateTime(
                DateTime(start.toInstant(ZoneOffset.UTC).toEpochMilli())
            ).setTimeZone("UTC")
        } else {
            // All-day event: today
            EventDateTime().setDate(DateTime(true, System.currentTimeMillis(), 0))
        }

        val endDt = if (end != null) {
            EventDateTime().setDateTime(
                DateTime(end.toInstant(ZoneOffset.UTC).toEpochMilli())
            ).setTimeZone("UTC")
        } else {
            startDt
        }

        return Event().apply {
            summary = item.title
            description = item.description
            this.start = startDt
            this.end = endDt
            location = item.location
            // Add a popup reminder 10 min before
            reminders = Event.Reminders().apply {
                useDefault = false
                overrides = listOf(
                    EventReminder().apply {
                        method = "popup"
                        minutes = 10
                    }
                )
            }
        }
    }
}
