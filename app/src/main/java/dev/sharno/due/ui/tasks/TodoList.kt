package dev.sharno.due.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sharno.due.Skill
import dev.sharno.due.Todo

@Composable
internal fun TodoList(
    todos: List<Todo>,
    nowMillis: Long,
    expandedIds: Set<String>,
    skillsByTodoId: Map<String, List<Skill>>,
    onToggleExpanded: (Todo) -> Unit,
    onCompleteChanged: (Todo, Boolean) -> Unit,
    onRequestDelete: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Extra bottom padding so the floating action button never covers the last row.
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(todos, key = Todo::id) { todo ->
            TodoRow(
                todo = todo,
                nowMillis = nowMillis,
                expanded = todo.id in expandedIds,
                skills = skillsByTodoId[todo.id].orEmpty(),
                onToggleExpanded = { onToggleExpanded(todo) },
                onCompleteChanged = onCompleteChanged,
                onRequestDelete = onRequestDelete,
                onEdit = onEdit,
                // Keeps neighbours sliding when a row expands or the completed sort order changes.
                modifier = Modifier.animateItem(),
            )
        }
    }
}
