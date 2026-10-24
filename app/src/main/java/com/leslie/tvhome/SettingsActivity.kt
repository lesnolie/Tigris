package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.leslie.tvhome.tv.TvControls

/**
 * tvOS-style settings: the title centred on top, a large icon on the left with a
 * description of the focused row under it, and grouped rows on the right.
 * Sub-pages (keep list, hidden apps) reuse the same list.
 */
class SettingsActivity : Activity() {
    companion object {
        const val SONY_DREAM = "com.sony.dtv.braviashow/com.sony.dtv.braviashow.BraviaDayDreamService"
        const val AVATAR_FILE = "profile_avatar.png"
        fun avatarFile(ctx: android.content.Context) = java.io.File(ctx.filesDir, AVATAR_FILE).takeIf { it.isFile }
        const val ICON_FILE = "settings_icon.png"
        const val BACKDROP_FILE = "settings_bg.jpg"

        /**
         * A picture of the user's own behind the whole settings page (Apple TV's, a TV on a blue haze,
         * say), with the TV where the big icon would be; uploaded from the phone page or pushed over adb.
         */
        fun backdropFile(ctx: android.content.Context): java.io.File? =
            listOfNotNull(java.io.File(ctx.filesDir, BACKDROP_FILE), ctx.getExternalFilesDir(null)?.let { java.io.File(it, BACKDROP_FILE) })
                .firstOrNull { it.isFile && it.length() > 0 }

        /**
         * A picture of the user's own for the settings icon (Apple TV's, say), uploaded from the phone
         * page or pushed over adb to Android/data/com.leslie.tvhome/files; null for the built-in gear.
         * Like the boot video, it is not part of the app.
         */
        fun customIcon(ctx: android.content.Context): java.io.File? = if (Prefs(ctx).ownIcon) iconFile(ctx) else null

        /** The uploaded picture whether or not it is in use ([Prefs.ownIcon]). */
        fun iconFile(ctx: android.content.Context): java.io.File? =
            listOfNotNull(java.io.File(ctx.filesDir, ICON_FILE), ctx.getExternalFilesDir(null)?.let { java.io.File(it, ICON_FILE) })
                .firstOrNull { it.isFile && it.length() > 0 }

        /** The icon decoded at no more than about [width] px wide: a 1280 px picture shown at 600 needn't take 6 MB. */
        fun decodeIcon(f: java.io.File, width: Int): android.graphics.Bitmap? {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= width) sample *= 2
            return android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }

        private val IDLE_TEXT = Color.argb(235, 255, 255, 255)
        private val IDLE_VALUE = Color.argb(130, 255, 255, 255)
        private val FOCUS_VALUE = Color.argb(140, 0, 0, 0)
    }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var list: LinearLayout
    private lateinit var heading: TextView
    private lateinit var about: TextView
    private lateinit var icon: ImageView
    private lateinit var backdrop: ImageView
    private lateinit var scroll: ScrollView
    private var bundledBackdrop = false
    private var page = Page.MAIN
    /** The main-page row that opened the current sub-page, focused again on the way back. */
    private var opener = 0

    private enum class Page { MAIN, KEEP, HIDDEN, REMOVED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepo(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(28, 28, 30)) }
        backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.MATRIX
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> placeBackdrop(v as ImageView) }
        }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        heading = text(28f, weight = 600).apply { gravity = Gravity.CENTER }
        root.addView(heading, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(40) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(84), dp(32), 0)
        }
        icon = ImageView(this).apply {
            showIcon(this)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipRounded(dp(18).toFloat())
            clipToOutline = true
        }
        left.addView(icon, LinearLayout.LayoutParams(-1, dp(207)))
        about = text(15f, Color.argb(150, 255, 255, 255)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(0f, 1.3f)
        }
        left.addView(about, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })
        showBackdrop()
        body.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        // Not clipped, so a focused row can grow past the list's edges.
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            setPadding(dp(12), dp(16), dp(12), dp(60))
            addView(list)
        }
        body.addView(scroll, LinearLayout.LayoutParams(0, -1, 1.05f).apply { marginEnd = dp(52) })
        root.addView(body, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(84) })
        setContentView(root)
        show(Page.MAIN)
    }

    private fun show(p: Page, focusIndex: Int = 0) {
        page = p
        list.removeAllViews()
        when (p) {
            Page.MAIN -> main()
            Page.KEEP -> appPicker(I18n.text(R.string.i18n_settingsactivity_001), prefs.keep) { pkg, on ->
                prefs.keep = if (on) prefs.keep + pkg else prefs.keep - pkg
            }
            Page.HIDDEN -> appPicker(I18n.text(R.string.i18n_settingsactivity_002), prefs.hidden, includeHidden = true) { pkg, on ->
                prefs.hidden = if (on) prefs.hidden + pkg else prefs.hidden - pkg
            }
            Page.REMOVED -> removedApps()
        }
        list.post {
            val target = list.getChildAt(focusIndex.coerceIn(0, maxOf(list.childCount - 1, 0)))
            (target?.takeIf { it.isFocusable } ?: (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.isFocusable })
                ?.requestFocus()
        }
    }

    /** The uploaded background, whose TV stands in for the big icon; or the plain dark page. RGB_565: half the memory. */
    private fun showBackdrop() {
        val file = if (prefs.settingsBg) backdropFile(this) else null
        bundledBackdrop = prefs.settingsBg && file == null
        val bmp = file?.let {
            android.graphics.BitmapFactory.decodeFile(it.path, android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            })
        } ?: if (bundledBackdrop) android.graphics.BitmapFactory.decodeResource(resources, R.drawable.default_settings_bg,
            android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) else null
        backdrop.setImageBitmap(bmp)
        placeBackdrop(backdrop)
        icon.visibility = if (bmp != null) android.view.View.INVISIBLE else android.view.View.VISIBLE
        // Below the TV in the picture, which reaches a little lower than the icon would.
        (about.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (bmp != null) 62 else 26)
        about.requestLayout()
    }

    /**
     * Fills the screen, 8% larger than needed and against the right edge: a subject in the left
     * third of a 16:9 picture (where the icon would be) then sits over the description column.
     */
    private fun placeBackdrop(v: ImageView) {
        val d = v.drawable ?: return
        if (v.width == 0 || d.intrinsicWidth <= 0) return
        // The bundled artwork sits further left, clear of the focused right-hand buttons.
        val s = maxOf(v.width / d.intrinsicWidth.toFloat(), v.height / d.intrinsicHeight.toFloat()) * if (bundledBackdrop) 1.12f else 1.08f
        v.imageMatrix = android.graphics.Matrix().apply {
            setScale(s, s)
            postTranslate(v.width - d.intrinsicWidth * s, (v.height - d.intrinsicHeight * s) / 2)
        }
    }

    private fun showIcon(v: ImageView) {
        val own = customIcon(this)?.let { decodeIcon(it, dp(380)) }
        if (own != null) v.setImageBitmap(own) else v.setImageResource(R.drawable.banner_settings)
    }

    private fun main() {
        heading.text = I18n.text(R.string.i18n_settingsactivity_003)
        about.text = ""

        section(I18n.text(R.string.i18n_settingsactivity_004))
        row(I18n.text(R.string.i18n_settingsactivity_005), I18n.text(R.string.i18n_settingsactivity_006), { I18n.text(R.string.i18n_settingsactivity_007) }) {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        section(I18n.text(R.string.i18n_settingsactivity_008))
        row(I18n.text(R.string.i18n_settingsactivity_009), I18n.text(R.string.i18n_settingsactivity_010),
            { if (prefs.clock24) I18n.text(R.string.i18n_settingsactivity_011) else I18n.text(R.string.i18n_settingsactivity_012) }) { prefs.clock24 = !prefs.clock24 }
        row(I18n.text(R.string.i18n_settingsactivity_013), I18n.text(R.string.i18n_settingsactivity_014), {
            when (prefs.clockStyle) { Prefs.CLOCK_LARGE -> I18n.text(R.string.i18n_settingsactivity_015); Prefs.CLOCK_OFF -> I18n.text(R.string.i18n_settingsactivity_016); else -> I18n.text(R.string.i18n_settingsactivity_017) }
        }) {
            val opts = listOf(Prefs.CLOCK_CORNER, Prefs.CLOCK_LARGE, Prefs.CLOCK_OFF)
            prefs.clockStyle = opts[(opts.indexOf(prefs.clockStyle) + 1) % opts.size]
        }
        if (backdropFile(this) != null) row(I18n.text(R.string.i18n_settingsactivity_018), I18n.text(R.string.i18n_settingsactivity_019), {
            if (prefs.settingsBg) I18n.text(R.string.i18n_settingsactivity_020) else I18n.text(R.string.i18n_settingsactivity_021)
        }) {
            prefs.settingsBg = !prefs.settingsBg
            showBackdrop()
        }
        if (iconFile(this) != null) row(I18n.text(R.string.i18n_settingsactivity_022), I18n.text(R.string.i18n_settingsactivity_023), {
            if (prefs.ownIcon) I18n.text(R.string.i18n_settingsactivity_024) else I18n.text(R.string.i18n_settingsactivity_025)
        }) {
            prefs.ownIcon = !prefs.ownIcon
            showIcon(icon)
        }
        row(I18n.text(R.string.i18n_settingsactivity_026), I18n.text(R.string.i18n_settingsactivity_027), { I18n.text(R.string.i18n_settingsactivity_028) }) {
            startActivity(Intent(this, WallpaperActivity::class.java).putExtra("upload_avatar", true))
        }
        row(I18n.text(R.string.i18n_settingsactivity_029), I18n.text(R.string.i18n_settingsactivity_030), { onOff(prefs.showAvatar) }) { prefs.showAvatar = !prefs.showAvatar }
        row(I18n.text(R.string.i18n_settingsactivity_031), I18n.text(R.string.i18n_settingsactivity_032) +
            I18n.text(R.string.i18n_settingsactivity_033), {
            auto(prefs.focusStyle, prefs.focusTvos(this), "tvOS", I18n.text(R.string.i18n_settingsactivity_034))
        }) {
            prefs.focusStyle = next(prefs.focusStyle, Prefs.FOCUS_TVOS, Prefs.FOCUS_SIMPLE)
        }
        row(I18n.text(R.string.i18n_settingsactivity_035), I18n.text(R.string.i18n_settingsactivity_036) +
            I18n.text(R.string.i18n_settingsactivity_037), {
            auto(prefs.ccStyle, prefs.ccGlass(this), I18n.text(R.string.i18n_settingsactivity_038), I18n.text(R.string.i18n_settingsactivity_039))
        }) {
            prefs.ccStyle = next(prefs.ccStyle, Prefs.CC_GLASS, Prefs.CC_PANEL)
        }

        // Weather is a self-contained optional card. No network traffic until opt-in.
        section(I18n.text(R.string.weather_settings))
        row(I18n.text(R.string.weather_settings), I18n.text(R.string.weather_settings_help),
            { onOff(prefs.weatherEnabled) }) { prefs.weatherEnabled = !prefs.weatherEnabled }
        row(I18n.text(R.string.weather_unit_settings), I18n.text(R.string.weather_unit_help),
            { if (prefs.weatherFahrenheit) "°F" else "°C" }) {
            prefs.weatherFahrenheit = !prefs.weatherFahrenheit
        }
        row(I18n.text(R.string.weather_city_settings), I18n.text(R.string.weather_city_help),
            { prefs.weatherCity.ifBlank { I18n.text(R.string.weather_city_unknown) } }, chevron = true) { value ->
            selectWeatherCity { city ->
                prefs.weatherCity = city.name
                prefs.weatherLatitude = city.latitude
                prefs.weatherLongitude = city.longitude
                value.text = city.name
            }
        }

        section(I18n.text(R.string.i18n_settingsactivity_040))
        val video = Intro.videoFile(this) != null
        row(I18n.text(R.string.i18n_settingsactivity_041), I18n.text(R.string.i18n_settingsactivity_042) +
            if (video) "" else I18n.text(R.string.i18n_settingsactivity_043), {
            when (prefs.intro) { Prefs.INTRO_BOOT -> I18n.text(R.string.i18n_settingsactivity_044); Prefs.INTRO_WAKE -> I18n.text(R.string.i18n_settingsactivity_045); else -> I18n.text(R.string.i18n_settingsactivity_046) }
        }) {
            val opts = listOf(Prefs.INTRO_OFF, Prefs.INTRO_BOOT, Prefs.INTRO_WAKE)
            prefs.intro = opts[(opts.indexOf(prefs.intro) + 1) % opts.size]
            // This boot counts as played: turning it on doesn't start it on the next return home.
            prefs.introBoot = Intro.bootStamp(this)
        }
        row(I18n.text(R.string.i18n_settingsactivity_047), I18n.text(R.string.i18n_settingsactivity_048) +
            if (video) "" else I18n.text(R.string.i18n_settingsactivity_049), {
            if (prefs.introSource == Prefs.INTRO_WORDMARK) I18n.text(R.string.i18n_settingsactivity_050) else if (video) I18n.text(R.string.i18n_settingsactivity_051) else I18n.text(R.string.i18n_settingsactivity_052)
        }) {
            prefs.introSource = if (prefs.introSource == Prefs.INTRO_WORDMARK) Prefs.INTRO_VIDEO else Prefs.INTRO_WORDMARK
        }
        row(I18n.text(R.string.i18n_settingsactivity_053), I18n.text(R.string.i18n_settingsactivity_054), {
            if (prefs.wakeDelay == 0) I18n.text(R.string.i18n_settingsactivity_055) else I18n.text(R.string.i18n_settingsactivity_056, prefs.wakeDelay / 1000.0).replace(".0 ", " ")
        }) {
            val opts = listOf(0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10_000)
            prefs.wakeDelay = opts.firstOrNull { it > prefs.wakeDelay } ?: 0
        }
        row(I18n.text(R.string.i18n_settingsactivity_057), I18n.text(R.string.i18n_settingsactivity_058), { onOff(prefs.introSound) }) { prefs.introSound = !prefs.introSound }
        row(I18n.text(R.string.i18n_settingsactivity_159), I18n.text(R.string.i18n_settingsactivity_160), { onOff(prefs.keySound) }) { prefs.keySound = !prefs.keySound }
        row(I18n.text(R.string.i18n_settingsactivity_059), I18n.text(R.string.i18n_settingsactivity_060), { "" }, chevron = true) {
            startActivity(Intent(this, HomeActivity::class.java)
                .putExtra(Intro.EXTRA_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        section(I18n.text(R.string.i18n_settingsactivity_061))
        row(I18n.text(R.string.i18n_settingsactivity_062), I18n.text(R.string.i18n_settingsactivity_063), {
            Backgrounds.THEMES[prefs.background]?.first ?: Wallpapers.label(Wallpapers.sourceFor(prefs.background)).let {
                if (prefs.background == Wallpapers.MINE) it else I18n.text(R.string.i18n_settingsactivity_064, it)
            }
        }, chevron = true) { startActivity(Intent(this, WallpaperActivity::class.java)) }
        row(I18n.text(R.string.media_quality_wall_title), I18n.text(R.string.media_quality_wall_desc), {
            when (prefs.wallQuality) {
                MediaQuality.UHD -> I18n.text(R.string.media_quality_4k)
                MediaQuality.COMPAT -> I18n.text(R.string.media_quality_1080)
                else -> I18n.text(R.string.media_quality_auto)
            }
        }) {
            prefs.wallQuality = when (prefs.wallQuality) {
                MediaQuality.AUTO -> MediaQuality.UHD
                MediaQuality.UHD -> MediaQuality.COMPAT
                else -> MediaQuality.AUTO
            }
            Thread {
                runCatching { Wallpapers(applicationContext, Wallpapers.sourceFor(prefs.background)).refresh(force = true) }
            }.start()
        }
        row(I18n.text(R.string.i18n_settingsactivity_065), I18n.text(R.string.i18n_settingsactivity_066), { minutesLabel(prefs.wallMinutes) }) {
            val opts = listOf(15, 30, 60, 180, 1440)
            prefs.wallMinutes = opts[(opts.indexOf(prefs.wallMinutes) + 1) % opts.size]
        }
        row(I18n.text(R.string.i18n_settingsactivity_067), I18n.text(R.string.i18n_settingsactivity_068),
            { I18n.text(R.string.i18n_settingsactivity_069, Wallpapers(this, Wallpapers.sourceFor(prefs.background)).files().size) }) { v ->
            v.text = I18n.text(R.string.i18n_settingsactivity_070)
            Thread {
                val w = Wallpapers(applicationContext, Wallpapers.sourceFor(prefs.background))
                w.refresh()
                w.advance(prefs.wallMinutes, force = true)
                runOnUiThread { v.text = I18n.text(R.string.i18n_settingsactivity_071, w.files().size) }
            }.start()
        }
        row(I18n.text(R.string.i18n_settingsactivity_072), I18n.text(R.string.i18n_settingsactivity_073),
            { onOff(prefs.frameScreensaver) }) { v ->
            val on = !prefs.frameScreensaver
            val current = Settings.Secure.getString(contentResolver, "screensaver_components").orEmpty()
            val mine = "$packageName/${PhotoDream::class.java.name}"
            // Whatever the TV had before goes back when the frame is turned off (Sony's own if unknown).
            if (on && !current.startsWith(packageName)) prefs.prevScreensaver = current
            val comp = if (on) mine else prefs.prevScreensaver.ifEmpty { SONY_DREAM }
            v.text = I18n.text(R.string.i18n_settingsactivity_074)
            Thread {
                val ok = putSecure("screensaver_components", comp) && putSecure("screensaver_enabled", "1")
                if (ok) prefs.frameScreensaver = on
                runOnUiThread { v.text = if (ok) onOff(prefs.frameScreensaver) else I18n.text(R.string.i18n_settingsactivity_075) }
            }.start()
        }
        row(I18n.text(R.string.i18n_settingsactivity_076), I18n.text(R.string.i18n_settingsactivity_077),
            { if (prefs.saverMode == Prefs.SAVER_AERIAL) I18n.text(R.string.i18n_settingsactivity_078) else I18n.text(R.string.i18n_settingsactivity_079) }) {
            prefs.saverMode = if (prefs.saverMode == Prefs.SAVER_AERIAL) Prefs.SAVER_PHOTOS else Prefs.SAVER_AERIAL
        }
        row(I18n.text(R.string.media_quality_aerial_title), I18n.text(R.string.media_quality_aerial_desc), {
            when (prefs.aerialQuality) {
                MediaQuality.UHD -> I18n.text(R.string.media_quality_4k)
                MediaQuality.COMPAT -> I18n.text(R.string.media_quality_compat)
                else -> I18n.text(R.string.media_quality_auto)
            }
        }) {
            prefs.aerialQuality = when (prefs.aerialQuality) {
                MediaQuality.AUTO -> MediaQuality.UHD
                MediaQuality.UHD -> MediaQuality.COMPAT
                else -> MediaQuality.AUTO
            }
        }
        row(I18n.text(R.string.i18n_settingsactivity_080), I18n.text(R.string.i18n_settingsactivity_081), { minutesLabel(idleMinutes()) }) { v ->
            val opts = listOf(1, 3, 5, 10, 15, 30)
            val next = opts[(opts.indexOf(idleMinutes()) + 1) % opts.size]
            v.text = I18n.text(R.string.i18n_settingsactivity_082)
            Thread {
                val ok = LocalAdb.shell(applicationContext, "settings put system screen_off_timeout ${next * 60_000}") != null
                runOnUiThread { v.text = if (ok) minutesLabel(next) else I18n.text(R.string.i18n_settingsactivity_083) }
            }.start()
        }
        row(I18n.text(R.string.i18n_settingsactivity_084), I18n.text(R.string.i18n_settingsactivity_085), { I18n.text(R.string.i18n_settingsactivity_086, prefs.frameSeconds) }) {
            val opts = listOf(15, 30, 60, 120)
            prefs.frameSeconds = opts[(opts.indexOf(prefs.frameSeconds) + 1) % opts.size]
        }

        section(I18n.text(R.string.i18n_settingsactivity_087))
        row(I18n.text(R.string.i18n_settingsactivity_088), I18n.text(R.string.i18n_settingsactivity_089), { onOff(prefs.cleanOnStandby) }) {
            prefs.cleanOnStandby = !prefs.cleanOnStandby
        }
        row(I18n.text(R.string.i18n_settingsactivity_090), I18n.text(R.string.i18n_settingsactivity_091), {
            if (prefs.cleanIntervalHours == 0) I18n.text(R.string.i18n_settingsactivity_092) else I18n.text(R.string.i18n_settingsactivity_093, prefs.cleanIntervalHours)
        }) {
            val opts = listOf(0, 2, 4, 8)
            prefs.cleanIntervalHours = opts[(opts.indexOf(prefs.cleanIntervalHours) + 1) % opts.size]
            KeeperService.start(this, KeeperService.ACTION_RESCHEDULE)
        }
        row(I18n.text(R.string.i18n_settingsactivity_094), I18n.text(R.string.i18n_settingsactivity_095), { I18n.text(R.string.i18n_settingsactivity_096, Cleaner.availMb(this)) }) { v ->
            v.text = I18n.text(R.string.i18n_settingsactivity_097)
            Thread {
                val r = Cleaner.clean(applicationContext)
                runOnUiThread { v.text = r.description() }
            }.start()
        }
        row(I18n.text(R.string.i18n_settingsactivity_098), I18n.text(R.string.i18n_settingsactivity_099), { I18n.text(R.string.i18n_settingsactivity_100, prefs.keep.size) }, chevron = true) {
            show(Page.KEEP)
        }
        row(I18n.text(R.string.i18n_settingsactivity_101), I18n.text(R.string.i18n_settingsactivity_102), {
            if (prefs.shutdownOnPowerKey) I18n.text(R.string.i18n_settingsactivity_103) else I18n.text(R.string.i18n_settingsactivity_104)
        }) {
            prefs.shutdownOnPowerKey = !prefs.shutdownOnPowerKey
            if (prefs.shutdownOnPowerKey && !prefs.adbAuthorized)
                Toast.makeText(this, I18n.text(R.string.i18n_settingsactivity_105), Toast.LENGTH_LONG).show()
        }

        section("App")
        row(I18n.text(R.string.i18n_settingsactivity_106), I18n.text(R.string.i18n_settingsactivity_107),
            { onOff(prefs.recentFirst) }) { prefs.recentFirst = !prefs.recentFirst }
        row(I18n.text(R.string.i18n_settingsactivity_108), I18n.text(R.string.i18n_settingsactivity_109), { I18n.text(R.string.i18n_settingsactivity_110, prefs.hidden.size) }, chevron = true) { show(Page.HIDDEN) }
        row(I18n.text(R.string.i18n_settingsactivity_111), I18n.text(R.string.i18n_settingsactivity_112), { I18n.text(R.string.i18n_settingsactivity_113, prefs.removed.size) }, chevron = true) {
            show(Page.REMOVED)
        }

        section(I18n.text(R.string.i18n_settingsactivity_114))
        row(I18n.text(R.string.i18n_settingsactivity_115), I18n.text(R.string.i18n_settingsactivity_116),
            { if (prefs.adbAuthorized) I18n.text(R.string.i18n_settingsactivity_117) else I18n.text(R.string.i18n_settingsactivity_118) }) { v ->
            v.text = I18n.text(R.string.i18n_settingsactivity_119)
            Thread {
                val ok = LocalAdb.shell(applicationContext, "echo ok", 60_000) != null
                runOnUiThread { v.text = if (ok) I18n.text(R.string.i18n_settingsactivity_120) else I18n.text(R.string.i18n_settingsactivity_121) }
            }.start()
        }
        // Resolver success is not proof that the physical HOME key reaches us.
        row(I18n.text(R.string.install_home_check_title), I18n.text(R.string.install_home_check_help), { "" }) {
            android.app.AlertDialog.Builder(this)
                .setTitle(I18n.text(R.string.install_home_check_title))
                .setMessage(I18n.text(R.string.install_home_check_status,
                    Guard.isDefaultHome(this).toString(), HomeGuardService.isRunning.toString(),
                    Guard.hasSecureSettings(this).toString()))
                .setPositiveButton(android.R.string.ok, null).show()
        }
        row(I18n.text(R.string.i18n_settingsactivity_122), I18n.text(R.string.i18n_settingsactivity_123),
            { if (!prefs.guard) I18n.text(R.string.i18n_settingsactivity_124) else if (HomeGuardService.isRunning) I18n.text(R.string.i18n_settingsactivity_125) else I18n.text(R.string.i18n_settingsactivity_126) }) { v ->
            val on = !prefs.guard
            v.text = I18n.text(R.string.i18n_settingsactivity_127)
            Thread {
                val ok = (Guard.hasSecureSettings(this) || Guard.grantViaLocalAdb(applicationContext)) && Guard.setEnabled(applicationContext, on)
                runOnUiThread {
                    v.text = if (!ok) I18n.text(R.string.i18n_settingsactivity_128) else if (on) I18n.text(R.string.i18n_settingsactivity_129) else I18n.text(R.string.i18n_settingsactivity_130)
                    if (ok && on) Toast.makeText(this, I18n.text(R.string.i18n_settingsactivity_131), Toast.LENGTH_LONG).show()
                }
            }.start()
        }
        if (Guard.isXiaomi() || prefs.keepAdb) row(I18n.text(R.string.i18n_settingsactivity_132), I18n.text(R.string.i18n_settingsactivity_133), { onOff(prefs.keepAdb) }) { v ->
            val on = !prefs.keepAdb
            v.text = I18n.text(R.string.i18n_settingsactivity_134)
            Thread {
                val ok = !on || Guard.hasSecureSettings(this) || Guard.grantViaLocalAdb(applicationContext)
                if (ok) { prefs.keepAdb = on; Guard.ensureAdb(applicationContext) }
                runOnUiThread { v.text = if (ok) onOff(prefs.keepAdb) else I18n.text(R.string.i18n_settingsactivity_135) }
            }.start()
        }
        row(I18n.text(R.string.i18n_settingsactivity_136), I18n.text(R.string.i18n_settingsactivity_137), { "" }, chevron = true) { runCatching { startActivity(TvControls(this).settingsIntent()) } }
        row(I18n.text(R.string.i18n_settingsactivity_138), I18n.text(R.string.i18n_settingsactivity_139),
            { I18n.text(R.string.i18n_settingsactivity_140) + packageManager.getPackageInfo(packageName, 0).versionName }) {}
    }

    private fun appPicker(title: String, selected: Set<String>, includeHidden: Boolean = false, toggle: (String, Boolean) -> Unit) {
        heading.text = title
        about.text = if (page == Page.KEEP) I18n.text(R.string.i18n_settingsactivity_141) else I18n.text(R.string.i18n_settingsactivity_142)
        val apps = if (includeHidden) repo.allApps().sortedBy { it.label } else repo.allApps().filter { !repo.isSystem(it.pkg) }.sortedBy { it.label }
        val sel = selected.toMutableSet()
        for (a in apps) {
            row(a.label, null, { if (a.pkg in sel) "✓" else "" }) { v ->
                val on = a.pkg !in sel
                if (on) sel += a.pkg else sel -= a.pkg
                toggle(a.pkg, on)
                v.text = if (on) "✓" else ""
            }
        }
    }

    /** Preinstalled apps deleted from the long-press menu; selecting one reinstalls it from the system partition. */
    private fun removedApps() {
        heading.text = I18n.text(R.string.i18n_settingsactivity_143)
        about.text = I18n.text(R.string.i18n_settingsactivity_144)
        if (prefs.removed.isEmpty()) row(I18n.text(R.string.i18n_settingsactivity_145), null, { "" }) {}
        for (pkg in prefs.removed.sorted()) {
            val label = try {
                packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(packageManager).toString()
            } catch (e: Exception) { pkg }
            row(label, null, { I18n.text(R.string.i18n_settingsactivity_146) }) { v ->
                v.text = I18n.text(R.string.i18n_settingsactivity_147)
                Thread {
                    val ok = LocalAdb.shell(applicationContext, "cmd package install-existing $pkg")?.contains("installed") == true
                    if (ok) prefs.removed = prefs.removed - pkg
                    runOnUiThread { v.text = if (ok) I18n.text(R.string.i18n_settingsactivity_148) else I18n.text(R.string.i18n_settingsactivity_149) }
                }.start()
            }
        }
    }

    /** Uses the TV keyboard. Search only on explicit user request. */
    private fun selectWeatherCity(onChosen: (Weather.City) -> Unit) {
        val input = EditText(this).apply {
            isSingleLine = true
            hint = I18n.text(R.string.weather_search_city)
            setText(prefs.weatherCity)
            setSelection(text.length)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(I18n.text(R.string.weather_choose_city))
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(I18n.text(R.string.weather_search_city)) { _, _ ->
                val query = input.text.toString().trim()
                if (query.length < 2) return@setPositiveButton
                Toast.makeText(this, I18n.text(R.string.weather_search_city), Toast.LENGTH_SHORT).show()
                Thread {
                    val cities = runCatching { Weather.search(query) }.getOrNull()
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        when {
                            cities == null -> Toast.makeText(this, I18n.text(R.string.weather_city_error), Toast.LENGTH_LONG).show()
                            cities.isEmpty() -> Toast.makeText(this, I18n.text(R.string.weather_city_none), Toast.LENGTH_LONG).show()
                            else -> android.app.AlertDialog.Builder(this)
                                .setTitle(I18n.text(R.string.weather_choose_city))
                                .setItems(cities.map { it.name }.toTypedArray()) { _, which ->
                                    onChosen(cities[which])
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                }.start()
            }.show()
    }

    /** A small grey group title above a run of rows. */
    private fun section(title: String) {
        val first = list.childCount == 0
        list.addView(text(14f, Color.argb(120, 255, 255, 255), weight = 500).apply { text = title },
            LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(18); topMargin = if (first) 0 else dp(22); bottomMargin = dp(8)
            })
    }

    /**
     * One settings row; [value] is re-read after each click, [desc] shows under the icon while
     * the row is focused, and [chevron] marks a row that opens another screen.
     */
    private fun row(label: String, desc: String?, value: () -> String, chevron: Boolean = false, onClick: (TextView) -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(20), 0, dp(if (chevron) 14 else 20), 0)
        }
        val l = text(17f, IDLE_TEXT, weight = 500).apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val v = text(16f, IDLE_VALUE).apply { text = value() }
        r.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        val arrow = if (chevron) text(24f, IDLE_VALUE, weight = 300).apply { text = "›" } else null
        arrow?.let { r.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10); bottomMargin = dp(2) }) }
        val idle = rounded(Color.argb(20, 255, 255, 255), dp(14).toFloat())
        val focus = rounded(Color.WHITE, dp(14).toFloat())
        r.background = idle
        r.setOnFocusChangeListener { _, f ->
            r.background = if (f) focus else idle
            l.setTextColor(if (f) Color.BLACK else IDLE_TEXT)
            v.setTextColor(if (f) FOCUS_VALUE else IDLE_VALUE)
            arrow?.setTextColor(if (f) FOCUS_VALUE else IDLE_VALUE)
            if (f && desc != null) about.text = desc
            r.tweenScale(if (f) 1.03f else 1f, 160)
        }
        r.setOnClickListener {
            if (page == Page.MAIN) opener = list.indexOfChild(r)
            onClick(v)
            if (page == Page.MAIN && !v.text.startsWith(I18n.text(R.string.i18n_settingsactivity_150)) && !v.text.startsWith(I18n.text(R.string.i18n_settingsactivity_151))) v.text = value()
        }
        list.addView(r, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(6) })
    }

    private fun onOff(b: Boolean) = if (b) I18n.text(R.string.i18n_settingsactivity_152) else I18n.text(R.string.i18n_settingsactivity_153)

    /** I18n.text(R.string.i18n_settingsactivity_154) with what auto picks on this TV, or the forced choice. */
    private fun auto(pref: String, on: Boolean, yes: String, no: String) =
        if (pref == Prefs.AUTO) I18n.text(R.string.i18n_settingsactivity_155, if (on) yes else no) else if (on) yes else no

    /** The next of 自动 → [a] → [b]. */
    private fun next(pref: String, a: String, b: String) = when (pref) { Prefs.AUTO -> a; a -> b; else -> Prefs.AUTO }

    /** A secure setting, written directly once WRITE_SECURE_SETTINGS is granted, otherwise through local adb. Blocking. */
    private fun putSecure(key: String, value: String): Boolean =
        if (Guard.hasSecureSettings(this)) runCatching { Settings.Secure.putString(contentResolver, key, value) }.getOrDefault(false)
        else LocalAdb.shell(applicationContext, "settings put secure $key $value") != null

    private fun minutesLabel(m: Int) = when {
        m >= 1440 -> I18n.text(R.string.i18n_settingsactivity_156)
        m >= 60 -> I18n.text(R.string.i18n_settingsactivity_157, m / 60)
        else -> I18n.text(R.string.i18n_settingsactivity_158, m)
    }

    /** On Android TV the screensaver starts after the "screen off" timeout. */
    private fun idleMinutes() = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 600_000) / 60_000

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && page != Page.MAIN) {
            show(Page.MAIN, opener)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && page != Page.MAIN) true else super.onKeyDown(keyCode, event)
}
