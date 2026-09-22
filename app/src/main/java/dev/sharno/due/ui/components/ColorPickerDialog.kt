package dev.sharno.due.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val HUE_STOPS = (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) }
private val HEX = Regex("^#?[0-9a-fA-F]{6}$")

/**
 * A dependency-free HSV picker.
 *
 * Hue, saturation and value are the source of truth rather than a derived [Color]: round-tripping
 * through ARGB loses hue at saturation or value zero, which makes the marker jump to a corner as
 * soon as the user drags back out of black.
 *
 * The chosen colour is only reported on confirm. Persisting per drag would re-encrypt and rewrite
 * the automatic cloud backup on every frame.
 */
@Composable
internal fun ColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initial = remember(initialArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialArgb, it) }
    }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    var hexDraft by remember { mutableStateOf<String?>(null) }

    val current = Color.hsv(hue, saturation, value)
    val currentArgb = current.argb()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Primary colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SaturationValueSquare(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                        hexDraft = null
                    },
                )
                HueSlider(
                    hue = hue,
                    onHueChange = {
                        hue = it
                        hexDraft = null
                    },
                )
                ColorSwatchRow(
                    selectedArgb = currentArgb,
                    onSelect = { argb ->
                        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                        hexDraft = null
                    },
                )
                val hexText = hexDraft ?: "#%06X".format(currentArgb and 0xFFFFFF)
                val hexInvalid = hexDraft != null && !HEX.matches(hexDraft.orEmpty())
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexDraft = text
                        if (HEX.matches(text)) {
                            val parsed = text.removePrefix("#").toInt(16) or (0xFF shl 24)
                            val hsv = FloatArray(3).also {
                                android.graphics.Color.colorToHSV(parsed, it)
                            }
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hex") },
                    singleLine = true,
                    isError = hexInvalid,
                    supportingText = if (hexInvalid) {
                        { Text("Use six hex digits, for example #E65100") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb) }) { Text("Use colour") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset -> reportSquare(offset, size.width, size.height, onChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> reportSquare(offset, size.width, size.height, onChange) },
                ) { change, _ ->
                    change.consume()
                    reportSquare(change.position, size.width, size.height, onChange)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val marker = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = marker, style = Stroke(2.5.dp.toPx()))
        drawCircle(Color.Black, radius = 11.dp.toPx(), center = marker, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset -> onHueChange(hueAt(offset.x, size.width)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange(hueAt(change.position.x, size.width))
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(HUE_STOPS))
        val x = (hue / 360f) * size.width
        drawLine(
            color = Color.White,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 4.dp.toPx(),
        )
    }
}

private fun reportSquare(
    offset: Offset,
    width: Int,
    height: Int,
    onChange: (Float, Float) -> Unit,
) {
    if (width == 0 || height == 0) return
    onChange(
        (offset.x / width).coerceIn(0f, 1f),
        (1f - offset.y / height).coerceIn(0f, 1f),
    )
}

private fun hueAt(x: Float, width: Int): Float =
    if (width == 0) 0f else ((x / width).coerceIn(0f, 1f) * 360f).coerceIn(0f, 359.999f)
