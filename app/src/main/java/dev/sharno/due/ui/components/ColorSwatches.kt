package dev.sharno.due.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Ready-made seeds, spread around the hue circle so every one produces a distinct scheme. */
internal val PRESET_SEEDS: List<Int> = listOf(
    0xFFE65100, // orange (the historical accent)
    0xFFD32F2F, // red
    0xFFC2185B, // pink
    0xFF7B1FA2, // purple
    0xFF4527A0, // deep indigo
    0xFF1565C0, // blue
    0xFF00838F, // teal
    0xFF2E7D32, // green
    0xFF9E9D24, // olive
    0xFF5D4037, // brown
).map { it.toInt() }

@Composable
internal fun SwatchDot(color: Color, selected: Boolean, size: Int = 28) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            ),
    )
}

@Composable
internal fun ColorSwatchRow(
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
    swatches: List<Int> = PRESET_SEEDS,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        swatches.forEach { argb ->
            Box(modifier = Modifier.clickable { onSelect(argb) }) {
                SwatchDot(
                    color = Color(argb),
                    selected = argb == selectedArgb,
                    size = 40,
                )
            }
        }
    }
}

internal fun Color.argb(): Int = toArgb()
