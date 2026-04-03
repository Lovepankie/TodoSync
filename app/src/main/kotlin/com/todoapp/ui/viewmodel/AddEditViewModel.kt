package com.todoapp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoapp.domain.model.Priority
import com.todoapp.domain.model.Recurrence
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import com.todoapp.alarm.AlarmScheduler
import com.todoapp.domain.repository.TodoRepository
import com.todoapp.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class AddEditUiState(
    val title: String = "",
    val description: String = "",
    val type: TodoType = TodoType.TASK,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDateTime? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val location: String = "",
    val recurrence: Recurrence = Recurrence.NONE,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val syncManager: SyncManager,
    private val alarmScheduler: AlarmScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: Long = savedStateHandle["itemId"] ?: -1L

    private val _state = MutableStateFlow(AddEditUiState())
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    init {
        if (itemId > 0) loadExistingItem()
    }

    private fun loadExistingItem() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val item = repository.getById(itemId)
            if (item != null) {
                _state.value = AddEditUiState(
                    title = item.title,
                    description = item.description,
                    type = item.type,
                    priority = item.priority,
                    dueDate = item.dueDate,
                    startDate = item.startDate,
                    endDate = item.endDate,
                    location = item.location ?: "",
                    recurrence = item.recurrence
                )
            }
        }
    }

    fun onTitleChange(v: String) { _state.value = _state.value.copy(title = v, error = null) }
    fun onDescriptionChange(v: String) { _state.value = _state.value.copy(description = v) }
    fun onTypeChange(v: TodoType) { _state.value = _state.value.copy(type = v) }
    fun onPriorityChange(v: Priority) { _state.value = _state.value.copy(priority = v) }
    fun onDueDateChange(v: LocalDateTime?) { _state.value = _state.value.copy(dueDate = v) }
    fun onStartDateChange(v: LocalDateTime?) { _state.value = _state.value.copy(startDate = v) }
    fun onEndDateChange(v: LocalDateTime?) { _state.value = _state.value.copy(endDate = v) }
    fun onLocationChange(v: String) { _state.value = _state.value.copy(location = v) }
    fun onRecurrenceChange(v: Recurrence) { _state.value = _state.value.copy(recurrence = v) }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.value = s.copy(error = "Title cannot be empty")
            return
        }

        viewModelScope.launch {
            val item = TodoItem(
                id = if (itemId > 0) itemId else 0,
                title = s.title.trim(),
                description = s.description.trim(),
                type = s.type,
                priority = s.priority,
                dueDate = s.dueDate,
                startDate = s.startDate,
                endDate = s.endDate,
                location = s.location.trim().takeIf { it.isNotBlank() },
                recurrence = s.recurrence
            )
            val savedId = repository.save(item)
            // Cancel any old alarm for this item then schedule the new one
            alarmScheduler.cancel(savedId)
            alarmScheduler.schedule(item.copy(id = savedId))
            syncManager.syncNow()
            _state.value = _state.value.copy(isSaved = true)
        }
    }
}
