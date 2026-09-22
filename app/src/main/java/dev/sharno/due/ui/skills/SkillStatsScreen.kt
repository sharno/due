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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sharno.due.SkillStats
import dev.sharno.due.ui.components.DetailTopBar
import dev.sharno.due.ui.components.DropdownPicker
import dev.sharno.due.ui.components.SwatchDot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

internal enum class StatsSort(val label: String) {
    AVERAGE("Highest average"),
    SESSIONS("Most sessions"),
    RECENT("Recently practised"),
    NAME("Name"),
}

private val lastPractisedFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Below this, a change is noise rather than a trend. */
private const val STEADY_THRESHOLD = 0.3

@Composable
internal fun SkillStatsScreen(
    stats: List<SkillStats>,
    sort: StatsSort,
    onSortChange: (StatsSort) -> Unit,
    onBack: () -> Unit,
) {
    val visible = stats.filterNot { it.skill.archived && it.ratedSessions == 0 }.sortedWith(sort.comparator())

    Scaffold(topBar = { DetailTopBar("Skill stats", onBack) }) { padding ->
        if (visible.none { it.ratedSessions > 0 }) {
            EmptyStats(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DropdownPicker(
                    label = sort.label,
                    options = StatsSort.entries,
                    optionLabel = StatsSort::label,
                    onSelected = onSortChange,
                )
            }
            items(visible, key = { it.skill.id }) { entry -> SkillStatsCard(entry) }
        }
    }
}

@Composable
private fun SkillStatsCard(stats: SkillStats) {
    val color = Color(stats.skill.colorArgb)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SwatchDot(color = color, selected = false, size = 16)
                Text(
                    stats.skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TrendChip(stats.trend)
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            stats.average?.let { "%.1f".format(it) } ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            " / 10",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ((stats.average ?: 0.0) / 10.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = color,
                    )
                }
                if (stats.recent.size >= 2) {
                    TrendSparkline(
                        points = stats.recent,
                        lineColor = color,
                        modifier = Modifier.width(110.dp).height(52.dp),
                    )
                }
            }

            Text(
                stats.detailLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendChip(trend: Double?) {
    if (trend == null) return
    val (text, color) = when {
        abs(trend) < STEADY_THRESHOLD -> "Steady" to MaterialTheme.colorScheme.onSurfaceVariant
        trend > 0 -> "▲ +%.1f".format(trend) to MaterialTheme.colorScheme.primary
        else -> "▼ %.1f".format(trend) to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

private fun SkillStats.detailLine(): String = buildString {
    append("$ratedSessions rated session")
    if (ratedSessions != 1) append("s")
    if (unratedSessions > 0) append(" · $unratedSessions unrated")
    if (best != null && worst != null && ratedSessions > 1) append(" · best $best, worst $worst")
    lastPractisedAtMillis?.let {
        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        append(" · last ${date.format(lastPractisedFormatter)}")
    }
}

private fun StatsSort.comparator(): Comparator<SkillStats> = when (this) {
    StatsSort.AVERAGE -> compareByDescending { it.average ?: -1.0 }
    StatsSort.SESSIONS -> compareByDescending { it.ratedSessions }
    StatsSort.RECENT -> compareByDescending { it.lastPractisedAtMillis ?: Long.MIN_VALUE }
    StatsSort.NAME -> compareBy { it.skill.name.lowercase() }
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text("No ratings yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Attach a skill to a task, then rate yourself when you complete it. " +
                    "Averages and trends appear here once you have rated a session.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
