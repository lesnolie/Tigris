package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import java.io.File

/**
 * Shared tvOS aerial/local video wallpaper. Video decoding stays in the hardware
 * layer. Glass receives bounded, *asynchronous* PixelCopy snapshots; unlike
 * TextureView.getBitmap(), the GPU readback never blocks the remote-control UI.
 *
 * The extra source Surface is only borrowed for a single PixelCopy and released.
 * PixelCopy can fail on vendor codecs: video keeps playing and the static glass
 * remains in place until a good frame arrives.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoWallpaper(
    private val ctx: Context,
    host: FrameLayout,
    private val canSample: () -> Boolean,
    private val onFrame: (Bitmap?) -> Unit,
) {
    private val texture = TextureView(ctx).apply { alpha = 0f; isOpaque = false }
    var frame: Bitmap? = null
        private set

    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val layer = object : FrameLayout(ctx) {
        override fun dispatchDraw(canvas: Canvas) {
            if (canvas.isHardwareAccelerated) super.dispatchDraw(canvas)
            else if (texture.alpha > 0f) frame?.let {
                canvas.drawBitmap(it, null, Rect(0, 0, width, height), framePaint)
            }
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private val copyThread = HandlerThread("TigrisVideoGlassCopy").apply { start() }
    private val copyHandler = Handler(copyThread.looper)
    private var player: ExoPlayer? = null
    private var key: String? = null
    private var films: List<List<String>> = emptyList()
    private var filmIndex = 0
    private var variant = 0
    private var failures = 0
    private var aspect = 16f / 9f
    private var captureGeneration = 0L
    private var pendingCapture = false
    private var failedCopies = 0
    private var nextCaptureAt = 0L
    private val lowEnd by lazy { Device.isLowEnd(ctx) }
    private val timeout = Runnable { nextAfterError() }

    private val sample = object : Runnable {
        override fun run() {
            if (player == null) return
            val now = SystemClock.uptimeMillis()
            if (!pendingCapture && now >= nextCaptureAt && texture.alpha > 0f &&
                texture.isAvailable && canSample() && texture.width > 0 && texture.height > 0
            ) {
                capture(now)
            }
            main.postDelayed(this, if (lowEnd) 350L else 200L)
        }
    }

    init {
        layer.addView(texture, FrameLayout.LayoutParams(-1, -1))
        host.addView(layer, FrameLayout.LayoutParams(-1, -1))
        texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> crop() }
    }

    /** Called from the UI thread; only enqueues an asynchronous PixelCopy. */
    private fun capture(now: Long) {
        val surfaceTexture = texture.surfaceTexture ?: return
        val width = minOf(if (lowEnd) 320 else 480, texture.width)
        val height = (width.toLong() * texture.height / texture.width).toInt().coerceAtLeast(1)
        val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            .getOrNull() ?: return
        val generation = captureGeneration
        pendingCapture = true
        nextCaptureAt = now + if (lowEnd) 1_000L else 500L
        val surface = Surface(surfaceTexture)
        try {
            PixelCopy.request(surface, bitmap, { result ->
                surface.release()
                main.post {
                    if (generation != captureGeneration || player == null) {
                        bitmap.recycle()
                        return@post
                    }
                    pendingCapture = false
                    if (result == PixelCopy.SUCCESS) {
                        failedCopies = 0
                        frame = bitmap
                        onFrame(bitmap)
                    } else {
                        bitmap.recycle()
                        failedCopies++
                        // Some vendor codecs do not support copying their video layer.
                        // Back off rather than spin and interfere with remote input.
                        nextCaptureAt = SystemClock.uptimeMillis() +
                            if (failedCopies >= 3) 10_000L else 1_500L
                    }
                }
            }, copyHandler)
        } catch (_: Exception) {
            surface.release()
            bitmap.recycle()
            pendingCapture = false
            failedCopies++
            nextCaptureAt = now + if (failedCopies >= 3) 10_000L else 1_500L
        }
    }

    fun start(source: String) {
        val file = File(ctx.filesDir, "wallpaper-video.mp4").takeIf { it.isFile && it.length() > 0 }
        val nextKey = if (source == LOCAL) "$source:${file?.lastModified()}:${file?.length()}" else source
        if (key == nextKey) return
        stop()
        key = nextKey
        if (source == LOCAL && file == null) { fail(); return }
        films = if (source == LOCAL) listOf(listOf(android.net.Uri.fromFile(file!!).toString())) else {
            val hevc = MediaQuality.hevcDecoderPresent()
            val uhd = MediaQuality.uhdAerial(ctx)
            Aerials.all(ctx).shuffled().map { MediaQuality.aerialCandidates(it.url, it.hevc, uhd, hevc) }
        }
        if (films.isEmpty()) { fail(); return }
        val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(5000).setReadTimeoutMs(8000)
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1))
        val buffers = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 15000, 500, 1000)
            .setTargetBufferBytes(24 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false).build()
        val p = ExoPlayer.Builder(ctx).setLoadControl(buffers).setMediaSourceFactory(sources).build()
        player = p
        p.volume = 0f
        p.repeatMode = if (source == LOCAL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.setVideoTextureView(texture)
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (player !== p) return
                main.removeCallbacks(timeout)
                texture.alpha = 1f
                main.removeCallbacks(sample)
                sample.run()
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
                crop()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player === p) nextAfterError()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (player !== p) return
                if (state == Player.STATE_BUFFERING) armTimeout()
                if (state == Player.STATE_READY) main.removeCallbacks(timeout)
                if (state == Player.STATE_ENDED) {
                    failures = 0
                    filmIndex = (filmIndex + 1) % films.size
                    variant = 0
                    playCurrent()
                }
            }
        })
        playCurrent()
    }

    private fun crop() {
        if (texture.width <= 0 || texture.height <= 0) return
        val viewAspect = texture.width.toFloat() / texture.height
        val sx = maxOf(1f, aspect / viewAspect)
        val sy = maxOf(1f, viewAspect / aspect)
        texture.setTransform(Matrix().apply { setScale(sx, sy, texture.width / 2f, texture.height / 2f) })
    }

    private fun armTimeout() {
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 20_000L)
    }

    private fun playCurrent() {
        val p = player ?: return
        texture.alpha = 0f
        clearFrame()
        p.setMediaItem(MediaItem.fromUri(films[filmIndex][variant]))
        armTimeout()
        p.prepare()
        p.play()
    }

    private fun nextAfterError() {
        if (player == null) return
        if (++variant < films[filmIndex].size) { playCurrent(); return }
        if (++failures >= 3 || films.size == 1) { fail(); return }
        filmIndex = (filmIndex + 1) % films.size
        variant = 0
        playCurrent()
    }

    private fun fail() {
        stop()
        Toast.makeText(ctx, I18n.text(R.string.live_wallpaper_failed), Toast.LENGTH_LONG).show()
    }

    private fun clearFrame() {
        captureGeneration++
        pendingCapture = false
        frame = null
        onFrame(null)
    }

    fun stop() {
        main.removeCallbacks(timeout)
        main.removeCallbacks(sample)
        player?.release()
        player = null
        texture.alpha = 0f
        clearFrame()
        key = null
        filmIndex = 0
        variant = 0
        failures = 0
        films = emptyList()
    }

    fun destroy() {
        stop()
        copyThread.quitSafely()
    }

    companion object {
        const val LOCAL = "video_local"
        const val AERIAL = "video_aerial"
        val SOURCES = listOf(LOCAL, AERIAL)
    }
}
