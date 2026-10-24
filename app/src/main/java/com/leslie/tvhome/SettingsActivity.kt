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
            Page.KEEP -> appPicker("Keep when freeing memory", prefs.keep) { pkg, on ->
                prefs.keep = if (on) prefs.keep + pkg else prefs.keep - pkg
            }
            Page.HIDDEN -> appPicker("Hidden apps", prefs.hidden, includeHidden = true) { pkg, on ->
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
        heading.text = "Home Screen Settings"
        about.text = ""

        section("Software")
        row("Software updates & casting", "Check for new Tigris and MiracastReceiver versions, install the casting component, or turn on automatic updates.", { "Open" }) {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        section("Appearance")
        row("Time format", "Whether the clock on the home screen and in Control Center uses 12-hour (11:49 AM) or 24-hour (23:49) time.",
            { if (prefs.clock24) "24-hour" else "12-hour" }) { prefs.clock24 = !prefs.clock24 }
        row("Clock position", "A small clock in the top-right corner, a large clock with the date in the top-left (the earlier style), or no clock at all.", {
            when (prefs.clockStyle) { Prefs.CLOCK_LARGE -> "Large clock, top left"; Prefs.CLOCK_OFF -> "Don't show"; else -> "Top right" }
        }) {
            val opts = listOf(Prefs.CLOCK_CORNER, Prefs.CLOCK_LARGE, Prefs.CLOCK_OFF)
            prefs.clockStyle = opts[(opts.indexOf(prefs.clockStyle) + 1) % opts.size]
        }
        if (backdropFile(this) != null) row("Settings background", "Use your uploaded image as the settings background, or a plain dark background.", {
            if (prefs.settingsBg) "Image" else "Plain"
        }) {
            prefs.settingsBg = !prefs.settingsBg
            showBackdrop()
        }
        if (iconFile(this) != null) row("Settings icon", "The icon for this entry: your own uploaded image, or the built-in gear.", {
            if (prefs.ownIcon) "Uploaded image" else "Gear"
        }) {
            prefs.ownIcon = !prefs.ownIcon
            showIcon(icon)
        }
        row("Upload avatar", "Scan the QR code with your phone, upload a photo under “Avatar” on the web page, then return to the home screen.", { "Upload from phone" }) {
            startActivity(Intent(this, WallpaperActivity::class.java).putExtra("upload_avatar", true))
        }
        row("Show avatar in the top-right corner", "Press the avatar in the top-right corner to open Home Screen Settings.", { onOff(prefs.showAvatar) }) { prefs.showAvatar = !prefs.showAvatar }
        row("Focus animation", "tvOS: the selected app grows, tilts with the direction and catches a highlight. Simple: it only grows." +
            "\n“Auto” uses Simple on lower-end TVs.", {
            auto(prefs.focusStyle, prefs.focusTvos(this), "tvOS", "Simple")
        }) {
            prefs.focusStyle = next(prefs.focusStyle, Prefs.FOCUS_TVOS, Prefs.FOCUS_SIMPLE)
        }
        row("Control Center style", "Glass: frosted glass that shows the image behind, and the selected button lights up its neighbours. Panel: the earlier dark panel, easier on performance." +
            "\n“Auto” uses Panel on lower-end TVs.", {
            auto(prefs.ccStyle, prefs.ccGlass(this), "Glass", "Panel")
        }) {
            prefs.ccStyle = next(prefs.ccStyle, Prefs.CC_GLASS, Prefs.CC_PANEL)
        }

        section("Boot animation")
        val video = Intro.videoFile(this) != null
        row("Boot animation", "Plays the first time the home screen appears after boot; the logo zooms into the desktop at the end. Press any key to skip." +
            if (video) "" else "\nPlays the built-in boot video by default; you can also upload your own.", {
            when (prefs.intro) { Prefs.INTRO_BOOT -> "On boot"; Prefs.INTRO_WAKE -> "On boot and wake"; else -> "Off" }
        }) {
            val opts = listOf(Prefs.INTRO_OFF, Prefs.INTRO_BOOT, Prefs.INTRO_WAKE)
            prefs.intro = opts[(opts.indexOf(prefs.intro) + 1) % opts.size]
            // This boot counts as played: turning it on doesn't start it on the next return home.
            prefs.introBoot = Intro.bootStamp(this)
        }
        row("Animation content", "Uploaded video: for example Apple TV's boot animation, with the logo zooming in at the end. Tigris wordmark: the wordmark fades in and then zooms." +
            if (video) "" else "\nWithout an uploaded video, video mode uses the built-in animation.", {
            if (prefs.introSource == Prefs.INTRO_WORDMARK) "Tigris wordmark" else if (video) "Uploaded video" else "Built-in video"
        }) {
            prefs.introSource = if (prefs.introSource == Prefs.INTRO_WORDMARK) Prefs.INTRO_VIDEO else Prefs.INTRO_WORDMARK
        }
        row("Wait after wake", "When waking from standby, stay black for this long before playing the animation. Many TVs (especially OLED) take a few seconds to actually light up; increase it if you miss the start of the animation, decrease it if the black screen lasts too long.", {
            if (prefs.wakeDelay == 0) "No wait" else "${prefs.wakeDelay / 1000.0} s".replace(".0 ", " ")
        }) {
            val opts = listOf(0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10_000)
            prefs.wakeDelay = opts.firstOrNull { it > prefs.wakeDelay } ?: 0
        }
        row("Boot animation sound", "Play the audio from the animation video.", { onOff(prefs.introSound) }) { prefs.introSound = !prefs.introSound }
        row("Preview boot animation", "Play it once now, back on the home screen.", { "" }, chevron = true) {
            startActivity(Intent(this, HomeActivity::class.java)
                .putExtra(Intro.EXTRA_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        section("Background & frame")
        row("Background", "Home screen background: a theme, a wallpaper source, or your own photos.", {
            Backgrounds.THEMES[prefs.background]?.first ?: Wallpapers.label(Wallpapers.sourceFor(prefs.background)).let {
                if (prefs.background == Wallpapers.MINE) it else "$it (rotates automatically)"
            }
        }, chevron = true) { startActivity(Intent(this, WallpaperActivity::class.java)) }
        row("Photo rotation interval", "How often the rotating background changes.", { minutesLabel(prefs.wallMinutes) }) {
            val opts = listOf(15, 30, 60, 180, 1440)
            prefs.wallMinutes = opts[(opts.indexOf(prefs.wallMinutes) + 1) % opts.size]
        }
        row("Change now", "Fetch a new photo from the wallpaper source now. Photos are downloaded when shown, and old ones are cleaned up after a few days.",
            { "Have ${Wallpapers(this, Wallpapers.sourceFor(prefs.background)).files().size}" }) { v ->
            v.text = "Downloading…"
            Thread {
                val w = Wallpapers(applicationContext, Wallpapers.sourceFor(prefs.background))
                w.refresh()
                w.advance(prefs.wallMinutes, force = true)
                runOnUiThread { v.text = "Have ${w.files().size}" }
            }.start()
        }
        row("Show frame when the TV is idle", "After the TV has been idle for a while, loop photos or aerial videos full screen instead of the built-in screensaver.",
            { onOff(prefs.frameScreensaver) }) { v ->
            val on = !prefs.frameScreensaver
            val current = Settings.Secure.getString(contentResolver, "screensaver_components").orEmpty()
            val mine = "$packageName/${PhotoDream::class.java.name}"
            // Whatever the TV had before goes back when the frame is turned off (Sony's own if unknown).
            if (on && !current.startsWith(packageName)) prefs.prevScreensaver = current
            val comp = if (on) mine else prefs.prevScreensaver.ifEmpty { SONY_DREAM }
            v.text = "Setting up…"
            Thread {
                val ok = putSecure("screensaver_components", comp) && putSecure("screensaver_enabled", "1")
                if (ok) prefs.frameScreensaver = on
                runOnUiThread { v.text = if (ok) onOff(prefs.frameScreensaver) else "Local debugging must be authorized first" }
            }.start()
        }
        row("Screensaver content", "Whether the frame shows background photos or Apple TV aerial videos.",
            { if (prefs.saverMode == Prefs.SAVER_AERIAL) "Apple TV aerial videos" else "Photos" }) {
            prefs.saverMode = if (prefs.saverMode == Prefs.SAVER_AERIAL) Prefs.SAVER_PHOTOS else Prefs.SAVER_AERIAL
        }
        row("Idle time before the frame appears", "How long without input before the frame appears.", { minutesLabel(idleMinutes()) }) { v ->
            val opts = listOf(1, 3, 5, 10, 15, 30)
            val next = opts[(opts.indexOf(idleMinutes()) + 1) % opts.size]
            v.text = "Setting up…"
            Thread {
                val ok = LocalAdb.shell(applicationContext, "settings put system screen_off_timeout ${next * 60_000}") != null
                runOnUiThread { v.text = if (ok) minutesLabel(next) else "Local debugging must be authorized first" }
            }.start()
        }
        row("Time per photo in the frame", "How long each photo is shown in the frame.", { "${prefs.frameSeconds} s" }) {
            val opts = listOf(15, 30, 60, 120)
            prefs.frameSeconds = opts[(opts.indexOf(prefs.frameSeconds) + 1) % opts.size]
        }

        section("Memory & power")
        row("Free memory automatically in standby", "In standby, only cached processes are cleaned; apps are not force-stopped. Skipped when the foreground app can't be confirmed.", { onOff(prefs.cleanOnStandby) }) {
            prefs.cleanOnStandby = !prefs.cleanOnStandby
        }
        row("Scheduled memory freeing", "Periodically clean cached processes without force-stopping apps. Off by default on new installs.", {
            if (prefs.cleanIntervalHours == 0) "Off" else "Every ${prefs.cleanIntervalHours} h"
        }) {
            val opts = listOf(0, 2, 4, 8)
            prefs.cleanIntervalHours = opts[(opts.indexOf(prefs.cleanIntervalHours) + 1) % opts.size]
            KeeperService.start(this, KeeperService.ACTION_RESCHEDULE)
        }
        row("Free memory now", "Close background apps now.", { "${Cleaner.availMb(this)} MB available" }) { v ->
            v.text = "Freeing…"
            Thread {
                val r = Cleaner.clean(applicationContext)
                runOnUiThread { v.text = r.description() }
            }.start()
        }
        row("Apps kept when freeing memory", "Apps that won't be closed when freeing memory, such as casting and music.", { "${prefs.keep.size}" }, chevron = true) {
            show(Page.KEEP)
        }
        row("When you press power on the remote", "Standby: as usual. Full power off: saves power, but boot takes longer.", {
            if (prefs.shutdownOnPowerKey) "Full power off" else "Standby"
        }) {
            prefs.shutdownOnPowerKey = !prefs.shutdownOnPowerKey
            if (prefs.shutdownOnPowerKey && !prefs.adbAuthorized)
                Toast.makeText(this, "Requires local debugging authorization to take effect", Toast.LENGTH_LONG).show()
        }

        section("App")
        row("Move recently opened apps to the front", "When on, an app or folder that opens successfully moves to the front. When off, the current order is kept and only long-press “Edit Home Screen” changes it; launching apps won't reshuffle.",
            { onOff(prefs.recentFirst) }) { prefs.recentFirst = !prefs.recentFirst }
        row("Hidden apps", "Apps that don't appear on the home screen.", { "${prefs.hidden.size}" }, chevron = true) { show(Page.HIDDEN) }
        row("Removed preinstalled apps", "Preinstalled apps removed from the long-press menu can be restored here.", { "${prefs.removed.size}" }, chevron = true) {
            show(Page.REMOVED)
        }

        section("System")
        row("Local debugging authorization", "Let the home screen use local debugging for actions like power off and freeing memory. Choose “Always allow” in the dialog.",
            { if (prefs.adbAuthorized) "Authorized" else "Not authorized — press OK to authorize" }) { v ->
            v.text = "Choose “Always allow” in the dialog"
            Thread {
                val ok = LocalAdb.shell(applicationContext, "echo ok", 60_000) != null
                runOnUiThread { v.text = if (ok) "Authorized" else "Not authorized — press OK to retry" }
            }.start()
        }
        // Only where the TV keeps Home for its own launcher; on Sony or stock Android TV Tigris is the real home.
        if (Guard.needed(this)) row("Home screen guardian", "When the Home key takes you to the stock launcher, come back here automatically.",
            { if (!prefs.guard) "Off" else if (HomeGuardService.isRunning) "On" else "On (not running)" }) { v ->
            val on = !prefs.guard
            v.text = "Setting up…"
            Thread {
                val ok = (Guard.hasSecureSettings(this) || Guard.grantViaLocalAdb(applicationContext)) && Guard.setEnabled(applicationContext, on)
                runOnUiThread {
                    v.text = if (!ok) "Local debugging must be authorized first" else if (on) "On" else "Off"
                    if (ok && on) Toast.makeText(this, "The Home key will bring you back here", Toast.LENGTH_LONG).show()
                }
            }.start()
        }
        if (Guard.isXiaomi() || prefs.keepAdb) row("Keep ADB debugging on", "Prevent the system from turning ADB debugging off.", { onOff(prefs.keepAdb) }) { v ->
            val on = !prefs.keepAdb
            v.text = "Setting up…"
            Thread {
                val ok = !on || Guard.hasSecureSettings(this) || Guard.grantViaLocalAdb(applicationContext)
                if (ok) { prefs.keepAdb = on; Guard.ensureAdb(applicationContext) }
                runOnUiThread { v.text = if (ok) onOff(prefs.keepAdb) else "Local debugging must be authorized first" }
            }.start()
        }
        row("System settings", "Open the TV's built-in settings.", { "" }, chevron = true) { runCatching { startActivity(TvControls(this).settingsIntent()) } }
        row("About", "Tigris home screen, a tvOS-inspired Android TV launcher.",
            { "Version " + packageManager.getPackageInfo(packageName, 0).versionName }) {}
    }

    private fun appPicker(title: String, selected: Set<String>, includeHidden: Boolean = false, toggle: (String, Boolean) -> Unit) {
        heading.text = title
        about.text = if (page == Page.KEEP) "The selected apps won't be closed when memory is freed." else "The selected apps won't appear on the home screen."
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
        heading.text = "Removed preinstalled apps"
        about.text = "Pick an app to reinstall it from the system partition."
        if (prefs.removed.isEmpty()) row("No removed apps", null, { "" }) {}
        for (pkg in prefs.removed.sorted()) {
            val label = try {
                packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(packageManager).toString()
            } catch (e: Exception) { pkg }
            row(label, null, { "Restore" }) { v ->
                v.text = "Restoring…"
                Thread {
                    val ok = LocalAdb.shell(applicationContext, "cmd package install-existing $pkg")?.contains("installed") == true
                    if (ok) prefs.removed = prefs.removed - pkg
                    runOnUiThread { v.text = if (ok) "Restored" else "Restore failed" }
                }.start()
            }
        }
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
            if (page == Page.MAIN && !v.text.startsWith("In progress") && !v.text.startsWith("Please check")) v.text = value()
        }
        list.addView(r, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(6) })
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    /** "Auto (…)" with what auto picks on this TV, or the forced choice. */
    private fun auto(pref: String, on: Boolean, yes: String, no: String) =
        if (pref == Prefs.AUTO) "Auto (${if (on) yes else no})" else if (on) yes else no

    /** The next of 自动 → [a] → [b]. */
    private fun next(pref: String, a: String, b: String) = when (pref) { Prefs.AUTO -> a; a -> b; else -> Prefs.AUTO }

    /** A secure setting, written directly once WRITE_SECURE_SETTINGS is granted, otherwise through local adb. Blocking. */
    private fun putSecure(key: String, value: String): Boolean =
        if (Guard.hasSecureSettings(this)) runCatching { Settings.Secure.putString(contentResolver, key, value) }.getOrDefault(false)
        else LocalAdb.shell(applicationContext, "settings put secure $key $value") != null

    private fun minutesLabel(m: Int) = when {
        m >= 1440 -> "Daily"
        m >= 60 -> "${m / 60} h"
        else -> "$m min"
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
