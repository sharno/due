package dev.sharno.due.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.sharno.due.R
import dev.sharno.due.Skill
import dev.sharno.due.ui.components.SwatchDot

/** Picks which skills a task tracks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillPicker(
    skills: List<Skill>,
    selectedIds: Set<String>,
    onToggle: (Skill) -> Unit,
    onCreateSkill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        skills.forEach { skill ->
            FilterChip(
                selected = skill.id in selectedIds,
                onClick = { onToggle(skill) },
                label = { Text(skill.name) },
                leadingIcon = { SwatchDot(color = Color(skill.colorArgb), selected = false, size = 12) },
            )
        }
        AssistChip(
            onClick = onCreateSkill,
            label = { Text("New skill") },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillChipRow(skills: List<Skill>, modifier: Modifier = Modifier) {
    if (skills.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        skills.forEach { skill ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(skill.name, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { SwatchDot(color = Color(skill.colorArgb), selected = false, size = 10) },
            )
        }
    }
}
