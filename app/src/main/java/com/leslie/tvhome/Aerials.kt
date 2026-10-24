package com.leslie.tvhome

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Apple TV's aerial films. Only the catalogue ships (assets/aerials.json, from Aerial's repo,
 * built from Apple's public resources-16.tar: addresses and place names); the films stream from
 * Apple's servers while the screensaver runs. 4K HEVC is attempted only on compatible TVs;
 * a missing 4K asset or decoding failure falls back to 2K HEVC then 2K AVC.
 */
class Aerial(val id: String, val url: String, private val captions: List<Pair<Int, String>>,
             val hevc: String? = null) {
    /** tvOS changes the place name as the camera moves on. */
    fun captionAt(seconds: Int): String = captions.lastOrNull { it.first <= seconds }?.second ?: ""
}

object Aerials {
    @Volatile private var cache: List<Aerial>? = null

    fun all(ctx: Context): List<Aerial> = cache ?: synchronized(this) {
        cache ?: load(ctx).also { cache = it }
    }

    private fun load(ctx: Context): List<Aerial> {
        val arr = JSONObject(ctx.assets.open("aerials.json").bufferedReader().use { it.readText() }).getJSONArray("videos")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val caps = o.getJSONArray("captions")
            Aerial(o.getString("id"), o.getString("url"), (0 until caps.length()).map { j ->
                caps.getJSONArray(j).let { it.getInt(0) to it.getString(1) }
            }, o.optString("hevc").takeIf { it.startsWith("https://") })
        }
    }
}

/** What the photo frame activity and the system screensaver show. */
interface Saver {
    val view: View
    fun start()
    fun stop()
    /** Left / right on the remote; true if handled. */
    fun skip(dir: Int): Boolean = false
}

/**
 * The aerial films full screen, with the time and the place in the corner. After three films in a
 * row fail to load (no network, Apple unreachable), [onFail] hands over to the photos.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class AerialSaver(private val ctx: Context, private val onFail: () -> Unit) : Saver {
    private val main = Handler(Looper.getMainLooper())
    private val surface = SurfaceView(ctx)
    private val clock = ctx.text(64f, weight = 250).apply { shadow() }
    private val place = ctx.text(18f, weight = 500).apply { shadow(); maxLines = 2 }
    private val corner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(clock, LinearLayout.LayoutParams(-1, -2))
        addView(place, LinearLayout.LayoutParams(ctx.dp(560), -2).apply { topMargin = ctx.dp(6) })
    }
    override val view = FrameLayout(ctx).apply {
        setBackgroundColor(Color.BLACK)
        addView(surface, FrameLayout.LayoutParams(-1, -1))
        addView(corner, FrameLayout.LayoutParams(ctx.dp(600), -2, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = ctx.dp(56); bottomMargin = ctx.dp(44)
        })
    }
    private var player: ExoPlayer? = null
    private var films: List<Aerial> = emptyList()
    private var sources: Map<String, List<String>> = emptyMap()
    private val selectedVariant = mutableMapOf<String, Int>()
    private var failures = 0

    private fun android.widget.TextView.shadow() =
        setShadowLayer(dp(10).toFloat(), 0f, dp(1).toFloat(), Color.argb(170, 0, 0, 0))

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
            val p = player
            val film = p?.currentMediaItem?.mediaId?.let { id -> films.firstOrNull { it.id == id } }
            val text = film?.captionAt((p.currentPosition / 1000).toInt()).orEmpty()
            if (text != place.text) place.animate().alpha(0f).setDuration(400).withEndAction {
                place.text = text
                place.animate().alpha(1f).setDuration(600).start()
            }.start()
            main.postDelayed(this, 1000)
        }
    }

    override fun start() {
        if (player != null) return
        films = Aerials.all(ctx).shuffled()
        val hevc = MediaQuality.hevcDecoderPresent()
        val uhd = hevc && MediaQuality.uhdAerial(ctx)
        selectedVariant.clear()
        sources = films.associate { film ->
            film.id to MediaQuality.aerialCandidates(film.url, film.hevc, uhd, hevc)
        }
        val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(5_000).setReadTimeoutMs(8_000)
        // ExoPlayer's default video buffer is sized for phones; 24 MB is 10–13 s of film.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 30_000, 1_000, 2_000)
            .setTargetBufferBytes(24 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val p = ExoPlayer.Builder(ctx)
            .setLoadControl(load)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1)))
            .build()
        p.volume = 0f
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        p.setVideoSurfaceView(surface)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val idx = p.currentMediaItemIndex
                val film = films.getOrNull(idx)
                val alternatives = film?.let { sources[it.id] }.orEmpty()
                val attempt = if (film == null) 0 else selectedVariant[film.id] ?: 0
                if (film != null && attempt + 1 < alternatives.size) {
                    val next = attempt + 1
                    selectedVariant[film.id] = next
                    Log.w("AerialSaver", "video variant $attempt failed; trying $next for ${film.id}", error)
                    p.replaceMediaItem(idx, MediaItem.Builder().setUri(alternatives[next])
                        .setMediaId(film.id).build())
                    p.prepare()
                    p.play()
                    return
                }
                Log.w("AerialSaver", "all variants failed; skipping film", error)
                if (++failures >= 3) { onFail(); return }
                p.seekToNextMediaItem()
                p.prepare()
                p.play()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) failures = 0
            }
        })
        p.setMediaItems(films.map {
            MediaItem.Builder().setUri(sources.getValue(it.id).first()).setMediaId(it.id).build()
        })
        p.prepare()
        p.play()
        player = p
        tick.run()
    }

    override fun stop() {
        main.removeCallbacks(tick)
        player?.release()
        player = null
        sources = emptyMap()
        selectedVariant.clear()
    }

    override fun skip(dir: Int): Boolean {
        val p = player ?: return false
        if (dir > 0) p.seekToNextMediaItem() else p.seekToPreviousMediaItem()
        return true
    }
}

/** Shows the saver chosen in settings, falling back to the photos when the films can't play. */
class SaverHost(ctx: Context) : FrameLayout(ctx) {
    private var saver: Saver = choose()
    private var started = false

    init {
        setBackgroundColor(Color.BLACK)
        addView(saver.view, LayoutParams(-1, -1))
    }

    private fun choose(): Saver =
        if (Prefs(context).saverMode == Prefs.SAVER_AERIAL) AerialSaver(context) { post { usePhotos() } } else photos()

    private fun photos() = PhotoFrameView(context).let { v ->
        object : Saver {
            override val view = v
            override fun start() = v.start()
            override fun stop() = v.stop()
        }
    }

    private fun usePhotos() {
        if (saver.view is PhotoFrameView) return
        saver.stop()
        removeView(saver.view)
        saver = photos()
        addView(saver.view, LayoutParams(-1, -1))
        if (started) saver.start()
    }

    fun start() { started = true; saver.start() }
    fun stop() { started = false; saver.stop() }

    fun onKey(e: KeyEvent): Boolean {
        val dir = when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> return false
        }
        if (e.action == KeyEvent.ACTION_DOWN) return saver.skip(dir)
        return saver.view !is PhotoFrameView
    }
}
