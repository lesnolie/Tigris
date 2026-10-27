package com.leslie.tvhome

import android.content.Context
import android.graphics.*

/** The same quiet, cached Home wallpaper for Settings and Software Update. Worker-thread only. */
internal object SettingsBackdrop {
    fun load(context: Context): Bitmap {
        val key = Prefs(context).background
        val source = runCatching {
            if (Wallpapers.isPhoto(key)) {
                val walls = Wallpapers(context, Wallpapers.sourceFor(key))
                walls.current()?.let { walls.forHome(it) }
            } else null
        }.getOrNull() ?: Backgrounds.render(if (Wallpapers.isPhoto(key)) "green" else key)
        val width = 480
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
        return image
    }
}
