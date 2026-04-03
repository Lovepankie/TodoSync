package com.todoapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.todoapp.domain.model.Priority
import com.todoapp.domain.model.SyncStatus
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import com.todoapp.ui.theme.PriorityHigh
import com.todoapp.ui.theme.PriorityLow
import com.todoapp.ui.theme.PriorityMedium
import com.todoapp.ui.theme.TypeEvent
import com.todoapp.ui.theme.TypeReminder
import com.todoapp.ui.theme.TypeTask
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoItemCard(
    item: TodoItem,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val typeColor = when (item.type) {
        TodoType.TASK -> TypeTask
        TodoType.EVENT -> TypeEvent
        TodoType.REMINDER -> TypeReminder
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onEdit,
        border = BorderStroke(1.dp, typeColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Completion checkbox
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggleComplete() },
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type chip
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = item.type.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        icon = { Icon(typeIcon(item.type), null, Modifier.size(14.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = typeColor.copy(alpha = 0.12f),
                            labelColor = typeColor,
                            iconContentColor = typeColor
                        ),
                        modifier = Modifier.height(24.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    // Priority dot
                    val priorityColor = when (item.priority) {
                        Priority.HIGH -> PriorityHigh
                        Priority.MEDIUM -> PriorityMedium
                        Priority.LOW -> PriorityLow
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = priorityColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = item.priority.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Sync indicator
                    when (item.syncStatus) {
                        SyncStatus.PENDING -> Icon(
                            Icons.Default.Sync, "Pending sync",
                            Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline
                        )
                        SyncStatus.SYNCED -> Icon(
                            Icons.Default.Cloud, "Synced",
                            Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary
                        )
                        SyncStatus.FAILED -> Icon(
                            Icons.Default.CloudOff, "Sync failed",
                            Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error
                        )
                        else -> {}
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                // Description
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Due/start date
                val dateText = when {
                    item.startDate != null -> "Starts: ${item.startDate.format(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))}"
                    item.dueDate != null -> "Due: ${item.dueDate.format(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))}"
                    else -> null
                }
                dateText?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Delete button
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete item?") },
            text = { Text("\"${item.title}\" will also be removed from Google services.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun typeIcon(type: TodoType): ImageVector = when (type) {
    TodoType.TASK -> Icons.Default.CheckCircle
    TodoType.EVENT -> Icons.Default.Event
    TodoType.REMINDER -> Icons.Default.Notifications
}
