package dev.sharno.due.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sharno.due.R
import dev.sharno.due.Skill
import dev.sharno.due.SkillStats
import dev.sharno.due.ui.components.DetailTopBar
import dev.sharno.due.ui.components.SwatchDot

@Composable
internal fun SkillsScreen(
    stats: List<SkillStats>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Skill) -> Unit,
    onArchive: (Skill) -> Unit,
    onRestore: (Skill) -> Unit,
    onRequestDelete: (SkillStats) -> Unit,
) {
    val active = stats.filterNot { it.skill.archived }.sortedBy { it.skill.name.lowercase() }
    val archived = stats.filter { it.skill.archived }.sortedBy { it.skill.name.lowercase() }

    Scaffold(
        topBar = { DetailTopBar("Skills", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = "New skill")
            }
        },
    ) { padding ->
        if (stats.isEmpty()) {
            EmptySkills(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(active, key = { it.skill.id }) { entry ->
                SkillRow(
                    stats = entry,
                    onEdit = { onEdit(entry.skill) },
                    onArchive = { onArchive(entry.skill) },
                    onRestore = { onRestore(entry.skill) },
                    onDelete = { onRequestDelete(entry) },
                )
            }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        "Archived",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(archived, key = { it.skill.id }) { entry ->
                    SkillRow(
                        stats = entry,
                        onEdit = { onEdit(entry.skill) },
                        onArchive = { onArchive(entry.skill) },
                        onRestore = { onRestore(entry.skill) },
                        onDelete = { onRequestDelete(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    stats: SkillStats,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwatchDot(color = Color(stats.skill.colorArgb), selected = false, size = 20)
            Column(modifier = Modifier.weight(1f)) {
                Text(stats.skill.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stats.summaryLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "More actions for ${stats.skill.name}",
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    if (stats.skill.archived) {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            onClick = { menuOpen = false; onRestore() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = { menuOpen = false; onArchive() },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text("Delete permanently", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun SkillStats.summaryLine(): String = buildString {
    if (ratedSessions == 0) {
        append("No ratings yet")
    } else {
        append("%.1f / 10".format(average))
        append(" · $ratedSessions session")
        if (ratedSessions != 1) append("s")
    }
    if (taskCount > 0) {
        append(" · $taskCount task")
        if (taskCount != 1) append("s")
    }
}

@Composable
private fun EmptySkills(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text("No skills yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Skills let you track how well a session went, not just whether you did it. " +
                    "Add one, attach it to a task, and rate yourself when you tick it off.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
