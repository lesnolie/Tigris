package com.leslie.tvhome

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Photo frame in the spirit of the Apple TV screensaver: the cached photos
 * cross-fade full screen with a slow pan and zoom (which also keeps an OLED
 * panel from holding a still image), with the time and the photo's caption
 * in a corner that drifts a little every minute.
 */
class PhotoFrameView(ctx: Context) : FrameLayout(ctx) {
    private val prefs = Prefs(ctx)
    private val walls = Wallpapers(ctx, Wallpapers.sourceFor(prefs.background))
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val layers = arrayOf(image(), image())
    private var front = 0
    private var last: File? = null
    private var any = false
    private var started = false

    private val clock = ctx.text(64f, weight = 250).apply { shadow() }
    private val date = ctx.text(18f, weight = 500).apply { shadow() }
    private val caption = ctx.text(14f, Color.argb(190, 255, 255, 255)).apply { shadow(); maxLines = 2 }
    private val corner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        // Full width: a wrap_content clock measured before its first text clips the last digit.
        addView(clock, LinearLayout.LayoutParams(-1, -2))
        addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        addView(caption, LinearLayout.LayoutParams(dp(520), -2).apply { topMargin = dp(10) })
    }

    private val advance = object : Runnable {
        override fun run() {
            next()
            main.postDelayed(this, prefs.frameSeconds * 1000L)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.CHINA).format(now)
            date.text = SimpleDateFormat("EEEE, MMM d", Locale.CHINA).format(now)
            // Drift a few pixels each minute so nothing sits still on the panel.
            corner.animate().translationX(((Math.random() - 0.5) * dp(24)).toFloat())
                .translationY(((Math.random() - 0.5) * dp(16)).toFloat()).setDuration(3000).start()
            main.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        layers.forEach { addView(it, LayoutParams(-1, -1)) }
        addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(150, 0, 0, 0), Color.TRANSPARENT))
        }, LayoutParams(-1, dp(220), android.view.Gravity.BOTTOM))
        addView(corner, LayoutParams(dp(560), -2, android.view.Gravity.BOTTOM or android.view.Gravity.START).apply {
            leftMargin = dp(56); bottomMargin = dp(44)
        })
    }

    private fun image() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
    }

    private fun android.widget.TextView.shadow() =
        setShadowLayer(dp(10).toFloat(), 0f, dp(1).toFloat(), Color.argb(150, 0, 0, 0))

    fun start() {
        if (started) return
        started = true
        tick.run()
        io.execute {
            walls.refresh()
            any = walls.files().isNotEmpty()
            main.post { if (started) advance.run() }
        }
    }

    fun stop() {
        started = false
        main.removeCallbacks(advance)
        main.removeCallbacks(tick)
        layers.forEach { it.animate().cancel() }
    }

    private fun next() {
        if (!any) return
        io.execute {
            // A new picture while the day's list lasts (downloaded now), then round the ones here.
            val f = walls.next(last) ?: return@execute
            last = f
            val bmp = walls.decode(f, full = true) ?: return@execute
            val text = walls.caption(f)
            main.post { if (started) show(bmp, text) }
        }
    }

    private fun show(bmp: Bitmap, text: String) {
        val back = layers[front]
        front = 1 - front
        val v = layers[front]
        v.animate().cancel()
        v.setImageBitmap(bmp)
        v.bringToFront()
        corner.bringToFront()
        // Ken Burns: a slow zoom and drift over the whole time the photo is up.
        val span = prefs.frameSeconds * 1000L + 2500
        val zoomIn = Math.random() < 0.5
        val (from, to) = if (zoomIn) 1.04f to 1.16f else 1.16f to 1.04f
        v.scaleX = from; v.scaleY = from
        v.translationX = ((Math.random() - 0.5) * dp(40)).toFloat()
        v.translationY = ((Math.random() - 0.5) * dp(24)).toFloat()
        v.animate().scaleX(to).scaleY(to)
            .translationX(((Math.random() - 0.5) * dp(40)).toFloat())
            .translationY(((Math.random() - 0.5) * dp(24)).toFloat())
            .setDuration(span).setInterpolator(LinearInterpolator()).start()
        android.animation.ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).apply {
            duration = 2000
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    back.alpha = 0f
                    back.setImageDrawable(null)
                }
            })
            start()
        }
        caption.text = text
        caption.visibility = if (text.isEmpty()) GONE else VISIBLE
    }
}

/**
 * Opened from the Control Center, or by the home screen ahead of Xiaomi's own screensaver: the
 * screensaver chosen in settings until any key but left / right is pressed. The screen is kept on
 * for four hours, after which the TV's own standby rules apply again.
 */
class FrameActivity : Activity() {
    private lateinit var frame: SaverHost
    private val main = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        main.postDelayed(releaseScreen, 4 * 3_600_000L)
        frame = SaverHost(this)
        setContentView(frame)
    }

    override fun onResume() { super.onResume(); frame.start() }
    override fun onPause() { frame.stop(); super.onPause() }
    override fun onDestroy() { main.removeCallbacks(releaseScreen); super.onDestroy() }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean = when (e.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME -> super.dispatchKeyEvent(e)
        else -> {
            if (!frame.onKey(e) && e.action == KeyEvent.ACTION_UP) finish()
            true
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

/** The same screensaver as the system one, shown after the TV has been idle. */
class PhotoDream : DreamService() {
    private var frame: SaverHost? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Interactive so that left / right can skip a film; every other key ends the dream.
        isInteractive = true
        isFullscreen = true
        frame = SaverHost(this).also { setContentView(it) }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (frame?.onKey(e) == true) return true
        when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(e)
        }
        if (e.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); frame?.start() }
    override fun onDreamingStopped() { frame?.stop(); super.onDreamingStopped() }
}
