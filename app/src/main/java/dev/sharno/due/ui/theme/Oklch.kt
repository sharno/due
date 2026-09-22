package dev.sharno.due.ui.theme

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A colour in the Oklch space: [lightness] 0..1, [chroma] roughly 0..0.4, [hue] in degrees.
 *
 * Oklab is perceptually uniform, so a fixed ladder of lightness values keeps the same apparent
 * contrast at every hue. Plain HSL does not: pure yellow and pure blue at "50% lightness" differ in
 * luminance by about ten times, which is what makes naive theme generators produce unreadable
 * yellow buttons.
 *
 * Deliberately free of Android imports so the scheme maths can be unit-tested on the JVM.
 */
internal data class Oklch(val lightness: Float, val chroma: Float, val hue: Float)

internal object OklchConverter {
    /** Material tones are CIE L*; anything below this uses the linear branch of the L* curve. */
    private const val LSTAR_LINEAR_LIMIT = 8f
    private const val LSTAR_LINEAR_SLOPE = 903.3f
    private const val GAMUT_STEPS = 48
    private const val GAMUT_CHROMA_DECAY = 0.94f

    fun fromArgb(argb: Int): Oklch {
        val r = linearize(red(argb))
        val g = linearize(green(argb))
        val b = linearize(blue(argb))

        val lRoot = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val mRoot = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val sRoot = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

        val lightness = 0.2104542553f * lRoot + 0.7936177850f * mRoot - 0.0040720468f * sRoot
        val a = 1.9779984951f * lRoot - 2.4285922050f * mRoot + 0.4505937099f * sRoot
        val bAxis = 0.0259040371f * lRoot + 0.7827717662f * mRoot - 0.8086757660f * sRoot

        val hue = Math.toDegrees(atan2(bAxis, a).toDouble()).toFloat()
        return Oklch(
            lightness = lightness,
            chroma = sqrt(a * a + bAxis * bAxis),
            hue = if (hue < 0f) hue + 360f else hue,
        )
    }

    /**
     * Converts back to sRGB, reducing chroma at constant lightness and hue until the colour fits in
     * the gamut. That is what lets an extreme seed desaturate gracefully instead of clipping into a
     * different hue.
     */
    fun toArgb(color: Oklch): Int {
        var chroma = color.chroma
        repeat(GAMUT_STEPS) {
            val candidate = toLinearRgb(color.lightness, chroma, color.hue)
            if (candidate.all { it >= -0.0001f && it <= 1.0001f }) return pack(candidate)
            chroma *= GAMUT_CHROMA_DECAY
        }
        return pack(toLinearRgb(color.lightness, 0f, color.hue))
    }

    /** Maps a Material tone (CIE L*, 0..100) onto an Oklab lightness. */
    fun toneToLightness(tone: Int): Float {
        val lStar = tone.toFloat().coerceIn(0f, 100f)
        val y = if (lStar > LSTAR_LINEAR_LIMIT) {
            ((lStar + 16f) / 116f).pow(3)
        } else {
            lStar / LSTAR_LINEAR_SLOPE
        }
        // For a neutral colour the Oklab lightness is simply the cube root of relative luminance.
        return cbrt(y)
    }

    /** WCAG 2.1 relative luminance. */
    fun relativeLuminance(argb: Int): Float =
        0.2126f * linearize(red(argb)) +
            0.7152f * linearize(green(argb)) +
            0.0722f * linearize(blue(argb))

    /** WCAG 2.1 contrast ratio, always >= 1. */
    fun contrastRatio(argb: Int, otherArgb: Int): Float {
        val first = relativeLuminance(argb)
        val second = relativeLuminance(otherArgb)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Smallest absolute difference between two hues, accounting for the wrap at 360 degrees. */
    fun hueDistance(hue: Float, otherHue: Float): Float {
        val difference = abs(hue - otherHue) % 360f
        return if (difference > 180f) 360f - difference else difference
    }

    private fun toLinearRgb(lightness: Float, chroma: Float, hue: Float): FloatArray {
        val radians = Math.toRadians(hue.toDouble())
        val a = chroma * cos(radians).toFloat()
        val b = chroma * sin(radians).toFloat()

        val lRoot = lightness + 0.3963377774f * a + 0.2158037573f * b
        val mRoot = lightness - 0.1055613458f * a - 0.0638541728f * b
        val sRoot = lightness - 0.0894841775f * a - 1.2914855480f * b

        val l = lRoot * lRoot * lRoot
        val m = mRoot * mRoot * mRoot
        val s = sRoot * sRoot * sRoot

        return floatArrayOf(
            4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
        )
    }

    private fun pack(linear: FloatArray): Int {
        val r = channel(linear[0])
        val g = channel(linear[1])
        val b = channel(linear[2])
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(linear: Float): Int =
        (delinearize(linear.coerceIn(0f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)

    private fun red(argb: Int) = ((argb shr 16) and 0xFF) / 255f

    private fun green(argb: Int) = ((argb shr 8) and 0xFF) / 255f

    private fun blue(argb: Int) = (argb and 0xFF) / 255f

    private fun linearize(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    private fun delinearize(channel: Float): Float =
        if (channel <= 0.0031308f) channel * 12.92f else 1.055f * channel.pow(1f / 2.4f) - 0.055f
}
