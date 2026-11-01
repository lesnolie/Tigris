package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The pre-31 dock is a full-size mask over a small blur. These tests lock that
 * policy and the corner shape. The preview test also writes software renderings
 * of the old video bake (mask computed in snapshot pixels, then stretched) and
 * the new slab. They are not emulator screenshots.
 */
class DockGlassFallbackTest {
    @Test fun modernPipelineStartsAtAndroid12() {
        assertFalse(DockGlassFallback.usesModernPipeline(28))
        assertFalse(DockGlassFallback.usesModernPipeline(29))
        assertFalse(DockGlassFallback.usesModernPipeline(30))
        assertTrue(DockGlassFallback.usesModernPipeline(31))
        assertTrue(DockGlassFallback.usesModernPipeline(33))
    }

    @Test fun videoRebuildsStayWellUnderFrameRate() {
        assertEquals(4_000L, DockGlassFallback.videoRefreshIntervalMs(weak = false))
        assertEquals(10_000L, DockGlassFallback.videoRefreshIntervalMs(weak = true))
        assertTrue(DockGlassFallback.shouldRefreshVideo(nowMs = 5_000L, lastMs = 0L, weak = true))
        assertFalse(DockGlassFallback.shouldRefreshVideo(nowMs = 5_000L, lastMs = 100L, weak = true))
        assertTrue(DockGlassFallback.shouldRefreshVideo(nowMs = 10_200L, lastMs = 100L, weak = true))
        assertFalse(DockGlassFallback.shouldRefreshVideo(nowMs = 3_000L, lastMs = 100L, weak = false))
        assertTrue(DockGlassFallback.shouldRefreshVideo(nowMs = 4_200L, lastMs = 100L, weak = false))
        assertFalse(DockGlassFallback.copyIsSlow(150))
        assertTrue(DockGlassFallback.copyIsSlow(151))
        val gb = 1024L * 1024 * 1024
        assertFalse(DockGlassFallback.weakHardware(lowRam = false, totalMemBytes = 3 * gb, cores = 4))
        assertTrue(DockGlassFallback.weakHardware(lowRam = true, totalMemBytes = 3 * gb, cores = 4))
        assertTrue(DockGlassFallback.weakHardware(lowRam = false, totalMemBytes = gb, cores = 4))
        assertTrue(DockGlassFallback.weakHardware(lowRam = false, totalMemBytes = 3 * gb, cores = 2))
    }

    @Test fun blurSampleIsASmallBackdropNotTheScreen() {
        val videoLow = DockGlassFallback.sampleSize(1920, 1080, lowEnd = true, video = true)
        val video = DockGlassFallback.sampleSize(1920, 1080, lowEnd = false, video = true)
        val stillLow = DockGlassFallback.sampleSize(1920, 1080, lowEnd = true, video = false)
        val still = DockGlassFallback.sampleSize(1920, 1080, lowEnd = false, video = false)
        assertEquals(320, videoLow.first)
        assertEquals(320, video.first)
        assertEquals(180, video.first * 1080 / 1920)
        assertTrue(stillLow.first <= 160)
        assertTrue(still.first <= 240)
        for ((w, h) in listOf(videoLow, video, stillLow, still)) {
            assertTrue(w >= 8 && h >= 8)
            assertTrue(w * h < 1920 * 1080 / 8)
        }
        assertEquals(160, stillLow.first)
        assertEquals((160L * 1080 / 1920).toInt(), stillLow.second)
    }

    @Test fun cornersAreTransparentAndTheStraightEdgeIsSymmetric() {
        val w = 240
        val h = 80
        val px = DockGlassFallback.compose(
            w, h, radius = 28f, backdrop = null, backdropW = 0, backdropH = 0,
            screenW = 960, screenH = 540, dockLeft = 40, dockTop = 400, video = true,
        )
        assertEquals(0, alpha(px, w, 0, 0))
        assertEquals(0, alpha(px, w, w - 1, 0))
        assertEquals(0, alpha(px, w, 0, h - 1))
        assertEquals(0, alpha(px, w, w - 1, h - 1))
        assertEquals(DockGlassFallback.VEIL_ALPHA, alpha(px, w, w / 2, h / 2))
        val y = h / 2
        for (i in 0 until 16) {
            assertEquals("column $i", alpha(px, w, i, y), alpha(px, w, w - 1 - i, y))
        }
        val diagonal = (0 until 24).map { alpha(px, w, it, it) }
        assertEquals(0, diagonal.first())
        assertTrue(diagonal.last() >= DockGlassFallback.VEIL_ALPHA)
        val partial = diagonal.dropWhile { it == 0 }.takeWhile { it != DockGlassFallback.VEIL_ALPHA }.size
        assertTrue("aa band should be a couple of pixels, was $diagonal", partial in 1..3)
    }

    @Test fun rimIsPresentOnBothEndsAndBackdropColorSurvives() {
        val w = 200
        val h = 70
        val tint = DockGlassFallback.compose(
            w, h, 24f, null, 0, 0, 800, 450, 0, 0, video = true,
        )
        val y = h / 2
        var left = 0
        while (left < w && alpha(tint, w, left, y) < DockGlassFallback.VEIL_ALPHA) left++
        var right = w - 1
        while (right > 0 && alpha(tint, w, right, y) < DockGlassFallback.VEIL_ALPHA) right--
        val edgeL = luma(tint[y * w + (left + 1).coerceAtMost(w - 1)])
        val edgeR = luma(tint[y * w + (right - 1).coerceAtLeast(0)])
        val mid = luma(tint[y * w + w / 2])
        assertTrue("left rim $edgeL vs center $mid", edgeL > mid)
        assertTrue("right rim $edgeR vs center $mid", edgeR > mid - 8)

        val bw = 10
        val bh = 10
        val backdrop = IntArray(bw * bh) { yx ->
            val row = yx / bw
            if (row >= 5) pack(255, 240, 240, 240) else pack(255, 8, 8, 12)
        }
        val glass = DockGlassFallback.compose(
            w, h, 18f, backdrop, bw, bh,
            screenW = 100, screenH = 100, dockLeft = 0, dockTop = 70, video = false,
        )
        val center = glass[(h / 2) * w + w / 2]
        assertTrue("bottom of the screen should read as the light half, was ${red(center)}", red(center) > 180)
        assertEquals(255, alpha(glass, w, w / 2, h / 2))

        val frost = DockGlassFallback.compose(
            w, h, 18f, backdrop, bw, bh,
            screenW = 100, screenH = 100, dockLeft = 0, dockTop = 70, video = true,
        )
        assertEquals(DockGlassFallback.VIDEO_FROST_ALPHA, alpha(frost, w, w / 2, h / 2))
        assertEquals(0, alpha(frost, w, 0, 0))
        assertTrue(alpha(frost, w, 0, h / 2) > DockGlassFallback.VIDEO_FROST_ALPHA)
    }

    @Test fun stretchingALowResolutionMaskWidensTheCorner() {
        val full = roundedAlpha(180, 60, 22f)
        val low = roundedAlpha(30, 10, 22f / 6f)
        // FIT_XY filters the bitmap, so a 1 px ramp in the snapshot becomes a wide fringe.
        val stretched = bilinearAlpha(low, 30, 10, 180, 60)
        val fullPartial = diagonalPartial(full, 180)
        val stretchedPartial = diagonalPartial(stretched, 180)
        assertTrue("full-res band $fullPartial", fullPartial <= 3)
        assertTrue("stretched band $stretchedPartial should be wider than $fullPartial", stretchedPartial > fullPartial)
        assertTrue(stretchedPartial >= 4)
    }

    @Test fun boxBlurSpreadsASpikeWithoutChangingSize() {
        val w = 7
        val h = 5
        val px = IntArray(w * h) { pack(255, 0, 0, 0) }
        px[2 * w + 3] = pack(255, 255, 0, 0)
        DockGlassFallback.boxBlur(px, w, h, 1)
        assertEquals(w * h, px.size)
        assertTrue(red(px[2 * w + 3]) in 1..254)
        assertTrue(red(px[2 * w + 2]) > 0)
        assertEquals(255, (px[0] ushr 24) and 255)
    }

    @Test fun previewsShowTheOldVideoCornersAndTheFallback() {
        val screenW = 960
        val screenH = 540
        val dockL = 48
        val dockT = 400
        val dockW = 864
        val dockH = 100
        val radius = 36f
        val city = nightCity(screenW, screenH, dockL, dockT, dockW, dockH)

        val scale = 160f / screenW
        val beforeSmall = liquidBake(
            city, screenW, screenH, screenW, screenH,
            (dockL * scale).toInt(), (dockT * scale).toInt(),
            ((dockL + dockW) * scale).toInt(), ((dockT + dockH) * scale).toInt(),
            radius * scale, frost = 0.3f, opacity = 0.5f,
        )
        val smallW = ((dockL + dockW) * scale).toInt() - (dockL * scale).toInt()
        val smallH = ((dockT + dockH) * scale).toInt() - (dockT * scale).toInt()
        val before = upscale(beforeSmall, smallW, smallH, dockW, dockH)
        val beforeScene = city.copyOf()
        composite(beforeScene, screenW, screenH, before, dockW, dockH, dockL, dockT)

        val (bw, bh) = DockGlassFallback.sampleSize(screenW, screenH, lowEnd = true, video = true)
        val blurred = downscale(city, screenW, screenH, bw, bh)
        repeat(DockGlassFallback.blurPasses(video = true)) {
            DockGlassFallback.boxBlur(blurred, bw, bh, DockGlassFallback.blurRadius(lowEnd = true, video = true))
        }
        val after = DockGlassFallback.compose(
            dockW, dockH, radius, blurred, bw, bh, screenW, screenH, dockL, dockT, video = true,
        )
        val afterScene = city.copyOf()
        composite(afterScene, screenW, screenH, after, dockW, dockH, dockL, dockT)

        val tint = DockGlassFallback.compose(
            dockW, dockH, radius, null, 0, 0, screenW, screenH, dockL, dockT, video = true,
        )
        val tintScene = city.copyOf()
        composite(tintScene, screenW, screenH, tint, dockW, dockH, dockL, dockT)

        val (sw, sh) = DockGlassFallback.sampleSize(screenW, screenH, lowEnd = true, video = false)
        val stillBlur = downscale(city, screenW, screenH, sw, sh)
        repeat(DockGlassFallback.blurPasses(video = false)) {
            DockGlassFallback.boxBlur(stillBlur, sw, sh, DockGlassFallback.blurRadius(lowEnd = true))
        }
        val still = DockGlassFallback.compose(
            dockW, dockH, radius, stillBlur, sw, sh, screenW, screenH, dockL, dockT, video = false,
        )
        val stillScene = city.copyOf()
        composite(stillScene, screenW, screenH, still, dockW, dockH, dockL, dockT)

        val reference = liquidBake(
            city, screenW, screenH, screenW, screenH,
            dockL, dockT, dockL + dockW, dockT + dockH,
            radius, frost = 1f, opacity = 1f,
        )
        val referenceScene = city.copyOf()
        composite(referenceScene, screenW, screenH, reference, dockW, dockH, dockL, dockT)

        val corners = cornerSheet(beforeScene, afterScene, screenW, dockL, dockT, dockW, dockH)
        val files = listOf(
            "01-api31-static-reference.png" to referenceScene,
            "02-api29-video-before.png" to beforeScene,
            "03-api29-video-after.png" to afterScene,
            "04-api29-video-veil.png" to tintScene,
            "05-api29-static-after.png" to stillScene,
            "06-corners-before-after.png" to corners.second,
        )
        val dirs = previewDirs()
        assertTrue(dirs.isNotEmpty())
        for (dir in dirs) {
            for ((name, pixels) in files) {
                val size = if (name.startsWith("06")) corners.first else screenW to screenH
                val file = File(dir, name)
                Png.write(file, size.first, size.second, pixels)
                assertTrue("$file is empty", file.length() > 1000)
            }
        }
        // The new mask reaches its interior alpha within a few pixels. The old
        // bake's 1 px ramp is stretched, so the diagonal stays partial for longer.
        val afterBand = bandBeforePlateau(after, dockW, DockGlassFallback.VIDEO_FROST_ALPHA)
        val beforeBand = bandBeforePlateau(before, dockW, 120)
        assertTrue("after band $afterBand", afterBand <= 3)
        assertTrue("before band $beforeBand", beforeBand >= 4)
    }

    private fun previewDirs(): List<File> {
        val dirs = mutableListOf<File>()
        for (path in listOf("build/dock-glass-previews", "app/build/dock-glass-previews")) {
            val dir = File(path)
            if (dir.mkdirs() || dir.isDirectory) dirs += dir
        }
        val artifacts = File("/opt/cursor/artifacts/dock-glass")
        if (artifacts.mkdirs() || artifacts.isDirectory) dirs += artifacts
        return dirs
    }
}

/** Coverage used by both LiquidGlass and the fallback: one pixel around the rounded rect. */
private fun roundedAlpha(w: Int, h: Int, radius: Float): IntArray {
    val out = IntArray(w * h)
    val cr = radius.coerceIn(0f, min(w, h) / 2f)
    val hw = w / 2f
    val hh = h / 2f
    for (j in 0 until h) {
        val py = j + 0.5f
        for (i in 0 until w) {
            val px = i + 0.5f
            val qx = abs(px - hw) - (hw - cr)
            val qy = abs(py - hh) - (hh - cr)
            val ox = max(qx, 0f)
            val oy = max(qy, 0f)
            val sdf = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - cr
            out[j * w + i] = ((0.5f - sdf).coerceIn(0f, 1f) * 255f).toInt()
        }
    }
    return out
}

private fun bilinearAlpha(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
    val out = IntArray(dw * dh)
    for (y in 0 until dh) {
        val fy = ((y + 0.5f) * sh / dh - 0.5f).coerceIn(0f, (sh - 1).toFloat())
        val y0 = fy.toInt().coerceIn(0, sh - 1)
        val y1 = (y0 + 1).coerceAtMost(sh - 1)
        val ty = fy - y0
        for (x in 0 until dw) {
            val fx = ((x + 0.5f) * sw / dw - 0.5f).coerceIn(0f, (sw - 1).toFloat())
            val x0 = fx.toInt().coerceIn(0, sw - 1)
            val x1 = (x0 + 1).coerceAtMost(sw - 1)
            val tx = fx - x0
            val top = src[y0 * sw + x0] * (1f - tx) + src[y0 * sw + x1] * tx
            val bot = src[y1 * sw + x0] * (1f - tx) + src[y1 * sw + x1] * tx
            out[y * dw + x] = (top * (1f - ty) + bot * ty).toInt().coerceIn(0, 255)
        }
    }
    return out
}

private fun diagonalPartial(alpha: IntArray, width: Int): Int {
    val n = min(24, min(width, alpha.size / width))
    return (0 until n).count { alpha[it * width + it] in 8..247 }
}

private fun alphasOf(argb: IntArray): IntArray =
    IntArray(argb.size) { i -> (argb[i] ushr 24) and 255 }

/** Partial pixels along the corner diagonal, after the outside of the curve. */
private fun bandBeforePlateau(argb: IntArray, width: Int, plateau: Int): Int {
    var n = 0
    var started = false
    for (i in 0 until 40) {
        val a = (argb[i * width + i] ushr 24) and 255
        if (!started) {
            if (a == 0) continue
            started = true
        }
        if (a >= plateau - 12) break
        n++
    }
    return n
}

/**
 * Test-only reproduction of [com.leslie.tvhome.LiquidGlass.bake]: the mask, rim
 * and opacity are evaluated in bake pixels. The pre-31 video path then stretches
 * that bitmap up to the dock.
 */
private fun liquidBake(
    source: IntArray,
    sourceW: Int,
    sourceH: Int,
    screenW: Int,
    screenH: Int,
    rectL: Int,
    rectT: Int,
    rectR: Int,
    rectB: Int,
    radius: Float,
    frost: Float,
    opacity: Float,
): IntArray {
    val w = (rectR - rectL).coerceAtLeast(1)
    val h = (rectB - rectT).coerceAtLeast(1)
    val cornerRadius = radius.coerceIn(0f, min(w, h) / 2f)
    val zone = min(h * 0.26f, cornerRadius * 1.4f).coerceAtLeast(0.001f)
    val pull = zone * 0.9f
    val blurR = 7
    val margin = (pull + blurR * 2 + 2).toInt()
    val x0 = rectL - margin
    val y0 = rectT - margin
    val cw = w + margin * 2
    val ch = h + margin * 2
    val region = IntArray(cw * ch)
    val fit = max(screenW / sourceW.toFloat(), screenH / sourceH.toFloat())
    val cropX = (screenW - sourceW * fit) / 2f
    val cropY = (screenH - sourceH * fit) / 2f
    for (j in 0 until ch) {
        val sy = y0 + j + 0.5f
        for (i in 0 until cw) {
            val sx = x0 + i + 0.5f
            val u = ((sx - cropX) / fit).toInt()
            val v = ((sy - cropY) / fit).toInt()
            region[j * cw + i] = if (u in 0 until sourceW && v in 0 until sourceH) {
                source[v * sourceW + u]
            } else {
                pack(255, 0, 0, 0)
            }
        }
    }
    repeat(2) { DockGlassFallback.boxBlur(region, cw, ch, blurR) }
    val out = IntArray(w * h)
    val hw = w / 2f
    val hh = h / 2f
    val rimW = 2.2f
    val frostClamped = frost.coerceIn(0f, 1f)
    val opacityClamped = opacity.coerceIn(0f, 1f)
    val transmission = 1f - 0.14f * frostClamped
    val lift = 30f * frostClamped
    for (j in 0 until h) {
        val py = j + 0.5f
        for (i in 0 until w) {
            val px = i + 0.5f
            val qx = abs(px - hw) - (hw - cornerRadius)
            val qy = abs(py - hh) - (hh - cornerRadius)
            val ox = max(qx, 0f)
            val oy = max(qy, 0f)
            val sdf = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - cornerRadius
            val a = (0.5f - sdf).coerceIn(0f, 1f)
            if (a <= 0f) continue
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
            val c = region[((py + ny * off + margin).toInt().coerceIn(0, ch - 1)) * cw +
                (px + nx * off + margin).toInt().coerceIn(0, cw - 1)]
            var r = ((c shr 16) and 255).toFloat()
            var g = ((c shr 8) and 255).toFloat()
            var b = (c and 255).toFloat()
            val luma = r * 0.3f + g * 0.59f + b * 0.11f
            r = luma + (r - luma) * 1.2f
            g = luma + (g - luma) * 1.2f
            b = luma + (b - luma) * 1.2f
            r = r * transmission + lift
            g = g * transmission + lift
            b = b * transmission + lift
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
            val ai = (a * opacityClamped * 255f).toInt().coerceIn(0, 255)
            out[j * w + i] = pack(ai, r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
        }
    }
    return out
}

private fun nightCity(w: Int, h: Int, dockL: Int, dockT: Int, dockW: Int, dockH: Int): IntArray {
    val px = IntArray(w * h)
    for (y in 0 until h) {
        val sky = 16 + 28 * y / h
        for (x in 0 until w) {
            var r = sky / 4
            var g = sky / 3
            var b = sky
            val inDockBand = y in dockT - 30 until dockT + dockH + 30
            if (y > h / 5 && x % 17 in 3..5 && y % 11 in 2..6 && ((x * 13 + y * 7) % 5) != 0) {
                r = 220; g = 190; b = 110
            }
            if (y in (h * 0.72).toInt()..(h * 0.78).toInt() && x % 29 < 4) {
                r = 150; g = 190; b = 230
            }
            // High-contrast lights just outside each dock corner, where the bad fringe shows.
            val nearLeft = x in dockL - 24 until dockL + 36 && inDockBand
            val nearRight = x in dockL + dockW - 36 until dockL + dockW + 24 && inDockBand
            if ((nearLeft || nearRight) && (x + y) % 5 == 0) {
                r = 255; g = 230; b = 140
            }
            px[y * w + x] = pack(255, r, g, b)
        }
    }
    return px
}

private fun downscale(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
    val out = IntArray(dw * dh)
    for (y in 0 until dh) {
        val sy = ((y + 0.5f) * sh / dh).toInt().coerceIn(0, sh - 1)
        for (x in 0 until dw) {
            val sx = ((x + 0.5f) * sw / dw).toInt().coerceIn(0, sw - 1)
            out[y * dw + x] = src[sy * sw + sx]
        }
    }
    return out
}

private fun upscale(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
    val out = IntArray(dw * dh)
    for (y in 0 until dh) {
        val fy = ((y + 0.5f) * sh / dh - 0.5f).coerceIn(0f, (sh - 1).toFloat())
        val y0 = fy.toInt().coerceIn(0, sh - 1)
        val y1 = (y0 + 1).coerceAtMost(sh - 1)
        val ty = fy - y0
        for (x in 0 until dw) {
            val fx = ((x + 0.5f) * sw / dw - 0.5f).coerceIn(0f, (sw - 1).toFloat())
            val x0 = fx.toInt().coerceIn(0, sw - 1)
            val x1 = (x0 + 1).coerceAtMost(sw - 1)
            val tx = fx - x0
            out[y * dw + x] = premulMix(
                src[y0 * sw + x0], src[y0 * sw + x1], src[y1 * sw + x0], src[y1 * sw + x1], tx, ty,
            )
        }
    }
    return out
}

private fun premulMix(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
    fun chan(c: Int, shift: Int, a: Float) = ((c shr shift) and 255) * a
    fun sample(c: Int): FloatArray {
        val a = ((c ushr 24) and 255) / 255f
        return floatArrayOf(a, chan(c, 16, a), chan(c, 8, a), chan(c, 0, a))
    }
    val s00 = sample(c00)
    val s10 = sample(c10)
    val s01 = sample(c01)
    val s11 = sample(c11)
    fun mix(k: Int): Float {
        val top = s00[k] * (1f - tx) + s10[k] * tx
        val bot = s01[k] * (1f - tx) + s11[k] * tx
        return top * (1f - ty) + bot * ty
    }
    val a = mix(0)
    if (a <= 0.001f) return 0
    fun un(k: Int) = (mix(k) / a).toInt().coerceIn(0, 255)
    return pack((a * 255f).toInt().coerceIn(0, 255), un(1), un(2), un(3))
}

private fun composite(dst: IntArray, dw: Int, dh: Int, src: IntArray, sw: Int, sh: Int, left: Int, top: Int) {
    for (y in 0 until sh) {
        val dy = top + y
        if (dy !in 0 until dh) continue
        for (x in 0 until sw) {
            val dx = left + x
            if (dx !in 0 until dw) continue
            val s = src[y * sw + x]
            val sa = ((s ushr 24) and 255) / 255f
            if (sa <= 0f) continue
            val d = dst[dy * dw + dx]
            fun mix(shift: Int) = (((s shr shift) and 255) * sa + ((d shr shift) and 255) * (1f - sa)).toInt().coerceIn(0, 255)
            dst[dy * dw + dx] = pack(255, mix(16), mix(8), mix(0))
        }
    }
}

private fun cornerSheet(
    before: IntArray,
    after: IntArray,
    screenW: Int,
    dockL: Int,
    dockT: Int,
    dockW: Int,
    dockH: Int,
): Pair<Pair<Int, Int>, IntArray> {
    val crop = 88
    val zoom = 3
    val tile = crop * zoom
    val sheetW = tile * 2
    val sheetH = tile * 2
    val out = IntArray(sheetW * sheetH)
    val crops = listOf(
        0 to 0 to (before to (dockL to dockT)),
        tile to 0 to (before to (dockL + dockW - crop to dockT)),
        0 to tile to (after to (dockL to dockT)),
        tile to tile to (after to (dockL + dockW - crop to dockT)),
    )
    for ((origin, source) in crops) {
        val (ox, oy) = origin
        val (pixels, at) = source
        val (cx, cy) = at
        for (y in 0 until tile) {
            val sy = (cy + y / zoom).coerceIn(0, dockT + dockH + crop)
            for (x in 0 until tile) {
                val sx = (cx + x / zoom).coerceIn(0, screenW - 1)
                if (sy * screenW + sx in pixels.indices) out[(oy + y) * sheetW + ox + x] = pixels[sy * screenW + sx]
            }
        }
    }
    return (sheetW to sheetH) to out
}

private fun alpha(px: IntArray, w: Int, x: Int, y: Int) = (px[y * w + x] ushr 24) and 255
private fun red(c: Int) = (c shr 16) and 255
private fun luma(c: Int): Int {
    val r = (c shr 16) and 255
    val g = (c shr 8) and 255
    val b = c and 255
    return (r * 3 + g * 6 + b) / 10
}

private fun pack(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

private object Png {
    fun write(file: File, width: Int, height: Int, argb: IntArray) {
        file.parentFile?.mkdirs()
        val raw = ByteArrayOutputStream()
        val row = ByteArray(1 + width * 4)
        for (y in 0 until height) {
            row[0] = 0
            for (x in 0 until width) {
                val c = argb[y * width + x]
                val i = 1 + x * 4
                row[i] = (c shr 16).toByte()
                row[i + 1] = (c shr 8).toByte()
                row[i + 2] = c.toByte()
                row[i + 3] = (c ushr 24).toByte()
            }
            raw.write(row)
        }
        val deflater = Deflater()
        val compressed = ByteArrayOutputStream()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val buf = ByteArray(8192)
        while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val t = type.toByteArray(Charsets.US_ASCII)
            val len = ByteArray(4)
            putInt(data.size, len, 0)
            png.write(len)
            png.write(t)
            png.write(data)
            val crc = CRC32()
            crc.update(t)
            crc.update(data)
            val c = ByteArray(4)
            putInt(crc.value.toInt(), c, 0)
            png.write(c)
        }
        val ihdr = ByteArray(13)
        putInt(width, ihdr, 0)
        putInt(height, ihdr, 4)
        ihdr[8] = 8
        ihdr[9] = 6
        chunk("IHDR", ihdr)
        chunk("IDAT", compressed.toByteArray())
        chunk("IEND", ByteArray(0))
        file.writeBytes(png.toByteArray())
    }

    private fun putInt(v: Int, dest: ByteArray, offset: Int) {
        dest[offset] = (v ushr 24).toByte()
        dest[offset + 1] = (v ushr 16).toByte()
        dest[offset + 2] = (v ushr 8).toByte()
        dest[offset + 3] = v.toByte()
    }
}
