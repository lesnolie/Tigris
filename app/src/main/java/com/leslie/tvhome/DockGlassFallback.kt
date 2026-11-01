package com.leslie.tvhome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Dock glass for Android 11 and older (API < 31).
 *
 * API 31+ keeps [LiquidGlass.bake] exactly, including the scaled video bake.
 * On older versions the moving picture stays on the TextureView under the dock.
 * A small PixelCopy is box-blurred and drawn at about 55% opacity inside a
 * mask and rim that are the dock's own pixels, so the corners stay smooth and
 * the live video shows through. A copy that takes longer than [SLOW_COPY_MS]
 * is not used: the dock becomes a light veil and a rim. See
 * docs/dock-glass-fallback.md.
 */
internal object DockGlassFallback {
    const val MODERN_MIN_SDK = 31

    /** Width of the video snapshot that is blurred. Height follows the view. */
    const val VIDEO_CAPTURE_WIDTH = 320

    /** How often a new frost is taken while video plays on Home. */
    const val VIDEO_REFRESH_MS = 4_000L

    /** Same gap on a TV with very little RAM or only one or two cores. */
    const val WEAK_REFRESH_MS = 10_000L

    /** A copy slower than this is dropped, and frost sampling stops. */
    const val SLOW_COPY_MS = 150L

    /** Interior of a video frost, about 55%, so the TextureView shows through. */
    const val VIDEO_FROST_ALPHA = 140

    /** Interior of the veil used when no frost is available. */
    const val VEIL_ALPHA = 46

    /** Rim alpha on a see-through plate. The body stays at the frost or veil alpha. */
    const val RIM_ALPHA = 220

    /** Crossfade when a new video frost replaces the previous one. */
    const val FROST_FADE_MS = 700

    const val VIDEO_BLUR_RADIUS = 12
    const val VIDEO_BLUR_PASSES = 3

    fun usesModernPipeline(sdkInt: Int): Boolean = sdkInt >= MODERN_MIN_SDK

    /**
     * Low RAM, under 1.8 GB, or one or two cores. An old SDK by itself is not
     * enough: Android 10 on a normal TV still gets the 4 s frost.
     */
    fun weakHardware(lowRam: Boolean, totalMemBytes: Long, cores: Int): Boolean =
        lowRam || (totalMemBytes in 1 until 1_800L * 1024 * 1024) || cores <= 2

    fun videoRefreshIntervalMs(weak: Boolean): Long = if (weak) WEAK_REFRESH_MS else VIDEO_REFRESH_MS

    fun shouldRefreshVideo(nowMs: Long, lastMs: Long, weak: Boolean): Boolean =
        lastMs == 0L || nowMs - lastMs >= videoRefreshIntervalMs(weak)

    fun copyIsSlow(elapsedMs: Long): Boolean = elapsedMs > SLOW_COPY_MS

    /**
     * Longest edge of a still backdrop that is blurred. Video snapshots use
     * [VIDEO_CAPTURE_WIDTH] instead. The dock mask is never reduced to this size.
     */
    fun blurSampleEdge(lowEnd: Boolean, video: Boolean): Int = when {
        video -> VIDEO_CAPTURE_WIDTH
        lowEnd -> 160
        else -> 240
    }

    fun sampleSize(screenW: Int, screenH: Int, lowEnd: Boolean, video: Boolean = false): Pair<Int, Int> {
        val sw = screenW.coerceAtLeast(1)
        val sh = screenH.coerceAtLeast(1)
        if (video) {
            val w = VIDEO_CAPTURE_WIDTH.coerceAtMost(sw)
            return w to (w.toLong() * sh / sw).toInt().coerceAtLeast(1)
        }
        val edge = blurSampleEdge(lowEnd, video = false)
        return if (sw >= sh) {
            val w = edge.coerceAtMost(sw)
            w to (w.toLong() * sh / sw).toInt().coerceAtLeast(1)
        } else {
            val h = edge.coerceAtMost(sh)
            (h.toLong() * sw / sh).toInt().coerceAtLeast(1) to h
        }
    }

    fun blurPasses(video: Boolean): Int = if (video) VIDEO_BLUR_PASSES else 2

    /**
     * In sample pixels. Video uses a wide kernel so a frame that is a few
     * seconds old does not draw a second sharp picture over the live video.
     */
    fun blurRadius(lowEnd: Boolean, video: Boolean = false): Int =
        if (video) VIDEO_BLUR_RADIUS else if (lowEnd) 2 else 3

    /**
     * Full-resolution dock slab. [backdrop] is an opaque blurred image of the
     * whole screen (center-cropped), or null when the video could not be read.
     * Pixels are straight (non-premultiplied) ARGB.
     */
    fun compose(
        width: Int,
        height: Int,
        radius: Float,
        backdrop: IntArray?,
        backdropW: Int,
        backdropH: Int,
        screenW: Int,
        screenH: Int,
        dockLeft: Int,
        dockTop: Int,
        video: Boolean,
    ): IntArray {
        if (width <= 0 || height <= 0) return IntArray(0)
        val out = IntArray(width * height)
        val cornerRadius = radius.coerceIn(0f, min(width, height) / 2f)
        val zone = min(height * 0.26f, cornerRadius * 1.4f).coerceAtLeast(1f)
        val pull = zone * if (video) 0.35f else 0.9f
        val rimW = 2.2f
        val frost = if (video) 0.85f else 1f
        val transmission = 1f - 0.14f * frost
        val lift = 30f * frost
        val hw = width / 2f
        val hh = height / 2f
        val sw = screenW.coerceAtLeast(1).toFloat()
        val sh = screenH.coerceAtLeast(1).toFloat()
        val image = backdrop
        val hasBackdrop = image != null && backdropW > 0 && backdropH > 0 && image.size >= backdropW * backdropH
        val bodyAlpha = when {
            !video -> 255
            hasBackdrop -> VIDEO_FROST_ALPHA
            else -> VEIL_ALPHA
        }
        for (j in 0 until height) {
            val py = j + 0.5f
            for (i in 0 until width) {
                val px = i + 0.5f
                val qx = abs(px - hw) - (hw - cornerRadius)
                val qy = abs(py - hh) - (hh - cornerRadius)
                val ox = max(qx, 0f)
                val oy = max(qy, 0f)
                val sdf = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - cornerRadius
                val coverage = (0.5f - sdf).coerceIn(0f, 1f)
                if (coverage <= 0f) continue
                var nx: Float
                var ny: Float
                if (qx > 0f && qy > 0f) {
                    val l = sqrt(ox * ox + oy * oy).coerceAtLeast(1e-3f)
                    nx = ox / l; ny = oy / l
                } else if (qx > qy) {
                    nx = 1f; ny = 0f
                } else {
                    nx = 0f; ny = 1f
                }
                if (px < hw) nx = -nx
                if (py < hh) ny = -ny
                val depth = -sdf
                val t = (1f - depth / zone).coerceIn(0f, 1f)
                val off = t * t * pull
                var r: Float
                var g: Float
                var b: Float
                if (image == null || !hasBackdrop) {
                    val down = (py / height).coerceIn(0f, 1f)
                    r = 168f + (1f - down) * 42f
                    g = 176f + (1f - down) * 38f
                    b = 194f + (1f - down) * 34f
                } else {
                    val sx = dockLeft + px + nx * off
                    val sy = dockTop + py + ny * off
                    val c = sampleBilinear(
                        image, backdropW, backdropH,
                        sx * backdropW / sw, sy * backdropH / sh,
                    )
                    r = ((c shr 16) and 255).toFloat()
                    g = ((c shr 8) and 255).toFloat()
                    b = (c and 255).toFloat()
                    val luma = r * 0.3f + g * 0.59f + b * 0.11f
                    r = luma + (r - luma) * 1.2f
                    g = luma + (g - luma) * 1.2f
                    b = luma + (b - luma) * 1.2f
                    r = r * transmission + lift
                    g = g * transmission + lift
                    b = b * transmission + lift
                }
                val facing = -(nx * 0.45f + ny * 0.9f)
                val light = 0.3f + 0.7f * max(facing, 0f) + 0.35f * max(-facing, 0f)
                if (depth < rimW) {
                    val k = (1f - depth / rimW).coerceIn(0f, 1f) * (0.35f + 0.6f * light)
                    r += (255f - r) * k; g += (255f - g) * k; b += (255f - b) * k
                }
                if (depth < zone) {
                    val k = (1f - depth / zone) * 0.16f * max(facing, 0f)
                    r += (255f - r) * k; g += (255f - g) * k; b += (255f - b) * k
                }
                // Alpha follows the edge, not the light direction, so the right
                // corner is not more transparent than the left. Colour still is.
                val rim = if (video && depth < rimW) (1f - depth / rimW).coerceIn(0f, 1f) else 0f
                val plate = bodyAlpha + (RIM_ALPHA - bodyAlpha) * rim
                val a = (coverage * plate).toInt().coerceIn(0, 255)
                out[j * width + i] = (a shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
        return out
    }

    /** Light veil and rim, used before the first frame and when copies are too slow. */
    fun frosted(width: Int, height: Int, radius: Float): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val px = compose(w, h, radius, null, 0, 0, 1, 1, 0, 0, video = true)
        return bitmapFrom(px, w, h)
    }

    /**
     * Blur [source] down to [sampleSize] and composite it into the dock rect.
     * The mask is [dock]'s full pixel size, not the blur size.
     */
    fun fromBackdrop(
        source: Bitmap,
        screenW: Int,
        screenH: Int,
        dock: Rect,
        radius: Float,
        video: Boolean,
        lowEnd: Boolean,
    ): Bitmap {
        val w = dock.width().coerceAtLeast(1)
        val h = dock.height().coerceAtLeast(1)
        if (dock.width() <= 0 || dock.height() <= 0 || source.width <= 0 || source.height <= 0 ||
            screenW <= 0 || screenH <= 0
        ) {
            return frosted(w, h, radius)
        }
        val (bw, bh) = sampleSize(screenW, screenH, lowEnd, video)
        val sample = try {
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return frosted(w, h, radius)
        }
        var copied: Bitmap? = null
        return try {
            val canvas = Canvas(sample)
            val scale = max(bw / source.width.toFloat(), bh / source.height.toFloat())
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate((bw - source.width * scale) / 2f, (bh - source.height * scale) / 2f)
            }
            val readable = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)?.also { copied = it }
            } else {
                source
            }
            if (readable == null) return frosted(w, h, radius)
            canvas.drawBitmap(readable, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            val px = IntArray(bw * bh)
            sample.getPixels(px, 0, bw, 0, 0, bw, bh)
            val radiusPx = blurRadius(lowEnd, video)
            repeat(blurPasses(video)) { boxBlur(px, bw, bh, radiusPx) }
            bitmapFrom(
                compose(w, h, radius, px, bw, bh, screenW, screenH, dock.left, dock.top, video),
                w, h,
            )
        } catch (_: Exception) {
            frosted(w, h, radius)
        } catch (_: OutOfMemoryError) {
            frosted(w, h, radius)
        } finally {
            copied?.recycle()
            sample.recycle()
        }
    }

    /** Separable box blur, three-channel, opaque. A stack of these stands in for RenderScript. */
    fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        if (r <= 0 || w <= 0 || h <= 0 || px.size < w * h) return
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = r * 2 + 1
        for (line in 0 until lines) {
            fun at(k: Int): Int {
                val p = k.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + p] else src[p * w + line]
            }
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val c = at(k); sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
            }
            for (k in 0 until len) {
                val idx = if (horizontal) line * w + k else k * w + line
                dst[idx] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = at(k + r + 1)
                val sub = at(k - r)
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }

    private fun sampleBilinear(px: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val x0 = x.coerceIn(0f, (w - 1).toFloat())
        val y0 = y.coerceIn(0f, (h - 1).toFloat())
        val i = x0.toInt().coerceIn(0, w - 1)
        val j = y0.toInt().coerceIn(0, h - 1)
        val i1 = (i + 1).coerceAtMost(w - 1)
        val j1 = (j + 1).coerceAtMost(h - 1)
        val tx = x0 - i
        val ty = y0 - j
        fun ch(c: Int, shift: Int) = (c shr shift) and 255
        fun mix(shift: Int): Int {
            val top = ch(px[j * w + i], shift) * (1f - tx) + ch(px[j * w + i1], shift) * tx
            val bot = ch(px[j1 * w + i], shift) * (1f - tx) + ch(px[j1 * w + i1], shift) * tx
            return (top * (1f - ty) + bot * ty).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun clamp(v: Float) = v.toInt().coerceIn(0, 255)

    private fun bitmapFrom(pixels: IntArray, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
            prepareToDraw()
        }
}
