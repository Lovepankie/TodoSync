package com.todoapp.ui.viewmodel

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.todoapp.alarm.AlarmScheduler
import com.todoapp.data.remote.google.GoogleAuthManager
import com.todoapp.domain.model.Recurrence
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import com.todoapp.domain.repository.TodoRepository
import com.todoapp.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class FilterTab { ALL, TASKS, EVENTS, REMINDERS, COMPLETED }

data class MainUiState(
    val items: List<TodoItem> = emptyList(),
    val activeFilter: FilterTab = FilterTab.ALL,
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val tabCounts: Map<FilterTab, Int> = emptyMap()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val authManager: GoogleAuthManager,
    private val syncManager: SyncManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    init {
        viewModelScope.launch { repository.autoCompleteExpired() }
        // Observe sync failures and surface them to the UI
        viewModelScope.launch {
            syncManager.observeSyncFailed().collect { failed ->
                _syncError.value = if (failed) "Sync failed. Check your internet connection." else null
            }
        }
    }

    private val _filter = MutableStateFlow(FilterTab.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)

    // Count badges — observe live counts for each tab
    private val tabCounts: Flow<Map<FilterTab, Int>> = combine(
        repository.observeAll().map { it.size },
        repository.observeByType(TodoType.TASK).map { it.size },
        repository.observeByType(TodoType.EVENT).map { it.size },
        repository.observeByType(TodoType.REMINDER).map { it.size },
        repository.observeByCompleted(true).map { it.size }
    ) { all, tasks, events, reminders, done ->
        mapOf(
            FilterTab.ALL to all,
            FilterTab.TASKS to tasks,
            FilterTab.EVENTS to events,
            FilterTab.REMINDERS to reminders,
            FilterTab.COMPLETED to done
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        _filter,
        _searchQuery,
        authManager.account,
        _isSyncing,
        _syncError
    ) { filter, query, account, syncing, error ->
        listOf(filter, query, account, syncing, error) // bundle for flatMapLatest
    }.flatMapLatest { bundle ->
        val filter = bundle[0] as FilterTab
        val query = bundle[1] as String
        val account = bundle[2]
        val syncing = bundle[3] as Boolean
        val error = bundle[4] as? String

        val itemsFlow = when (filter) {
            FilterTab.ALL -> repository.observeAll()
            FilterTab.TASKS -> repository.observeByType(TodoType.TASK)
            FilterTab.EVENTS -> repository.observeByType(TodoType.EVENT)
            FilterTab.REMINDERS -> repository.observeByType(TodoType.REMINDER)
            FilterTab.COMPLETED -> repository.observeByCompleted(true)
        }

        combine(itemsFlow, tabCounts) { items, counts ->
            val filtered = if (query.isBlank()) items
            else items.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
            MainUiState(
                items = filtered,
                activeFilter = filter,
                searchQuery = query,
                isSyncing = syncing,
                syncError = error,
                isSignedIn = account != null,
                userEmail = (account as? com.google.android.gms.auth.api.signin.GoogleSignInAccount)?.email,
                tabCounts = counts
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(isSignedIn = authManager.isSignedIn)
    )

    fun setFilter(tab: FilterTab) { _filter.value = tab }
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearSyncError() { _syncError.value = null }

    fun toggleComplete(id: Long) {
        viewModelScope.launch {
            val item = repository.getById(id) ?: return@launch
            repository.toggleComplete(id)

            if (!item.isCompleted) {
                // Going incomplete → complete
                alarmScheduler.cancel(id)
                // If recurring, create the next occurrence
                if (item.recurrence != Recurrence.NONE) {
                    createNextOccurrence(item)
                }
            } else {
                // Going complete → incomplete → reschedule alarm
                alarmScheduler.schedule(item.copy(isCompleted = false))
            }
            triggerSync()
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            alarmScheduler.cancel(id)
            repository.delete(id)
            triggerSync()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            syncManager.syncNow()
            _isSyncing.value = false
        }
    }

    fun handleSignInResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                authManager.onSignInSuccess(account)
                syncManager.schedulePeriodicSync()
                triggerSync()
            } catch (e: ApiException) {
                _syncError.value = "Sign-in failed: ${e.statusCode}"
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        syncManager.cancelAll()
    }

    fun getSignInIntent(): Intent = authManager.signInClient.signInIntent

    private fun triggerSync() {
        if (authManager.isSignedIn) syncManager.syncNow()
    }

    private suspend fun createNextOccurrence(item: TodoItem) {
        val base = item.startDate ?: item.dueDate ?: return
        val nextDate: LocalDateTime = when (item.recurrence) {
            Recurrence.DAILY -> base.plusDays(1)
            Recurrence.WEEKLY -> base.plusWeeks(1)
            Recurrence.MONTHLY -> base.plusMonths(1)
            Recurrence.NONE -> return
        }
        val next = item.copy(
            id = 0,
            isCompleted = false,
            googleId = null,
            dueDate = if (item.startDate == null) nextDate else item.dueDate?.plusDays(
                when (item.recurrence) {
                    Recurrence.DAILY -> 1
                    Recurrence.WEEKLY -> 7
                    Recurrence.MONTHLY -> 30
                    else -> 0
                }
            ),
            startDate = item.startDate?.let {
                when (item.recurrence) {
                    Recurrence.DAILY -> it.plusDays(1)
                    Recurrence.WEEKLY -> it.plusWeeks(1)
                    Recurrence.MONTHLY -> it.plusMonths(1)
                    else -> it
                }
            },
            endDate = item.endDate?.let {
                when (item.recurrence) {
                    Recurrence.DAILY -> it.plusDays(1)
                    Recurrence.WEEKLY -> it.plusWeeks(1)
                    Recurrence.MONTHLY -> it.plusMonths(1)
                    else -> it
                }
            },
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val savedId = repository.save(next)
        alarmScheduler.schedule(next.copy(id = savedId))
    }
}
