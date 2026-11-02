package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The boot animation: a video of the user's (an Apple TV start-up clip, say) played full screen,
 * then the logo it ends on zooms towards the viewer and opens onto the home screen, the way tvOS
 * flies into its home screen. The logo is taken from the video's last frame (whatever is bright on
 * black), so any clip that ends on a logo works. Without a video, the name "Tigris" stands in.
 *
 * A default video is available as an optional download; the phone page (壁纸 → QR code) can upload an override, or adb
 * can push one to Android/data/com.leslie.tvhome/files/intro.mp4.
 *
 * API 31+ starts on a fixed delay from the wake signal ([start]). Android 9–11 wait until the
 * display is actually on ([startAfterDisplay]): those panels stay dark after screen-on, and a
 * TextureView started in that window plays audio with a black picture.
 */
class Intro(
    private val ctx: Context,
    private val host: FrameLayout,
    private val scene: View,
    private val sound: Boolean,
    private val onDone: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val overlay = FrameLayout(ctx).apply {
        setBackgroundColor(Color.BLACK)
        // Above dialogs (200 dp) and the control center (100 dp).
        translationZ = ctx.dp(400).toFloat()
        outlineProvider = null
    }
    private var player: MediaPlayer? = null
    private var texture: TextureView? = null
    private var reveal: Reveal? = null
    private val zoom = Tween()
    private var finished = false
    private var revealing = false
    /** The logo, cut out of a frame shortly before the end, so the zoom can start the moment the video ends. */
    private var logo: Logo? = null
    private var cutting = false
    /** Cutting is over; [logo] stays null if nothing could be cut out. */
    private var cut = false
    /** The video ended (or was skipped) while the logo was still being cut out. */
    private var ended = false
    /** Nothing may keep the home screen covered: whatever goes wrong, the intro ends by then. */
    private val watchdog: Runnable = Runnable { onWatchdog() }

    private fun onWatchdog() {
        if (!finished && deferring && !begun && displayState() != BootIntroGate.DISPLAY_ON &&
            SystemClock.elapsedRealtime() - armedAt < MAX_WAIT_MS
        ) {
            BootLog.i("watchdog extended display=${BootIntroGate.displayLabel(displayState())}")
            main.postDelayed(watchdog, 5_000L)
            return
        }
        finish()
    }
    private var begun = false
    private var deferring = false
    private var wakePlay = false
    private var settleMs = 0L
    private var armedAt = 0L
    private var readySince: Long? = null
    private var frameOk = false
    private var framePending = false
    private var settleLogged = false
    private var lastDisplay = Int.MIN_VALUE
    private var displayManager: DisplayManager? = null
    private var playerSurface: Surface? = null
    private var playingFile: File? = null
    private var renderAttempts = 0
    private var loggedTexture = false
    private var heldForDisplay = false
    private val settleRunnable = Runnable { evaluate("settle") }
    private val giveUpRunnable = Runnable { evaluate("give-up") }
    private val renderWatch = Runnable { retargetIfNoFrame() }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY || finished) return
            val state = displayState()
            if (state == lastDisplay) return
            BootLog.i("display ${BootIntroGate.displayLabel(lastDisplay)} -> ${BootIntroGate.displayLabel(state)}")
            lastDisplay = state
            if (begun) holdForDisplay(state != BootIntroGate.DISPLAY_ON)
            evaluate("display")
        }
    }

    /** Covers the screen at once; the animation itself begins after [delay] ms of black. */
    fun start(delay: Long = 0) {
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        BootLog.i("cover path=legacy delay=$delay sdk=${Build.VERSION.SDK_INT}")
        main.postDelayed(watchdog, 15_000 + delay)
        if (delay > 0) main.postDelayed({ if (!finished) begin() }, delay) else begin()
    }

    /**
     * API 28–30. The black cover goes up immediately. Playback waits until the display is
     * [BootIntroGate.DISPLAY_ON], this window has focus and a frame has been drawn, then
     * [wakeDelayMs] (wake only) so the panel can light before the clip's audio starts.
     */
    fun startAfterDisplay(wake: Boolean, wakeDelayMs: Long) {
        deferring = true
        wakePlay = wake
        settleMs = wakeDelayMs
        armedAt = SystemClock.elapsedRealtime()
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        main.postDelayed(watchdog, 15_000L + wakeDelayMs.coerceAtLeast(0L) + BootIntroGate.GIVE_UP_MS)
        main.postDelayed(giveUpRunnable, BootIntroGate.GIVE_UP_MS)
        val manager = ctx.getSystemService(DisplayManager::class.java)
        displayManager = manager
        lastDisplay = displayState()
        BootLog.i(
            "cover path=pre31 wake=$wake settle=$wakeDelayMs display=${BootIntroGate.displayLabel(lastDisplay)} focus=${host.hasWindowFocus()} sdk=${Build.VERSION.SDK_INT}"
        )
        manager?.registerDisplayListener(displayListener, main)
        evaluate("arm")
    }

    /** Still waiting for a lit, focused window. A pause in that gap must not cancel the wake clip. */
    fun isWaitingForDisplay(): Boolean = deferring && !begun && !finished

    /** [HomeActivity] forwards window focus. Ignored once playback has begun and on the legacy path. */
    fun onWindowFocus(hasFocus: Boolean) {
        if (!deferring || begun || finished) return
        evaluate(if (hasFocus) "focus" else "unfocus")
    }

    private fun displayState(): Int =
        (displayManager ?: ctx.getSystemService(DisplayManager::class.java))
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: -1

    private fun evaluate(reason: String) {
        if (finished || begun || !deferring) return
        val state = displayState()
        val focus = host.hasWindowFocus()
        val now = SystemClock.elapsedRealtime()
        val displayOn = state == BootIntroGate.DISPLAY_ON
        if (!displayOn || !focus) {
            if (frameOk || readySince != null) {
                BootLog.i("wait display=${BootIntroGate.displayLabel(state)} focus=$focus reason=$reason")
            }
            frameOk = false
            readySince = null
            settleLogged = false
            main.removeCallbacks(settleRunnable)
        } else if (!frameOk && !framePending) {
            framePending = true
            Choreographer.getInstance().postFrameCallback {
                framePending = false
                if (finished || begun || !deferring) return@postFrameCallback
                if (displayState() == BootIntroGate.DISPLAY_ON && host.hasWindowFocus()) {
                    frameOk = true
                    BootLog.i("first frame reason=$reason")
                }
                evaluate("frame")
            }
        }
        if (displayOn && focus && frameOk && readySince == null) readySince = now
        val decision = BootIntroGate.decide(
            Build.VERSION.SDK_INT, wakePlay, settleMs, state, focus, frameOk, now, readySince, armedAt,
        )
        when (decision.action) {
            BootIntroGate.Action.WAIT -> {
                if (decision.delayMs > 0L && !settleLogged) {
                    settleLogged = true
                    BootLog.i("settle ${decision.delayMs}ms reason=$reason")
                    main.postDelayed(settleRunnable, decision.delayMs)
                }
            }
            BootIntroGate.Action.START -> {
                BootLog.i("display ready reason=$reason display=${BootIntroGate.displayLabel(state)}")
                begin()
            }
            BootIntroGate.Action.START_UNREADY -> {
                // Still dark, unfocused, or waiting on the frame we just scheduled: keep the cover.
                // The watchdog gives up later so Home cannot stay black.
                if (!displayOn || !focus || framePending) {
                    BootLog.i(
                        "readiness timeout display=${BootIntroGate.displayLabel(state)} focus=$focus frame=$frameOk reason=$reason; still waiting"
                    )
                    return
                }
                BootLog.i("readiness timeout; starting without a frame reason=$reason")
                begin()
            }
        }
    }

    /** Pause a clip that has already started if the panel drops out again, then resume on STATE_ON. */
    private fun holdForDisplay(hold: Boolean) {
        val mp = player ?: return
        if (hold == heldForDisplay) return
        if (hold) {
            val playing = runCatching { mp.isPlaying }.getOrDefault(false)
            if (!playing) return
            if (runCatching { mp.pause() }.isFailure) return
            heldForDisplay = true
            main.removeCallbacks(renderWatch)
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, 20_000L)
            BootLog.i("playback paused; display not on")
        } else {
            heldForDisplay = false
            runCatching { mp.start() }
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, 15_000L)
            if (!loggedTexture) armRenderWatch()
            BootLog.i("playback resumed; display on")
        }
    }

    private fun begin() {
        if (finished || begun) return
        begun = true
        deferring = false
        main.removeCallbacks(settleRunnable)
        main.removeCallbacks(giveUpRunnable)
        if (Build.VERSION.SDK_INT < 31) {
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, 15_000L)
        }
        val wordmark = Prefs(ctx).introSource == Prefs.INTRO_WORDMARK
        val video = if (wordmark) null else videoFile(ctx) ?: OptionalAssets.file(ctx, OptionalAssets.intro)
        playingFile = video
        BootLog.i("animation start source=${if (video == null) "wordmark" else "video"} file=${video?.name ?: "-"}")
        if (video == null) { showWordmark(); return }
        val tv = TextureView(ctx)
        if (Build.VERSION.SDK_INT < 31) {
            // An opaque TextureView on API 28 (MediaTek BRAVIA) composites as black while audio runs.
            tv.isOpaque = false
        }
        texture = tv
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                BootLog.i("surface available ${w}x${h}")
                if (Build.VERSION.SDK_INT >= 31) {
                    play(video, Surface(st))
                    return
                }
                if (!BootIntroGate.surfaceCanPlay(Build.VERSION.SDK_INT, w, h)) return
                st.setDefaultBufferSize(w, h)
                bindSurface(video, st)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                BootLog.i("surface size ${w}x${h}")
                if (Build.VERSION.SDK_INT >= 31 || !BootIntroGate.surfaceCanPlay(Build.VERSION.SDK_INT, w, h)) return
                st.setDefaultBufferSize(w, h)
                if (player == null) bindSurface(video, st)
            }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                BootLog.i("surface destroyed")
                if (Build.VERSION.SDK_INT < 31) {
                    runCatching { player?.setSurface(null) }
                    playerSurface?.release()
                    playerSurface = null
                }
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                if (!loggedTexture) {
                    loggedTexture = true
                    BootLog.i("texture frame")
                    main.removeCallbacks(renderWatch)
                }
                val mp = player ?: return
                val left = runCatching { mp.duration - mp.currentPosition }.getOrDefault(Int.MAX_VALUE)
                if (left in 0..600) cutLogo()
            }
        }
        overlay.addView(tv, FrameLayout.LayoutParams(-1, -1))
    }

    private fun bindSurface(file: File, st: SurfaceTexture) {
        val surface = Surface(st)
        val existing = player
        if (existing != null) {
            runCatching { existing.setSurface(surface) }.onFailure {
                BootLog.i("surface retarget failed ${it.javaClass.simpleName}")
            }
            playerSurface?.release()
            playerSurface = surface
            BootLog.i("surface retarget")
            armRenderWatch()
            return
        }
        playerSurface?.release()
        playerSurface = surface
        play(file, surface)
    }

    private fun armRenderWatch() {
        main.removeCallbacks(renderWatch)
        main.postDelayed(renderWatch, RENDER_WAIT_MS)
    }

    /** One retarget: a decoder left attached through standby plays audio into a dead buffer. */
    private fun retargetIfNoFrame() {
        if (finished || player == null || Build.VERSION.SDK_INT >= 31) return
        val file = playingFile ?: return
        val st = texture?.surfaceTexture
        if (st == null || renderAttempts >= 1) {
            BootLog.i("video frame missing")
            return
        }
        renderAttempts++
        BootLog.i("video frame missing; retarget")
        bindSurface(file, st)
    }

    private fun play(file: File, surface: Surface) {
        if (finished) return
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(file.path)
            mp.setSurface(surface)
            if (!sound) mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h -> BootLog.i("video size ${w}x${h}") }
            mp.setOnInfoListener { _, what, extra ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        BootLog.i("video frame")
                        main.removeCallbacks(renderWatch)
                    }
                    MediaPlayer.MEDIA_INFO_VIDEO_NOT_PLAYING -> BootLog.i("video not playing extra=$extra")
                }
                false
            }
            mp.setOnPreparedListener {
                BootLog.i("prepared duration=${runCatching { it.duration }.getOrDefault(-1)}")
                it.start()
                BootLog.i("playback started")
                if (Build.VERSION.SDK_INT < 31) {
                    if (displayState() != BootIntroGate.DISPLAY_ON) holdForDisplay(true)
                    if (!heldForDisplay) armRenderWatch()
                }
            }
            mp.setOnCompletionListener { main.post { revealFromVideo() } }
            mp.setOnErrorListener { _, what, extra ->
                BootLog.i("player error what=$what extra=$extra")
                main.post { revealFromVideo() }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            BootLog.i("player failed ${e.javaClass.simpleName}")
            revealFromVideo()
        }
    }

    /** Cuts the logo out of the frame on screen, off the main thread (the video ends on a still logo). */
    private fun cutLogo() {
        if (cutting || cut || finished) return
        cutting = true
        val frame = texture?.takeIf { it.isAvailable }?.let { runCatching { it.getBitmap(host.width, host.height) }.getOrNull() }
        if (frame == null) { main.post { cutDone(null) }; return }
        Thread {
            val l = runCatching { Logo.from(frame) }.getOrNull()
            frame.recycle()
            main.post { cutDone(l) }
        }.start()
    }

    private fun cutDone(l: Logo?) {
        logo = l
        cutting = false
        cut = true
        if (ended) zoomOrFade()
    }

    /** The video ended: its last frame stays up until the logo is cut out of it, then the zoom starts. */
    private fun revealFromVideo() {
        if (finished || revealing) return
        revealing = true
        ended = true
        BootLog.i("reveal")
        if (!cut) cutLogo()
        // Releasing a player can block for a while; the last frame stays on the TextureView meanwhile.
        player?.let { mp ->
            if (Build.VERSION.SDK_INT < 31) {
                runCatching { mp.setSurface(null) }
                playerSurface?.release()
                playerSurface = null
            }
            Thread { runCatching { mp.release() } }.start()
        }
        player = null
        if (cut) zoomOrFade()
    }

    private fun zoomOrFade() { logo?.let { zoomInto(it) } ?: fadeOut() }

    /** No video: the name fades in on black, then flies open like a logo would. */
    private fun showWordmark() {
        revealing = true
        val w = host.width.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels
        val h = host.height.takeIf { it > 0 } ?: ctx.resources.displayMetrics.heightPixels
        Thread {
            val logo = Logo.wordmark(ctx, w, h)
            main.post {
                if (finished) return@post
                val r = Reveal(logo).also { reveal = it }
                overlay.addView(r, FrameLayout.LayoutParams(-1, -1))
                r.alpha = 0f
                r.animate().alpha(1f).setDuration(600).withEndAction {
                    main.postDelayed({ zoomInto(logo) }, 700)
                }.start()
            }
        }.start()
    }

    /** The logo swells, as if the viewer flew into it, and the home screen opens up through it. */
    private fun zoomInto(logo: Logo) {
        if (finished) return
        val r = reveal ?: Reveal(logo).also { reveal = it; overlay.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        // The reveal draws the black itself; the overlay's own black and the video go.
        overlay.background = null
        texture?.let { overlay.removeView(it) }
        texture = null
        scene.pivotX = logo.pivotX
        scene.pivotY = logo.pivotY
        scene.scaleX = 1.12f; scene.scaleY = 1.12f
        val accel = AccelerateInterpolator(1.6f)
        // Real time, like the video before it: a faster animation scale would cut the flight short.
        zoom.start((1100 / Tween.scale()).toLong(), android.view.animation.LinearInterpolator(), end = { finish() }) { t ->
            // A slight draw back first, then the zoom gathers speed (even in log scale).
            val pull = 1f - 0.05f * sin(Math.PI * min(t / 0.28f, 1f)).toFloat()
            r.scale = logo.endScale.pow(accel.getInterpolation(t)) * pull
            r.progress = t
            r.invalidate()
            val settle = Motion.ease.getInterpolation(((t - 0.25f) / 0.75f).coerceIn(0f, 1f))
            scene.scaleX = lerp(1.12f, 1f, settle); scene.scaleY = scene.scaleX
        }
    }

    private fun fadeOut() {
        if (finished) return
        overlay.animate().alpha(0f).setDuration(500).withEndAction { finish() }.start()
    }

    /** A key was pressed: go straight to the closing zoom. */
    fun skip() {
        if (finished || zoom.running) return
        if (player != null) revealFromVideo() else if (!revealing) fadeOut()
    }

    fun finish() {
        if (finished) return
        finished = true
        BootLog.i("intro end begun=$begun")
        main.removeCallbacks(watchdog)
        main.removeCallbacks(settleRunnable)
        main.removeCallbacks(giveUpRunnable)
        main.removeCallbacks(renderWatch)
        displayManager?.let { runCatching { it.unregisterDisplayListener(displayListener) } }
        displayManager = null
        zoom.cancel()
        if (Build.VERSION.SDK_INT < 31) runCatching { player?.setSurface(null) }
        player?.let { runCatching { it.release() } }
        player = null
        playerSurface?.release()
        playerSurface = null
        scene.scaleX = 1f; scene.scaleY = 1f
        overlay.animate().cancel()
        host.removeView(overlay)
        onDone()
    }

    /** Black with a logo-shaped hole: everything outside the logo hides the home screen. */
    private inner class Reveal(val logo: Logo) : View(ctx) {
        var scale = 1f
        var progress = 0f
        private val cut = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        private val ink = Paint(Paint.FILTER_BITMAP_FLAG)

        override fun onDraw(c: Canvas) {
            val layer = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            // The black thins out at the very end, so no rim of it is left at the screen's corners.
            c.drawColor(Color.argb((255 * (1f - smooth(0.78f, 1f, progress))).toInt(), 0, 0, 0))
            c.save()
            c.scale(scale, scale, logo.pivotX, logo.pivotY)
            c.drawBitmap(logo.mask, 0f, 0f, cut)
            c.restore()
            c.restoreToCount(layer)
            // The logo's own colours fade as it passes the viewer, leaving the window it opened.
            ink.alpha = (255 * (1f - smooth(0.05f, 0.5f, progress))).toInt()
            if (ink.alpha > 0) {
                c.save()
                c.scale(scale, scale, logo.pivotX, logo.pivotY)
                c.drawBitmap(logo.color, 0f, 0f, ink)
                c.restore()
            }
        }
    }

    /** A logo cut out of a frame: its shape, its colours, and where to zoom into it. */
    class Logo(val mask: Bitmap, val color: Bitmap, val pivotX: Float, val pivotY: Float, val endScale: Float) {
        companion object {
            /** Whatever is bright on the black of [frame]; null if there is nothing. */
            fun from(frame: Bitmap): Logo? {
                val w = frame.width
                val h = frame.height
                val px = IntArray(w * h)
                frame.getPixels(px, 0, w, 0, 0, w, h)
                val alpha = ByteArray(w * h)
                var any = false
                for (i in px.indices) {
                    val c = px[i]
                    val r = (c shr 16) and 255
                    val g = (c shr 8) and 255
                    val b = c and 255
                    val m = max(r, max(g, b))
                    // Near-black is background; the logo's glow fades out over the next ~90 levels.
                    val a = ((m - 14) * 255 / 90).coerceIn(0, 255)
                    alpha[i] = a.toByte()
                    if (a == 0) { px[i] = 0; continue }
                    any = true
                    // The frame is the logo laid over black; take the black back out of its colour.
                    px[i] = (a shl 24) or ((min(255, r * 255 / a)) shl 16) or ((min(255, g * 255 / a)) shl 8) or min(255, b * 255 / a)
                }
                if (!any) return null
                val color = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                return of(alphaBitmap(alpha, w, h), color, alpha, w, h)
            }

            fun wordmark(ctx: Context, w: Int, h: Int): Logo {
                val art = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Fonts.inter(ctx, 700)
                    textSize = h * 0.16f
                    textAlign = Paint.Align.CENTER
                    color = Color.WHITE
                    letterSpacing = -0.02f
                }
                Canvas(art).drawText("Tigris", w / 2f, h / 2f - (p.descent() + p.ascent()) / 2, p)
                val px = IntArray(w * h)
                art.getPixels(px, 0, w, 0, 0, w, h)
                val alpha = ByteArray(w * h) { (px[it] ushr 24).toByte() }
                return of(alphaBitmap(alpha, w, h), art, alpha, w, h)
            }

            private fun alphaBitmap(alpha: ByteArray, w: Int, h: Int): Bitmap {
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
                val stride = b.rowBytes
                val buf = if (stride == w) alpha else ByteArray(stride * h).also { out ->
                    for (y in 0 until h) System.arraycopy(alpha, y * w, out, y * stride, w)
                }
                b.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(buf))
                return b
            }

            /**
             * Zooms into the logo's thickest part (the body of the apple, not the gap between it
             * and the letters), far enough that the hole there covers the whole screen.
             */
            private fun of(mask: Bitmap, color: Bitmap, alpha: ByteArray, w: Int, h: Int): Logo {
                val k = 6
                val gw = w / k
                val gh = h / k
                val inf = 1 shl 20
                val d = IntArray(gw * gh) { i ->
                    val x = (i % gw) * k + k / 2
                    val y = (i / gw) * k + k / 2
                    if ((alpha[y * w + x].toInt() and 255) > 200) inf else 0
                }
                // Two-pass chamfer distance to the nearest point outside the logo.
                for (y in 0 until gh) for (x in 0 until gw) {
                    val i = y * gw + x
                    if (d[i] == 0) continue
                    var v = d[i]
                    if (x > 0) v = min(v, d[i - 1] + 3)
                    if (y > 0) v = min(v, d[i - gw] + 3)
                    if (x > 0 && y > 0) v = min(v, d[i - gw - 1] + 4)
                    if (x < gw - 1 && y > 0) v = min(v, d[i - gw + 1] + 4)
                    if (x == 0 || y == 0 || x == gw - 1 || y == gh - 1) v = min(v, 3)
                    d[i] = v
                }
                var best = 0
                var bi = 0
                for (y in gh - 1 downTo 0) for (x in gw - 1 downTo 0) {
                    val i = y * gw + x
                    if (d[i] == 0) continue
                    var v = d[i]
                    if (x < gw - 1) v = min(v, d[i + 1] + 3)
                    if (y < gh - 1) v = min(v, d[i + gw] + 3)
                    if (x < gw - 1 && y < gh - 1) v = min(v, d[i + gw + 1] + 4)
                    if (x > 0 && y < gh - 1) v = min(v, d[i + gw - 1] + 4)
                    d[i] = v
                    if (v > best) { best = v; bi = i }
                }
                val px = ((bi % gw) * k + k / 2).toFloat()
                val py = ((bi / gw) * k + k / 2).toFloat()
                val radius = max(best / 3f * k, 4f)
                val far = maxOf(hypot(px, py), hypot(w - px, py), hypot(px, h - py), hypot(w - px, h - py))
                return Logo(mask, color, px, py, (far / radius * 1.25f).coerceIn(4f, 120f))
            }
        }
    }

    companion object {
        const val EXTRA_PREVIEW = "com.leslie.tvhome.INTRO_PREVIEW"
        const val FILE = "intro.mp4"
        private const val RENDER_WAIT_MS = 1_500L
        /** Keep the cover while a slow panel is still off, then give up so Home cannot stay black. */
        private const val MAX_WAIT_MS = 45_000L

        /** The uploaded video (app storage), else one pushed over adb (Android/data/…/files). */
        fun videoFile(ctx: Context): File? =
            listOfNotNull(File(ctx.filesDir, FILE), ctx.getExternalFilesDir(null)?.let { File(it, FILE) })
                .firstOrNull { it.isFile && it.length() > 0 }

        /** Identifies the current boot, so the intro plays once per boot. */
        fun bootStamp(ctx: Context): Long =
            runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim().hashCode().toLong() }.getOrNull()
                ?: runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT).toLong() }.getOrDefault(0L)

        /**
         * The first start of the home screen since the TV booted (not a restart of the app, an
         * update, or a later crash), and the intro is on.
         */
        fun dueAfterBoot(ctx: Context): Boolean {
            val prefs = Prefs(ctx)
            return prefs.intro != Prefs.INTRO_OFF && SystemClock.elapsedRealtime() < 10 * 60_000L &&
                prefs.introBoot != bootStamp(ctx)
        }

        private fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
    }
}
