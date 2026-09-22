package dev.sharno.due.ui.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sharno.due.PendingRating
import dev.sharno.due.Skill
import dev.sharno.due.ui.components.SwatchDot
import kotlin.math.roundToInt

/**
 * Rates each skill attached to a task that was just completed.
 *
 * A bottom sheet rather than the app's usual AlertDialog for one reason: its dismissal — scrim tap,
 * back gesture, drag down — maps exactly onto "skip", with no extra wiring. The task is already
 * complete by the time this appears, so every way out of it is safe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatingSheet(
    pending: PendingRating,
    onSkip: () -> Unit,
    onSave: (Map<String, Int>) -> Unit,
) {
    var ratings by remember(pending.completionId) { mutableStateOf(pending.initialRatings) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onSkip, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("How did it go?", style = MaterialTheme.typography.headlineSmall)
            Text(
                pending.taskTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A plain Column, not a LazyColumn: a task carries a handful of skills, and a nested
            // lazy list inside a bottom sheet fights the sheet's own drag.
            pending.skills.forEach { skill ->
                SkillRatingSlider(
                    skill = skill,
                    rating = ratings[skill.id] ?: DEFAULT_RATING,
                    onRatingChange = { ratings = ratings + (skill.id to it) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
                Button(onClick = { onSave(ratings) }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SkillRatingSlider(skill: Skill, rating: Int, onRatingChange: (Int) -> Unit) {
    val color = Color(skill.colorArgb)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwatchDot(color = color, selected = false, size = 14)
            Text(
                skill.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text("$rating", style = MaterialTheme.typography.titleLarge, color = color)
        }
        Slider(
            value = rating.toFloat(),
            onValueChange = { onRatingChange(it.roundToInt()) },
            valueRange = 1f..10f,
            // `steps` counts the stops *between* the ends, so ten integer positions means eight.
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "${skill.name} rating, $rating out of 10" },
        )
    }
}

private const val DEFAULT_RATING = 5
