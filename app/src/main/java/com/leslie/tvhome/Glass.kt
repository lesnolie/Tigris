package com.leslie.tvhome

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Liquid Glass baked on the CPU. Android 12 has no runtime shaders, but the
 * home background is a still picture, so the glass can be computed once per
 * picture and then shown as a plain bitmap.
 */
object LiquidGlass {

    /**
     * Bakes a glass slab covering [rect] (screen pixels) over [source], which the
     * screen shows centre-cropped, with [overlay] drawn on top of it in screen
     * coordinates. Inside, the picture is softly blurred and brightened; towards
     * the rim it is pulled in from beyond the edge, the way a thick rounded edge
     * refracts; the rim itself is a thin line lit from the top left and tinted
     * by what is behind it.
     */
    fun bake(
        source: Bitmap,
        screenW: Int,
        screenH: Int,
        rect: Rect,
        radius: Float,
        overlay: Drawable? = null,
        frostStrength: Float = 1f,
    ): Bitmap {
        val w = rect.width()
        val h = rect.height()
        // Match Canvas round-rect geometry for capsules, including when the requested radius exceeds half the short side.
        val cornerRadius = radius.coerceIn(0f, min(w, h) / 2f)
        val zone = min(h * 0.26f, cornerRadius * 1.4f)    // refracting band along the rim
        val pull = zone * 0.9f                       // how far the rim reaches outside
        val blurR = 7
        val margin = (pull + blurR * 2 + 2).toInt()

        // The part of the screen around the slab, as the user sees it.
        val x0 = rect.left - margin
        val y0 = rect.top - margin
        val cw = w + margin * 2
        val ch = h + margin * 2
        val region = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(region)
        canvas.drawColor(Color.BLACK)
        canvas.translate(-x0.toFloat(), -y0.toFloat())
        val s = max(screenW / source.width.toFloat(), screenH / source.height.toFloat())
        val m = Matrix().apply {
            setScale(s, s)
            postTranslate((screenW - source.width * s) / 2, (screenH - source.height * s) / 2)
        }
        canvas.drawBitmap(source, m, Paint(Paint.FILTER_BITMAP_FLAG))
        overlay?.let { it.setBounds(0, 0, screenW, screenH); it.draw(canvas) }
        val src = IntArray(cw * ch)
        region.getPixels(src, 0, cw, 0, 0, cw, ch)
        region.recycle()
        repeat(2) { boxBlur(src, cw, ch, blurR) }

        val out = IntArray(w * h)
        val hw = w / 2f
        val hh = h / 2f
        val rimW = 2.2f
        for (j in 0 until h) {
            val py = j + 0.5f
            for (i in 0 until w) {
                val px = i + 0.5f
                // Signed distance to the rounded rectangle (negative inside).
                val qx = abs(px - hw) - (hw - cornerRadius)
                val qy = abs(py - hh) - (hh - cornerRadius)
                val ox = max(qx, 0f)
                val oy = max(qy, 0f)
                val sdf = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - cornerRadius
                val a = (0.5f - sdf).coerceIn(0f, 1f)
                if (a <= 0f) continue
                val depth = -sdf
                var nx: Float
                var ny: Float
                if (qx > 0f && qy > 0f) {
                    val l = sqrt(ox * ox + oy * oy).coerceAtLeast(1e-3f)
                    nx = ox / l; ny = oy / l
                } else if (qx > qy) { nx = 1f; ny = 0f } else { nx = 0f; ny = 1f }
                if (px < hw) nx = -nx
                if (py < hh) ny = -ny

                val t = (1f - depth / zone).coerceIn(0f, 1f)
                val off = t * t * pull
                val c = sample(src, cw, ch, px + nx * off + margin, py + ny * off + margin)
                var r = Color.red(c).toFloat()
                var g = Color.green(c).toFloat()
                var b = Color.blue(c).toFloat()
                // A little milky and a little more saturated than what is behind.
                val luma = r * 0.3f + g * 0.59f + b * 0.11f
                r = luma + (r - luma) * 1.2f; g = luma + (g - luma) * 1.2f; b = luma + (b - luma) * 1.2f
                val frost = frostStrength.coerceIn(0f, 1f)
                val transmission = 1f - 0.14f * frost
                val lift = 30f * frost
                r = r * transmission + lift; g = g * transmission + lift; b = b * transmission + lift

                // Specular rim: strongest where it faces the light (top left), a softer
                // reflection on the opposite side, tinted by the colour behind it.
                val facing = -(nx * 0.45f + ny * 0.9f)
                val light = 0.3f + 0.7f * max(facing, 0f) + 0.35f * max(-facing, 0f)
                if (depth < rimW) {
                    val k = (1f - depth / rimW).coerceIn(0f, 1f) * (0.35f + 0.6f * light)
                    r += (255f - r) * k; g += (255f - g) * k; b += (255f - b) * k
                }
                // Soft inner glow just inside the lit edge.
                if (depth < zone) {
                    val k = (1f - depth / zone) * 0.16f * max(facing, 0f)
                    r += (255f - r) * k; g += (255f - g) * k; b += (255f - b) * k
                }
                out[j * w + i] = Color.argb((a * 255).toInt(), clamp(r), clamp(g), clamp(b))
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun clamp(v: Float) = v.toInt().coerceIn(0, 255)

    private fun sample(px: IntArray, w: Int, h: Int, x: Float, y: Float): Int =
        px[y.toInt().coerceIn(0, h - 1) * w + x.toInt().coerceIn(0, w - 1)]

    /** Separable box blur in place; three channels, opaque output. */
    fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
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
            for (k in -r..r) { val c = at(k); sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255 }
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

    /**
     * A soft drop shadow for a rounded card of [w]×[h], padded by [pad] on every
     * side so the blur has room. Drawn in software, where BlurMaskFilter works.
     */
    fun shadow(w: Int, h: Int, radius: Float, pad: Int): Bitmap {
        val b = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
            maskFilter = BlurMaskFilter(pad * 0.55f, BlurMaskFilter.Blur.NORMAL)
        }
        // A touch narrower than the card, so the shadow pools underneath rather than haloing.
        val inset = pad * 0.18f
        Canvas(b).drawRoundRect(RectF(pad + inset, pad.toFloat(), pad + w - inset, (pad + h).toFloat()), radius, radius, p)
        return b
    }
}
