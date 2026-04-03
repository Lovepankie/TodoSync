package com.todoapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.todoapp.domain.model.TodoItem
import com.todoapp.domain.model.TodoType
import com.todoapp.ui.components.TodoItemCard
import com.todoapp.ui.viewmodel.FilterTab
import com.todoapp.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun TodoItem.groupDate(): LocalDate? = (startDate ?: dueDate)?.toLocalDate()

private fun List<TodoItem>.groupedByDate(): List<Pair<LocalDate?, List<TodoItem>>> =
    groupBy { it.groupDate() }
        .entries
        .sortedWith(compareBy(nullsLast()) { it.key })
        .map { it.key to it.value }

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAddItem: (TodoType) -> Unit,
    onEditItem: (Long) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showFab by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show sync error snackbar
    LaunchedEffect(state.syncError) {
        state.syncError?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.clearSyncError()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.handleSignInResult(result) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showSearch) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = { showSearch = false; viewModel.setSearchQuery("") }
                )
            } else {
                TopAppBar(
                    title = { Text("TodoSync") },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { viewModel.syncNow() }) {
                                Icon(Icons.Default.Sync, "Sync")
                            }
                        }
                        if (state.isSignedIn) {
                            IconButton(onClick = { viewModel.signOut() }) {
                                Icon(Icons.Default.Logout, "Sign out")
                            }
                        } else {
                            IconButton(onClick = {
                                signInLauncher.launch(viewModel.getSignInIntent())
                            }) {
                                Icon(Icons.Default.Login, "Sign in with Google")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showFab) {
                    FabOption("Event", Icons.Default.Event) {
                        showFab = false; onAddItem(TodoType.EVENT)
                    }
                    Spacer(Modifier.height(8.dp))
                    FabOption("Reminder", Icons.Default.Notifications) {
                        showFab = false; onAddItem(TodoType.REMINDER)
                    }
                    Spacer(Modifier.height(8.dp))
                    FabOption("Task", Icons.Default.CheckCircle) {
                        showFab = false; onAddItem(TodoType.TASK)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FloatingActionButton(onClick = { showFab = !showFab }) {
                    Icon(
                        if (showFab) Icons.Default.Close else Icons.Default.Add,
                        "Add item"
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Account banner
            if (state.isSignedIn) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(state.userEmail ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.weight(1f))
                        Text("Syncing to Google",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in to sync with Google Calendar & Gmail",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            signInLauncher.launch(viewModel.getSignInIntent())
                        }) { Text("Sign In") }
                    }
                }
            }

            // Filter tabs with count badges
            ScrollableTabRow(
                selectedTabIndex = FilterTab.entries.indexOf(state.activeFilter),
                edgePadding = 16.dp,
                divider = {}
            ) {
                FilterTab.entries.forEach { tab ->
                    val label = when (tab) {
                        FilterTab.ALL -> "All"
                        FilterTab.TASKS -> "Tasks"
                        FilterTab.EVENTS -> "Events"
                        FilterTab.REMINDERS -> "Reminders"
                        FilterTab.COMPLETED -> "Done"
                    }
                    val count = state.tabCounts[tab] ?: 0
                    Tab(
                        selected = state.activeFilter == tab,
                        onClick = { viewModel.setFilter(tab) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(label)
                                if (count > 0) {
                                    Badge { Text(count.toString()) }
                                }
                            }
                        }
                    )
                }
            }

            // Grouped list
            val grouped = remember(state.items) { state.items.groupedByDate() }

            if (grouped.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            when {
                                state.searchQuery.isNotBlank() -> "No results for \"${state.searchQuery}\""
                                state.activeFilter == FilterTab.COMPLETED -> "Nothing completed yet."
                                else -> "No items yet. Tap + to add one."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    grouped.forEach { (date, items) ->
                        item(key = "header_${date}") { DateHeader(date) }
                        items(items, key = { it.id }) { item ->
                            TodoItemCard(
                                item = item,
                                onToggleComplete = { viewModel.toggleComplete(item.id) },
                                onEdit = { onEditItem(item.id) },
                                onDelete = { viewModel.deleteItem(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search tasks, events, reminders…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = if (query.isNotBlank()) {
                    { IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, "Clear") } }
                } else null
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Close search") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
private fun DateHeader(date: LocalDate?) {
    val label = when {
        date == null -> "No Date"
        date == LocalDate.now() -> "Today  \u2022  ${date.format(DAY_FORMATTER)}"
        date == LocalDate.now().plusDays(1) -> "Tomorrow  \u2022  ${date.format(DAY_FORMATTER)}"
        date == LocalDate.now().minusDays(1) -> "Yesterday  \u2022  ${date.format(DAY_FORMATTER)}"
        else -> date.format(DAY_FORMATTER)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FabOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(8.dp))
        SmallFloatingActionButton(onClick = onClick) { Icon(icon, label) }
    }
}
