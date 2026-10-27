package com.leslie.tvhome

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * The 壁纸 app: every background as a picture to pick with one press, and beside it the QR code
 * for the phone page ([PhoneServer]), which runs while this is open and for five minutes after so
 * photos still on their way arrive. Changes made on the phone show up here as they happen.
 */
class WallpaperActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val cards = LinkedHashMap<String, Card>()
    private lateinit var qr: ImageView
    private lateinit var address: TextView
    private var counts = ""

    private inner class Card(val key: String) : LinearLayout(this@WallpaperActivity) {
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.argb(40, 255, 255, 255), dp(12).toFloat())
            clipRounded(dp(12).toFloat())
        }
        private val frame = FrameLayout(context)
        private val ring = View(context)
        private val check = text(14f, Color.WHITE, bold = true).apply {
            text = "✓"
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(10, 132, 255), dp(11).toFloat())
        }
        private val empty = text(13f, Color.argb(170, 255, 255, 255)).apply { gravity = Gravity.CENTER }
        val label = text(14f, Color.argb(200, 255, 255, 255), weight = 500).apply { gravity = Gravity.CENTER }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            frame.clipChildren = false
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            frame.addView(empty, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), 0, dp(8), 0) })
            frame.addView(ring, FrameLayout.LayoutParams(-1, -1))
            frame.addView(check, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
            addView(frame, LayoutParams(dp(128), dp(72)))
            addView(label, LayoutParams(-1, -2).apply { topMargin = dp(7) })
            setOnFocusChangeListener { _, f ->
                ring.background = if (f) GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat(); setStroke(dp(3), Color.WHITE)
                } else null
                label.setTextColor(if (f) Color.WHITE else Color.argb(200, 255, 255, 255))
                animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f)
                    .translationZ(if (f) dp(10).toFloat() else 0f).setDuration(160).setInterpolator(Motion.ease).start()
            }
            setOnClickListener { pick(key) }
        }

        fun selected(on: Boolean) { check.visibility = if (on) VISIBLE else GONE }
        fun emptyText(s: String?) { empty.text = s.orEmpty(); empty.visibility = if (s == null) GONE else VISIBLE }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        val keys = listOf(VideoWallpaper.AERIAL) + Wallpapers.SOURCES + Backgrounds.THEMES.keys
        for (row in keys.chunked(4)) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            for (k in row) {
                val c = Card(k)
                cards[k] = c
                r.addView(c, LinearLayout.LayoutParams(dp(128), -2).apply { marginEnd = dp(16) })
            }
            grid.addView(r, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(14) })
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(text(30f, bold = true).apply { text = I18n.text(R.string.i18n_wallpaperactivity_001) })
            addView(text(15f, Color.argb(160, 255, 255, 255)).apply { text = I18n.text(R.string.i18n_wallpaperactivity_002) },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(22) })
            addView(grid)
        }
        qr = ImageView(this).apply {
            background = rounded(Color.WHITE, dp(14).toFloat())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        address = text(13f, Color.argb(130, 255, 255, 255))
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(20f, bold = true).apply { text = I18n.text(R.string.i18n_wallpaperactivity_003) })
            addView(text(15f, Color.argb(190, 255, 255, 255)).apply {
                text = I18n.text(R.string.i18n_wallpaperactivity_004)
                setLineSpacing(0f, 1.25f)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(18) })
            addView(qr, LinearLayout.LayoutParams(dp(220), dp(220)))
            addView(address, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(16, 16, 18))
            clipChildren = false
            // The focused first-column tile expands into the root's left padding.
            clipToPadding = false
            setPadding(dp(64), dp(44), dp(56), dp(32))
            addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            addView(right, LinearLayout.LayoutParams(dp(250), -2).apply { marginStart = dp(24) })
        })
        cards[prefs.background]?.requestFocus() ?: cards.values.first().requestFocus()
    }

    override fun onStart() {
        super.onStart()
        stopLater.removeCallbacks(stop)
        address.text = I18n.text(R.string.i18n_wallpaperactivity_005)
        io.execute {
            val url = PhoneServer.start(this)?.let { if (intent.getBooleanExtra("upload_avatar", false)) it + "#avatar" else it }
            val bmp = url?.let { PhoneServer.qr(it) }
            main.post {
                address.text = url ?: I18n.text(R.string.i18n_wallpaperactivity_006)
                qr.setImageBitmap(bmp)
            }
        }
        counts = ""
        poll.run()
    }

    override fun onStop() {
        main.removeCallbacks(poll)
        stopLater.postDelayed(stop, 5 * 60_000L)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** Follows the phone: a new choice moves the check mark, new photos redraw the previews. */
    private val poll = object : Runnable {
        override fun run() {
            cards.forEach { (k, c) -> c.selected(k == prefs.background) }
            io.execute {
                val now = Wallpapers.SOURCES.joinToString { s -> Wallpapers(this@WallpaperActivity, s).files().firstOrNull()?.name + Wallpapers(this@WallpaperActivity, s).files().size }
                if (now != counts) { counts = now; loadPreviews() }
            }
            main.postDelayed(this, 3000)
        }
    }

    /** Runs on [io]. */
    private fun loadPreviews() {
        for (s in Wallpapers.SOURCES) {
            val files = Wallpapers(this, s).files()
            val bmp = files.firstOrNull()?.let { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 8 }) }
            val label = Wallpapers.label(s) + if (s == Wallpapers.MINE && files.isNotEmpty()) " · ${files.size}" else ""
            val empty = when {
                files.isNotEmpty() -> null
                s == Wallpapers.MINE -> I18n.text(R.string.i18n_wallpaperactivity_007)
                s == Wallpapers.CUSTOM -> I18n.text(R.string.i18n_wallpaperactivity_008)
                else -> I18n.text(R.string.i18n_wallpaperactivity_009)
            }
            main.post { cards[s]?.apply { image.setImageBitmap(bmp); this.label.text = label; emptyText(empty) } }
        }
        val aerialPreview = Backgrounds.render("blue")
        main.post {
            cards[VideoWallpaper.AERIAL]?.apply {
                image.setImageBitmap(aerialPreview)
                label.text = I18n.text(R.string.live_wallpaper_aerial)
                emptyText(null)
            }
        }
        for ((k, v) in Backgrounds.THEMES) {
            val bmp: Bitmap = Backgrounds.render(k)
            main.post { cards[k]?.apply { image.setImageBitmap(bmp); label.text = v.first; emptyText(null) } }
        }
    }

    private fun pick(key: String) {
        if (key == Wallpapers.MINE || key == Wallpapers.CUSTOM) {
            if (Wallpapers(this, key).files().isEmpty()) {
                Toast.makeText(this, if (key == Wallpapers.MINE) I18n.text(R.string.i18n_wallpaperactivity_010) else I18n.text(R.string.i18n_wallpaperactivity_011), Toast.LENGTH_LONG).show()
                return
            }
        }
        prefs.background = key
        cards.forEach { (k, c) -> c.selected(k == key) }
        io.execute { Wallpapers.trimExcept(this, key) }
        if (key in Wallpapers.SOURCES && Wallpapers(this, key).files().isEmpty()) io.execute {
            Wallpapers(this, key).refresh(force = true)
            counts = ""
        }
    }

    companion object {
        private val stopLater = Handler(Looper.getMainLooper())
        private val stop = Runnable { Thread { PhoneServer.stop() }.start() }
    }
}
