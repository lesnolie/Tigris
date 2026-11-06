package com.leslie.tvhome

import android.content.Context
import android.graphics.*

/** The same quiet, cached Home wallpaper for Settings and Software Update. Worker-thread only. */
internal object SettingsBackdrop {
    private const val WIDTH = 480
    private var cached: Pair<String, Bitmap>? = null

    /**
     * What [load] would draw right now: the Home background and, for photos, the
     * picture on show. A directory listing and one tiny file read; any thread.
     */
    fun key(context: Context): String {
        val background = Prefs(context).background
        if (!Wallpapers.isPhoto(background)) return background
        val photo = runCatching { Wallpapers(context, Wallpapers.sourceFor(background)).current() }.getOrNull()
        return "$background:${photo?.path}:${photo?.lastModified()}"
    }

    fun load(context: Context): Bitmap {
        val key = key(context)
        synchronized(this) { cached?.takeIf { it.first == key }?.let { return it.second } }
        val background = Prefs(context).background
        val source = runCatching {
            if (Wallpapers.isPhoto(background)) {
                val walls = Wallpapers(context, Wallpapers.sourceFor(background))
                // The 4K home photo decoded at 1/4 or 1/8 by the JPEG decoder itself.
                walls.current()?.let { walls.forHome(it, minWidth = WIDTH) }
            } else null
        }.getOrNull() ?: Backgrounds.render(if (Wallpapers.isPhoto(background)) "green" else background)
        val width = WIDTH
        val height = (width * source.height / source.width).coerceAtLeast(1)
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        LiquidGlass.boxBlur(pixels, width, height, 2)
        image.setPixels(pixels, 0, width, 0, 0, width, height)
        canvas.drawColor(Color.argb(55, 5, 9, 18))
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                Color.argb(75, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        // Read-only from here on: Settings and Software Update share it.
        synchronized(this) { cached = key to image }
        return image
    }

    fun clear() = synchronized(this) { cached = null }
}
