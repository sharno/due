package dev.sharno.due.ui.tasks

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sharno.due.R
import dev.sharno.due.TaskFilter
import dev.sharno.due.Skill
import dev.sharno.due.Todo
import dev.sharno.due.statusAt
import dev.sharno.due.ui.canScheduleExactAlarms
import dev.sharno.due.ui.components.rememberNowMillis
import dev.sharno.due.ui.exactAlarmsSettingsIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksScreen(
    todos: List<Todo>,
    filter: TaskFilter,
    skillsByTodoId: Map<String, List<Skill>>,
    onFilterChange: (TaskFilter) -> Unit,
    onCompleteChanged: (Todo, Boolean) -> Unit,
    onRequestDelete: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    onAddTodo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenStats: () -> Unit,
    unratedSessions: Int,
    onRateUnrated: () -> Unit,
) {
    val context = LocalContext.current
    val nowMillis by rememberNowMillis()
    // Hoisted so expansion survives scrolling a row off-screen, a filter change and process death.
    var menuOpen by remember { mutableStateOf(false) }
    var expandedIds by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet<String>()) }

    // Re-filtering every tick would be wasteful; a task's bucket can only change once a minute.
    val counts = remember(todos, nowMillis / 60_000L) {
        TaskFilter.entries.associateWith { candidate ->
            todos.count { candidate.matches(it.statusAt(nowMillis)) }
        }
    }
    val visibleTodos = remember(todos, filter, nowMillis / 60_000L) {
        todos.filter { filter.matches(it.statusAt(nowMillis)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Due") },
                actions = {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !canScheduleExactAlarms(context)
                    ) {
                        TextButton(onClick = { context.startActivity(exactAlarmsSettingsIntent()) }) {
                            Text("Enable exact reminders")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = "More",
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Skills") },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_skills), contentDescription = null)
                                },
                                onClick = { menuOpen = false; onOpenSkills() },
                            )
                            DropdownMenuItem(
                                text = { Text("Skill stats") },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_stats), contentDescription = null)
                                },
                                onClick = { menuOpen = false; onOpenStats() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                                },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTodo) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Add todo",
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (unratedSessions > 0) {
                UnratedSessionsBanner(count = unratedSessions, onClick = onRateUnrated)
            }
            TaskFilterChips(
                selected = filter,
                counts = counts,
                onFilterChange = onFilterChange,
            )
            if (visibleTodos.isEmpty()) {
                EmptyTodos(
                    filter = filter,
                    hasAnyTodos = todos.isNotEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TodoList(
                    todos = visibleTodos,
                    nowMillis = nowMillis,
                    expandedIds = expandedIds,
                    skillsByTodoId = skillsByTodoId,
                    onToggleExpanded = { todo ->
                        expandedIds = if (todo.id in expandedIds) {
                            expandedIds - todo.id
                        } else {
                            expandedIds + todo.id
                        }
                    },
                    onCompleteChanged = onCompleteChanged,
                    onRequestDelete = onRequestDelete,
                    onEdit = onEdit,
                )
            }
        }
    }
}

/** Sessions completed from the notification never saw a rating sheet; this is where they resurface. */
@Composable
private fun UnratedSessionsBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                if (count == 1) "1 session waiting to be rated" else "$count sessions waiting to be rated",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "Completed from a notification. Tap to rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TaskFilterChips(
    selected: TaskFilter,
    counts: Map<TaskFilter, Int>,
    onFilterChange: (TaskFilter) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskFilter.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == selected,
                onClick = { onFilterChange(candidate) },
                label = { Text("${candidate.label} (${counts[candidate] ?: 0})") },
            )
        }
    }
}

@Composable
private fun EmptyTodos(
    filter: TaskFilter,
    hasAnyTodos: Boolean,
    modifier: Modifier = Modifier,
) {
    val (headline, body) = when {
        !hasAnyTodos -> "Nothing due" to "Add a task. Its date and time start as today and now."
        filter == TaskFilter.TODO ->
            "All clear" to "Nothing left to do. Switch to All to see completed tasks."
        filter == TaskFilter.OVERDUE ->
            "Nothing overdue" to "Everything with a past due date is done."
        filter == TaskFilter.DONE ->
            "Nothing completed yet" to "Completed tasks collect here."
        else -> "Nothing due" to "Add a task. Its date and time start as today and now."
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
