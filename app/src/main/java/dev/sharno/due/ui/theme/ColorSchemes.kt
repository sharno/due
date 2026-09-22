package dev.sharno.due.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Seeds below this chroma carry no usable hue, so the palette borrows one. */
private const val ACHROMATIC_CHROMA = 0.012f
private const val FALLBACK_HUE = 258f
private const val MIN_PRIMARY_CHROMA = 0.06f
private const val MAX_PRIMARY_CHROMA = 0.16f
private const val SECONDARY_CHROMA = 0.045f
private const val TERTIARY_HUE_SHIFT = 60f
private const val NEUTRAL_CHROMA = 0.005f
private const val NEUTRAL_VARIANT_CHROMA = 0.016f

/** Errors must stay recognisable even when the user seeds the app with red or green. */
private const val ERROR_HUE = 27.5f
private const val ERROR_CHROMA = 0.16f

/** A ladder of Material tones at one hue and chroma. */
private class TonalPalette(private val hue: Float, private val chroma: Float) {
    fun tone(tone: Int): Color = Color(
        OklchConverter.toArgb(
            Oklch(
                lightness = OklchConverter.toneToLightness(tone),
                chroma = chroma,
                hue = hue,
            ),
        ),
    )
}

/**
 * Builds a full Material 3 scheme from an arbitrary seed colour.
 *
 * The tone assignments are Material's own (light 40/100/90/10, dark 80/20/30/90). They are
 * contrast-correct at any hue because the ladder is perceptually uniform in Oklab — see
 * `ColorSchemesTest`, which asserts WCAG contrast for a grid of deliberately awkward seeds.
 */
internal fun dueColorScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val seed = OklchConverter.fromArgb(seedArgb)
    val hue = if (seed.chroma < ACHROMATIC_CHROMA) FALLBACK_HUE else seed.hue
    val primaryChroma = seed.chroma.coerceIn(MIN_PRIMARY_CHROMA, MAX_PRIMARY_CHROMA)

    val primary = TonalPalette(hue, primaryChroma)
    val secondary = TonalPalette(hue, SECONDARY_CHROMA)
    val tertiary = TonalPalette((hue + TERTIARY_HUE_SHIFT) % 360f, primaryChroma * 0.75f)
    val neutral = TonalPalette(hue, NEUTRAL_CHROMA)
    val neutralVariant = TonalPalette(hue, NEUTRAL_VARIANT_CHROMA)
    val error = TonalPalette(ERROR_HUE, ERROR_CHROMA)

    return if (dark) {
        darkColorScheme(
            primary = primary.tone(80),
            onPrimary = primary.tone(20),
            primaryContainer = primary.tone(30),
            onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondary.tone(80),
            onSecondary = secondary.tone(20),
            secondaryContainer = secondary.tone(30),
            onSecondaryContainer = secondary.tone(90),
            tertiary = tertiary.tone(80),
            onTertiary = tertiary.tone(20),
            tertiaryContainer = tertiary.tone(30),
            onTertiaryContainer = tertiary.tone(90),
            error = error.tone(80),
            onError = error.tone(20),
            errorContainer = error.tone(30),
            onErrorContainer = error.tone(90),
            background = neutral.tone(10),
            onBackground = neutral.tone(90),
            surface = neutral.tone(10),
            onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30),
            onSurfaceVariant = neutralVariant.tone(80),
            surfaceTint = primary.tone(80),
            inverseSurface = neutral.tone(90),
            inverseOnSurface = neutral.tone(20),
            outline = neutralVariant.tone(60),
            outlineVariant = neutralVariant.tone(30),
            scrim = neutral.tone(0),
            surfaceBright = neutral.tone(24),
            surfaceDim = neutral.tone(6),
            surfaceContainerLowest = neutral.tone(4),
            surfaceContainerLow = neutral.tone(10),
            surfaceContainer = neutral.tone(12),
            surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22),
        )
    } else {
        lightColorScheme(
            primary = primary.tone(40),
            onPrimary = primary.tone(100),
            primaryContainer = primary.tone(90),
            onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),
            secondary = secondary.tone(40),
            onSecondary = secondary.tone(100),
            secondaryContainer = secondary.tone(90),
            onSecondaryContainer = secondary.tone(10),
            tertiary = tertiary.tone(40),
            onTertiary = tertiary.tone(100),
            tertiaryContainer = tertiary.tone(90),
            onTertiaryContainer = tertiary.tone(10),
            error = error.tone(40),
            onError = error.tone(100),
            errorContainer = error.tone(90),
            onErrorContainer = error.tone(10),
            background = neutral.tone(99),
            onBackground = neutral.tone(10),
            surface = neutral.tone(99),
            onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(90),
            onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primary.tone(40),
            inverseSurface = neutral.tone(20),
            inverseOnSurface = neutral.tone(95),
            outline = neutralVariant.tone(50),
            outlineVariant = neutralVariant.tone(80),
            scrim = neutral.tone(0),
            surfaceBright = neutral.tone(98),
            surfaceDim = neutral.tone(87),
            surfaceContainerLowest = neutral.tone(100),
            surfaceContainerLow = neutral.tone(96),
            surfaceContainer = neutral.tone(94),
            surfaceContainerHigh = neutral.tone(92),
            surfaceContainerHighest = neutral.tone(90),
        )
    }
}
