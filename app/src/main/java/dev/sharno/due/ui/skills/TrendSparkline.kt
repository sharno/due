package dev.sharno.due.ui.skills

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val MIN_RATING = 1f
private const val MAX_RATING = 10f

/**
 * Recent ratings as a line, with the skill's mean as a dashed baseline.
 *
 * Drawn on a fixed 1–10 domain rather than auto-scaled to the data. Auto-scaling would stretch a
 * skill that drifted from 6.9 to 7.1 into a dramatic climb, which is exactly the wrong story.
 */
@Composable
internal fun TrendSparkline(
    points: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.size < 2) return
    val average = remember(points) { points.average().toFloat() }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Trend across the last ${points.size} rated sessions"
        },
    ) {
        fun y(value: Float) = size.height * (1f - (value - MIN_RATING) / (MAX_RATING - MIN_RATING))
        val stepX = size.width / (points.size - 1)

        val baseline = y(average)
        drawLine(
            color = lineColor.copy(alpha = 0.3f),
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        val path = Path().apply {
            moveTo(0f, y(points.first().toFloat()))
            points.forEachIndexed { index, value ->
                if (index > 0) lineTo(index * stepX, y(value.toFloat()))
            }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = Offset(size.width, y(points.last().toFloat())),
        )
    }
}
