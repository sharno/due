package dev.sharno.due.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.sharno.due.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme lets the user pick any primary colour, so legibility cannot be checked by eye on a few
 * samples. These tests are the reason the palette is generated here instead of by a third-party
 * library: they prove every text-on-background pair clears WCAG contrast for deliberately awkward
 * seeds, in both light and dark.
 */
class ColorSchemesTest {
    private val seeds = mapOf(
        "black" to 0xFF000000.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "mid grey" to 0xFF808080.toInt(),
        "pure red" to 0xFFFF0000.toInt(),
        "pure yellow" to 0xFFFFFF00.toInt(),
        "pure green" to 0xFF00FF00.toInt(),
        "pure blue" to 0xFF0000FF.toInt(),
        "pure cyan" to 0xFF00FFFF.toInt(),
        "pure magenta" to 0xFFFF00FF.toInt(),
        "default orange" to ThemeSettings.DEFAULT_SEED_ARGB,
    )

    @Test
    fun textIsLegibleOnEverySurfaceForEverySeed() {
        forEachScheme { label, scheme ->
            scheme.assertContrast(label, "onPrimary", scheme.onPrimary, scheme.primary, 4.5f)
            scheme.assertContrast(label, "onSecondary", scheme.onSecondary, scheme.secondary, 4.5f)
            scheme.assertContrast(label, "onTertiary", scheme.onTertiary, scheme.tertiary, 4.5f)
            scheme.assertContrast(label, "onError", scheme.onError, scheme.error, 4.5f)
            scheme.assertContrast(
                label, "onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5f,
            )
            scheme.assertContrast(
                label, "onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5f,
            )
            scheme.assertContrast(
                label, "onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, 4.5f,
            )
            scheme.assertContrast(
                label, "onErrorContainer", scheme.onErrorContainer, scheme.errorContainer, 4.5f,
            )
            scheme.assertContrast(label, "onSurface", scheme.onSurface, scheme.surface, 4.5f)
            scheme.assertContrast(label, "onBackground", scheme.onBackground, scheme.background, 4.5f)
            scheme.assertContrast(
                label, "onSurfaceVariant on surface", scheme.onSurfaceVariant, scheme.surface, 4.5f,
            )
            scheme.assertContrast(
                label, "onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5f,
            )
            scheme.assertContrast(
                label, "onSurface on surfaceContainerHighest", scheme.onSurface, scheme.surfaceContainerHighest, 4.5f,
            )
            scheme.assertContrast(
                label, "inverseOnSurface", scheme.inverseOnSurface, scheme.inverseSurface, 4.5f,
            )
        }
    }

    @Test
    fun nonTextElementsClearTheThreeToOneThreshold() {
        forEachScheme { label, scheme ->
            scheme.assertContrast(label, "outline", scheme.outline, scheme.surface, 3f)
            scheme.assertContrast(label, "primary on surface", scheme.primary, scheme.surface, 3f)
            scheme.assertContrast(label, "error on surface", scheme.error, scheme.surface, 3f)
        }
    }

    @Test
    fun chromaticSeedsKeepTheirHue() {
        val chromatic = listOf("pure red", "pure yellow", "pure green", "pure blue", "default orange")
        for (name in chromatic) {
            val seed = seeds.getValue(name)
            val seedHue = OklchConverter.fromArgb(seed).hue
            for (dark in listOf(false, true)) {
                val primaryHue = OklchConverter.fromArgb(dueColorScheme(seed, dark).primary.argb()).hue
                val drift = OklchConverter.hueDistance(seedHue, primaryHue)
                assertTrue(
                    "$name (dark=$dark) drifted ${drift}deg from the seed hue",
                    drift < 10f,
                )
            }
        }
    }

    @Test
    fun achromaticSeedsStillProduceAnAccent() {
        for (name in listOf("black", "white", "mid grey")) {
            for (dark in listOf(false, true)) {
                val primary = dueColorScheme(seeds.getValue(name), dark).primary.argb()
                assertTrue(
                    "$name (dark=$dark) produced a colourless primary",
                    OklchConverter.fromArgb(primary).chroma > 0.03f,
                )
            }
        }
    }

    @Test
    fun darkSchemesAreDarkerThanLightOnes() {
        for ((name, seed) in seeds) {
            val light = OklchConverter.relativeLuminance(dueColorScheme(seed, dark = false).surface.argb())
            val dark = OklchConverter.relativeLuminance(dueColorScheme(seed, dark = true).surface.argb())
            assertTrue("$name: dark surface is not darker than light", dark < light)
            assertTrue("$name: light surface is not bright", light > 0.7f)
            assertTrue("$name: dark surface is not dim", dark < 0.1f)
        }
    }

    @Test
    fun errorStaysRedRegardlessOfTheSeed() {
        val redHue = OklchConverter.fromArgb(dueColorScheme(seeds.getValue("pure red"), false).error.argb()).hue
        val greenHue = OklchConverter.fromArgb(dueColorScheme(seeds.getValue("pure green"), false).error.argb()).hue
        assertEquals(redHue, greenHue, 0.5f)
    }

    @Test
    fun toneLadderIsMonotonic() {
        var previous = -1f
        for (tone in 0..100 step 5) {
            val luminance = OklchConverter.relativeLuminance(
                OklchConverter.toArgb(Oklch(OklchConverter.toneToLightness(tone), 0f, 0f)),
            )
            assertTrue("tone $tone is not lighter than the previous tone", luminance > previous)
            previous = luminance
        }
    }

    private fun forEachScheme(block: (String, ColorScheme) -> Unit) {
        for ((name, seed) in seeds) {
            for (dark in listOf(false, true)) {
                block("$name (dark=$dark)", dueColorScheme(seed, dark))
            }
        }
    }

    private fun ColorScheme.assertContrast(
        label: String,
        pair: String,
        foreground: Color,
        background: Color,
        minimum: Float,
    ) {
        val ratio = OklchConverter.contrastRatio(foreground.argb(), background.argb())
        assertTrue(
            "$label: $pair contrast is $ratio, below $minimum",
            ratio >= minimum,
        )
    }

    private fun Color.argb(): Int = toArgb()
}
