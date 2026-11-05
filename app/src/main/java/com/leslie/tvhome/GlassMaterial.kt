package com.leslie.tvhome

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A small, cheap read of the colours behind a glass surface. It is sampled once
 * when the backdrop or layout changes, never per frame.
 */
internal data class GlassTone(
    val luminance: Float,
    val complexity: Float,
    /** How colourful the backdrop is, 0 = grey, 1 = fully saturated. */
    val chroma: Float,
    val light: Boolean,
    val tint: Int,
    /** Opaque average colour behind the glass, used only to season the ink. */
    val average: Int,
    val ink: Int,
    val secondaryInk: Int,
    val darkTheme: Boolean = false,
    val tintScale: Float = 1f,
) {
    companion object {
        /** Dense and light-on-dark when sampling is unavailable, as the reference specifies. */
        val Fallback = GlassMaterial.tone(0f, 1f)
    }
}

internal object GlassMaterial {
    const val LIGHT_THRESHOLD = 0.5f

    private const val LIGHT_TINT = 0xffffffff.toInt()
    private const val DARK_TINT = 0xff141418.toInt()

    /**
     * Samples an inset grid inside [left, top, right, bottom]. Luminance chooses
     * light or dark ink; closeness, texture and colour form the tint density.
     */
    fun analyze(
        pixels: IntArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        previous: GlassTone? = null,
        darkTheme: Boolean = false,
        tintScale: Float = 1f,
    ): GlassTone {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return GlassTone.Fallback
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = right.coerceIn(l + 1, width)
        val b = bottom.coerceIn(t + 1, height)
        val insetX = max(1, (r - l) / 12)
        val insetY = max(1, (b - t) / 12)
        val x0 = (l + insetX).coerceAtMost(r - 1)
        val x1 = (r - insetX).coerceAtLeast(x0 + 1)
        val y0 = (t + insetY).coerceAtMost(b - 1)
        val y1 = (b - insetY).coerceAtLeast(y0 + 1)
        val xs = 5
        val ys = 3
        val samples = ArrayList<Int>(xs * ys)
        for (row in 0 until ys) {
            val y = y0 + ((y1 - y0 - 1) * row / max(1, ys - 1))
            for (col in 0 until xs) {
                val x = x0 + ((x1 - x0 - 1) * col / max(1, xs - 1))
                samples += pixels[y * width + x]
            }
        }
        // Drop two darkest and two brightest points before averaging: one bright
        // window or a black corner should not decide the whole surface's ink.
        val order = samples.indices.sortedBy { luminance(samples[it]) }
        val trim = if (samples.size > 6) 2 else 0
        val kept = order.subList(trim, samples.size - trim)
        var sum = 0f
        var sumR = 0
        var sumG = 0
        var sumB = 0
        var chroma = 0f
        var minLuma = 1f
        var maxLuma = 0f
        for (index in kept) {
            val color = samples[index]
            val luma = luminance(color)
            sum += luma
            sumR += red(color)
            sumG += green(color)
            sumB += blue(color)
        }
        for (color in samples) {
            val luma = luminance(color)
            minLuma = min(minLuma, luma)
            maxLuma = max(maxLuma, luma)
            val high = max(red(color), max(green(color), blue(color)))
            val low = min(red(color), min(green(color), blue(color)))
            chroma += (high - low) / 255f
        }
        chroma /= samples.size
        val mean = sum / kept.size
        val texture = (maxLuma - minLuma).coerceIn(0f, 1f)
        // The reference's density: background close to the ink's own luminance,
        // plus visual texture and colour. Smooth grey glass stays thin; busy
        // photographic areas move towards the dense end of the tint range.
        val inkLuma = if (!darkTheme && mean >= LIGHT_THRESHOLD) 0.12f else 0.94f
        val closeness = (1f - abs(mean - inkLuma)).coerceIn(0f, 1f)
        val density = (0.55f * closeness + 0.7f * texture + 0.3f * chroma).coerceIn(0f, 1f)
        val average = (0xff shl 24) or ((sumR / kept.size) shl 16) or ((sumG / kept.size) shl 8) or (sumB / kept.size)
        // Keep the last light/dark decision near the threshold so a surface does
        // not flip its tint and ink while a wallpaper moves by a few pixels.
        val light = if (previous != null && previous != GlassTone.Fallback &&
            abs(mean - LIGHT_THRESHOLD) <= 0.05f
        ) previous.light else null
        return tone(mean, density, average, chroma, light, darkTheme, tintScale)
    }

    fun tone(
        luminance: Float,
        complexity: Float,
        average: Int = 0xff808080.toInt(),
        chroma: Float = 0f,
        lightOverride: Boolean? = null,
        darkTheme: Boolean = false,
        tintScale: Float = 1f,
    ): GlassTone {
        val luma = luminance.coerceIn(0f, 1f)
        val detail = complexity.coerceIn(0f, 1f)
        val colour = chroma.coerceIn(0f, 1f)
        val light = !darkTheme && (lightOverride ?: (luma >= LIGHT_THRESHOLD))
        val ink = if (light) 0xdb000000.toInt() else 0xf0ffffff.toInt()
        val secondary = if (light) 0xb33c3c43.toInt() else 0xa3ebebf5.toInt()
        return GlassTone(
            luminance = luma,
            complexity = detail,
            chroma = colour,
            light = light,
            tint = if (light) LIGHT_TINT else if (darkTheme) DARK_TINT else 0xff101216.toInt(),
            average = average,
            ink = ink,
            secondaryInk = secondary,
            darkTheme = darkTheme,
            tintScale = tintScale,
        )
    }

    /** Rebuild a surface's ink and tint after a neighbouring surface fixed the row decision. */
    fun withLight(tone: GlassTone, light: Boolean): GlassTone {
        if (tone.light == light) return tone
        return tone(tone.luminance, 1f, tone.average, tone.chroma, light, tone.darkTheme, tone.tintScale)
    }

    data class Recipe(val tintAlpha: Float, val saturation: Float, val brightness: Float, val edgeStrength: Float)

    fun transition(from: GlassTone, to: GlassTone, progress: Float): GlassTone {
        val amount = progress.coerceIn(0f, 1f)
        fun channel(start: Int, end: Int) = (start + (end - start) * amount).toInt()
        return to.copy(
            complexity = from.complexity + (to.complexity - from.complexity) * amount,
            tintScale = from.tintScale + (to.tintScale - from.tintScale) * amount,
            tint = alpha(1f, channel(red(from.tint), red(to.tint)),
                channel(green(from.tint), green(to.tint)), channel(blue(from.tint), blue(to.tint))),
        )
    }

    fun recipe(tone: GlassTone, tintScale: Float = tone.tintScale): Recipe {
        val scale = tintScale.coerceIn(0f, 1.5f)
        val density = tone.complexity
        val alpha = when {
            tone.darkTheme -> 0.16f + 0.22f * density
            tone.light -> 0.06f + 0.16f * density
            else -> 0.10f + 0.20f * density
        }
        val saturation = when { tone.darkTheme -> 1.6f; tone.light -> 1.8f; else -> 1.7f }
        val brightness = when { tone.darkTheme -> 0.8f; tone.light -> 1.06f; else -> 0.85f }
        return Recipe(alpha * scale,
            1f + (saturation * max(0.45f, 1f - 0.75f * tone.chroma) - 1f) * scale.coerceAtMost(1f),
            1f + (brightness - 1f) * scale.coerceAtMost(1f),
            if (tone.darkTheme) 1f / 3f else if (tone.light) 1f else 0.45f)
    }

    /**
     * The article's 6–22% white / 16–38% charcoal range. Callers can soften it
     * for small controls where keeping the wallpaper visible matters more.
     */
    fun tintAlpha(light: Boolean, complexity: Float, strength: Float = 1f): Float {
        val detail = complexity.coerceIn(0f, 1f)
        val range = if (light) 0.06f + 0.16f * detail else 0.16f + 0.22f * detail
        return (range * strength.coerceIn(0f, 1f)).coerceIn(0f, 0.38f)
    }

    fun luminance(argb: Int): Float =
        (((argb shr 16) and 255) * 0.299f +
            ((argb shr 8) and 255) * 0.587f +
            (argb and 255) * 0.114f) / 255f

    /**
     * A busy, colourful scene loses some of the filter's saturation boost
     * instead of turning garish. Keep at least a small boost: glass should
     * still take colour from the wallpaper, as in the reference video.
     */
    fun saturation(light: Boolean, chroma: Float): Float {
        val base = if (light) 1.8f else 1.6f
        return base * max(0.45f, 1f - 0.75f * chroma.coerceIn(0f, 1f))
    }

    fun red(argb: Int): Int = (argb shr 16) and 255
    fun green(argb: Int): Int = (argb shr 8) and 255
    fun blue(argb: Int): Int = argb and 255

    fun alpha(alpha: Float, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).shl(24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

}
