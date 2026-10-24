package com.leslie.tvhome

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.leslie.tvhome.tv.Input
import com.leslie.tvhome.tv.Level
import com.leslie.tvhome.tv.TvControls
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The Control Center, in one of two looks ([Prefs.ccGlass]): independent liquid-glass controls
 * floating over the home screen, or, on low-end TVs, the same controls on one plain dark panel.
 *
 * The glass is cheap to draw: the home screen behind the controls is captured once at a quarter
 * of its size and blurred when the Control Center opens (and again only if the screen behind
 * changes), and each control paints its share of that picture as a texture. Nothing is blurred,
 * clipped to a path or offscreen-rendered per frame, which kept the live GPU blur of v1.9 at
 * 15 ms of GPU time per frame on a BRAVIA (MT5895).
 */
class ControlCenter(private val home: HomeActivity, private val host: FrameLayout) {
    private val ctx = home
    private val prefs = Prefs(ctx)
    private val gap = ctx.dp(12)
    private val cell = ctx.dp(47)
    private val width = cell * 4 + gap * 3
    private var glassStyle = prefs.ccGlass(ctx)
    private val surfaces = ArrayList<GlassSurface>()
    private var destroyed = false
    private var refreshing = false

    // ---- the blurred picture of the home screen behind the controls ----
    private val sampleScale = 0.25f
    private var blur: Bitmap? = null
    private var blurShader: BitmapShader? = null
    /** Where [blur] starts, in host coordinates. */
    private var blurX = 0f
    private var blurY = 0f
    private var blurDirty = true
    private val resample = Runnable { if (blurDirty) sampleBehind() }
    /** Saturated and lifted, so the glass takes on the colour of what is behind it. */
    private val toneMatrix = ColorMatrix().apply {
        setSaturation(1.5f)
        postConcat(ColorMatrix(floatArrayOf(
            0.88f, 0f, 0f, 0f, 22f,
            0f, 0.88f, 0f, 0f, 22f,
            0f, 0f, 0.88f, 0f, 22f,
            0f, 0f, 0f, 1f, 0f)))
    }

    private val scrim = View(ctx).apply {
        setBackgroundColor(Color.argb(32, 0, 0, 0))
        alpha = 0f
        translationZ = ctx.dp(100).toFloat()
        // Raised only to sit above the tiles. With an outline, Android would also cast a
        // screen-sized shadow under it, redrawn every frame: about 5 ms of GPU time on a BRAVIA.
        outlineProvider = null
    }
    // Glass: layout only, the gaps between controls show the actual wallpaper. Panel: one dark card.
    private val panel = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        translationZ = ctx.dp(101).toFloat()
        outlineProvider = null
        visibility = View.INVISIBLE
        alpha = 1f
    }
    private val panelCard = rounded(Color.argb(228, 30, 30, 34), ctx.dp(28).toFloat())

    // ---- the light that follows focus, spilling onto neighbouring controls (glass only) ----
    private var glowX = 0f
    private var glowY = 0f
    private var glowR = 0f
    private var glowA = 0f
    private val glowTween = Tween()
    // Unit-radius light, moved and sized with a local matrix instead of a new shader per frame.
    private val pool = android.graphics.RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.argb(60, 255, 255, 255), Color.argb(95, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.TRANSPARENT),
        floatArrayOf(0f, 0.32f, 0.6f, 1f), Shader.TileMode.CLAMP)
    // Added rather than layered on: the glass glows brighter in its own colours instead of turning
    // milky. PorterDuff ADD is the same blend as BlendMode.PLUS and exists on Android 9.
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val panelToHost = Matrix()

    /** Glides the light to the newly focused control. */
    private fun glowAt(v: View, strength: Float = if (v is Slider) 0.5f else 1f) {
        if (!glassStyle) return
        if (v.width == 0) { v.post { if (v.isFocused) glowAt(v) }; return }
        val r = android.graphics.Rect(0, 0, v.width, v.height)
        panel.offsetDescendantRectToMyCoords(v, r)
        val tx = r.exactCenterX()
        val ty = r.exactCenterY()
        val tr = maxOf(v.width, v.height) * 0.5f + ctx.dp(110)
        val fromX = glowX; val fromY = glowY; val fromR = glowR; val fromA = glowA
        val fresh = fromA == 0f
        glowTween.start(300) { f ->
            glowX = if (fresh) tx else lerp(fromX, tx, f)
            glowY = if (fresh) ty else lerp(fromY, ty, f)
            glowR = if (fresh) tr else lerp(fromR, tr, f)
            glowA = lerp(fromA, strength, f)
            invalidateSurfaces()
        }
    }

    private fun invalidateSurfaces() { for (s in surfaces) s.owner.invalidate() }

    /** The glass body's colour filter, per wash strength and focus level (cached in 16 steps). */
    private val bodies = HashMap<Int, ColorMatrixColorFilter>()

    private fun body(wash: Int, lit: Float): ColorMatrixColorFilter {
        val step = (lit * 16).roundToInt().coerceIn(0, 16)
        return bodies.getOrPut(wash * 32 + step) {
            val l = step / 16f
            // A black wash at rest, white as it lights up: the same as layers over the toned picture.
            val black = 34f * (1f - l) / 255f
            val white = (10f + wash * l) / 255f
            val k = (1f - black) * (1f - white)
            val o = 255f * white
            ColorMatrixColorFilter(ColorMatrix(toneMatrix).apply {
                postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, o, 0f, k, 0f, 0f, o, 0f, 0f, k, 0f, o, 0f, 0f, 0f, 1f, 0f)))
            })
        }
    }

    /** The rim's share of the focus light, per strength (cached in 16 steps). */
    private val edges = arrayOfNulls<Shader>(17)

    private fun edgeFor(a: Float): Shader {
        val step = (a * 16).roundToInt().coerceIn(0, 16)
        return edges[step] ?: android.graphics.RadialGradient(0f, 0f, 1f,
            intArrayOf(Color.argb((230 * step / 16f).toInt(), 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            .also { edges[step] = it }
    }

    private val memButton: GlassButton
    private val brightness: Slider
    private val volume: Slider
    private val inputModule: GlassButton
    private val networkModule: GlassButton
    private var inputs: List<Input> = emptyList()
    private var volumeTarget = "speaker"
    private val status = ctx.text(11f, Color.argb(190, 255, 255, 255))
    private val focusables = ArrayList<View>()
    private val io = Executors.newSingleThreadExecutor()
    private val tv = TvControls(ctx)
    private val ticker = object : Runnable {
        override fun run() {
            followVolume()
            host.postDelayed(this, 2_000)
        }
    }

    private fun followVolume() {
        if (!isOpen) return
        io.execute {
            val v = tv.volume() ?: return@execute
            host.post { if (isOpen && !volume.hasFocus()) volume.set(v) }
        }
    }

    var isOpen = false
        private set

    init {
        // tvOS layout: a square power tile beside two capsules, then a row of round buttons.
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val power = GlassButton("Power off", R.drawable.ic_power, Shape.TILE) {
            confirm("Power off", "The TV will fully power down. Turn it back on with the power button on the remote.") { Cleaner.shutdown(ctx) }
        }
        top.addView(power, LinearLayout.LayoutParams(0, cell * 2 + gap, 1f).apply { marginEnd = gap })
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        networkModule = GlassButton("Network", R.drawable.ic_wifi, Shape.CAPSULE, accent = Color.rgb(48, 222, 239)) {
            close()
            runCatching { ctx.startActivity(tv.wifiIntent()) }
        }
        right.addView(networkModule, LinearLayout.LayoutParams(-1, cell))
        right.addView(GlassButton("Standby", R.drawable.ic_moon, Shape.CAPSULE) { needAdb { close(); Cleaner.sleep(ctx) } },
            LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })
        top.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(top)

        val utilities = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        fun utility(label: String, icon: Int, action: () -> Unit): GlassButton =
            GlassButton(label, icon, Shape.ROUND, action = action).also {
                utilities.addView(it, LinearLayout.LayoutParams(cell, cell).apply {
                    if (utilities.childCount > 0) marginStart = (this@ControlCenter.width - cell * 4) / 3
                })
            }
        memButton = utility("Clear memory", R.drawable.ic_memory) { cleanNow() }
        utility("Frame", R.drawable.ic_photo) {
            close()
            ctx.openFrame()
        }
        utility("Restart", R.drawable.ic_restart) {
            confirm("Restart", "The TV will restart. This takes about a minute.") { Cleaner.reboot(ctx) }
        }
        utility("Settings", R.drawable.ic_gear) {
            close()
            ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
        }
        panel.addView(utilities, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })

        brightness = Slider(R.drawable.ic_sun, "Brightness") { v -> tv.setBrightness(v) }.also { focusables += it }
        panel.addView(brightness, LinearLayout.LayoutParams(-1, ctx.dp(38)).apply { topMargin = gap })
        volume = Slider(R.drawable.ic_volume, "Volume", steps = 50) { v -> tv.setVolume(volumeTarget, v) }.also { focusables += it }
        panel.addView(volume, LinearLayout.LayoutParams(-1, ctx.dp(38)).apply { topMargin = gap })
        inputModule = GlassButton("Input", R.drawable.ic_input, Shape.CAPSULE) { pickInput() }
        panel.addView(inputModule, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })
        panel.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(10) })
        // The host clips the panel to its bounds; the padding is room for focused controls to grow into.
        val room = ctx.dp(16)
        panel.setPadding(room, room, room, room)
        host.addView(panel, FrameLayout.LayoutParams(width + room * 2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = ctx.dp(66) - room; marginEnd = ctx.dp(32) - room
        })
        panel.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (l != oldL || t != oldT || r != oldR || b != oldB) refreshGlass()
        }
        panel.post { refresh() }
        host.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
            if (isOpen && now != null && focusables.contains(now)) glowAt(now)
        }
        // Report cleanup without expanding the button and moving every control below it.
        status.setShadowLayer(ctx.dp(3).toFloat(), 0f, 0f, Color.BLACK)
        applyStyle()
    }

    /** Picks up a change of [Prefs.ccStyle] made in settings. */
    fun applyStyle() {
        val glass = prefs.ccGlass(ctx)
        if (glass == glassStyle && panel.tag != null) return
        panel.tag = true
        glassStyle = glass
        panel.background = if (glass) null else panelCard
        if (!glass) { blur = null; blurShader = null }
        blurDirty = true
        invalidateSurfaces()
    }

    /** Maps [v]'s own coordinates to the host's, through every scale and translation on the way. */
    private fun toHost(v: View, out: Matrix) {
        out.reset()
        var cur: View = v
        while (cur !== host) {
            out.postConcat(cur.matrix)
            val p = cur.parent as? View ?: break
            out.postTranslate((cur.left - p.scrollX).toFloat(), (cur.top - p.scrollY).toFloat())
            cur = p
        }
    }

    /** Each control owns its own background: its share of the blurred screen, a sheen, a lit rim. */
    private inner class GlassSurface(val owner: View, val radius: Float) : Drawable() {
        /** How much white the focused glass takes on; sliders keep it low so their fill reads. */
        var focusWash = 140
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()
        private val ownerToHost = Matrix()
        private val hostToOwner = Matrix()
        private val m = Matrix()
        init { surfaces += this }

        /** 0 = resting, 1 = focused; animated so the light swells in rather than switching. */
        var lit = 0f
            set(value) { field = value; invalidateSelf() }
        private val litTween = Tween()
        private var sheen: Shader? = null
        private var rim: Shader? = null
        private var spot: Shader? = null
        private val r get() = minOf(radius, bounds.width() / 2f, bounds.height() / 2f)

        fun light(on: Boolean) {
            val from = lit
            litTween.start(if (on) 280 else 200) { f -> lit = lerp(from, if (on) 1f else 0f, f) }
        }

        override fun onBoundsChange(b: android.graphics.Rect) {
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            // A sheen across the upper half, as light falling on a curved top surface.
            sheen = android.graphics.LinearGradient(0f, 0f, 0f, h,
                intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP)
            // Rim: bright where it faces the light (top left), a softer reflection bottom right.
            rim = android.graphics.LinearGradient(0f, 0f, w, h,
                intArrayOf(Color.WHITE, Color.argb(40, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.argb(170, 255, 255, 255)),
                floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
            // The focus light: a soft glow pooled under the top left of the glass.
            spot = android.graphics.RadialGradient(w * 0.3f, h * 0.1f, maxOf(w, h) * 0.95f,
                intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null, Shader.TileMode.CLAMP)
        }

        override fun draw(c: Canvas) {
            rect.set(bounds)
            val r = r
            if (!glassStyle) { drawFlat(c, r); return }
            toHost(owner, ownerToHost)
            ownerToHost.invert(hostToOwner)
            // Every full-size pass costs about a millisecond of GPU time per frame on a BRAVIA's
            // Mali, so what can be merged is: three passes when resting, five when lit.
            val shader = blurShader
            if (shader != null) {
                // The blurred screen, mapped so this control shows exactly the part behind it,
                // with the milky wash and the focus brightening folded into its colour filter.
                m.setScale(1f / sampleScale, 1f / sampleScale)
                m.postTranslate(blurX, blurY)
                m.postConcat(hostToOwner)
                shader.setLocalMatrix(m)
                glassPaint.shader = shader
                glassPaint.colorFilter = body(focusWash, lit)
                c.drawRoundRect(rect, r, r, glassPaint)
            } else {
                paint.color = Color.argb((90 + focusWash * lit).toInt().coerceAtMost(255), 48, 52, 58)
                c.drawRoundRect(rect, r, r, paint)
            }
            // A sheen across the top, as light falling on a curved surface; a pooled glow when lit.
            paint.color = Color.WHITE
            paint.shader = sheen
            paint.alpha = (110 + 145 * lit).toInt()
            c.drawRoundRect(rect, r, r, paint)
            if (lit > 0f) {
                paint.shader = spot
                paint.alpha = (255 * lit).toInt()
                c.drawRoundRect(rect, r, r, paint)
            }
            // The focused control lights the glass around it: a pool of light spilling into its
            // neighbours, and their rims catching it. Kept in panel coordinates, so it rides
            // along with the panel's own motion; controls out of its reach skip it.
            val glow = glowA > 0f && nearGlow()
            if (glow) {
                toHost(panel, panelToHost)
                m.setScale(glowR, glowR)
                m.postTranslate(glowX, glowY)
                m.postConcat(panelToHost)
                m.postConcat(hostToOwner)
                pool.setLocalMatrix(m)
                lightPaint.shader = pool
                lightPaint.alpha = (255 * glowA).toInt()
                c.drawRoundRect(rect, r, r, lightPaint)
            }
            // Rim: bright where it faces the light (top left), a softer reflection bottom right,
            // and the focus light caught on it, all in one stroke.
            val rimShader = rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ctx.dp(1f + 0.6f * lit).toFloat()
            paint.alpha = (150 + 105 * lit).toInt()
            paint.shader = rimShader
            if (glow && rimShader != null) {
                val e = edgeFor(glowA)
                m.setScale(glowR * 2.2f, glowR * 2.2f)
                m.postTranslate(glowX, glowY)
                m.postConcat(panelToHost)
                m.postConcat(hostToOwner)
                e.setLocalMatrix(m)
                paint.shader = android.graphics.ComposeShader(rimShader, e, PorterDuff.Mode.ADD)
            }
            val inset = paint.strokeWidth / 2
            rect.inset(inset, inset)
            c.drawRoundRect(rect, r, r, paint)
            paint.shader = null
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }

        private val ownerRect = android.graphics.Rect()

        /** Whether the focus light reaches this control at all (its rim light reaches 2.2x as far). */
        private fun nearGlow(): Boolean {
            ownerRect.set(0, 0, owner.width, owner.height)
            try { panel.offsetDescendantRectToMyCoords(owner, ownerRect) } catch (e: IllegalArgumentException) { return true }
            val reach = glowR * 2.2f
            val dx = maxOf(ownerRect.left - glowX, 0f, glowX - ownerRect.right)
            val dy = maxOf(ownerRect.top - glowY, 0f, glowY - ownerRect.bottom)
            return dx * dx + dy * dy < reach * reach
        }

        /** The panel look: flat translucent controls that turn white when focused. */
        private fun drawFlat(c: Canvas, r: Float) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            val a = lerp(34f, if (focusWash > 0) 255f else 70f, lit).toInt()
            paint.color = Color.argb(a, 255, 255, 255)
            c.drawRoundRect(rect, r, r, paint)
        }

        override fun getOutline(outline: android.graphics.Outline) = outline.setRoundRect(bounds, r)
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun glassFor(view: View, radius: Float): GlassSurface = GlassSurface(view, radius).also {
        view.background = it
        view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        view.clipToOutline = true
        // No elevation: through translucent glass the platform shadow shows a hard-edged hole.
    }

    private fun focus(view: View, glass: GlassSurface, on: Boolean, grow: Float = 1.08f) {
        glass.light(on)
        // The focused control lifts towards the viewer; its glass keeps showing what is behind it.
        view.tweenScale(if (on) grow else 1f, if (on) 260 else 200, if (on) Motion.spring else Motion.ease) {
            if (glassStyle) view.invalidate()
        }
    }

    /** The screen behind changed (background, shelf, layout): the glass samples it again. */
    fun refreshGlass() {
        if (destroyed) return
        blurDirty = true
        if (glassStyle) {
            host.removeCallbacks(resample)
            host.postDelayed(resample, 80)
        }
    }

    /**
     * Captures the home screen behind the controls at a quarter of its size and blurs it, on the
     * UI thread: a few milliseconds, once per opening at most.
     */
    private fun sampleBehind() {
        if (destroyed || !glassStyle || host.width == 0 || panel.width == 0) return
        val margin = ctx.dp(48)
        val left = (panel.left - margin).coerceAtLeast(0)
        val top = (panel.top - margin).coerceAtLeast(0)
        val right = (panel.right + margin).coerceAtMost(host.width)
        val bottom = (panel.bottom + margin).coerceAtMost(host.height)
        val w = ((right - left) * sampleScale).roundToInt().coerceAtLeast(1)
        val h = ((bottom - top) * sampleScale).roundToInt().coerceAtLeast(1)
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply {
                    scale(sampleScale, sampleScale)
                    translate(-left.toFloat(), -top.toFloat())
                    // Keep the last good texture until the wallpaper is ready after resume/recreation.
                    if (!home.drawGlassScene(this)) return
                }
            }
        } catch (e: RuntimeException) {
            return
        }
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // Three box passes come close to a Gaussian blur.
        repeat(3) { LiquidGlass.boxBlur(px, w, h, 7) }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        bmp.prepareToDraw()
        // Not recycled: a frame still being drawn may hold the previous picture. It is small.
        blur = bmp
        blurShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        blurX = left.toFloat()
        blurY = top.toFloat()
        blurDirty = false
        invalidateSurfaces()
    }

    private enum class Shape { TILE, CAPSULE, ROUND }

    private inner class GlassButton(
        label: String,
        icon: Int,
        shape: Shape,
        accent: Int = Color.WHITE,
        action: () -> Unit,
    ) : LinearLayout(ctx) {
        // Capsules and circles clamp this to half their height.
        private val glass = glassFor(this, ctx.dp(if (shape == Shape.TILE) 24 else 100).toFloat())
        private val glyph = ImageView(ctx)
        val title = ctx.text(12.5f, weight = 600).apply { text = label; maxLines = 1 }
        val sub = ctx.text(10.5f, Color.argb(205, 255, 255, 255), weight = 500).apply {
            visibility = GONE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        init {
            id = View.generateViewId()
            contentDescription = label
            focusables += this
            orientation = if (shape == Shape.CAPSULE) HORIZONTAL else VERTICAL
            gravity = if (shape == Shape.CAPSULE) Gravity.CENTER_VERTICAL else Gravity.CENTER
            isFocusable = true
            isClickable = true
            if (shape == Shape.CAPSULE) setPadding(ctx.dp(13), 0, ctx.dp(10), 0)
            val size = ctx.dp(when (shape) { Shape.TILE -> 40; Shape.CAPSULE -> 20; Shape.ROUND -> 21 })
            addView(glyph.apply { setImageResource(icon); setColorFilter(accent) },
                LayoutParams(size, size).apply { if (shape == Shape.CAPSULE) marginEnd = ctx.dp(9) })
            // Round buttons are icon-only, as on tvOS; the label stays for accessibility.
            when (shape) {
                Shape.TILE -> addView(title, LayoutParams(-2, -2).apply { topMargin = ctx.dp(8) })
                Shape.CAPSULE -> addView(LinearLayout(ctx).apply {
                    orientation = VERTICAL
                    addView(title)
                    addView(sub, LayoutParams(-1, -2).apply { topMargin = ctx.dp(1) })
                }, LayoutParams(0, -2, 1f))
                Shape.ROUND -> {}
            }
            setOnClickListener { action() }
            setOnFocusChangeListener { _, on ->
                focus(this, glass, on, when (shape) { Shape.TILE -> 1.06f; Shape.CAPSULE -> 1.05f; Shape.ROUND -> 1.1f })
                // Lit glass is bright: dark glyphs keep the focused control readable on any wallpaper.
                val ink = Color.argb(225, 0, 0, 0)
                glyph.setColorFilter(if (on) ink else accent)
                title.setTextColor(if (on) ink else Color.WHITE)
                sub.setTextColor(if (on) Color.argb(170, 0, 0, 0) else Color.argb(205, 255, 255, 255))
            }
        }
    }

    /**
     * iOS-style capsule slider: the white fill is the value. Left/right change it,
     * and the TV follows while the key is held.
     */
    private inner class Slider(
        icon: Int,
        label: String,
        private val steps: Int = 25,
        private val send: (Int) -> Unit,
    ) : FrameLayout(ctx) {
        private val corner = ctx.dp(19).toFloat()
        private val glass = glassFor(this, corner).apply { focusWash = 0 }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillClip = android.graphics.Path()
        private var darkIcon = false
        private var lastInteraction = 0L
        private val img = ImageView(ctx).apply { setImageResource(icon) }
        private val nameText = ctx.text(12.5f, weight = 600).apply { text = label }
        private val valueText = ctx.text(12.5f, weight = 600)
        private var value = 0
        private var min = 0
        private var max = 100
        private var ready = false
        // Logical target drives hardware writes; displayedValue is visual-only and animates between targets.
        private var displayedValue = 0f
        private var valueAnimator: android.animation.ValueAnimator? = null
        // One write in flight at a time; presses that land meanwhile collapse into the latest value,
        // so holding the key changes the TV as fast as it can take it (BRAVIA: ~45 ms per write).
        private var sending = false
        private var sent = Int.MIN_VALUE

        private fun push() {
            if (sending || value == sent) return
            sending = true
            val v = value
            sent = v
            io.execute {
                send(v)
                host.post { sending = false; push() }
            }
        }

        init {
            isFocusable = true
            clipRounded(corner)
            setWillNotDraw(false)
            contentDescription = label
            addView(img, LayoutParams(ctx.dp(18), ctx.dp(18), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = ctx.dp(14) })
            addView(nameText, LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = ctx.dp(41) })
            addView(valueText, LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.END).apply { marginEnd = ctx.dp(15) })
            setOnFocusChangeListener { _, f ->
                focus(this, glass, f, 1.03f)
                draw()
            }
            setOnKeyListener { _, code, e ->
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return@setOnKeyListener false
                if (e.action == KeyEvent.ACTION_DOWN && ready) {
                    val dir = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    val step = ((max - min) / steps).coerceAtLeast(1)
                    val nv = (value + dir * step).coerceIn(min, max)
                    if (nv == value) {
                        if (e.repeatCount == 0) Motion.bump(this, dir, 0)
                    } else {
                        lastInteraction = android.os.SystemClock.uptimeMillis()
                        value = nv
                        valueText.text = value.toString()
                        animateValueTo(value)
                        push()
                    }
                }
                true
            }
            set(null)
        }

        fun set(level: Level?) {
            // An asynchronous read started before a key press must not overwrite its newer target.
            if (ready && (sending || android.os.SystemClock.uptimeMillis() - lastInteraction < 600)) return
            // The panel polls every 2 s; an unchanged reading must not relayout and redraw the panel.
            if (ready && level != null && level.value.coerceIn(level.min, level.max) == value &&
                level.min == min && level.max == max) return
            val wasReady = ready
            val oldTarget = value
            ready = level != null
            if (level != null) {
                min = level.min
                max = level.max
                value = level.value.coerceIn(min, max)
                sent = value
                valueText.text = value.toString()
                if (!wasReady) {
                    displayedValue = value.toFloat()
                    draw()
                } else if (value != oldTarget) animateValueTo(value) else draw()
            } else {
                valueAnimator?.cancel()
                valueAnimator = null
                displayedValue = min.toFloat()
                valueText.text = "—"
                draw()
            }
            alpha = if (ready) 1f else 0.45f
        }

        private fun animateValueTo(target: Int) {
            valueAnimator?.cancel()
            val end = target.toFloat()
            if (displayedValue == end) {
                draw()
                return
            }
            valueAnimator = android.animation.ValueAnimator.ofFloat(displayedValue, end).apply {
                duration = 100L
                interpolator = Motion.ease
                addUpdateListener {
                    displayedValue = it.animatedValue as Float
                    draw()
                }
                start()
            }
        }

        fun finishAnimation() {
            valueAnimator?.cancel()
            valueAnimator = null
            displayedValue = value.toFloat()
            draw()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            draw()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val fraction = if (ready && max > min)
                ((displayedValue - min) / (max - min)).coerceIn(0f, 1f) else 0f
            fillPaint.color = Color.argb(if (isFocused) 255 else 190, 255, 255, 255)
            val save = canvas.save()
            fillClip.reset()
            fillClip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), corner, corner, android.graphics.Path.Direction.CW)
            canvas.clipPath(fillClip)
            canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), fillPaint)
            canvas.restoreToCount(save)
        }

        private fun draw() {
            if (width <= 0) return
            val fraction = if (ready && max > min)
                ((displayedValue - min) / (max - min)).coerceIn(0f, 1f) else 0f
            val filled = width * fraction
            val dark = Color.argb(230, 0, 0, 0)
            val iconIsDark = filled > ctx.dp(32)
            if (darkIcon != iconIsDark) {
                darkIcon = iconIsDark
                img.setColorFilter(if (darkIcon) dark else Color.WHITE)
            }
            val nameColor = if (filled > ctx.dp(41) + nameText.width) dark else Color.WHITE
            if (nameText.currentTextColor != nameColor) nameText.setTextColor(nameColor)
            val valueColor = if (filled > width - ctx.dp(15)) dark else Color.WHITE
            if (valueText.currentTextColor != valueColor) valueText.setTextColor(valueColor)
            // Only drawing changes. No animated child width or per-frame text relayout.
            invalidate()
        }
    }

    // ---- open / close -------------------------------------------------------------

    fun open() {
        if (isOpen) return
        isOpen = true
        glowTween.cancel()
        glowA = 0f
        home.pauseShelfRotation()
        panel.animate().cancel()
        scrim.animate().cancel()
        refresh()
        home.setGridBlocked(true)
        // A full-screen pass every frame: only the panel look, which hides less of the screen, uses it.
        if (scrim.parent == null && !glassStyle) host.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        panel.bringToFront()
        // Sampled before the first frame, so the glass never shows up empty.
        if (glassStyle && (blurDirty || blur == null)) sampleBehind()
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.translationX = ctx.dp(24).toFloat()
        panel.scaleX = 0.97f
        panel.scaleY = 0.97f
        panel.pivotX = panel.width - panel.paddingRight.toFloat()
        panel.pivotY = panel.paddingTop.toFloat()
        scrim.animate().alpha(1f).setDuration(220).start()
        panel.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(Motion.ease).setUpdateListener { if (glassStyle) invalidateSurfaces() }.start()
        memButton.requestFocus()
        host.removeCallbacks(ticker)
        host.post(ticker)
    }

    fun atRightEdge(): Boolean {
        val current = panel.findFocus() ?: return false
        // Right on a slider must continue to adjust its value.
        if (current.contentDescription == "Brightness" || current.contentDescription == "Volume") return false
        val next = current.focusSearch(View.FOCUS_RIGHT)
        var parent: android.view.ViewParent? = next?.parent
        while (parent != null) {
            if (parent === panel) return false
            parent = parent.parent
        }
        return true
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        host.removeCallbacks(ticker)
        brightness.finishAnimation()
        volume.finishAnimation()
        scrim.animate().alpha(0f).setDuration(180).start()
        panel.animate().alpha(0f).translationX(ctx.dp(24).toFloat()).scaleX(0.97f).scaleY(0.97f).setDuration(200)
            .setUpdateListener { if (glassStyle) invalidateSurfaces() }
            .withEndAction {
                host.removeView(scrim)
                panel.visibility = View.INVISIBLE
            }.start()
        home.restoreGridFocus()
        home.resumeShelfRotation()
    }

    fun destroy() {
        destroyed = true
        isOpen = false
        host.removeCallbacks(ticker)
        host.removeCallbacks(resample)
        panel.animate().cancel()
        scrim.animate().cancel()
        glowTween.cancel()
        blur = null
        blurShader = null
        io.shutdown()
    }

    fun refocus() {
        (focusables.firstOrNull { it.isFocused } ?: memButton).requestFocus()
    }

    fun refresh() {
        if (destroyed) return
        status.text = if (prefs.adbAuthorized) "" else "Local debugging not authorized: power off, standby and deep clean unavailable"
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val wired = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
        networkModule.title.text = if (wired) "Wired network" else "Wi-Fi"
        networkModule.sub.text = if (caps == null) "Not connected" else "Connected"
        networkModule.sub.visibility = View.VISIBLE
        if (refreshing) return
        refreshing = true
        io.execute {
            val b = tv.brightness()
            val v = tv.volume()
            val list = tv.inputs()
            val cur = tv.currentInput()
            val summary = tv.inputSummary(list)
            host.post {
                refreshing = false
                if (destroyed) return@post
                brightness.set(b)
                brightness.visibility = if (b == null) View.GONE else View.VISIBLE
                v?.target?.takeIf { it.isNotEmpty() }?.let { volumeTarget = it }
                volume.set(v)
                inputs = list
                inputModule.sub.visibility = View.VISIBLE
                inputModule.sub.text = list.firstOrNull { it.uri == cur }?.name ?: summary
            }
        }
    }

    private fun pickInput() {
        if (inputs.isEmpty()) return
        // The list replaces the panel instead of stacking on top of it.
        close()
        val sheet = Sheet(host).title("Input").message(null)
        for (i in inputs) sheet.button(i.name, R.drawable.ic_input) {
            io.execute { tv.setInput(i) }
        }
        sheet.button("Show on Home Screen…") { pinInputs() }
        sheet.button("Cancel") {}
        home.showSheet(sheet)
    }

    /** Inputs shown as tiles on the home grid; one press there switches to them. */
    private fun pinInputs() {
        val sheet = Sheet(host).title("Show on Home Screen")
            .message("The selected input becomes an icon on the home screen. Press it to switch.")
        for (i in inputs) {
            val on = home.isPinned(i)
            sheet.button(if (on) "✓  ${i.name}" else i.name, R.drawable.ic_input) { home.pinInput(i, !on) }
        }
        sheet.button("Done") {}
        home.showSheet(sheet)
    }

    private fun cleanNow() {
        status.text = "Freeing memory…"
        status.visibility = View.VISIBLE
        Thread {
            val r = Cleaner.clean(ctx.applicationContext)
            host.post {
                status.text = r.description()
            }
        }.start()
    }

    private fun needAdb(action: () -> Unit) {
        if (prefs.adbAuthorized) action() else home.showSheet(Sheet(host).title("Local debugging must be authorized first")
            .message("After you tap Start, the TV will ask “Allow debugging?”. Check “Always allow”, then tap Allow.")
            .button("Start") { home.authorizeAdb() }
            .button("Cancel") {})
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) = needAdb {
        home.showSheet(Sheet(host).title("$title？").message(msg)
            .button(title) { action() }
            .button("Cancel") {})
    }
}
