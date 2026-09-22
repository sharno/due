package dev.sharno.due.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.sharno.due.R
import dev.sharno.due.Skill
import dev.sharno.due.Todo
import dev.sharno.due.TodoStatus
import dev.sharno.due.statusAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoRow(
    todo: Todo,
    nowMillis: Long,
    expanded: Boolean,
    skills: List<Skill>,
    onToggleExpanded: () -> Unit,
    onCompleteChanged: (Todo, Boolean) -> Unit,
    onRequestDelete: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = todo.statusAt(nowMillis)
    val overdue = status == TodoStatus.OVERDUE

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.45f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onRequestDelete(todo)
                SwipeToDismissBoxValue.StartToEnd -> onEdit(todo)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Never settle into a dismissed state. The card springs back on its own and the dialog
            // decides what happens, which sidesteps the usual "row stuck off-screen after cancel"
            // problem entirely — there is no swipe state left to reset.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
    ) {
        Card(
            onClick = onToggleExpanded,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit ${todo.title}") { onEdit(todo); true },
                        CustomAccessibilityAction("Delete ${todo.title}") { onRequestDelete(todo); true },
                    )
                },
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = todo.completed,
                        onCheckedChange = { checked -> onCompleteChanged(todo, checked) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = todo.title,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = todoStatus(status, todo.dueAtMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        todo.recurrence?.let { recurrence ->
                            Text(
                                text = recurrenceSummary(recurrence),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "chevron",
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = if (expanded) "Collapse" else "Show details",
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    TodoDetailsPanel(
                        todo = todo,
                        skills = skills,
                        onEdit = { onEdit(todo) },
                        onDelete = { onRequestDelete(todo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeBackground(target: SwipeToDismissBoxValue) {
    val deleting = target == SwipeToDismissBoxValue.EndToStart
    val editing = target == SwipeToDismissBoxValue.StartToEnd
    val background = when {
        deleting -> MaterialTheme.colorScheme.errorContainer
        editing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                deleting -> {
                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                editing -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text("Edit", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }

                else -> Unit
            }
        }
    }
}

private fun todoStatus(status: TodoStatus, dueAtMillis: Long): String = when (status) {
    TodoStatus.DONE -> "Completed"
    TodoStatus.OVERDUE -> "Overdue · ${formatDueAt(dueAtMillis)}"
    TodoStatus.TODO -> "Due ${formatDueAt(dueAtMillis)}"
}
