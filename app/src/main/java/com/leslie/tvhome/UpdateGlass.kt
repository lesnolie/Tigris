package com.leslie.tvhome

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Page-owned glass controls. Reuses the Home dock's refracting renderer rather than
 * stacking flat translucent panels. Baking is bounded and never runs on the input thread.
 */
internal class UpdateGlass(private val root: View, private val moving: Boolean = false,
                           private val shadowsEnabled: Boolean = true,
                           private val tintStrength: Float = 1f) {
    private val prefs = Prefs(root.context)
    private val worker = Executors.newSingleThreadExecutor()
    private val surfaces = mutableListOf<Surface>()
    private var source: Bitmap? = null
    private var requestedSource: Bitmap? = null
    private var sourcePixels: IntArray? = null
    private var softSource: Bitmap? = null
    @Volatile private var textureRevision = 0
    private var appearance = ""
    private var live: MovingGlass? = null
    /** The lens fading out while [live] fades in, in step with the page's own backdrop fade. */
    private var previousLive: MovingGlass? = null
    private var fadeStart = 0L
    private var fadeMs = 1L
    private var fadeCurve: android.animation.TimeInterpolator? = null
    /** The last lens committed, reused when the same picture comes back (leaving a Top Shelf). */
    private var lens: Lens? = null
    @Volatile private var liveExtreme = false
    private var nextLiveSample = 0L
    private var transmission = false
    private val lighting = mutableMapOf<String, Bitmap>()
    private val pendingLighting = mutableSetOf<String>()
    private val shadows = mutableMapOf<String, Bitmap>()
    private val pendingShadows = mutableSetOf<String>()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowLayer = object : Drawable() {
        override fun draw(canvas: Canvas) {
            if (!shadowsEnabled || !prefs.extremeGlass || prefs.solidGlass(root.context)) return
            val rootXY = IntArray(2).also { root.getLocationInWindow(it) }
            val xy = IntArray(2)
            val path = Path()
            for (surface in surfaces) {
                val shadow = surface.shadow ?: continue
                val owner = surface.owner
                if (!owner.isShown) continue
                owner.getLocationInWindow(xy)
                val x = xy[0] - rootXY[0]
                val y = xy[1] - rootXY[1]
                // Rows scrolled out of the page would only add clips the renderer throws away.
                val reach = owner.dp(16)
                if (x + owner.width * owner.scaleX + reach < 0 || x - reach > root.width ||
                    y + owner.height * owner.scaleY + reach < 0 || y - reach > root.height
                ) continue
                val saved = canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                canvas.scale(owner.scaleX, owner.scaleY)
                path.reset()
                val radius = surface.radius.coerceAtMost(minOf(owner.width, owner.height) / 2f)
                path.addRoundRect(0f, 0f, owner.width.toFloat(), owner.height.toFloat(), radius, radius, Path.Direction.CW)
                canvas.clipOutPath(path)
                val pad = owner.dp(12).toFloat()
                canvas.drawBitmap(shadow, -pad, -pad + owner.dp(3), shadowPaint)
                canvas.restoreToCount(saved)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(filter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    init { root.overlay.add(shadowLayer) }
    @Volatile private var generation = 0
    @Volatile private var closed = false
    private val rebake = Runnable { bake() }

    fun control(
        view: View,
        radius: Int = 22,
        content: Boolean = false,
        installBackground: Boolean = true,
        onTone: ((GlassTone) -> Unit)? = null,
    ): Surface =
        Surface(view, view.dp(radius).toFloat(), content, { live }, { prefs.extremeGlass },
            { prefs.solidGlass(root.context) }, { transmission }, onTone, { shadowLayer.invalidateSelf() },
            previous = { previousLive }, fade = { fadeProgress() }).also {
            surfaces += it
            if (installBackground) view.background = it
            view.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (l != ol || t != ot || r != or || b != ob) refresh()
            }
        }

    fun texture(bitmap: Bitmap) {
        requestedSource = bitmap
        liveExtreme = prefs.extremeGlass
        val ready = lens?.takeIf { it.origin === bitmap }
        if (liveExtreme && ready != null) {
            textureRevision++
            commit(ready, 0L)
            return
        }
        if (liveExtreme) prepareExtremeSource(bitmap)
        else {
            transmission = false
            textureRevision++
            source = bitmap
            sourcePixels = null
            softSource = null
            live = if (moving) MovingGlass(root, bitmap) else null
        }
        refresh()
    }

    /**
     * Commits a lens that [prepare] built off the UI thread, in this frame. With
     * [fadeMs] the old lens cross-fades into it, so the glass follows a backdrop
     * that fades over the same time instead of jumping before or after it.
     */
    fun texture(prepared: Lens, fadeMs: Long = 0L, curve: android.animation.TimeInterpolator? = null) {
        if (closed) return
        if (!prefs.extremeGlass) { texture(prepared.origin); return }
        requestedSource = prepared.origin
        liveExtreme = true
        textureRevision++
        commit(prepared, fadeMs, curve)
    }

    private fun commit(prepared: Lens, fade: Long, curve: android.animation.TimeInterpolator? = null) {
        val old = live
        lens = prepared
        source = prepared.image
        transmission = false
        sourcePixels = prepared.pixels
        softSource = prepared.blurred
        live = MovingGlass(root, prepared.blurred)
        if (fade > 0L && old != null) {
            previousLive = old
            fadeStart = android.os.SystemClock.uptimeMillis()
            fadeMs = fade
            fadeCurve = curve
        } else previousLive = null
        refresh()
    }

    private fun fadeProgress(): Float {
        if (previousLive == null) return 1f
        val t = ((android.os.SystemClock.uptimeMillis() - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
        if (t >= 1f) {
            previousLive = null
            return 1f
        }
        return fadeCurve?.getInterpolation(t) ?: t
    }

    fun liveTexture(bitmap: Bitmap) {
        if (closed || !prefs.extremeGlass) return
        previousLive = null
        textureRevision++
        requestedSource = bitmap
        source = bitmap
        liveExtreme = true
        transmission = true
        val now = android.os.SystemClock.uptimeMillis()
        if (now >= nextLiveSample || sourcePixels?.size != bitmap.width * bitmap.height) {
            sourcePixels = IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
            nextLiveSample = now + 160L
            surfaces.forEach { sampleMaterial(it) }
        }
        surfaces.forEach { it.invalidateSelf(); it.owner.invalidate() }
    }

    /** Ordinary settings keep their original texture without extra pixel copies or blur work. */
    private fun prepareExtremeSource(bitmap: Bitmap) {
        val revision = ++textureRevision
        val sceneWidth = root.width.coerceAtLeast(root.resources.displayMetrics.widthPixels)
        worker.execute {
            if (closed || revision != textureRevision) return@execute
            val prepared = prepare(bitmap, sceneWidth)
            root.post {
                if (!closed && revision == textureRevision && requestedSource === bitmap && prefs.extremeGlass) {
                    commit(prepared, 0L)
                }
            }
        }
    }

    /** Everything the extreme lens needs from one picture, made ready off the UI thread. */
    internal class Lens(val origin: Bitmap, val image: Bitmap, val pixels: IntArray, val blurred: Bitmap)

    companion object {
        /**
         * Any thread. A copy of at most 960 px is all the lens samples; its pixels
         * feed the ink/tint reading, and a light blur of them is what refracts.
         */
        fun prepare(bitmap: Bitmap, sceneWidth: Int): Lens {
            val sampleWidth = minOf(bitmap.width, 960)
            val sampleHeight = (bitmap.height * sampleWidth.toFloat() / bitmap.width).roundToInt().coerceAtLeast(1)
            val image = if (sampleWidth == bitmap.width) bitmap
                else Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            // Blur a copy of the pixels already read: no second bitmap copy and readback.
            val px = pixels.copyOf()
            val radius = (2f * image.width / sceneWidth.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
            repeat(2) { LiquidGlass.boxBlur(px, image.width, image.height, radius) }
            val blurred = Bitmap.createBitmap(px, image.width, image.height, Bitmap.Config.ARGB_8888)
            blurred.prepareToDraw()
            return Lens(bitmap, image, pixels, blurred)
        }
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
        shadowLayer.setBounds(0, 0, root.width, root.height)
        val changed = liveExtreme != prefs.extremeGlass
        val nextAppearance = "${prefs.extremeGlass}:${prefs.glassLightTint}:${prefs.glassDarkTint}:${prefs.solidGlass(root.context)}:${root.resources.configuration.uiMode}"
        val toneChanged = appearance != nextAppearance
        appearance = nextAppearance
        if (changed) {
            liveExtreme = prefs.extremeGlass
            (requestedSource ?: source)?.let {
                val ready = lens?.takeIf { prepared -> prepared.origin === it }
                if (liveExtreme && ready != null) commit(ready, 0L)
                else if (liveExtreme) prepareExtremeSource(it)
                else {
                    textureRevision++
                    source = it
                    sourcePixels = null
                    live = if (moving) MovingGlass(root, it) else null
                }
            }
        }
        if (moving || liveExtreme) {
            root.getLocationInWindow(rootXY)
            surfaces.forEach {
                // A long page holds dozens of rows, most of them scrolled away. Only those
                // within a screen of the viewport are re-sampled and re-recorded; any other
                // row gets the same treatment on the scroll step that brings it this close,
                // before it can be seen.
                if (it.owner.isShown && near(it.owner)) {
                    it.invalidateSelf()
                    it.owner.invalidate()
                    if (!liveExtreme) prepareLighting(it)
                    else if (shadowsEnabled) prepareShadow(it)
                    sampleMaterial(it, force = toneChanged)
                }
            }
            shadowLayer.invalidateSelf()
            return
        }
        surfaces.forEach { sampleMaterial(it, force = toneChanged) }
        generation++
        root.removeCallbacks(rebake)
        root.postDelayed(rebake, 80)
    }

    private val rootXY = IntArray(2)
    private val nearXY = IntArray(2)

    private fun near(view: View): Boolean {
        view.getLocationInWindow(nearXY)
        val top = nearXY[1] - rootXY[1]
        val left = nearXY[0] - rootXY[0]
        return top + view.height > -root.height && top < root.height * 2 &&
            left + view.width > -root.width && left < root.width * 2
    }

    private fun prepareShadow(surface: Surface) {
        val width = surface.owner.width
        val height = surface.owner.height
        if (width <= 0 || height <= 0) return
        val key = "$width:$height:${surface.radius}"
        shadows[key]?.let { surface.shadow = it; return }
        if (!pendingShadows.add(key)) return
        val pad = surface.owner.dp(12)
        worker.execute {
            if (closed) return@execute
            val image = LiquidGlass.shadow(width, height, surface.radius, pad, alpha = 45)
            root.post {
                if (!closed) {
                    shadows[key] = image
                    pendingShadows.remove(key)
                    surface.shadow = image
                    shadowLayer.invalidateSelf()
                }
            }
        }
    }

    /**
     * The settings artwork is already a root-sized texture. Read the crop behind
     * each row so its tint and text colours follow the picture as it scrolls.
     */
    private fun sampleMaterial(surface: Surface, force: Boolean = false) {
        if (!prefs.extremeGlass) {
            surface.applyTone(GlassTone.Fallback, force)
            return
        }
        val image = source ?: return
        val pixels = sourcePixels ?: return
        val owner = surface.owner
        if (owner.width <= 0 || owner.height <= 0 || root.width <= 0 || root.height <= 0) return
        val rootXY = IntArray(2).also { root.getLocationInWindow(it) }
        val ownerXY = IntArray(2).also { owner.getLocationInWindow(it) }
        val fit = max(root.width / image.width.toFloat(), root.height / image.height.toFloat())
        val cropX = (root.width - image.width * fit) / 2f
        val cropY = (root.height - image.height * fit) / 2f
        val sx = ownerXY[0] - rootXY[0]
        val sy = ownerXY[1] - rootXY[1]
        val left = ((sx - cropX) / fit).toInt()
        val top = ((sy - cropY) / fit).toInt()
        val right = ((sx + owner.width - cropX) / fit).toInt()
        val bottom = ((sy + owner.height - cropY) / fit).toInt()
        val darkTheme = root.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val inkKey = "${root.context.javaClass.simpleName}:${surfaces.indexOf(surface)}:$darkTheme"
        val previous = surface.material.takeUnless { it == GlassTone.Fallback }
            ?: prefs.rememberedGlassInk(inkKey)?.let { GlassMaterial.tone(0.5f, 0f, lightOverride = it, darkTheme = darkTheme) }
        var tone = GlassMaterial.analyze(
            pixels, image.width, image.height, left, top, right, bottom,
            previous = previous, darkTheme = darkTheme && !transmission, tintScale = prefs.glassTint(darkTheme) * tintStrength,
        )
        if (darkTheme && transmission) tone = tone.copy(darkTheme = true, tint = 0xff141418.toInt())
        prefs.rememberGlassInk(inkKey, tone.light)
        surface.applyTone(tone, force)
    }

    /** Lighting depends only on the lens shape. Cache it once, independently of the moving scene. */
    private fun prepareLighting(surface: Surface) {
        if (surface.owner.width <= 0 || surface.owner.height <= 0 || root.width <= 0) return
        val scale = minOf(1f, 720f / root.width)
        val w = (surface.owner.width * scale).toInt().coerceAtLeast(1)
        val h = (surface.owner.height * scale).toInt().coerceAtLeast(1)
        // This mask belongs only to the ordinary renderer. Capture its mode here:
        // switching the preference while the worker is queued must not poison the cache.
        val key = "$w:$h:${surface.radius}:${surface.content}"
        lighting[key]?.let { surface.bitmap = it; return }
        if (!pendingLighting.add(key)) return
        worker.execute {
            if (closed) return@execute
            val black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            val baked = LiquidGlass.bake(black, w, h, Rect(0, 0, w, h), surface.radius * scale,
                frostStrength = if (surface.content) .12f else .18f,
                extreme = false)
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
                        glassOpacity = if (target.surface.content) 0.8f else 0.94f,
                        extreme = prefs.extremeGlass,
                        darkTheme = root.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
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
        root.overlay.remove(shadowLayer)
        surfaces.forEach { it.release() }
        source = null
        requestedSource = null
        sourcePixels = null
        softSource = null
        live = null
        previousLive = null
        lens = null
        lighting.clear()
        shadows.clear()
        worker.shutdownNow()
    }

    internal class Surface(
        val owner: View,
        val radius: Float,
        val content: Boolean,
        private val live: () -> MovingGlass?,
        private val extreme: () -> Boolean,
        private val solid: () -> Boolean,
        private val transmission: () -> Boolean,
        private val onTone: ((GlassTone) -> Unit)? = null,
        private val onFrame: () -> Unit = {},
        private val previous: () -> MovingGlass? = { null },
        private val fade: () -> Float = { 1f },
    ) : Drawable() {
        var bitmap: Bitmap? = null
        var shadow: Bitmap? = null
        var material = GlassTone.Fallback
            private set
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()
        private val light = Tween()
        private val tintTween = Tween()
        private var targetTone = GlassTone.Fallback
        private var lit = 0f
        val focusAmount: Float get() = lit
        private var opacity = 255
        private var sheen: Shader? = null
        private var rim: Shader? = null
        private var spot: Shader? = null
        private val lightMatrix = Matrix()
        private var ordinaryMesh: MovingGlass.Mesh? = null
        private var extremeMesh: MovingGlass.Mesh? = null
        private val toneFilters = HashMap<Int, ColorMatrixColorFilter>()
        private data class InkBinding(val view: TextView, val secondary: Boolean,
                                      val original: android.content.res.ColorStateList)
        private val inkBindings = mutableListOf<InkBinding>()
        private var focused = false

        /** Explicit labels only: nested glass buttons keep their own sampled ink. */
        fun bindInk(primary: List<TextView>, secondary: List<TextView> = emptyList()) {
            primary.forEach { inkBindings += InkBinding(it, false, it.textColors) }
            secondary.forEach { inkBindings += InkBinding(it, true, it.textColors) }
            refreshInk()
        }

        private fun refreshInk() {
            inkBindings.forEach { binding ->
                GlassInk.apply(binding.view, extreme() && !solid(), focused && !content || material.light)
                if (!extreme()) binding.view.setTextColor(binding.original)
                else binding.view.setTextColor(when {
                    focused && !content -> Color.argb(if (binding.secondary) 180 else 235, 0, 0, 0)
                    binding.secondary -> material.secondaryInk
                    else -> material.ink
                })
            }
        }

        fun applyTone(tone: GlassTone, force: Boolean = false) {
            if (!force && targetTone == tone) return
            val from = material
            // The first reading lands at once: gliding from the unsampled fallback made
            // every row visibly settle after the page was already on screen.
            val glide = extreme() && from != GlassTone.Fallback &&
                (from.tintScale != tone.tintScale || from.light != tone.light)
            targetTone = tone
            tintTween.cancel()
            material = tone
            refreshInk()
            onTone?.invoke(tone)
            invalidateSelf()
            owner.invalidate()
            if (glide) tintTween.start(180) { progress ->
                material = GlassMaterial.transition(from, tone, progress)
                invalidateSelf()
                owner.invalidate()
            }
        }

        private fun toneFilter(): ColorMatrixColorFilter {
            val chroma = (material.chroma * 8f).roundToInt().coerceIn(0, 8)
            val key = ((if (material.darkTheme) 32 else if (material.light) 16 else 0) + chroma) * 151 +
                (material.tintScale * 100).roundToInt()
            return toneFilters.getOrPut(key) {
                val recipe = GlassMaterial.recipe(material.copy(chroma = chroma / 8f))
                val brightness = recipe.brightness
                ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(recipe.saturation)
                    postConcat(ColorMatrix(floatArrayOf(
                        brightness, 0f, 0f, 0f, 0f,
                        0f, brightness, 0f, 0f, 0f,
                        0f, 0f, brightness, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f)))
                })
            }
        }

        fun focus(on: Boolean) {
            focused = on
            refreshInk()
            val from = lit
            light.start(if (on) 260 else 200) { p ->
                lit = lerp(from, if (on) 1f else 0f, p)
                invalidateSelf()
                onFrame()
            }
        }

        fun release() {
            light.cancel()
            tintTween.cancel()
            (owner.getTag(R.id.motion_focus) as? Tween)?.cancel()
            bitmap = null
        }

        override fun onBoundsChange(bounds: Rect) {
            if (bounds.isEmpty) return
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            ordinaryMesh = MovingGlass.Mesh(w, h, radius.coerceAtMost(minOf(w, h) / 2), extreme = false)
            extremeMesh = null
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
            val isExtreme = extreme()
            paint.shader = null
            paint.alpha = if (isExtreme) opacity else (opacity * (if (content) 0.58f + 0.22f * lit else 0.8f + 0.2f * lit)).toInt()
            val texture = bitmap
            val movingTexture = live()
            val geometry = if (isExtreme && !bounds.isEmpty) {
                extremeMesh ?: MovingGlass.Mesh(rect.width(), rect.height(), r, extreme = true)
                    .also { extremeMesh = it }
            } else ordinaryMesh
            if (isExtreme && solid()) {
                paint.color = if (material.light) Color.rgb(240, 242, 247) else Color.rgb(20, 20, 24)
                paint.alpha = opacity
                canvas.drawRoundRect(rect, r, r, paint)
            } else if (isExtreme && transmission()) {
                paint.color = material.tint
                paint.alpha = (GlassMaterial.recipe(material).tintAlpha * opacity).roundToInt()
                canvas.drawRoundRect(rect, r, r, paint)
            } else if (movingTexture != null && geometry != null) {
                val filter = if (isExtreme) toneFilter() else null
                val amount = fade()
                val outgoing = if (amount < 1f) previous() else null
                // Old lens underneath at full strength, the new one over it: an opaque
                // cross-fade, the same maths as the backdrop's TransitionDrawable.
                outgoing?.draw(canvas, owner, geometry, paint.alpha, colorFilter = filter, chromatic = isExtreme)
                movingTexture.draw(
                    canvas, owner, geometry,
                    if (outgoing != null) (paint.alpha * amount).roundToInt() else paint.alpha,
                    colorFilter = filter,
                    chromatic = isExtreme,
                )
                if (outgoing != null) invalidateSelf()
                if (isExtreme) {
                    val tint = material.tint
                    val amount = GlassMaterial.recipe(material).tintAlpha
                    paint.color = tint
                    paint.alpha = (amount * opacity).roundToInt().coerceIn(0, 255)
                    canvas.drawRoundRect(rect, r, r, paint)
                } else if (texture != null) {
                    canvas.drawBitmap(texture, null, rect, paint)
                }
            }
            else if (texture != null) canvas.drawBitmap(texture, null, rect, paint)
            else {
                paint.color = if (isExtreme) Color.argb(opacity, 20, 20, 24) else Color.argb(opacity * 55 / 255, 210, 220, 235)
                canvas.drawRoundRect(rect, r, r, paint)
            }
            // A lit control keeps the wallpaper's colour, rather than turning opaque white.
            val highlight = if (isExtreme) {
                if (content) 40 * lit else 110 * lit
            } else {
                if (content) 3 + 8 * lit else 4 + 22 * lit
            }
            val highlightAlpha = (highlight * opacity / 255).toInt()
            // Unlit rows are most of a page: an invisible wash would still cost a draw call.
            if (highlightAlpha > 0) {
                paint.color = Color.argb(highlightAlpha, 255, 255, 255)
                canvas.drawRoundRect(rect, r, r, paint)
            }
            paint.shader = sheen
            val edgeStrength = if (isExtreme) GlassMaterial.recipe(material).edgeStrength else 1f
            paint.alpha = (opacity * (if (content) 0.3f else if (isExtreme) 0.4f else 0.6f) * edgeStrength).toInt()
            canvas.drawRoundRect(rect, r, r, paint)
            if (lit > 0f) {
                paint.shader = spot
                lightMatrix.setTranslate(bounds.width() * 0.32f * lit, 0f)
                spot?.setLocalMatrix(lightMatrix)
                paint.alpha = (opacity * lit * (if (content) 0.45f else 1f) * edgeStrength).toInt()
                canvas.drawRoundRect(rect, r, r, paint)
            }
            paint.shader = rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = owner.resources.displayMetrics.density * (0.6f + 0.35f * lit)
            // The rim is the only edge light on these rows, so a dark-theme
            // 1/3 edge strength would erase it. Keep it directional but visible.
            val rimStrength = if (isExtreme) 0.45f + 0.55f * edgeStrength else 1f
            paint.alpha = (opacity * (if (content) 0.22f + 0.32f * lit else 0.38f + 0.62f * lit) * rimStrength).toInt()
            rect.inset(paint.strokeWidth / 2, paint.strokeWidth / 2)
            canvas.drawRoundRect(rect, r, r, paint)
            if (isExtreme) {
                val inner = owner.resources.displayMetrics.density * 1.5f
                rect.inset(inner, inner)
                paint.strokeWidth = owner.resources.displayMetrics.density * 0.5f
                paint.alpha = (opacity * 0.3f * edgeStrength).toInt()
                canvas.drawRoundRect(rect, (r - inner).coerceAtLeast(0f), (r - inner).coerceAtLeast(0f), paint)
            }
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.alpha = 255
        }

        override fun getOutline(outline: Outline) = outline.setRoundRect(bounds,
            radius.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2f))
        override fun setAlpha(alpha: Int) {
            if (opacity == alpha) return
            opacity = alpha
            invalidateSelf()
        }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
