package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Icons the user picked for their home-screen tiles (long press → 更换图标), stored privately
 * per package. They replace the app's own artwork until restored.
 */
object TileIcons {
    private fun dir(ctx: Context) = File(ctx.filesDir, "tile_icons").apply { mkdirs() }

    fun safeName(pkg: String) = pkg.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun file(ctx: Context, pkg: String) = File(dir(ctx), safeName(pkg) + ".png")

    fun custom(ctx: Context, pkg: String): File? = file(ctx, pkg).takeIf { it.isFile && it.length() > 0 }

    /** Blocking. Written beside the old one and renamed, so a tile never reads half a file. */
    fun save(ctx: Context, pkg: String, icon: Bitmap): Boolean = runCatching {
        val target = file(ctx, pkg)
        val tmp = File(target.path + ".part")
        FileOutputStream(tmp).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(target)) { target.delete(); check(tmp.renameTo(target)) }
        Prefs(ctx).noteTileIcon(pkg)
    }.isSuccess

    fun clear(ctx: Context, pkg: String) {
        file(ctx, pkg).delete()
        Prefs(ctx).noteTileIcon(pkg)
    }

    fun decode(f: File): Bitmap? = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()

    /**
     * A picked icon on a wide tile. Already wide pictures fill it. A square app icon is shown
     * at full height; its background continues to the edges when its sides are one colour
     * (most app icons), otherwise a soft, darkened blur of the icon fills the sides.
     */
    fun draw(c: Canvas, icon: Bitmap, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val iw = icon.width
        val ih = icon.height
        if (iw.toFloat() / ih >= 1.3f) {
            val p = AppTileArtwork.banner(iw, ih, w, h)
            if (p.mode == AppTileArtwork.Mode.CONTAIN) c.drawColor(Color.rgb(16, 19, 25))
            c.drawBitmap(icon, null, Rect(p.left, p.top, p.right, p.bottom), paint)
            return
        }
        val scale = h.toFloat() / ih
        val dw = iw * scale
        val left = (w - dw) / 2
        if (plainEdge(icon, 0) && plainEdge(icon, iw - 1)) {
            c.drawBitmap(icon, Rect(0, 0, 1, ih), RectF(0f, 0f, left + 1, h.toFloat()), paint)
            c.drawBitmap(icon, Rect(iw - 1, 0, iw, ih), RectF(left + dw - 1, 0f, w.toFloat(), h.toFloat()), paint)
            c.drawBitmap(icon, null, RectF(left, 0f, left + dw, h.toFloat()), paint)
            return
        }
        // Blur by drawing a tiny copy large; then the icon, inset with rounded corners.
        val tiny = Bitmap.createScaledBitmap(icon, 12, max(1, 12 * ih / iw), true)
        val cover = max(w.toFloat() / tiny.width, h.toFloat() / tiny.height)
        val bw = tiny.width * cover
        val bh = tiny.height * cover
        c.drawBitmap(tiny, null, RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), paint)
        tiny.recycle()
        c.drawColor(Color.argb(70, 0, 0, 0))
        val side = h * 0.8f
        val box = RectF((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
        val radius = side * 0.2f
        c.save()
        c.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
        c.drawBitmap(icon, null, box, paint)
        c.restore()
    }

    /** Whether a pixel column is close to one colour, sampled top to bottom. */
    private fun plainEdge(icon: Bitmap, x: Int): Boolean {
        val samples = (0 until 16).map { icon.getPixel(x, it * (icon.height - 1) / 15) }
        if (samples.any { Color.alpha(it) < 200 }) return false
        fun spread(ch: (Int) -> Int) = samples.maxOf(ch) - samples.minOf(ch)
        // A gentle vertical gradient still continues cleanly when stretched sideways.
        return max(spread(Color::red), max(spread(Color::green), spread(Color::blue))) <= 60 &&
            samples.zipWithNext().all { (a, b) ->
                min(255, kotlin.math.abs(Color.red(a) - Color.red(b)) + kotlin.math.abs(Color.green(a) - Color.green(b)) +
                    kotlin.math.abs(Color.blue(a) - Color.blue(b))) <= 40
            }
    }
}
