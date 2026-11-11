package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A full-screen clock as the third kind of screensaver: neon tubes, flip cards, an LCD panel
 * or large minimal type, each in several colours, with optional seconds, sounds and snow or
 * stars. Left / right change the style, up / down the colour. The whole face drifts a little
 * every minute so nothing sits still on an OLED panel.
 */
class ClockSaver(ctx: Context) : Saver {
    private val prefs = Prefs(ctx)
    private val face = ClockFaceView(ctx)

    override val view: View get() = face
    override fun start() = face.start()
    override fun stop() = face.stop()

    override fun skip(dir: Int): Boolean {
        val styles = Prefs.CLOCK_SAVER_STYLES
        val i = styles.indexOf(prefs.clockSaverStyle).coerceAtLeast(0)
        prefs.clockSaverStyle = styles[ClockThemes.wrap(i + dir, styles.size)]
        face.reload(announce = true)
        return true
    }

    override fun cycle(dir: Int): Boolean {
        val style = prefs.clockSaverStyle
        prefs.setClockColor(style, ClockThemes.wrap(prefs.clockColor(style) + dir, ClockThemes.count(style)))
        face.reload(announce = true, strike = false)
        return true
    }
}

class ClockFaceView(ctx: Context) : View(ctx) {
    private val prefs = Prefs(ctx)
    private val lowEnd = Device.isLowEnd(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var sounds: ClockSounds? = null

    private var style = prefs.clockSaverStyle
    private var color = prefs.clockColor(style)
    private var seconds = prefs.clockSeconds
    private var effect = prefs.clockEffect

    private var secondKey = -1L
    private var hours = ""
    private var minutes = ""
    private var secs = ""
    /** Hours and minutes before the last minute change; empty: everything lights up as new. */
    private var previousHm = ""
    private var previousSecs = ""
    private var minuteAt = 0L
    private var secondAt = 0L
    private var date = ""

    private var driftFromX = 0f
    private var driftFromY = 0f
    private var driftToX = 0f
    private var driftToY = 0f
    private var driftAt = -DRIFT_MS

    private var announcement = ""
    private var announcedAt = -ANNOUNCE_MS

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            update()
            invalidate()
            main.postDelayed(this, nextDelay())
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start() {
        if (running) return
        running = true
        sounds = ClockSounds(context)
        reload(announce = false)
        main.post(loop)
    }

    fun stop() {
        running = false
        main.removeCallbacks(loop)
        sounds?.release()
        sounds = null
        clearGlyphs()
    }

    /** Settings changed (from the remote): read them again; [strike] lights the face up anew. */
    fun reload(announce: Boolean, strike: Boolean = true) {
        style = prefs.clockSaverStyle
        color = prefs.clockColor(style)
        seconds = prefs.clockSeconds
        effect = prefs.clockEffect
        clearGlyphs()
        particles = null
        if (strike) {
            secondKey = -1
            hours = ""
        }
        if (announce) {
            announcement = I18n.text(styleName(style)) + "  ·  " + I18n.text(ClockThemes.name(style, color))
            announcedAt = SystemClock.uptimeMillis()
        }
        update()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        clearGlyphs()
        particles = null
    }

    private fun update() {
        val nowMs = System.currentTimeMillis()
        val key = nowMs / 1000
        if (key == secondKey) return
        val first = secondKey < 0 || hours.isEmpty()
        secondKey = key
        val now = Date(nowMs)
        val locale = Locale.getDefault()
        val h = SimpleDateFormat(if (prefs.clock24) "HH" else "h", locale).format(now)
        val m = SimpleDateFormat("mm", locale).format(now)
        val s = SimpleDateFormat("ss", locale).format(now)
        val uptime = SystemClock.uptimeMillis()
        val minuteChanged = first || h != hours || m != minutes
        if (minuteChanged) {
            val d = SimpleDateFormat(I18n.text(R.string.i18n_photoframe_001), locale).format(now)
            date = if (prefs.clock24) d else d + "  ·  " + SimpleDateFormat("a", locale).format(now)
            previousHm = if (first) "" else hours + minutes
            minuteAt = uptime
            if (!first) {
                val (x, y) = drift()
                driftFromX = x; driftFromY = y
                driftToX = ((Math.random() - 0.5) * dp(56)).toFloat()
                driftToY = ((Math.random() - 0.5) * dp(36)).toFloat()
                driftAt = uptime
            }
            if (style == Prefs.CLOCK_SAVER_NEON && prefs.neonBuzz)
                sounds?.play(if (first) ClockSynth.Sound.NEON_STRIKE else ClockSynth.Sound.NEON_FLICK, if (first) 0.55f else 0.3f)
        }
        previousSecs = if (first) s else secs
        secondAt = uptime
        hours = h; minutes = m; secs = s
        if (!first && seconds && prefs.clockTick) sounds?.play(tickSound(), 0.4f)
    }

    private fun tickSound() = when (style) {
        Prefs.CLOCK_SAVER_FLIP -> ClockSynth.Sound.FLAP
        Prefs.CLOCK_SAVER_LCD -> ClockSynth.Sound.TICK_DIGITAL
        Prefs.CLOCK_SAVER_NEON -> ClockSynth.Sound.TICK_NEON
        else -> ClockSynth.Sound.TICK_SOFT
    }

    private fun nextDelay(): Long {
        val now = SystemClock.uptimeMillis()
        val animating = now - minuteAt < STRIKE_MS || now - driftAt < DRIFT_MS || now - announcedAt < ANNOUNCE_MS ||
            (style == Prefs.CLOCK_SAVER_FLIP && seconds && now - secondAt < FLIP_MS)
        return when {
            animating -> 16
            effect != Prefs.CLOCK_EFFECT_NONE -> if (lowEnd) 50 else 33
            style == Prefs.CLOCK_SAVER_NEON && !lowEnd -> 50
            // The colon blinks on the half second.
            else -> 500 - System.currentTimeMillis() % 500 + 5
        }
    }

    private fun drift(): Pair<Float, Float> {
        val p = ((SystemClock.uptimeMillis() - driftAt) / DRIFT_MS.toFloat()).coerceIn(0f, 1f)
        val e = (1 - cos(p * PI).toFloat()) / 2
        return (driftFromX + (driftToX - driftFromX) * e) to (driftFromY + (driftToY - driftFromY) * e)
    }

    /** Colon visibility without seconds: on for the first half of each second. */
    private fun blinkOn() = seconds || System.currentTimeMillis() % 1000 < 500

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(background())
        if (hours.isEmpty() || width == 0) return
        if (effect == Prefs.CLOCK_EFFECT_STARS) drawParticles(canvas)
        val (dx, dy) = drift()
        canvas.save()
        canvas.translate(dx, dy)
        when (style) {
            Prefs.CLOCK_SAVER_FLIP -> drawFlip(canvas)
            Prefs.CLOCK_SAVER_MINIMAL -> drawMinimal(canvas)
            Prefs.CLOCK_SAVER_LCD -> drawLcd(canvas)
            else -> drawNeon(canvas)
        }
        canvas.restore()
        if (effect == Prefs.CLOCK_EFFECT_SNOW) drawParticles(canvas)
        drawAnnouncement(canvas)
    }

    private fun background() = when (style) {
        Prefs.CLOCK_SAVER_FLIP -> flipTheme().page
        Prefs.CLOCK_SAVER_MINIMAL -> minimalTheme().background
        Prefs.CLOCK_SAVER_LCD -> lcdTheme().background
        else -> Color.BLACK
    }

    private fun light(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 140

    private fun neonTheme() = ClockThemes.neon[ClockThemes.wrap(color, ClockThemes.neon.size)]
    private fun flipTheme() = ClockThemes.flip[ClockThemes.wrap(color, ClockThemes.flip.size)]
    private fun minimalTheme() = ClockThemes.minimal[ClockThemes.wrap(color, ClockThemes.minimal.size)]
    private fun lcdTheme() = ClockThemes.lcd[ClockThemes.wrap(color, ClockThemes.lcd.size)]

    /** Whether the digit at [index] of hours + minutes changed with the last minute. */
    private fun changed(index: Int): Boolean {
        val hm = hours + minutes
        return previousHm.length != hm.length || previousHm.getOrNull(index) != hm[index]
    }

    // ---- Neon ------------------------------------------------------------------------------

    /** One character's tube at baseline 0, and its halo blurred once in software at reduced size. */
    private class Glyph(val path: Path, val glow: Bitmap, val glowRect: RectF, val advance: Float)

    private val glyphs = HashMap<String, Glyph>()
    private var glyphSize = 0f
    private var flutterIndex = -1
    private var flutterUntil = 0L

    private val neonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }
    private val tubePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val neonDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = 0.18f
        textAlign = Paint.Align.CENTER
    }
    private val floorFade = Paint()
    private var floorFadeAt = -1f

    private fun clearGlyphs() {
        glyphs.values.forEach { it.glow.recycle() }
        glyphs.clear()
        glyphSize = 0f
    }

    private fun glyph(c: Char, tint: Int): Glyph = glyphs.getOrPut("$c|$tint") {
        val s = c.toString()
        val size = neonPaint.textSize
        val path = Path().also { neonPaint.getTextPath(s, 0, 1, 0f, 0f, it) }
        val radius = size * 0.11f
        val rect = RectF().also { path.computeBounds(it, true) }
        rect.inset(-radius * 2.4f, -radius * 2.4f)
        val scale = if (lowEnd) 0.25f else 0.5f
        val bmp = Bitmap.createBitmap(max(1, (rect.width() * scale).toInt()), max(1, (rect.height() * scale).toInt()),
            Bitmap.Config.ARGB_8888)
        val scaled = Path(path).apply {
            transform(Matrix().apply { setTranslate(-rect.left, -rect.top); postScale(scale, scale) })
        }
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = tint
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = size * 0.025f * scale
        }
        p.maskFilter = BlurMaskFilter(radius * scale, BlurMaskFilter.Blur.NORMAL)
        p.alpha = 210
        canvas.drawPath(scaled, p)
        p.maskFilter = BlurMaskFilter(radius * 0.32f * scale, BlurMaskFilter.Blur.NORMAL)
        p.alpha = 255
        canvas.drawPath(scaled, p)
        Glyph(path, bmp, rect, neonPaint.measureText(s))
    }

    /** A tube striking up: a few stutters, then a steady glow (the buzz follows the same steps). */
    private fun strike(ms: Long): Float = when {
        ms >= 900 -> 1f
        ms < 80 -> 0f
        ms < 140 -> 0.9f
        ms < 260 -> 0.08f
        ms < 320 -> 1f
        ms < 420 -> 0.25f
        else -> 0.6f + 0.4f * (ms - 420) / 480f
    }

    private fun drawNeon(canvas: Canvas) {
        val theme = neonTheme()
        val template = if (seconds) "88:88:88" else "88:88"
        neonPaint.textSize = 100f
        val size = min(height * 0.34f, width * 0.86f / neonPaint.measureText(template) * 100f)
        neonPaint.textSize = size
        if (size != glyphSize) { clearGlyphs(); glyphSize = size }

        val now = SystemClock.uptimeMillis()
        val sinceMinute = now - minuteAt
        val breath = if (lowEnd) 1f else 0.93f + 0.07f * sin(now * 2 * PI / 4200).toFloat()
        val ms = System.currentTimeMillis() % 1000
        val colon = when {
            seconds -> 1f
            lowEnd -> if (ms < 500) 1f else 0.4f
            else -> 0.45f + 0.55f * (0.5f + 0.5f * cos(ms * 2 * PI / 1000).toFloat())
        }

        // Characters with their tint and brightness.
        val chars = ArrayList<Triple<Char, Int, Float>>()
        hours.forEachIndexed { i, c -> chars += Triple(c, theme.hour, if (changed(i)) strike(sinceMinute) else 1f) }
        chars += Triple(':', theme.accent, colon)
        minutes.forEachIndexed { i, c -> chars += Triple(c, theme.minute, if (changed(hours.length + i)) strike(sinceMinute) else 1f) }
        if (seconds) {
            chars += Triple(':', theme.accent, 1f)
            secs.forEach { c -> chars += Triple(c, theme.second, 1f) }
        }
        if (!lowEnd && flutterIndex < 0 && Math.random() < 0.0012) {
            flutterIndex = (Math.random() * chars.size).toInt()
            flutterUntil = now + 160
        }
        if (now > flutterUntil) flutterIndex = -1

        val total = chars.sumOf { glyph(it.first, it.second).advance.toDouble() }.toFloat()
        val baseline = height * 0.58f
        val floor = baseline + size * 0.07f
        if (floor != floorFadeAt) {
            floorFade.shader = LinearGradient(0f, floor, 0f, floor + size * 0.9f,
                Color.argb(90, 0, 0, 0), Color.BLACK, Shader.TileMode.CLAMP)
            floorFadeAt = floor
        }

        fun pass(reflection: Boolean) {
            var x = (width - total) / 2
            chars.forEachIndexed { i, (c, tint, level) ->
                val g = glyph(c, tint)
                val l = level * breath * (if (i == flutterIndex) 0.45f else 1f) * (if (reflection) 0.22f else 1f)
                canvas.save()
                canvas.translate(x, baseline)
                drawTube(canvas, g, tint, l)
                canvas.restore()
                x += g.advance
            }
        }
        // Reflection on a dark floor, faded out below.
        canvas.save()
        canvas.scale(1f, -1f, 0f, floor)
        pass(reflection = true)
        canvas.restore()
        canvas.drawRect(-width.toFloat(), floor, width * 2f, height * 2f, floorFade)
        pass(reflection = false)

        neonDatePaint.textSize = height * 0.034f
        neonDatePaint.color = lighten(theme.accent, 0.55f)
        neonDatePaint.setShadowLayer(height * 0.012f, 0f, 0f, theme.accent)
        // The date strikes up with the first digits, then stays lit.
        neonDatePaint.alpha = (235 * breath * (if (previousHm.isEmpty()) strike(sinceMinute) else 1f)).toInt()
        canvas.drawText(date, width / 2f, baseline - size * 0.86f, neonDatePaint)
    }

    private fun drawTube(canvas: Canvas, g: Glyph, tint: Int, level: Float) {
        // An unlit tube stays faintly visible, like glass in the dark.
        tubePaint.color = tint
        tubePaint.alpha = 34
        canvas.drawPath(g.path, tubePaint)
        if (level <= 0.01f) return
        val a = (255 * level.coerceIn(0f, 1f)).toInt()
        glowPaint.alpha = a
        canvas.drawBitmap(g.glow, null, g.glowRect, glowPaint)
        tubePaint.color = lighten(tint, 0.72f)
        tubePaint.alpha = a
        canvas.drawPath(g.path, tubePaint)
    }

    // ---- Flip cards -------------------------------------------------------------------------

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    private val cardRect = RectF()

    private fun drawFlip(canvas: Canvas) {
        val theme = flipTheme()
        val count = if (seconds) 3 else 2
        var cardH = height * (if (seconds) 0.42f else 0.5f)
        var cardW = cardH * 1.08f
        var gap = cardW * 0.14f
        val fit = width * 0.9f / (cardW * count + gap * (count - 1))
        if (fit < 1f) { cardH *= fit; cardW *= fit; gap *= fit }
        val top = (height - cardH) / 2 - height * 0.04f
        val left = (width - cardW * count - gap * (count - 1)) / 2
        cardTextPaint.textSize = cardH * 0.74f
        cardTextPaint.color = theme.digit
        val now = SystemClock.uptimeMillis()
        val parts = listOf(hours, minutes, secs)
        val before = if (previousHm.length < 2) listOf("", "", previousSecs)
            else listOf(previousHm.dropLast(2), previousHm.takeLast(2), previousSecs)
        val since = listOf(now - minuteAt, now - minuteAt, now - secondAt)
        // Separators read against the page, whichever is lighter: card or ink.
        val mark = if (light(theme.card)) theme.card else theme.digit
        for (i in 0 until count) {
            val x = left + i * (cardW + gap)
            cardRect.set(x, top, x + cardW, top + cardH)
            val old = before[i]
            if (previousHm.isEmpty() || old == parts[i] || since[i] >= FLIP_MS) drawCard(canvas, cardRect, parts[i], theme)
            else flip(canvas, RectF(cardRect), old, parts[i], since[i] / FLIP_MS.toFloat(), theme)
            if (i < count - 1) {
                cardPaint.color = mark
                cardPaint.alpha = if (blinkOn()) 210 else 40
                val cx = x + cardW + gap / 2
                val r = cardH * 0.028f
                canvas.drawCircle(cx, top + cardH * 0.36f, r, cardPaint)
                canvas.drawCircle(cx, top + cardH * 0.64f, r, cardPaint)
            }
        }
        captionPaint.textSize = height * 0.034f
        captionPaint.color = mark
        captionPaint.alpha = 160
        canvas.drawText(date, width / 2f, top + cardH + height * 0.11f, captionPaint)
    }

    private fun flip(canvas: Canvas, r: RectF, old: String, now: String, p: Float, theme: ClockThemes.Flip) {
        val c = cos(p * PI).toFloat()
        half(canvas, r, now, top = true, scale = 1f, shade = 0f, theme = theme)
        half(canvas, r, old, top = false, scale = 1f, shade = 0f, theme = theme)
        if (p < 0.5f) half(canvas, r, old, top = true, scale = c, shade = (1 - c) * 0.6f, theme = theme)
        else half(canvas, r, now, top = false, scale = -c, shade = (1 + c) * 0.6f, theme = theme)
    }

    private fun half(canvas: Canvas, r: RectF, text: String, top: Boolean, scale: Float, shade: Float, theme: ClockThemes.Flip) {
        if (scale <= 0.001f) return
        val mid = r.centerY()
        canvas.save()
        canvas.clipRect(r.left, if (top) r.top else mid, r.right, if (top) mid else r.bottom)
        canvas.scale(1f, scale, r.centerX(), mid)
        drawCard(canvas, r, text, theme)
        if (shade > 0f) {
            shadePaint.color = Color.argb((255 * shade.coerceIn(0f, 1f)).toInt(), 0, 0, 0)
            val radius = r.width() * 0.06f
            canvas.drawRoundRect(r, radius, radius, shadePaint)
        }
        canvas.restore()
    }

    private fun drawCard(canvas: Canvas, r: RectF, text: String, theme: ClockThemes.Flip) {
        val radius = r.width() * 0.06f
        val mid = r.centerY()
        cardPaint.color = theme.card
        canvas.drawRoundRect(r, radius, radius, cardPaint)
        // The upper flap catches a little more light.
        canvas.save()
        canvas.clipRect(r.left, r.top, r.right, mid)
        cardPaint.color = if (light(theme.card)) Color.argb(14, 255, 255, 255) else Color.argb(10, 255, 255, 255)
        canvas.drawRoundRect(r, radius, radius, cardPaint)
        canvas.restore()
        val fm = cardTextPaint.fontMetrics
        canvas.drawText(text, r.centerX(), mid - (fm.ascent + fm.descent) / 2, cardTextPaint)
        // The hinge.
        cardPaint.color = theme.page
        val line = max(2f, r.height() * 0.008f)
        canvas.drawRect(r.left, mid - line / 2, r.right, mid + line / 2, cardPaint)
        val pin = r.height() * 0.022f
        cardPaint.color = Color.rgb(12, 12, 12)
        canvas.drawRoundRect(r.left - pin * 0.3f, mid - pin * 1.6f, r.left + pin, mid + pin * 1.6f, pin, pin, cardPaint)
        canvas.drawRoundRect(r.right - pin, mid - pin * 1.6f, r.right + pin * 0.3f, mid + pin * 1.6f, pin, pin, cardPaint)
    }

    // ---- LCD --------------------------------------------------------------------------------

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segmentPath = Path()
    private val lcdTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.2f
        textAlign = Paint.Align.CENTER
    }

    private fun drawLcd(canvas: Canvas) {
        val theme = lcdTheme()
        var digitH = height * 0.42f
        var digitW = digitH * 0.56f
        var gap = digitW * 0.24f
        var colonW = digitW * 0.62f
        val small = 0.46f
        fun total() = digitW * 4 + gap * 2 + colonW +
            (if (seconds) gap * 0.6f + digitW * small * 2 + gap * small else 0f)
        val fit = width * 0.84f / total()
        if (fit < 1f) { digitH *= fit; digitW *= fit; gap *= fit; colonW *= fit }
        val top = (height - digitH) / 2 - height * 0.05f
        var x = (width - total()) / 2
        val cx = width / 2f
        val cy = top + digitH / 2
        canvas.save()
        canvas.translate(cx, cy); canvas.skew(-0.07f, 0f); canvas.translate(-cx, -cy)
        val hh = hours.padStart(2, ' ')
        for (c in hh) { segments(canvas, c, x, top, digitW, digitH, theme); x += digitW + gap }
        // The colon: two squares centred between hours and minutes, blinking unless seconds are shown.
        val hourEnd = x - gap
        val dot = digitW * 0.17f
        val colonX = hourEnd + colonW / 2 - dot / 2
        segmentPaint.style = Paint.Style.FILL
        lit(theme, blinkOn())
        canvas.drawRect(colonX, top + digitH * 0.3f - dot / 2, colonX + dot, top + digitH * 0.3f + dot / 2, segmentPaint)
        canvas.drawRect(colonX, top + digitH * 0.7f - dot / 2, colonX + dot, top + digitH * 0.7f + dot / 2, segmentPaint)
        x = hourEnd + colonW
        for (c in minutes) { segments(canvas, c, x, top, digitW, digitH, theme); x += digitW + gap }
        if (seconds) {
            x += gap * 0.6f
            val sw = digitW * small
            val sh = digitH * small
            for (c in secs) { segments(canvas, c, x, top + digitH - sh, sw, sh, theme); x += sw + gap * small }
        }
        canvas.restore()
        lcdTextPaint.textSize = height * 0.04f
        lcdTextPaint.color = theme.lit
        lcdTextPaint.clearShadowLayer()
        if (theme.glow) lcdTextPaint.setShadowLayer(height * 0.012f, 0f, 0f, theme.lit)
        canvas.drawText(date, width / 2f, top + digitH + height * 0.12f, lcdTextPaint)
    }

    private fun lit(theme: ClockThemes.Lcd, on: Boolean) {
        segmentPaint.color = if (on) theme.lit else theme.unlit
        segmentPaint.clearShadowLayer()
        if (on && theme.glow) segmentPaint.setShadowLayer(height * 0.014f, 0f, 0f, theme.lit)
    }

    /** One seven-segment digit; unlit segments stay faintly visible, as on a real panel. A space lights none. */
    private fun segments(canvas: Canvas, c: Char, x: Float, y: Float, w: Float, h: Float, theme: ClockThemes.Lcd) {
        val on = SEGMENTS[c] ?: 0
        val t = w * 0.19f
        val g = t * 0.12f
        val top = y + t / 2; val mid = y + h / 2; val bottom = y + h - t / 2
        val left = x + t / 2; val right = x + w - t / 2
        // a, b, c, d, e, f, g
        val bars = arrayOf(
            floatArrayOf(left, top, right, top), floatArrayOf(right, top, right, mid),
            floatArrayOf(right, mid, right, bottom), floatArrayOf(left, bottom, right, bottom),
            floatArrayOf(left, mid, left, bottom), floatArrayOf(left, top, left, mid),
            floatArrayOf(left, mid, right, mid))
        for (pass in 0..1) for (i in 0..6) {
            val isOn = on and (1 shl i) != 0
            if ((pass == 0) == isOn) continue
            lit(theme, isOn)
            val b = bars[i]
            bar(canvas, b[0], b[1], b[2], b[3], t, g)
        }
    }

    /** A segment with pointed ends, from (x0, y0) to (x1, y1), horizontal or vertical. */
    private fun bar(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, t: Float, g: Float) {
        val half = t / 2
        segmentPath.reset()
        if (y0 == y1) {
            val a = x0 + g; val b = x1 - g
            segmentPath.moveTo(a, y0); segmentPath.lineTo(a + half, y0 - half); segmentPath.lineTo(b - half, y0 - half)
            segmentPath.lineTo(b, y0); segmentPath.lineTo(b - half, y0 + half); segmentPath.lineTo(a + half, y0 + half)
        } else {
            val a = y0 + g; val b = y1 - g
            segmentPath.moveTo(x0, a); segmentPath.lineTo(x0 + half, a + half); segmentPath.lineTo(x0 + half, b - half)
            segmentPath.lineTo(x0, b); segmentPath.lineTo(x0 - half, b - half); segmentPath.lineTo(x0 - half, a + half)
        }
        segmentPath.close()
        canvas.drawPath(segmentPath, segmentPaint)
    }

    // ---- Minimal ----------------------------------------------------------------------------

    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = 0.04f
    }

    private fun drawMinimal(canvas: Canvas) {
        val theme = minimalTheme()
        bigPaint.textSize = 100f
        val secondsScale = 0.38f
        val templateW = bigPaint.measureText("88:88") + (if (seconds) bigPaint.measureText(" 88") * secondsScale else 0f)
        val size = min(height * 0.32f, width * 0.82f / templateW * 100f)
        bigPaint.textSize = size
        val x0 = width * 0.09f
        val baseline = height * 0.57f
        val fade = min(1f, (SystemClock.uptimeMillis() - minuteAt) / 700f)
        bigPaint.color = theme.ink
        bigPaint.alpha = (240 * fade).toInt()
        var x = x0
        canvas.drawText(hours, x, baseline, bigPaint); x += bigPaint.measureText(hours)
        bigPaint.alpha = (240 * fade * (if (blinkOn()) 1f else 0.15f)).toInt()
        canvas.drawText(":", x, baseline, bigPaint); x += bigPaint.measureText(":")
        bigPaint.alpha = (240 * fade).toInt()
        canvas.drawText(minutes, x, baseline, bigPaint); x += bigPaint.measureText(minutes)
        if (seconds) {
            bigPaint.textSize = size * secondsScale
            bigPaint.alpha = 150
            canvas.drawText(secs, x + size * 0.08f, baseline, bigPaint)
            bigPaint.textSize = size
        }
        smallPaint.textSize = height * 0.042f
        smallPaint.color = theme.ink
        smallPaint.alpha = 165
        canvas.drawText(date, x0 + height * 0.012f, baseline + height * 0.12f, smallPaint)
    }

    // ---- Snow and stars ---------------------------------------------------------------------

    /** x, y, radius, speed, phase per particle. */
    private var particles: FloatArray? = null
    private var particlesAt = 0L
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(SystemClock.uptimeMillis())

    private fun drawParticles(canvas: Canvas) {
        val snow = effect == Prefs.CLOCK_EFFECT_SNOW
        val n = if (snow) (if (lowEnd) 45 else 110) else (if (lowEnd) 70 else 140)
        val r = random
        val p = particles ?: FloatArray(n * 5).also { a ->
            for (i in 0 until n) {
                a[i * 5] = r.nextFloat() * width
                a[i * 5 + 1] = r.nextFloat() * height
                a[i * 5 + 2] = dp(if (snow) 1.2f + r.nextFloat() * 3.6f else 0.6f + r.nextFloat() * 1.6f).toFloat()
                a[i * 5 + 3] = if (snow) dp(18 + r.nextFloat() * 50).toFloat() else 0.6f + r.nextFloat() * 1.6f
                a[i * 5 + 4] = r.nextFloat() * 6.28f
            }
            particles = a
            particlesAt = SystemClock.uptimeMillis()
        }
        val now = SystemClock.uptimeMillis()
        val dt = ((now - particlesAt) / 1000f).coerceIn(0f, 0.1f)
        particlesAt = now
        val t = now / 1000f
        val ink = if (light(background())) Color.rgb(90, 90, 90) else Color.WHITE
        for (i in 0 until p.size / 5) {
            val k = i * 5
            if (snow) {
                p[k + 1] += p[k + 3] * dt
                p[k] += sin(t * 0.8f + p[k + 4]) * dp(14) * dt
                if (p[k + 1] > height + p[k + 2]) { p[k + 1] = -p[k + 2]; p[k] = r.nextFloat() * width }
                particlePaint.color = ink
                // Larger flakes are nearer: brighter.
                particlePaint.alpha = (90 + 140 * (p[k + 2] / dp(4.8f))).toInt().coerceIn(60, 230)
            } else {
                particlePaint.color = ink
                particlePaint.alpha = (40 + 190 * (0.5f + 0.5f * sin(t * p[k + 3] + p[k + 4]))).toInt()
            }
            canvas.drawCircle(p[k], p[k + 1], p[k + 2], particlePaint)
        }
    }

    // ---- Style / colour label ---------------------------------------------------------------

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val labelBack = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawAnnouncement(canvas: Canvas) {
        val since = SystemClock.uptimeMillis() - announcedAt
        if (since >= ANNOUNCE_MS || announcement.isEmpty()) return
        val a = if (since < ANNOUNCE_MS - 500) 1f else (ANNOUNCE_MS - since) / 500f
        labelPaint.textSize = height * 0.03f
        val w = labelPaint.measureText(announcement) + height * 0.06f
        val h = height * 0.06f
        val cy = height * 0.9f
        labelBack.color = Color.argb((150 * a).toInt(), 40, 40, 44)
        canvas.drawRoundRect(width / 2f - w / 2, cy - h / 2, width / 2f + w / 2, cy + h / 2, h / 2, h / 2, labelBack)
        labelPaint.color = Color.argb((235 * a).toInt(), 255, 255, 255)
        val fm = labelPaint.fontMetrics
        canvas.drawText(announcement, width / 2f, cy - (fm.ascent + fm.descent) / 2, labelPaint)
    }

    private fun lighten(c: Int, amount: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * amount).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * amount).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * amount).toInt())

    companion object {
        private const val STRIKE_MS = 1000L
        private const val FLIP_MS = 640L
        private const val DRIFT_MS = 3000L
        private const val ANNOUNCE_MS = 1800L

        /** Bits a..g (0..6) lit for each digit. */
        val SEGMENTS: Map<Char, Int> = mapOf(
            '0' to 0b0111111, '1' to 0b0000110, '2' to 0b1011011, '3' to 0b1001111, '4' to 0b1100110,
            '5' to 0b1101101, '6' to 0b1111101, '7' to 0b0000111, '8' to 0b1111111, '9' to 0b1101111)

        fun styleName(style: String) = when (style) {
            Prefs.CLOCK_SAVER_FLIP -> R.string.clock_saver_flip
            Prefs.CLOCK_SAVER_MINIMAL -> R.string.clock_saver_minimal
            Prefs.CLOCK_SAVER_LCD -> R.string.clock_saver_lcd
            else -> R.string.clock_saver_neon
        }

        fun effectName(effect: String) = when (effect) {
            Prefs.CLOCK_EFFECT_SNOW -> R.string.clock_effect_snow
            Prefs.CLOCK_EFFECT_STARS -> R.string.clock_effect_stars
            else -> R.string.clock_effect_none
        }
    }
}
