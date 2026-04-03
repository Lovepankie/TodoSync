package com.todoapp.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.todoapp.domain.model.Priority
import com.todoapp.domain.model.Recurrence
import com.todoapp.domain.model.TodoType
import com.todoapp.ui.viewmodel.AddEditViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    initialType: TodoType = TodoType.TASK,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Set initial type only on first composition (new item)
    LaunchedEffect(initialType) {
        if (state.title.isEmpty()) viewModel.onTypeChange(initialType)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.title.isEmpty() && !state.isSaved) "New ${state.type.displayName}" else "Edit Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = { viewModel.save() }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type selector
            Text("Type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(type.displayName) }
                    )
                }
            }

            // Title
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title *") },
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Priority selector
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(
                        selected = state.priority == p,
                        onClick = { viewModel.onPriorityChange(p) },
                        label = { Text(p.displayName) }
                    )
                }
            }

            // Date/time fields depend on type
            when (state.type) {
                TodoType.EVENT -> {
                    DateTimePickerField(
                        label = "Start Date & Time",
                        value = state.startDate,
                        onValueChange = viewModel::onStartDateChange
                    )
                    DateTimePickerField(
                        label = "End Date & Time",
                        value = state.endDate,
                        onValueChange = viewModel::onEndDateChange
                    )
                    OutlinedTextField(
                        value = state.location,
                        onValueChange = viewModel::onLocationChange,
                        label = { Text("Location (optional)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                else -> {
                    DateTimePickerField(
                        label = "Due Date & Time",
                        value = state.dueDate,
                        onValueChange = viewModel::onDueDateChange
                    )
                }
            }

            // Recurrence picker (only shown when a due/start date is set)
            if (state.dueDate != null || state.startDate != null) {
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Recurrence.entries.forEach { r ->
                        FilterChip(
                            selected = state.recurrence == r,
                            onClick = { viewModel.onRecurrenceChange(r) },
                            label = { Text(r.displayName) }
                        )
                    }
                }
            }

            // Help text about sync
            val syncHint = when (state.type) {
                TodoType.TASK -> "Will be added to Google Tasks"
                TodoType.EVENT -> "Will be added to Google Calendar"
                TodoType.REMINDER -> "Will be saved as a Gmail draft"
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Sync, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary)
                    Text(syncHint, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save ${state.type.displayName}")
            }
        }
    }
}

@Composable
private fun DateTimePickerField(
    label: String,
    value: LocalDateTime?,
    onValueChange: (LocalDateTime?) -> Unit
) {
    val context = LocalContext.current
    val displayText = value?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
        ?: "Not set"

    OutlinedTextField(
        value = displayText,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        leadingIcon = { Icon(Icons.Default.Schedule, null) },
        trailingIcon = {
            if (value != null) {
                IconButton(onClick = { onValueChange(null) }) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    // Trigger pickers on tap by overlaying a clickable surface
    val now = remember { Calendar.getInstance() }

    LaunchedEffect(Unit) { /* no-op — we handle clicks below */ }

    // Date + time picker chain
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance().apply {
                    value?.let {
                        set(it.year, it.monthValue - 1, it.dayOfMonth, it.hour, it.minute)
                    }
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onValueChange(LocalDateTime.of(year, month + 1, day, hour, minute))
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.EditCalendar, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Pick Date & Time")
        }
    }
}
