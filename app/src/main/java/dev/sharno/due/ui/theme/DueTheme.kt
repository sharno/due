package dev.sharno.due.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.sharno.due.ThemeMode
import dev.sharno.due.ThemeSettings

@Composable
internal fun DueTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = remember(settings.seedArgb, dark) {
        dueColorScheme(settings.seedArgb, dark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(colorScheme, dark) {
            val window = (view.context as Activity).window
            // values-night/ only follows the *system* setting, so an in-app Dark override on a light
            // system would otherwise show a light window behind the app and in the recents snapshot.
            window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            onDispose { }
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
