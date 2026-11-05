package com.leslie.tvhome

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/** Samples the same blurred wallpaper in screen coordinates, even while the owner scrolls. */
class FrostGlass(private val owner: View, private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private val location = IntArray(2)
    private val path = Path()
    private var bitmap: Bitmap? = null
    private var texture: BitmapShader? = null
    private val prefs = Prefs(owner.context)
    private var extremeGlass: UpdateGlass? = null
    private var extremeSurface: UpdateGlass.Surface? = null
    private var softGlass: UpdateGlass? = null
    private var softSurface: UpdateGlass.Surface? = null
    var expansion = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }
    private val extremeLocation = IntArray(2)
    private var extremePosition = Long.MIN_VALUE
    private val surfaceCallback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) { invalidateSelf() }
        override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) {
            scheduleSelf(what, whenMillis)
        }
        override fun unscheduleDrawable(who: Drawable, what: Runnable) { unscheduleSelf(what) }
    }
    var focused = false
        set(value) { field = value; invalidateSelf() }
    fun texture(image: Bitmap, clearImage: Bitmap = image) {
        bitmap = image; texture = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); invalidateSelf()
        if (prefs.extremeGlass) {
            val material = extremeGlass ?: UpdateGlass(owner.rootView, moving = true, shadowsEnabled = false, tintStrength = 0.25f).also {
                extremeGlass = it
                extremeSurface = it.control(owner,
                    (radius / owner.resources.displayMetrics.density).toInt(), installBackground = false)
                    .also { surface -> surface.callback = surfaceCallback }
            }
            material.texture(clearImage)
            val soft = softGlass ?: UpdateGlass(owner.rootView, moving = true, shadowsEnabled = false, tintStrength = 0.25f).also {
                softGlass = it
                softSurface = it.control(owner,
                    (radius / owner.resources.displayMetrics.density).toInt(), installBackground = false)
                    .also { surface -> surface.callback = surfaceCallback }
            }
            soft.texture(image)
        }
    }
    fun liveTexture(image: Bitmap) {
        if (extremeGlass == null) texture(image, image)
        extremeGlass?.liveTexture(image)
        invalidateSelf()
    }
    fun softTexture(image: Bitmap) {
        bitmap = image
        texture = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        softGlass?.liveTexture(image)
        invalidateSelf()
    }
    fun close() { extremeGlass?.close(); softGlass?.close() }
    /**
     * Alpha of the blurred fill on API 30 and below. 255 is a still plate.
     * [DockGlassFallback.VIDEO_FROST_ALPHA] lets the live video show through.
     */
    var frostAlpha = 255
        set(value) { field = value; invalidateSelf() }
    /** Light veil and rim only, after a snapshot was too slow to use. */
    var veil = false
        set(value) { field = value; invalidateSelf() }
    override fun draw(canvas: Canvas) {
        if (prefs.extremeGlass && extremeSurface != null) {
            extremeSurface?.bounds = bounds
            softSurface?.bounds = bounds
            owner.getLocationInWindow(extremeLocation)
            val position = (extremeLocation[0].toLong() shl 32) xor extremeLocation[1].toLong()
            if (position != extremePosition) {
                extremePosition = position
                extremeGlass?.refresh()
                softGlass?.refresh()
            }
            if (expansion < 1f) extremeSurface?.draw(canvas)
            if (expansion > 0f) {
                softSurface?.alpha = (expansion * 255).toInt()
                softSurface?.draw(canvas)
            }
            return
        }
        // Hardware clipPath is not anti-aliased, and the stroke below was inset
        // without reducing its radius, so pre-31 corners (the scroll dock) went
        // jagged. API 31+ keeps the original draw.
        if (!DockGlassFallback.usesModernPipeline(android.os.Build.VERSION.SDK_INT)) {
            drawFullResolution(canvas)
            return
        }
        rect.set(bounds)
        val r = radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2)
        path.reset(); path.addRoundRect(rect, r, r, Path.Direction.CW)
        val save = canvas.save(); canvas.clipPath(path)
        bitmap?.let { b ->
            owner.getLocationInWindow(location)
            val screen = owner.rootView
            val scale = maxOf(screen.width.toFloat() / b.width, screen.height.toFloat() / b.height)
            matrix.setScale(scale, scale)
            matrix.postTranslate((screen.width - b.width * scale) / 2 - location[0], (screen.height - b.height * scale) / 2 - location[1])
            texture?.setLocalMatrix(matrix); paint.shader = texture; paint.alpha = 255
            canvas.drawRect(rect, paint)
        }
        paint.shader = LinearGradient(0f, rect.top, rect.right, rect.bottom,
            intArrayOf(Color.argb(if (focused) 100 else 48, 255, 255, 255), Color.argb(26, 210, 224, 255), Color.argb(55, 12, 18, 34)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint); paint.shader = null
        canvas.restoreToCount(save)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = if (focused) 3f else 1.5f
        paint.color = Color.argb(if (focused) 240 else 100, 255, 255, 255)
        rect.inset(paint.strokeWidth / 2, paint.strokeWidth / 2)
        canvas.drawRoundRect(rect, r, r, paint); paint.style = Paint.Style.FILL
    }

    /**
     * Same fill, sheen and rim as [draw], masked with an anti-aliased round rect
     * instead of [Canvas.clipPath]. The stroke radius shrinks with the inset so
     * it stays on the fill's corner.
     */
    private fun drawFullResolution(canvas: Canvas) {
        rect.set(bounds)
        val r = radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
        paint.style = Paint.Style.FILL
        if (veil) {
            paint.shader = null
            paint.color = Color.argb(DockGlassFallback.VEIL_ALPHA, 255, 255, 255)
            canvas.drawRoundRect(rect, r, r, paint)
        } else {
            bitmap?.let { b ->
                owner.getLocationInWindow(location)
                val screen = owner.rootView
                val scale = maxOf(screen.width.toFloat() / b.width, screen.height.toFloat() / b.height)
                matrix.setScale(scale, scale)
                matrix.postTranslate(
                    (screen.width - b.width * scale) / 2f - location[0],
                    (screen.height - b.height * scale) / 2f - location[1],
                )
                texture?.setLocalMatrix(matrix)
                paint.shader = texture
                paint.alpha = frostAlpha
                canvas.drawRoundRect(rect, r, r, paint)
            }
            // A full-strength sheen would hide the video. Still plates keep it.
            if (frostAlpha >= 255) {
                paint.shader = LinearGradient(
                    0f, rect.top, rect.right, rect.bottom,
                    intArrayOf(
                        Color.argb(if (focused) 100 else 48, 255, 255, 255),
                        Color.argb(26, 210, 224, 255),
                        Color.argb(55, 12, 18, 34),
                    ),
                    null, Shader.TileMode.CLAMP,
                )
                paint.alpha = 255
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (focused) 3f else 1.5f
        paint.color = Color.argb(if (focused) 240 else if (veil || frostAlpha < 255) 200 else 100, 255, 255, 255)
        val inset = paint.strokeWidth / 2f
        rect.inset(inset, inset)
        val strokeRadius = (r - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(rect, strokeRadius, strokeRadius, paint)
        paint.style = Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
