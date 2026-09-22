package dev.sharno.due.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sharno.due.RecurrenceEnd
import dev.sharno.due.Skill
import dev.sharno.due.Todo
import dev.sharno.due.ui.components.SwatchDot

/** The inline panel revealed by tapping a task row. */
@Composable
internal fun TodoDetailsPanel(
    todo: Todo,
    skills: List<Skill>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()

        DetailLine("Due", formatDueAtLong(todo.dueAtMillis))

        todo.recurrence?.let { rule ->
            DetailLine("Repeats", recurrenceSummary(rule))
            val end = rule.end
            if (end is RecurrenceEnd.After) {
                DetailLine("Progress", "${todo.occurrencesCompleted} of ${end.occurrences} done")
            }
        }

        if (skills.isEmpty()) {
            Text(
                "No skills attached. Add one to rate yourself when this task is completed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Skills",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                skills.forEach { skill ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SwatchDot(color = Color(skill.colorArgb), selected = false, size = 12)
                        Text(skill.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
