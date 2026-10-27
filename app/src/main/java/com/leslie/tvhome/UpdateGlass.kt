package com.leslie.tvhome

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import java.util.concurrent.Executors

/**
 * Page-owned glass controls. Reuses the Home dock's refracting renderer rather than
 * stacking flat translucent panels. Baking is bounded and never runs on the input thread.
 */
internal class UpdateGlass(private val root: View, private val moving: Boolean = false) {
    private val worker = Executors.newSingleThreadExecutor()
    private val surfaces = mutableListOf<Surface>()
    private var source: Bitmap? = null
    private var live: MovingGlass? = null
    private val lighting = mutableMapOf<String, Bitmap>()
    private val pendingLighting = mutableSetOf<String>()
    @Volatile private var generation = 0
    @Volatile private var closed = false
    private val rebake = Runnable { bake() }

    fun control(view: View, radius: Int = 22, content: Boolean = false): Surface = Surface(view, view.dp(radius).toFloat(), content) { live }.also {
        surfaces += it
        view.background = it
        view.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (l != ol || t != ot || r != or || b != ob) refresh()
        }
    }

    fun texture(bitmap: Bitmap) {
        source = bitmap
        if (moving) live = MovingGlass(root, bitmap)
        refresh()
    }

    /** Rebuilt settings subpages must not retain their old rows or textures. */
    fun removeWithin(parent: View) {
        surfaces.removeAll { surface ->
            var view: View? = surface.owner
            while (view != null && view !== parent) view = view.parent as? View
            (view === parent).also { if (it) surface.release() }
        }
        refresh()
    }

    /** Debounce scrolling/layout; an obsolete bake cannot replace the latest geometry. */
    fun refresh() {
        if (closed) return
        if (moving) {
            surfaces.forEach {
                if (it.owner.isShown) {
                    it.invalidateSelf()
                    prepareLighting(it)
                }
            }
            return
        }
        generation++
        root.removeCallbacks(rebake)
        root.postDelayed(rebake, 80)
    }

    /** Lighting depends only on the lens shape. Cache it once, independently of the moving scene. */
    private fun prepareLighting(surface: Surface) {
        if (surface.owner.width <= 0 || surface.owner.height <= 0 || root.width <= 0) return
        val scale = minOf(1f, 720f / root.width)
        val w = (surface.owner.width * scale).toInt().coerceAtLeast(1)
        val h = (surface.owner.height * scale).toInt().coerceAtLeast(1)
        val key = "$w:$h:${surface.radius}:${surface.content}"
        lighting[key]?.let { surface.bitmap = it; return }
        if (!pendingLighting.add(key)) return
        worker.execute {
            if (closed) return@execute
            val black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            val baked = LiquidGlass.bake(black, w, h, Rect(0, 0, w, h), surface.radius * scale,
                frostStrength = if (surface.content) .12f else .18f)
            black.recycle()
            val pixels = IntArray(w * h)
            baked.getPixels(pixels, 0, w, 0, 0, w, h)
            baked.recycle()
            val base = if (surface.content) 3 else 5
            for (i in pixels.indices) {
                val alpha = ((Color.red(pixels[i]) - base).coerceAtLeast(0) * Color.alpha(pixels[i]) / (255 - base))
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            val mask = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            mask.prepareToDraw()
            root.post {
                if (!closed) {
                    lighting[key] = mask
                    pendingLighting.remove(key)
                    refresh()
                }
            }
        }
    }

    private data class Target(val surface: Surface, val rect: Rect, val radius: Float)

    private fun bake() {
        val image = source ?: return
        if (closed || root.width == 0 || root.height == 0) return
        val ticket = generation
        // At most a 720 px-wide scene, even on a 4K TV. The controls are much smaller.
        val scale = minOf(1f, 720f / root.width)
        val sw = (root.width * scale).toInt().coerceAtLeast(1)
        val sh = (root.height * scale).toInt().coerceAtLeast(1)
        val origin = IntArray(2).also { root.getLocationInWindow(it) }
        val visibleRect = Rect()
        val targets = surfaces.filter {
            val visible = it.owner.isShown && it.owner.width > 0 && it.owner.height > 0 &&
                it.owner.getGlobalVisibleRect(visibleRect)
            if (!visible) it.bitmap = null
            visible
        }.map { surface ->
            val xy = IntArray(2).also { surface.owner.getLocationInWindow(it) }
            val left = ((xy[0] - origin[0]) * scale).toInt()
            val top = ((xy[1] - origin[1]) * scale).toInt()
            Target(surface, Rect(left, top,
                left + (surface.owner.width * scale).toInt().coerceAtLeast(1),
                top + (surface.owner.height * scale).toInt().coerceAtLeast(1)), surface.radius * scale)
        }
        worker.execute {
            if (closed || ticket != generation) return@execute
            val baked = targets.mapNotNull { target ->
                if (closed || ticket != generation) return@execute
                runCatching {
                    val texture = LiquidGlass.bake(image, sw, sh, target.rect, target.radius,
                        frostStrength = if (target.surface.content) 0.12f else 0.18f,
                        glassOpacity = if (target.surface.content) 0.8f else 0.94f)
                    texture.prepareToDraw()
                    target.surface to texture
                }.getOrNull()
            }
            root.post {
                if (!closed && ticket == generation) baked.forEach { (surface, bitmap) ->
                    surface.bitmap = bitmap
                    surface.invalidateSelf()
                }
            }
        }
    }

    fun close() {
        closed = true
        generation++
        root.removeCallbacks(rebake)
        surfaces.forEach { it.release() }
        source = null
        live = null
        lighting.clear()
        worker.shutdownNow()
    }

    internal class Surface(val owner: View, val radius: Float, val content: Boolean,
                           private val live: () -> MovingGlass?) : Drawable() {
        var bitmap: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()
        private val light = Tween()
        private var lit = 0f
        val focusAmount: Float get() = lit
        private var opacity = 255
        private var sheen: Shader? = null
        private var rim: Shader? = null
        private var spot: Shader? = null
        private val lightMatrix = Matrix()
        private var mesh: MovingGlass.Mesh? = null

        fun focus(on: Boolean) {
            val from = lit
            light.start(if (on) 260 else 200) { p ->
                lit = lerp(from, if (on) 1f else 0f, p)
                invalidateSelf()
            }
        }

        fun release() {
            light.cancel()
            (owner.getTag(R.id.motion_focus) as? Tween)?.cancel()
            bitmap = null
        }

        override fun onBoundsChange(bounds: Rect) {
            if (bounds.isEmpty) return
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            mesh = MovingGlass.Mesh(w, h, radius.coerceAtMost(minOf(w, h) / 2))
            sheen = LinearGradient(0f, 0f, w * 0.3f, h,
                intArrayOf(0x28ffffff, 0x00ffffff, 0x06ffffff),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            rim = LinearGradient(0f, 0f, w, h,
                intArrayOf(0xe6ffffff.toInt(), 0x18ffffff, 0x0cffffff, 0x88ffffff.toInt()),
                floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
            spot = RadialGradient(w * 0.15f, 0f, maxOf(w, h) * 0.75f,
                intArrayOf(0x60ffffff, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }

        override fun draw(canvas: Canvas) {
            rect.set(bounds)
            val r = radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2)
            paint.shader = null
            paint.alpha = (opacity * (if (content) 0.58f + 0.22f * lit else 0.8f + 0.2f * lit)).toInt()
            val texture = bitmap
            val movingTexture = live()
            val geometry = mesh
            if (movingTexture != null && geometry != null) {
                movingTexture.draw(canvas, owner, geometry, paint.alpha)
                if (texture != null) canvas.drawBitmap(texture, null, rect, paint)
            }
            else if (texture != null) canvas.drawBitmap(texture, null, rect, paint)
            else {
                paint.color = Color.argb(opacity * 55 / 255, 210, 220, 235)
                canvas.drawRoundRect(rect, r, r, paint)
            }
            // A lit control keeps the wallpaper's colour, rather than turning opaque white.
            paint.color = Color.argb(((if (content) 3 + 8 * lit else 4 + 22 * lit) * opacity / 255).toInt(), 255, 255, 255)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.shader = sheen
            paint.alpha = (opacity * (if (content) 0.3f else 0.6f)).toInt()
            canvas.drawRoundRect(rect, r, r, paint)
            if (lit > 0f) {
                paint.shader = spot
                lightMatrix.setTranslate(bounds.width() * 0.32f * lit, 0f)
                spot?.setLocalMatrix(lightMatrix)
                paint.alpha = (opacity * lit * if (content) 0.45f else 1f).toInt()
                canvas.drawRoundRect(rect, r, r, paint)
            }
            paint.shader = rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = owner.resources.displayMetrics.density * (0.6f + 0.35f * lit)
            paint.alpha = (opacity * (if (content) 0.22f + 0.32f * lit else 0.38f + 0.62f * lit)).toInt()
            rect.inset(paint.strokeWidth / 2, paint.strokeWidth / 2)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.alpha = 255
        }

        override fun getOutline(outline: Outline) = outline.setRoundRect(bounds,
            radius.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2f))
        override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
