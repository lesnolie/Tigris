package com.leslie.tvhome

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
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
    private var extremeGlass = prefs.extremeGlass
    private val surfaces = ArrayList<GlassSurface>()
    private var destroyed = false
    private var refreshing = false

    // ---- the blurred picture of the home screen behind the controls ----
    private val sampleScale get() = if (extremeGlass) 0.5f else 0.25f
    private var blur: Bitmap? = null
    private var blurPixels: IntArray? = null
    private var blurShader: BitmapShader? = null
    private var lens: MovingGlass? = null
    private var videoLens = false
    private var backdropScale = 0.25f
    private var nextLiveToneAt = 0L
    private val glassWorker = Executors.newSingleThreadExecutor()
    private var sampling = false
    private var sampleRevision = 0
    private var appearance = ""
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
    private val extremeTones = HashMap<Int, ColorMatrix>()
    private val night get() = (ctx.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun extremeTone(tone: GlassTone, chromaStep: Int): ColorMatrix {
        val key = ((if (tone.darkTheme) 32 else if (tone.light) 16 else 0) + chromaStep) * 151 +
            (tone.tintScale * 100).roundToInt()
        return extremeTones.getOrPut(key) {
            val recipe = GlassMaterial.recipe(tone.copy(chroma = chromaStep / 8f))
            val brightness = recipe.brightness
            ColorMatrix().apply {
                setSaturation(recipe.saturation)
                postConcat(ColorMatrix(floatArrayOf(
                    brightness, 0f, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f, 0f,
                    0f, 0f, brightness, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f)))
            }
        }
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
    private val shadowPad = ctx.dp(10)
    private val shadowOffset = ctx.dp(2)
    private val shadowCache = HashMap<String, Bitmap>()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadows = Shadows()

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
    private val bodies = HashMap<String, ColorMatrixColorFilter>()

    private fun body(wash: Int, lit: Float, material: GlassTone?): ColorMatrixColorFilter {
        if (bodies.size > 512) bodies.clear()
        val step = (lit * 16).roundToInt().coerceIn(0, 16)
        val tintKind = when {
            !extremeGlass || material == null -> 0
            material.light -> 1
            else -> 2
        }
        val chromaStep = ((material?.chroma ?: 0f) * 8f).roundToInt().coerceIn(0, 8)
        val toneKey = tintKind * 9 + if (extremeGlass) chromaStep else 0
        val tintAlpha = if (extremeGlass) material?.let {
            GlassMaterial.recipe(it).tintAlpha * (1f - step / 16f)
        } ?: 0f else 0f
        val tintStep = (tintAlpha * 32f).roundToInt().coerceIn(0, 32)
        val mode = if (extremeGlass) 1 else 0
        return bodies.getOrPut("$mode:$toneKey:$tintStep:$step:$wash:${material?.darkTheme}:${material?.tintScale}:${material?.tint}") {
            val l = step / 16f
            // A black wash at rest, white as it lights up: the same as layers over the toned picture.
            val black = if (extremeGlass) 0f else 34f * (1f - l) / 255f
            val white = if (extremeGlass) (if (wash > 0) 0.46f * l else 0f) else (10f + wash * l) / 255f
            val washK = (1f - black) * (1f - white)
            val washO = 255f * white
            // Fold the adaptive tint into the same colour matrix as the wash, so
            // sampling the wallpaper still costs no extra draw pass.
            val amount = tintStep / 32f
            val tint = material?.tint ?: 0
            val k = washK * (1f - amount)
            val r = washO * (1f - amount) + GlassMaterial.red(tint) * amount
            val g = washO * (1f - amount) + GlassMaterial.green(tint) * amount
            val b = washO * (1f - amount) + GlassMaterial.blue(tint) * amount
            val base = when {
                !extremeGlass -> toneMatrix
                else -> extremeTone(material ?: GlassTone.Fallback, chromaStep)
            }
            ColorMatrixColorFilter(ColorMatrix(base).apply {
                postConcat(ColorMatrix(floatArrayOf(
                    k, 0f, 0f, 0f, r,
                    0f, k, 0f, 0f, g,
                    0f, 0f, k, 0f, b,
                    0f, 0f, 0f, 1f, 0f)))
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
    private val mediaModule: GlassButton
    private lateinit var mediaPrevious: GlassButton
    private lateinit var mediaNext: GlassButton
    private val profileModule: GlassButton
    // Each Control Center page stays within the TV safe area; no unbounded vertical stack.
    private val generalPage = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
    private val mediaPage = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
    private val profilePage = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
    private lateinit var controlsTab: GlassButton
    private lateinit var mediaTab: GlassButton
    private lateinit var profileTab: GlassButton
    private var selectedTab = 0

    private fun selectTab(index: Int, moveFocus: Boolean = true) {
        selectedTab = if (index == 1 && mediaTab.visibility != View.VISIBLE) 0 else index.coerceIn(0, 2)
        generalPage.visibility = if (selectedTab == 0) View.VISIBLE else View.GONE
        mediaPage.visibility = if (selectedTab == 1) View.VISIBLE else View.GONE
        profilePage.visibility = if (selectedTab == 2) View.VISIBLE else View.GONE
        if (moveFocus && isOpen) {
            (when (selectedTab) { 1 -> mediaModule; 2 -> profileModule; else -> memButton }).requestFocus()
        }
        if (isOpen) host.post { refreshGlass() }
    }
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

    private fun sendTransport(action: String) {
        io.execute {
            val success = if (action == "toggle") NowPlaying.toggle(ctx)
                          else NowPlaying.transport(ctx, action)
            host.post {
                if (!success) android.widget.Toast.makeText(ctx,
                    R.string.tvos_now_playing_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                refresh()
            }
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
        // tvOS-style contextual tabs; Now Playing appears only for an authorized active session.
        val tabRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        controlsTab = GlassButton(ctx.getString(R.string.tvos_controls), R.drawable.ic_gear, Shape.CAPSULE) { selectTab(0) }
        mediaTab = GlassButton(ctx.getString(R.string.tvos_now_playing), R.drawable.ic_volume, Shape.CAPSULE) { selectTab(1) }
        profileTab = GlassButton(ctx.getString(R.string.tvos_profiles), R.drawable.ic_user, Shape.CAPSULE) { selectTab(2) }
        listOf(controlsTab, mediaTab, profileTab).forEachIndexed { index, button ->
            tabRow.addView(button, LinearLayout.LayoutParams(0, cell, 1f).apply {
                if (index > 0) leftMargin = ctx.dp(5)
            })
        }
        panel.addView(tabRow, LinearLayout.LayoutParams(-1, cell))
        panel.addView(generalPage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        panel.addView(mediaPage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        panel.addView(profilePage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        mediaPage.visibility = View.GONE
        profilePage.visibility = View.GONE

        // tvOS layout: a square power tile beside two capsules, then a row of round buttons.
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val power = GlassButton(I18n.text(R.string.i18n_controlcenter_001), R.drawable.ic_power, Shape.TILE) {
            confirm(I18n.text(R.string.i18n_controlcenter_002), I18n.text(R.string.i18n_controlcenter_003)) { Cleaner.shutdown(ctx) }
        }
        top.addView(power, LinearLayout.LayoutParams(0, cell * 2 + gap, 1f).apply { marginEnd = gap })
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        networkModule = GlassButton(I18n.text(R.string.i18n_controlcenter_004), R.drawable.ic_wifi, Shape.CAPSULE, accent = Color.rgb(48, 222, 239)) {
            close()
            runCatching { ctx.startActivity(tv.wifiIntent()) }
        }
        right.addView(networkModule, LinearLayout.LayoutParams(-1, cell))
        right.addView(GlassButton(I18n.text(R.string.i18n_controlcenter_005), R.drawable.ic_moon, Shape.CAPSULE) { needAdb { close(); Cleaner.sleep(ctx) } },
            LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })
        top.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        generalPage.addView(top)

        val utilities = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        fun utility(label: String, icon: Int, action: () -> Unit): GlassButton =
            GlassButton(label, icon, Shape.ROUND, action = action).also {
                utilities.addView(it, LinearLayout.LayoutParams(cell, cell).apply {
                    if (utilities.childCount > 0) marginStart = (this@ControlCenter.width - cell * 4) / 3
                })
            }
        memButton = utility(I18n.text(R.string.i18n_controlcenter_006), R.drawable.ic_memory) { cleanNow() }
        utility(I18n.text(R.string.i18n_controlcenter_007), R.drawable.ic_photo) {
            close()
            ctx.openFrame()
        }
        utility(I18n.text(R.string.i18n_controlcenter_008), R.drawable.ic_restart) {
            confirm(I18n.text(R.string.i18n_controlcenter_009), I18n.text(R.string.i18n_controlcenter_010)) { Cleaner.reboot(ctx) }
        }
        utility(I18n.text(R.string.i18n_controlcenter_011), R.drawable.ic_gear) {
            close()
            ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
        }
        generalPage.addView(utilities, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })

        brightness = Slider(R.drawable.ic_sun, I18n.text(R.string.i18n_controlcenter_012)) { v -> tv.setBrightness(v) }.also { focusables += it }
        generalPage.addView(brightness, LinearLayout.LayoutParams(-1, ctx.dp(38)).apply { topMargin = gap })
        volume = Slider(R.drawable.ic_volume, I18n.text(R.string.i18n_controlcenter_013), steps = 50) { v -> tv.setVolume(volumeTarget, v) }.also { focusables += it }
        generalPage.addView(volume, LinearLayout.LayoutParams(-1, ctx.dp(38)).apply { topMargin = gap })
        inputModule = GlassButton(I18n.text(R.string.i18n_controlcenter_014), R.drawable.ic_input, Shape.CAPSULE) { pickInput() }
        generalPage.addView(inputModule, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })

        // Apple TV-inspired quick actions. No global Home-key interception or system tasks.
        val tools = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        val recentButton = GlassButton(ctx.getString(R.string.tvos_recents_title), R.drawable.ic_apps, Shape.CAPSULE) {
            close()
            ctx.startActivity(Intent(ctx, AppSwitcherActivity::class.java))
        }
        tools.addView(recentButton, LinearLayout.LayoutParams(0, cell, 1f).apply { marginEnd = ctx.dp(6) })
        val searchButton = GlassButton(ctx.getString(R.string.tvos_search_title), R.drawable.ic_search, Shape.CAPSULE) {
            close()
            ctx.startActivity(Intent(ctx, TvOSSearchActivity::class.java))
        }
        tools.addView(searchButton, LinearLayout.LayoutParams(0, cell, 1f))
        mediaPage.addView(tools, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })

        profileModule = GlassButton(ctx.getString(R.string.tvos_profiles), R.drawable.ic_user, Shape.CAPSULE) {
            val names = (1..3).map { ctx.getString(R.string.tvos_profile_number, it) }.toTypedArray()
            android.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.tvos_profiles)
                .setSingleChoiceItems(names, prefs.activeProfile) { dialog, selected ->
                    dialog.dismiss()
                    close()
                    home.switchProfile(selected)
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        profilePage.addView(profileModule, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })

        profilePage.addView(GlassButton(ctx.getString(R.string.tvos_device_settings_title),
            R.drawable.ic_gear, Shape.CAPSULE) {
            close()
            ctx.startActivity(Intent(ctx, TvOSDeviceSettingsActivity::class.java))
        }, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })
        mediaModule = GlassButton(ctx.getString(R.string.tvos_now_playing), R.drawable.ic_volume, Shape.CAPSULE) {
            // MediaSession actions only when Android explicitly grants notification-listener access.
            io.execute {
                val ok = NowPlaying.toggle(ctx)
                host.post {
                    if (!ok) android.widget.Toast.makeText(ctx, R.string.tvos_now_playing_unavailable,
                        android.widget.Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
        mediaPage.addView(mediaModule, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })
        val transport = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        mediaPrevious = GlassButton(ctx.getString(R.string.tvos_media_previous),
            R.drawable.ic_skip_previous, Shape.ROUND) { sendTransport("previous") }
        val mediaPlayPause = GlassButton(ctx.getString(R.string.tvos_media_play_pause),
            R.drawable.ic_play_arrow, Shape.ROUND) { sendTransport("toggle") }
        mediaNext = GlassButton(ctx.getString(R.string.tvos_media_next),
            R.drawable.ic_skip_next, Shape.ROUND) { sendTransport("next") }
        listOf(mediaPrevious, mediaPlayPause, mediaNext).forEach { button ->
            transport.addView(button, LinearLayout.LayoutParams(cell, cell).apply { marginEnd = gap })
        }
        mediaPage.addView(transport, LinearLayout.LayoutParams(-1, cell).apply { topMargin = gap })
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
        val extreme = prefs.extremeGlass
        val nextAppearance = "$extreme:$night:${prefs.glassTint(night)}:${prefs.solidGlass(ctx)}"
        if (glass == glassStyle && nextAppearance == appearance && panel.tag != null) return
        appearance = nextAppearance
        panel.tag = true
        glassStyle = glass
        extremeGlass = extreme
        videoLens = false
        sampleRevision++
        bodies.clear()
        extremeTones.clear()
        panel.background = when {
            !glass -> panelCard
            extreme -> shadows
            else -> null
        }
        if (!glass) { blur = null; blurPixels = null; blurShader = null; lens = null }
        else lens = if (extreme) blur?.let {
            MovingGlass(host, it, MovingGlass.Mapping(blurX, blurY, sampleScale))
        } else null
        surfaces.forEach {
            it.owner.clipToOutline = !(glass && extreme)
            it.updateRim()
        }
        blurDirty = true
        if (glass && extreme) panel.invalidate()
        surfaces.forEach { it.reapplyTone() }
        invalidateSurfaces()
        if (isOpen) {
            host.removeCallbacks(resample)
            host.postDelayed(resample, 80)
        }
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
    private inner class GlassSurface(
        val owner: View,
        val radius: Float,
        private val onTone: ((GlassTone) -> Unit)? = null,
    ) : Drawable() {
        /** How much white the focused glass takes on; sliders keep it low so their fill reads. */
        var focusWash = 140
        /** Sliders keep a stable body so white fill and ink stay readable. */
        var focusSpot = true
        var material = GlassTone.Fallback
            private set
        var pendingTone = GlassTone.Fallback
        val id = surfaces.size
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
        private var mesh: MovingGlass.Mesh? = null
        private val tintTween = Tween()
        private var targetTone = GlassTone.Fallback

        fun applyTone(tone: GlassTone) {
            if (targetTone == tone) return
            val from = material
            // The first reading lands at once: gliding from the unsampled fallback made
            // every control visibly settle after the page was already on screen.
            val glide = extremeGlass && from != GlassTone.Fallback &&
                (from.tintScale != tone.tintScale || from.light != tone.light)
            targetTone = tone
            tintTween.cancel()
            material = tone
            onTone?.invoke(tone)
            invalidateSelf()
            if (glide) tintTween.start(180) { progress ->
                material = GlassMaterial.transition(from, tone, progress)
                invalidateSelf()
            }
        }

        fun release() { tintTween.cancel(); litTween.cancel() }

        fun reapplyTone() {
            onTone?.invoke(material)
        }

        fun light(on: Boolean) {
            val from = lit
            litTween.start(if (on) 280 else 200) { f -> lit = lerp(from, if (on) 1f else 0f, f) }
        }

        override fun onBoundsChange(b: android.graphics.Rect) {
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            mesh = MovingGlass.Mesh(w, h, r)
            // A sheen across the upper half, as light falling on a curved top surface.
            sheen = android.graphics.LinearGradient(0f, 0f, 0f, h,
                intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP)
            // Rim: bright where it faces the light (top left), a softer reflection bottom right.
            updateRim()
            // The focus light: a soft glow pooled under the top left of the glass.
            spot = android.graphics.RadialGradient(w * 0.3f, h * 0.1f, maxOf(w, h) * 0.95f,
                intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null, Shader.TileMode.CLAMP)
        }

        fun updateRim() {
            if (bounds.isEmpty) return
            rim = android.graphics.LinearGradient(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(),
                if (extremeGlass) intArrayOf(Color.WHITE, 0x1effffff, 0x12ffffff, 0x78ffffff)
                else intArrayOf(Color.WHITE, 0x28ffffff, 0x28ffffff, 0xaaffffff.toInt()),
                if (extremeGlass) floatArrayOf(0f, 0.30f, 0.70f, 1f)
                else floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
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
            val lens = this@ControlCenter.lens
            val geometry = mesh
            if (extremeGlass && prefs.solidGlass(ctx)) {
                paint.shader = null
                paint.color = if (material.light) Color.rgb(240, 242, 247) else Color.rgb(20, 20, 24)
                c.drawRoundRect(rect, r, r, paint)
            } else if (extremeGlass && videoLens) {
                paint.shader = null
                paint.color = material.tint
                paint.alpha = (GlassMaterial.recipe(material).tintAlpha * (1f - lit) * 255).toInt()
                c.drawRoundRect(rect, r, r, paint)
                if (focusWash > 0 && lit > 0f) {
                    paint.color = Color.WHITE
                    paint.alpha = (0.42f * lit * 255).toInt()
                    c.drawRoundRect(rect, r, r, paint)
                }
            } else if (extremeGlass && lens != null && geometry != null) {
                lens.draw(c, owner, geometry, 255, body(focusWash, lit, material), chromatic = true)
            } else if (shader != null) {
                // The blurred screen, mapped so this control shows exactly the part behind it,
                // with the milky wash and the focus brightening folded into its colour filter.
                m.setScale(1f / sampleScale, 1f / sampleScale)
                m.postTranslate(blurX, blurY)
                m.postConcat(hostToOwner)
                shader.setLocalMatrix(m)
                glassPaint.shader = shader
                glassPaint.colorFilter = body(focusWash, lit, material)
                c.drawRoundRect(rect, r, r, glassPaint)
            } else {
                paint.color = Color.argb((90 + focusWash * lit).toInt().coerceAtMost(255), 48, 52, 58)
                c.drawRoundRect(rect, r, r, paint)
            }
            // A sheen across the top, as light falling on a curved surface; a pooled glow when lit.
            paint.color = Color.WHITE
            paint.shader = sheen
            paint.alpha = if (extremeGlass) (55 + 40 * lit).toInt() else (110 + 145 * lit).toInt()
            c.drawRoundRect(rect, r, r, paint)
            if (lit > 0f && (!extremeGlass || focusSpot)) {
                paint.shader = spot
                paint.alpha = ((if (extremeGlass) 65 else 255) * lit).toInt()
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
                lightPaint.alpha = ((if (extremeGlass) 45 else 255) * glowA).toInt()
                c.drawRoundRect(rect, r, r, lightPaint)
            }
            // Rim: bright where it faces the light (top left), a softer reflection bottom right,
            // and the focus light caught on it, all in one stroke.
            val rimShader = rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ctx.dp(1f + 0.6f * lit).toFloat()
            paint.alpha = when {
                !extremeGlass -> (150 + 105 * lit).toInt()
                material.darkTheme -> (50 + 80 * lit).toInt()
                else -> (110 + 90 * lit).toInt()
            }
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
            if (extremeGlass) {
                val inner = ctx.dp(1.5f).toFloat()
                rect.inset(inner, inner)
                paint.strokeWidth = ctx.dp(0.5f).toFloat()
                paint.alpha = (70 * GlassMaterial.recipe(material).edgeStrength).toInt()
                c.drawRoundRect(rect, (r - inner).coerceAtLeast(0f), (r - inner).coerceAtLeast(0f), paint)
            }
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
        fun resolvedRadius() = r
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /**
     * The panel's background draws every control's shadow in one pass. A view's
     * own background is clipped to its bounds, so drawing the shadow there
     * cuts off exactly the soft falloff that gives the glass its thickness.
     */
    private inner class Shadows : Drawable() {
        private val ownerToHost = Matrix()
        private val hostToPanel = Matrix()
        private val m = Matrix()

        override fun draw(c: Canvas) {
            if (!glassStyle || !extremeGlass) return
            toHost(panel, hostToPanel)
            hostToPanel.invert(hostToPanel)
            shadowPaint.alpha = 255
            for (surface in surfaces) {
                val owner = surface.owner
                val w = owner.width
                val h = owner.height
                if (w <= 0 || h <= 0) continue
                val shadow = shadowFor(w, h, surface.resolvedRadius()) ?: continue
                toHost(owner, ownerToHost)
                m.reset()
                m.postTranslate(-shadowPad.toFloat(), -shadowPad + shadowOffset.toFloat())
                m.postConcat(ownerToHost)
                m.postConcat(hostToPanel)
                c.drawBitmap(shadow, m, shadowPaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun glassFor(
        view: View,
        radius: Float,
        onTone: ((GlassTone) -> Unit)? = null,
    ): GlassSurface = GlassSurface(view, radius, onTone).also {
        view.background = it
        view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        view.clipToOutline = !(glassStyle && extremeGlass)
        // No elevation: through translucent glass the platform shadow shows a hard-edged hole.
    }

    private fun shadowFor(w: Int, h: Int, radius: Float): Bitmap? {
        if (!extremeGlass || w <= 0 || h <= 0) return null
        return shadowCache.getOrPut("$w:$h:${radius.toInt()}") {
            LiquidGlass.shadow(w, h, radius, shadowPad, alpha = 95, hollow = true)
        }
    }

    private fun focus(view: View, glass: GlassSurface, on: Boolean, grow: Float = 1.08f) {
        glass.light(on)
        // The focused control lifts towards the viewer; its glass keeps showing what is behind it.
        view.tweenScale(if (on) grow else 1f, if (on) 260 else 200, if (on) Motion.spring else Motion.ease) {
            if (glassStyle) view.invalidate()
            if (glassStyle && extremeGlass) panel.invalidate()
        }
    }

    /** The screen behind changed (background, shelf, layout): the glass samples it again. */
    fun refreshGlass() {
        if (destroyed) return
        sampleRevision++
        if (glassStyle && extremeGlass) panel.invalidate()
        blurDirty = true
        if (glassStyle) {
            host.removeCallbacks(resample)
            if (isOpen) host.postDelayed(resample, 80)
        }
    }

    fun liveFrame(frame: Bitmap) {
        if (destroyed || !isOpen || !glassStyle || !extremeGlass || host.width <= 0) return
        sampleRevision++
        blur = frame
        val scale = minOf(frame.width / host.width.toFloat(), frame.height / host.height.toFloat())
        backdropScale = scale
        blurX = (host.width - frame.width / scale) / 2f
        blurY = (host.height - frame.height / scale) / 2f
        videoLens = true
        blurDirty = false
        val now = android.os.SystemClock.uptimeMillis()
        if (now >= nextLiveToneAt || blurPixels?.size != frame.width * frame.height) {
            blurPixels = IntArray(frame.width * frame.height).also {
                frame.getPixels(it, 0, frame.width, 0, 0, frame.width, frame.height)
            }
            nextLiveToneAt = now + 160L
            refreshTones()
        }
        invalidateSurfaces()
    }

    /**
     * Captures the home screen behind the controls at a quarter of its size and blurs it, on the
     * UI thread: a few milliseconds, once per opening at most.
     */
    private fun sampleBehind() {
        if (destroyed || sampling || !glassStyle || host.width == 0 || panel.width == 0) return
        if (extremeGlass) home.glassVideoFrame()?.let { liveFrame(it); return }
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
        val revision = sampleRevision
        val extreme = extremeGlass
        val scale = sampleScale
        sampling = true
        glassWorker.execute {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val samples = if (extreme) px.copyOf() else px
        // Keep a little scene detail visible through the extreme lens; the
        // ordinary glass keeps its established softer bake unchanged. Two passes
        // at half resolution land near the reference's 2dp blur on a 1080p TV.
        repeat(if (extreme) 2 else 3) {
            LiquidGlass.boxBlur(px, w, h, if (extreme) 1 else 7)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        bmp.prepareToDraw()
        // Not recycled: a frame still being drawn may hold the previous picture. It is small.
        host.post {
        sampling = false
        if (destroyed) return@post
        if (revision != sampleRevision) {
            if (isOpen) sampleBehind()
            return@post
        }
        blur = bmp
        blurPixels = samples
        blurShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        blurX = left.toFloat()
        blurY = top.toFloat()
        backdropScale = scale
        videoLens = false
        lens = if (extreme) MovingGlass(host, bmp, MovingGlass.Mapping(blurX, blurY, scale)) else null
        blurDirty = false
        invalidateSurfaces()
        refreshTones()
        }
        }
    }

    /**
     * Reads the wallpaper under each control once per backdrop change. A card
     * over a pale sky gets dark ink and a white tint; one over a dark scene gets
     * white ink and charcoal tint. The values are cached until the next sample.
     */
    private fun refreshTones() {
        val pixels = blurPixels ?: return
        val image = blur ?: return
        val bounds = RectF()
        val transform = Matrix()
        val samples = ArrayList<FloatArray>()
        for (surface in surfaces) {
            val owner = surface.owner
            if (owner.width <= 0 || owner.height <= 0) continue
            bounds.set(0f, 0f, owner.width.toFloat(), owner.height.toFloat())
            toHost(owner, transform)
            transform.mapRect(bounds)
            val left = ((bounds.left - blurX) * backdropScale).roundToInt()
            val top = ((bounds.top - blurY) * backdropScale).roundToInt()
            val right = ((bounds.right - blurX) * backdropScale).roundToInt()
            val bottom = ((bounds.bottom - blurY) * backdropScale).roundToInt()
            var tone = GlassMaterial.analyze(
                pixels, image.width, image.height, left, top, right, bottom,
                previous = surface.material.takeUnless { it == GlassTone.Fallback }
                    ?: prefs.rememberedGlassInk("control:${surface.id}:$night")?.let {
                        GlassMaterial.tone(0.5f, 0f, lightOverride = it, darkTheme = night)
                    },
                darkTheme = night && !videoLens,
                tintScale = prefs.glassTint(night),
            )
            if (night && videoLens) tone = tone.copy(darkTheme = true, tint = 0xff141418.toInt())
            surface.pendingTone = tone
            samples += floatArrayOf(
                surface.id.toFloat(),
                layoutTop(owner),
                bounds.width() * bounds.height(),
                tone.luminance,
            )
        }
        // Adjacent controls in one row choose one ink direction together.
        val groups = LinkedHashMap<Int, MutableList<FloatArray>>()
        for (sample in samples) {
            groups.getOrPut((sample[1] / ctx.dp(4f)).roundToInt()) { ArrayList() } += sample
        }
        for (members in groups.values) {
            val area = members.sumOf { it[2].toDouble() }.coerceAtLeast(1.0)
            val mean = members.sumOf { (it[2] * it[3]).toDouble() } / area
            val previous = surfaces[members.first()[0].toInt()].material
            val light = if (previous != GlassTone.Fallback && kotlin.math.abs(mean - 0.5) <= 0.05)
                previous.light else mean >= GlassMaterial.LIGHT_THRESHOLD
            for (sample in members) {
                val surface = surfaces.firstOrNull { it.id == sample[0].toInt() } ?: continue
                val tone = if (videoLens && night) {
                    GlassMaterial.withLight(surface.pendingTone.copy(darkTheme = false), light)
                        .copy(darkTheme = true, tint = 0xff141418.toInt())
                } else GlassMaterial.withLight(surface.pendingTone, light)
                surface.applyTone(tone)
                prefs.rememberGlassInk("control:${surface.id}:$night", surface.material.light)
            }
        }
    }

    private enum class Shape { TILE, CAPSULE, ROUND }

    private fun layoutTop(owner: View): Float {
        var current = owner
        var top = 0f
        while (current !== panel) {
            top += current.top
            current = current.parent as? View ?: break
        }
        return top
    }

    private inner class GlassButton(
        label: String,
        icon: Int,
        shape: Shape,
        private val accent: Int = Color.WHITE,
        action: () -> Unit,
    ) : LinearLayout(ctx) {
        // Capsules and circles clamp this to half their height.
        private var glassTone = GlassTone.Fallback
        private val glass = glassFor(this, ctx.dp(if (shape == Shape.TILE) 24 else 100).toFloat()) {
            glassTone = it
            applyInk(isFocused)
        }
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
                applyInk(on)
            }
            applyInk(false)
        }

        /**
         * Focus is always the bright selected state, so its ink stays dark. At
         * rest the wallpaper decides: pale glass gets dark ink, dark glass keeps
         * the accent or white.
         */
        private fun applyInk(focused: Boolean) {
            val blend = extremeGlass && glassStyle && !prefs.solidGlass(ctx)
            listOf(glyph, title, sub).forEach { GlassInk.apply(it, blend, focused || glassTone.light) }
            val focusInk = Color.argb(235, 0, 0, 0)
            if (!extremeGlass || !glassStyle) {
                // Keep the pre-existing look (and reset it if extreme was just switched off).
                val originalInk = Color.argb(225, 0, 0, 0)
                glyph.setColorFilter(if (focused) originalInk else accent)
                title.setTextColor(if (focused) originalInk else Color.WHITE)
                sub.setTextColor(if (focused) Color.argb(170, 0, 0, 0) else Color.argb(205, 255, 255, 255))
                return
            }
            val ink = if (focused) focusInk else glassTone.ink
            glyph.setColorFilter(if (focused) focusInk else if (glassTone.light && GlassMaterial.luminance(accent) > 0.62f) ink else accent)
            title.setTextColor(ink)
            sub.setTextColor(if (focused) Color.argb(175, 0, 0, 0) else glassTone.secondaryInk)
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
        private var tone = GlassTone.Fallback
        private val glass = glassFor(this, corner) { tone = it; draw() }.apply {
            focusWash = 0
            focusSpot = false
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillClip = android.graphics.Path()
        private var darkIcon = false
        private var iconInk = 0
        private val filledInkPaint = Paint().apply {
            colorFilter = android.graphics.PorterDuffColorFilter(Color.argb(230, 0, 0, 0), PorterDuff.Mode.SRC_IN)
        }
        private val emptyInkPaint = Paint()
        private var emptyInk = 0
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

        override fun dispatchDraw(canvas: Canvas) {
            if (!extremeGlass || !glassStyle) {
                super.dispatchDraw(canvas)
                return
            }
            val fraction = if (ready && max > min)
                ((displayedValue - min) / (max - min)).coerceIn(0f, 1f) else 0f
            val filled = width * fraction
            // Clip the glyphs themselves at the fill boundary: even half a letter
            // stays readable. Layer filters preserve text antialiasing and avoid
            // changing child colours (and invalidating them) during drawing.
            inkPass(canvas, 0f, filled, filledInkPaint)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                emptyInkPaint.blendMode = if (prefs.solidGlass(ctx)) BlendMode.SRC_OVER
                    else if (tone.light) BlendMode.MULTIPLY else BlendMode.PLUS
            } else {
                emptyInkPaint.xfermode = if (prefs.solidGlass(ctx)) null else PorterDuffXfermode(
                    if (tone.light) PorterDuff.Mode.DARKEN else PorterDuff.Mode.ADD)
            }
            inkPass(canvas, filled, width.toFloat(), emptyInkPaint)
        }

        private fun inkPass(canvas: Canvas, left: Float, right: Float, paint: Paint) {
            if (right <= left) return
            val clip = canvas.save()
            canvas.clipRect(left, 0f, right, height.toFloat())
            val layer = canvas.saveLayer(left, 0f, right, height.toFloat(), paint)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(layer)
            canvas.restoreToCount(clip)
        }

        private fun draw() {
            if (width <= 0) return
            val fraction = if (ready && max > min)
                ((displayedValue - min) / (max - min)).coerceIn(0f, 1f) else 0f
            val filled = width * fraction
            val dark = Color.argb(230, 0, 0, 0)
            val iconIsDark = filled > ctx.dp(32)
            val baseInk = if (extremeGlass && glassStyle) tone.ink else Color.WHITE
            if (extremeGlass && glassStyle) {
                if (emptyInk != baseInk) {
                    emptyInk = baseInk
                    emptyInkPaint.colorFilter = android.graphics.PorterDuffColorFilter(baseInk, PorterDuff.Mode.SRC_IN)
                }
                if (iconInk != Color.WHITE) {
                    iconInk = Color.WHITE
                    img.setColorFilter(Color.WHITE)
                }
                if (nameText.currentTextColor != Color.WHITE) nameText.setTextColor(Color.WHITE)
                if (valueText.currentTextColor != Color.WHITE) valueText.setTextColor(Color.WHITE)
                invalidate()
                return
            }
            val ink = if (iconIsDark) dark else baseInk
            if (darkIcon != iconIsDark || iconInk != ink) {
                darkIcon = iconIsDark
                iconInk = ink
                img.setColorFilter(ink)
            }
            val nameColor = if (filled > ctx.dp(41) + nameText.width) dark else baseInk
            if (nameText.currentTextColor != nameColor) nameText.setTextColor(nameColor)
            val valueColor = if (filled > width - ctx.dp(15)) dark else baseInk
            if (valueText.currentTextColor != valueColor) valueText.setTextColor(valueColor)
            // Only drawing changes. No animated child width or per-frame text relayout.
            invalidate()
        }
    }

    // ---- open / close -------------------------------------------------------------

    fun open() {
        if (isOpen) return
        applyStyle()
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
        selectTab(0, moveFocus = false)
        memButton.requestFocus()
        host.removeCallbacks(ticker)
        host.post(ticker)
    }

    fun atRightEdge(): Boolean {
        val current = panel.findFocus() ?: return false
        // Right on a slider must continue to adjust its value.
        if (current.contentDescription == I18n.text(R.string.i18n_controlcenter_015) || current.contentDescription == I18n.text(R.string.i18n_controlcenter_016)) return false
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
        blurPixels = null
        blurShader = null
        lens = null
        shadowCache.values.forEach { it.recycle() }
        shadowCache.clear()
        surfaces.forEach { it.release() }
        glassWorker.shutdown()
        io.shutdown()
    }

    fun refocus() {
        (focusables.firstOrNull { it.isFocused } ?: memButton).requestFocus()
    }

    fun refresh() {
        if (destroyed) return
        status.text = if (prefs.adbAuthorized) "" else I18n.text(R.string.i18n_controlcenter_017)
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val wired = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
        networkModule.title.text = if (wired) I18n.text(R.string.i18n_controlcenter_018) else "Wi-Fi"
        networkModule.sub.text = if (caps == null) I18n.text(R.string.i18n_controlcenter_019) else I18n.text(R.string.i18n_controlcenter_020)
        networkModule.sub.visibility = View.VISIBLE
        if (refreshing) return
        refreshing = true
        io.execute {
            val b = tv.brightness()
            val v = tv.volume()
            val list = tv.inputs()
            val cur = tv.currentInput()
            val summary = tv.inputSummary(list)
            val track = NowPlaying.current(ctx)
            host.post {
                refreshing = false
                if (destroyed) return@post
                brightness.set(b)
                brightness.visibility = if (b == null) View.GONE else View.VISIBLE
                v?.target?.takeIf { it.isNotEmpty() }?.let { volumeTarget = it }
                volume.set(v)
                inputs = list
                mediaTab.visibility = if (track != null) View.VISIBLE else View.GONE
                mediaPrevious.visibility = if (track?.previous == true) View.VISIBLE else View.INVISIBLE
                mediaNext.visibility = if (track?.next == true) View.VISIBLE else View.INVISIBLE
                if (track == null && selectedTab == 1) selectTab(0)
                profileModule.sub.text = ctx.getString(R.string.tvos_profile_number, prefs.activeProfile + 1)
                profileModule.sub.visibility = View.VISIBLE
                mediaModule.title.text = track?.title ?: ctx.getString(R.string.tvos_now_playing)
                mediaModule.sub.text = if (track != null) {
                    listOf(track.artist, if (track.playing) "▶" else "Ⅱ").filter { it.isNotBlank() }.joinToString(" · ")
                } else ctx.getString(R.string.tvos_now_playing_unavailable)
                mediaModule.sub.visibility = View.VISIBLE
                inputModule.sub.visibility = View.VISIBLE
                inputModule.sub.text = list.firstOrNull { it.uri == cur }?.name ?: summary
            }
        }
    }

    private fun pickInput() {
        if (inputs.isEmpty()) return
        // The list replaces the panel instead of stacking on top of it.
        close()
        val sheet = Sheet(host).title(I18n.text(R.string.i18n_controlcenter_021)).message(null)
        for (i in inputs) sheet.button(i.name, R.drawable.ic_input) {
            io.execute { tv.setInput(i) }
        }
        sheet.button(I18n.text(R.string.i18n_controlcenter_022)) { pinInputs() }
        sheet.button(I18n.text(R.string.i18n_controlcenter_023)) {}
        home.showSheet(sheet)
    }

    /** Inputs shown as tiles on the home grid; one press there switches to them. */
    private fun pinInputs() {
        val sheet = Sheet(host).title(I18n.text(R.string.i18n_controlcenter_024))
            .message(I18n.text(R.string.i18n_controlcenter_025))
        for (i in inputs) {
            val on = home.isPinned(i)
            sheet.button(if (on) "✓  ${i.name}" else i.name, R.drawable.ic_input) { home.pinInput(i, !on) }
        }
        sheet.button(I18n.text(R.string.i18n_controlcenter_026)) {}
        home.showSheet(sheet)
    }

    private fun cleanNow() {
        status.text = I18n.text(R.string.i18n_controlcenter_027)
        status.visibility = View.VISIBLE
        Thread {
            val r = Cleaner.clean(ctx.applicationContext)
            host.post {
                status.text = r.description()
            }
        }.start()
    }

    private fun needAdb(action: () -> Unit) {
        if (prefs.adbAuthorized) action() else home.showSheet(Sheet(host).title(I18n.text(R.string.i18n_controlcenter_028))
            .message(I18n.text(R.string.i18n_controlcenter_029))
            .button(I18n.text(R.string.i18n_controlcenter_030)) { home.authorizeAdb() }
            .button(I18n.text(R.string.i18n_controlcenter_031)) {})
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) = needAdb {
        home.showSheet(Sheet(host).title("$title？").message(msg)
            .button(title) { action() }
            .button(I18n.text(R.string.i18n_controlcenter_032)) {})
    }
}
