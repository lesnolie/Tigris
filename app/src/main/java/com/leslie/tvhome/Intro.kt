package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
    private val watchdog = Runnable { finish() }

    /** Covers the screen at once; the animation itself begins after [delay] ms of black. */
    fun start(delay: Long = 0) {
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        main.postDelayed(watchdog, 15_000 + delay)
        if (delay > 0) main.postDelayed({ if (!finished) begin() }, delay) else begin()
    }

    private fun begin() {
        if (Prefs(ctx).introSource == Prefs.INTRO_WORDMARK) {
            showWordmark()
            return
        }
        val video = videoFile(ctx) ?: OptionalAssets.file(ctx, OptionalAssets.intro)
        if (video == null) { showWordmark(); return }
        val tv = TextureView(ctx)
        texture = tv
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = play(video, Surface(st))
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                val mp = player ?: return
                val left = runCatching { mp.duration - mp.currentPosition }.getOrDefault(Int.MAX_VALUE)
                if (left in 0..600) cutLogo()
            }
        }
        overlay.addView(tv, FrameLayout.LayoutParams(-1, -1))
    }

    private fun play(file: File, surface: Surface) {
        if (finished) return
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(file.path)
            mp.setSurface(surface)
            if (!sound) mp.setVolume(0f, 0f)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { main.post { revealFromVideo() } }
            mp.setOnErrorListener { _, _, _ -> main.post { revealFromVideo() }; true }
            mp.prepareAsync()
        } catch (e: Exception) {
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
        if (!cut) cutLogo()
        // Releasing a player can block for a while; the last frame stays on the TextureView meanwhile.
        player?.let { mp -> Thread { runCatching { mp.release() } }.start() }
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
        main.removeCallbacks(watchdog)
        zoom.cancel()
        player?.let { runCatching { it.release() } }
        player = null
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
