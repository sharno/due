package dev.sharno.due.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * A clock that recomposition can actually see.
 *
 * Reading `System.currentTimeMillis()` during composition is not reactive, so a task silently stayed
 * labelled "Due" after its time passed until something unrelated recomposed. With a status filter
 * that becomes visible: the task would never move into Overdue on its own.
 *
 * Ticks only while the screen is resumed, so it never wakes a backgrounded device.
 */
@Composable
internal fun rememberNowMillis(tickMillis: Long = 30_000L): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, tickMillis) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now.longValue = System.currentTimeMillis()
                delay(tickMillis)
            }
        }
    }
    return now
}
