package com.leslie.tvhome

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.min

/** First-use welcome. Offline vector ink on a cached sky; no video decoder or network. */
internal class Welcome(
    context: Context,
    private val onContinue: () -> Unit,
    private val onCancel: () -> Unit,
) : FrameLayout(context), Choreographer.FrameCallback {
    companion object {
        const val EXTRA_PREVIEW = "com.leslie.tvhome.WELCOME_PREVIEW"
        private const val END_MS = 5_600L
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val artwork = SkyLogo(context)
    private val next = context.text(17f, weight = 500).apply {
        text = I18n.text(R.string.welcome_continue)
        gravity = Gravity.CENTER
        setTextColor(0xff315576.toInt())
        setPadding(context.dp(24), context.dp(12), context.dp(24), context.dp(12))
        visibility = View.INVISIBLE
        isFocusable = true; isClickable = true
        setOnClickListener { complete() }
        setOnFocusChangeListener { _, focused ->
            setTextColor(if (focused) 0xff254563.toInt() else 0xff315576.toInt())
            tweenScale(if (focused) 1.035f else 1f, 220)
        }
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private var loaded = false
    private var active = false
    private var closed = false
    private var ready = false
    private var elapsed = 0L
    private var startTime = 0L

    init {
        setBackgroundColor(0xff3687ce.toInt())
        translationZ = context.dp(402).toFloat()
        isFocusable = true
        // While writing, one confirmation finishes the ink; another confirmation continues.
        contentDescription = I18n.text(R.string.welcome_writing)
        addView(artwork, LayoutParams(-1, -1))
        addView(next, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (context.resources.displayMetrics.heightPixels * .105f).toInt()
        })
        worker.execute {
            val result = runCatching { SkyLogo.load(context) }.getOrNull()
            post {
                if (!closed) {
                    artwork.assets = result
                    loaded = true
                    if (active) schedule()
                }
            }
        }
        requestFocus()
    }

    fun resume() {
        if (closed || active) return
        active = true
        if (loaded) schedule()
    }

    fun pause() {
        if (!active) return
        if (startTime != 0L) elapsed += SystemClock.uptimeMillis() - startTime
        startTime = 0L
        active = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun schedule() {
        if (elapsed >= END_MS) { render(elapsed); return }
        startTime = SystemClock.uptimeMillis()
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (closed || !active) return
        val now = elapsed + SystemClock.uptimeMillis() - startTime
        render(now)
        if (now < END_MS) Choreographer.getInstance().postFrameCallback(this)
        else { elapsed = END_MS; startTime = 0L }
    }

    private fun render(time: Long) {
        artwork.progress = ((time - 300) / 4_300f).coerceIn(0f, 1f)
        val hint = ((time - 4_950) / 650f).coerceIn(0f, 1f)
        if (hint > 0f && !ready) {
            ready = true
            contentDescription = null
            next.visibility = View.VISIBLE
            next.requestFocus()
        }
        next.alpha = hint
        next.translationY = context.dp(8) * (1 - hint)
    }

    /** Consume navigation so the covered Home grid never responds to the remote. */
    fun key(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP || event.isCanceled) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (ready) complete() else finishWriting()
            }
            KeyEvent.KEYCODE_BACK -> { close(); onCancel() }
        }
        return true
    }

    private fun finishWriting() {
        elapsed = END_MS
        startTime = 0L
        Choreographer.getInstance().removeFrameCallback(this)
        render(END_MS)
    }

    private fun complete() {
        if (closed || !ready) return
        close()
        onContinue()
    }

    fun close() {
        if (closed) return
        closed = true
        active = false
        Choreographer.getInstance().removeFrameCallback(this)
        (next.getTag(R.id.motion_focus) as? Tween)?.cancel()
        worker.shutdownNow()
    }

    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }

    /** Reuses the approved logo's outlines, revealed by actual centreline strokes in writing order. */
    private class SkyLogo(context: Context) : View(context) {
        data class Stroke(val measure: PathMeasure, val width: Float, val start: Float) {
            val length = measure.length
        }
        data class Letter(val x: Float, val outline: Path, val strokes: List<Stroke>)
        data class Assets(val sky: Bitmap, val letters: List<Letter>, val length: Float)
        var assets: Assets? = null
            set(value) { field = value; invalidate() }
        var progress = 0f
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val segment = Path()
        private val bounds = RectF()

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        override fun onDraw(canvas: Canvas) {
            val data = assets
            if (data == null) {
                // Still usable if the asset fails: do not leave a permanently covered Home screen.
                if (progress > 0f) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = width * .09f
                    paint.typeface = Typeface.create("cursive", Typeface.NORMAL)
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("Tigris", width * .5f, height * .53f, paint)
                }
                return
            }
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(data.sky, null, bounds, paint)
            val sceneScale = width * .43f / 1024
            val unit = sceneScale * .3f
            val left = width * .5f - width * .43f / 2 + (1024 - 2550 * .3f) / 2 * sceneScale
            val baseline = height * .5f + (315 - 256) * sceneScale
            val distance = progress * data.length
            for (letter in data.letters) {
                val saved = canvas.save()
                canvas.translate(left + letter.x * unit, baseline)
                canvas.scale(unit, -unit)
                val last = letter.strokes.last()
                if (distance >= last.start + last.length) {
                    paint.style = Paint.Style.FILL
                    canvas.drawPath(letter.outline, paint)
                } else {
                    canvas.clipPath(letter.outline)
                    paint.style = Paint.Style.STROKE
                    for (stroke in letter.strokes) {
                        val length = min(stroke.length, distance - stroke.start)
                        if (length <= 0) continue
                        segment.rewind()
                        stroke.measure.getSegment(0f, length, segment, true)
                        // Keep the first dot segment visible on older hardware renderers.
                        segment.rLineTo(0f, 0f)
                        paint.strokeWidth = stroke.width
                        canvas.drawPath(segment, paint)
                    }
                }
                canvas.restoreToCount(saved)
            }
        }

        companion object {
            fun load(context: Context): Assets {
                val json = context.assets.open("welcome/wordmark.json").bufferedReader().use { JSONObject(it.readText()) }
                var distance = 0f
                val letters = json.getJSONArray("letters")
                val result = (0 until letters.length()).map { index ->
                    val item = letters.getJSONObject(index)
                    val paths = item.getJSONArray("strokes")
                    val strokes = (0 until paths.length()).map { i ->
                        val stroke = paths.getJSONObject(i)
                        Stroke(PathMeasure(path(stroke.getString("path")), false), stroke.getDouble("width").toFloat(), distance).also {
                            distance += it.length + 100
                        }
                    }
                    Letter(item.getDouble("x").toFloat(), path(item.getString("path")), strokes)
                }
                return Assets(sky(), result, distance)
            }

            /** This small asset uses only absolute M/L/Q/C/Z commands and implicit line segments. */
            private fun path(source: String): Path {
                val tokens = Regex("[MLQCZ]|[-+]?(?:\\d*\\.\\d+|\\d+)").findAll(source).map { it.value }.toList()
                val path = Path()
                var i = 0
                var command = 'M'
                fun number() = tokens[i++].toFloat()
                while (i < tokens.size) {
                    if (tokens[i][0].isLetter()) command = tokens[i++][0]
                    when (command) {
                        'M' -> { path.moveTo(number(), number()); command = 'L' }
                        'L' -> path.lineTo(number(), number())
                        'Q' -> path.quadTo(number(), number(), number(), number())
                        'C' -> path.cubicTo(number(), number(), number(), number(), number(), number())
                        'Z' -> path.close()
                    }
                }
                return path
            }

            /** Soft white cloud banks on the same blue sky as the approved first variant. */
            private fun sky(): Bitmap {
                val image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
                val c = Canvas(image)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = LinearGradient(0f, 0f, 0f, 360f,
                    intArrayOf(0xff3284ca.toInt(), 0xff7db3df.toInt(), 0xffcee4f6.toInt()),
                    floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, 640f, 360f, p)
                fun cloud(x: Float, y: Float, rx: Float, ry: Float, opacity: Int) {
                    val saved = c.save()
                    c.translate(x * 640, y * 360)
                    c.scale(rx * 640, ry * 360)
                    p.shader = RadialGradient(0f, 0f, 1f,
                        intArrayOf(Color.argb(opacity, 255, 255, 255), Color.argb(opacity * 3 / 4, 255, 255, 255), Color.TRANSPARENT),
                        floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
                    c.drawRect(-1f, -1f, 1f, 1f, p)
                    c.restoreToCount(saved)
                }
                cloud(.15f, 1.04f, .39f, .45f, 210)
                cloud(.40f, 1.08f, .24f, .51f, 225)
                cloud(.76f, 1.06f, .30f, .49f, 235)
                cloud(.97f, 1.01f, .22f, .31f, 190)
                cloud(.61f, 1.2f, .68f, .45f, 195)
                return image
            }
        }
    }
}
