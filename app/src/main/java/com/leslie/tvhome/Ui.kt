package com.leslie.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Number): Int = context.dp(v)

fun rounded(color: Int, radius: Float): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = radius }

fun View.clipRounded(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) =
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
    clipToOutline = true
}

object Motion {
    val ease = DecelerateInterpolator(2f)
    val spring = OvershootInterpolator(1.6f)

    /** tvOS "bump" when focus cannot move any further in that direction. */
    fun bump(v: View, dx: Int, dy: Int) {
        v.cameraDistance = v.width * 12f
        v.animate().cancel()
        val t = v.getTag(R.id.motion_bump) as? Tween ?: Tween().also { v.setTag(R.id.motion_bump, it) }
        t.start(260, android.view.animation.LinearInterpolator()) { p ->
            val f = if (p < 0.5f) p * 2 else (1 - p) * 2
            v.rotationY = dx * 7f * f
            v.rotationX = -dy * 7f * f
            v.translationX = dx * v.dp(5) * f
            v.translationY = dy * v.dp(5) * f
        }
    }
}

/**
 * A frame-by-frame animation that still plays where the TV's animation scale is 0 ("animations
 * off", common on Chinese firmwares and in speed-up guides), which makes every ValueAnimator and
 * ViewPropertyAnimator jump straight to its end: focus would change with no zoom at all. Slowed
 * or sped-up scales still apply, down to half speed, so a TV set to 0.5 looks as it always did.
 */
class Tween : android.view.Choreographer.FrameCallback {
    private var startNanos = -1L
    private var durationNanos = 1L
    private var interp: android.animation.TimeInterpolator = Motion.ease
    private var update: ((Float) -> Unit)? = null
    private var end: (() -> Unit)? = null
    var running = false
        private set

    fun start(ms: Long, interpolator: android.animation.TimeInterpolator = Motion.ease, end: (() -> Unit)? = null, update: (Float) -> Unit) {
        cancel()
        this.interp = interpolator
        this.update = update
        this.end = end
        durationNanos = (ms * scale() * 1_000_000).toLong().coerceAtLeast(1)
        startNanos = -1L
        running = true
        update(interpolator.getInterpolation(0f))
        android.view.Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (startNanos < 0) startNanos = frameTimeNanos
        val t = ((frameTimeNanos - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
        update?.invoke(interp.getInterpolation(t))
        if (t >= 1f) {
            running = false
            end?.invoke()
        } else android.view.Choreographer.getInstance().postFrameCallback(this)
    }

    fun cancel() {
        if (!running) return
        running = false
        android.view.Choreographer.getInstance().removeFrameCallback(this)
    }

    companion object {
        @Volatile var systemScale = 1f

        /** Read once per start of the home screen; the setting can only change in system settings. */
        fun readScale(ctx: Context) {
            systemScale = runCatching {
                android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }.getOrDefault(1f)
        }

        fun scale() = if (systemScale <= 0f) 0.5f else systemScale.coerceIn(0.5f, 1.5f)
    }
}

/** Linear interpolation, for animating between a start and an end value. */
fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Focus zoom: scales the view to [s] (and lifts it to [z]) with a [Tween] kept on the view. */
fun View.tweenScale(s: Float, ms: Long, interp: android.animation.TimeInterpolator = Motion.ease, z: Float? = null, onFrame: (() -> Unit)? = null) {
    animate().cancel()
    val t = getTag(R.id.motion_focus) as? Tween ?: Tween().also { setTag(R.id.motion_focus, it) }
    val s0 = scaleX
    val z0 = translationZ
    t.start(ms, interp) { f ->
        scaleX = lerp(s0, s, f)
        scaleY = scaleX
        if (z != null) translationZ = lerp(z0, z, f)
        onFrame?.invoke()
    }
}

const val FONT_BOLD = "sans-serif-medium"

/**
 * Inter (variable, OFL) stands in for Apple's SF: Roboto's digits are what
 * make a screen read as "Android" at a glance. Chinese falls back to the system font.
 */
object Fonts {
    private val cache = HashMap<Int, Typeface>()

    fun inter(ctx: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        try {
            Typeface.Builder(ctx.assets, "inter.ttf")
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .setFallback("sans-serif")
                .build() ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.create(if (weight >= 500) FONT_BOLD else "sans-serif", Typeface.NORMAL)
        }
    }
}

fun Context.text(sizeSp: Float, color: Int = Color.WHITE, bold: Boolean = false, weight: Int = if (bold) 600 else 400): TextView =
    GlassTextView(this).apply {
        textSize = sizeSp
        setTextColor(color)
        typeface = Fonts.inter(this@text, weight)
        includeFontPadding = false
    }

/**
 * A pill button in tvOS style: translucent when idle, solid white with
 * dark text and a slight lift when focused.
 */
class PillButton(ctx: Context, label: String, iconRes: Int? = null, val onClick: () -> Unit) : LinearLayout(ctx) {
    private val tv = ctx.text(17f, bold = true).apply { text = label }
    private val icon: ImageView? = iconRes?.let { ImageView(ctx).apply { setImageResource(it) } }
    private val idle = rounded(Color.argb(46, 255, 255, 255), dp(14).toFloat())
    private val focus = rounded(Color.WHITE, dp(14).toFloat())
    private var glass: UpdateGlass.Surface? = null
    private var glassTone = GlassTone.Fallback

    internal fun useGlass(material: UpdateGlass) {
        glass = material.control(this, 14) { tone ->
            glassTone = tone
            icon?.setColorFilter(if (isFocused) Color.BLACK else tone.ink)
            icon?.let { GlassInk.apply(it, !Prefs(context).solidGlass(context), isFocused || tone.light) }
        }.also { it.bindInk(listOf(tv)) }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = idle
        setPadding(dp(24), 0, dp(24), 0)
        icon?.let { addView(it, LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) }) }
        addView(tv)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { _, f ->
            val material = glass
            if (material == null) {
                background = if (f) focus else idle
                tv.setTextColor(if (f) Color.BLACK else Color.WHITE)
                icon?.setColorFilter(if (f) Color.BLACK else Color.WHITE)
            } else {
                material.focus(f)
                icon?.setColorFilter(if (f) Color.BLACK else glassTone.ink)
                icon?.let { GlassInk.apply(it, !Prefs(context).solidGlass(context), f || glassTone.light) }
            }
            tweenScale(if (f) 1.06f else 1f, 160, z = if (f) dp(10).toFloat() else 0f)
        }
    }
}

/**
 * Full-screen alert in the style of tvOS: dimmed backdrop, centred title,
 * message and a vertical stack of buttons. Back dismisses it.
 */
class Sheet(private val host: FrameLayout) {
    private val ctx = host.context
    val root = FrameLayout(ctx).apply {
        setBackgroundColor(Color.argb(200, 0, 0, 0))
        // Focused controls can scale past the bounds of their parent.
        clipChildren = false
        isClickable = true
        alpha = 0f
        // Above focused tiles, which lift themselves with translationZ; no shadow (see ControlCenter.scrim).
        translationZ = ctx.dp(200).toFloat()
        outlineProvider = null
    }
    private val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // Pills grow to 1.06x on focus, beyond this wrap-content column.
        clipChildren = false
    }
    private val title = ctx.text(28f, bold = true).apply { gravity = Gravity.CENTER }
    private val message = ctx.text(16f, Color.argb(180, 255, 255, 255)).apply {
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.25f)
    }
    private var onDismiss: (() -> Unit)? = null
    private var glass: UpdateGlass? = null
    private val glassSceneTick = object : Runnable {
        override fun run() {
            if (!isShowing || glass == null) return
            sampleGlassScene()
            root.postDelayed(this, 1000)
        }
    }

    private fun sampleGlassScene() {
        if (host.width <= 0 || host.height <= 0) return
        val width = minOf(host.width, 960)
        val height = (host.height * width.toFloat() / host.width).toInt().coerceAtLeast(1)
        val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(image)
        canvas.scale(width / host.width.toFloat(), height / host.height.toFloat())
        val home = ctx as? HomeActivity
        if (home != null) home.drawGlassScene(canvas) else host.draw(canvas)
        glass?.texture(image)
    }

    init {
        col.addView(title)
        col.addView(message, LinearLayout.LayoutParams(ctx.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ctx.dp(12); bottomMargin = ctx.dp(28)
        })
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.setOnKeyListener { _, code, e ->
            if (code == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { dismiss(); true } else false
        }
    }

    fun title(t: String) = apply { title.text = t }
    fun message(m: String?) = apply {
        message.text = m ?: ""
        message.visibility = if (m.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun button(label: String, icon: Int? = null, keepOpen: Boolean = false, action: () -> Unit) = apply {
        val b = PillButton(ctx, label, icon) {
            if (!keepOpen) dismiss()
            action()
        }
        col.addView(b, LinearLayout.LayoutParams(ctx.dp(420), ctx.dp(52)).apply { topMargin = ctx.dp(12) })
    }

    fun onDismiss(f: () -> Unit) = apply { onDismiss = f }

    fun show(): Sheet {
        if (Prefs(ctx).extremeGlass && host.width > 0 && host.height > 0) {
            val material = UpdateGlass(root, moving = true)
            glass = material
            for (index in 0 until col.childCount) (col.getChildAt(index) as? PillButton)?.useGlass(material)
            sampleGlassScene()
            if (ctx is HomeActivity) root.postDelayed(glassSceneTick, 1000)
        }
        host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.animate().alpha(1f).setDuration(180).start()
        (0 until col.childCount).map { col.getChildAt(it) }.firstOrNull { it is PillButton }?.requestFocus()
        return this
    }

    val isShowing get() = root.parent != null

    fun dismiss() {
        if (!isShowing) return
        root.animate().alpha(0f).setDuration(140).withEndAction {
            host.removeView(root)
            root.removeCallbacks(glassSceneTick)
            glass?.close()
            glass = null
            onDismiss?.invoke()
        }.start()
    }

    /** Back is delivered to the focused child, so buttons forward it here. */
    fun handlesBack(): Boolean { dismiss(); return true }
}
