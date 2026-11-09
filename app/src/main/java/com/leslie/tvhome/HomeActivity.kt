package com.leslie.tvhome

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leslie.tvhome.tv.Input
import com.leslie.tvhome.tv.TvControls
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * tvOS style home: a low glass dock and a compact status row in the top-right.
 */
class HomeActivity : Activity(), TileAdapter.Listener {
    companion object {
        const val SHELF_SECONDS = 8
        private const val EXTRA_GUARD = "com.leslie.tvhome.GUARD"
        private const val EXTRA_WAKE = "com.leslie.tvhome.WAKE"

        /**
         * How the home guard and the boot receiver bring the home screen back: the same task, with
         * no HOME category. Where Tigris is not the system home, that category made Android open a
         * second, home-type task with a fresh screen every time (Aerial, Xiaomi MiTV-MFTR0).
         */
        fun bringBack(ctx: Context): Intent = Intent(ctx, HomeActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            .putExtra(EXTRA_GUARD, true)
    }

    private lateinit var root: FrameLayout
    private lateinit var scene: FrameLayout
    private lateinit var backdrop: FrameLayout
    private lateinit var bg: ImageView
    private lateinit var videoWallpaper: VideoWallpaper
    private var wallpaperPlaying = false
    private val videoGlassFrames = VideoGlassFrames()
    private val wallpaperChanges = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (resumed && (key == "background" || key == "wallpaper_video_revision")) applyBackground()
    }
    private lateinit var header: FrameLayout
    private lateinit var weatherCard: TextView
    private var weatherLoading = false
    private var lastWeatherAttempt = 0L
    private lateinit var clockText: TextView
    /** The large clock top left, with the date above it ([Prefs.CLOCK_LARGE]). */
    private lateinit var bigClock: LinearLayout
    private lateinit var bigDate: TextView
    private lateinit var bigTime: TextView
    private var intro: Intro? = null
    private var welcome: Welcome? = null
    private lateinit var avatar: IconButton
    private lateinit var netButton: IconButton
    private lateinit var ccButton: IconButton
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TileAdapter
    private var tileStyleSeen = ""
    private var textScaleSeen = 1f
    private var previousHomeIntentAt = 0L

    // Only launches made by this Activity may animate a subsequent return.
    // No attempt is made to animate or inspect another application's window.
    private var outboundLaunch: HomeReturnPolicy.Launch? = null
    private var returnCueView: View? = null

    private fun noteExternalLaunch(pkg: String) {
        if (pkg != packageName) {
            outboundLaunch = HomeReturnPolicy.Launch(pkg, SystemClock.uptimeMillis())
        }
    }

    private fun cancelReturnCue() {
        returnCueView?.let { view ->
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
        }
        returnCueView = null
    }

    /** A tiny hardware-composited card settle, never a simulated third-party task snapshot. */
    private fun revealReturnFocus() {
        val launch = outboundLaunch ?: return
        if (!resumed || !hasWindowFocus() || isFinishing || isDestroyed) return
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
        val blocked = intro != null || welcome != null || wakeIntro ||
            sheets.isNotEmpty() || control.isOpen || moving >= 0
        if (!HomeReturnPolicy.canReveal(launch, SystemClock.uptimeMillis(),
                interactive, blocked, false)) {
            outboundLaunch = null
            return
        }
        val pos = adapter.apps.indexOfFirst { it.pkg == launch.pkg }
        // If an app was removed or a settings change rebuilt the grid, do not animate
        // an unrelated card or force the RecyclerView to scroll just for decoration.
        val tile = if (pos >= 0) grid.findViewHolderForAdapterPosition(pos)?.itemView else null
        outboundLaunch = null
        if (tile == null) return
        if (!tile.hasFocus()) tile.requestFocus()
        cancelReturnCue()
        val lowEnd = Device.isLowEnd(this)
        returnCueView = tile
        tile.animate().cancel()
        tile.translationY = dp(HomeReturnPolicy.liftDp(lowEnd)).toFloat()
        tile.alpha = 0.92f
        tile.animate().translationY(0f).alpha(1f)
            .setDuration(HomeReturnPolicy.settleDurationMs(lowEnd))
            .setInterpolator(Motion.ease)
            .withEndAction {
                if (returnCueView === tile) returnCueView = null
            }.start()
    }
    private lateinit var repo: AppRepo
    private lateinit var prefs: Prefs
    private lateinit var control: ControlCenter
    private val tv by lazy { TvControls(this) }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val sheets = ArrayList<Sheet>()

    private var cols = DockLayout.DEFAULT_COLUMNS
    private var tileW = 0
    private var tileH = 0
    private var rowPitch = 0
    private var firstRowY = 0
    /** Where the focused row sits once the grid is expanded below the dock. */
    private var expandedRowY = 0

    private var focusedPos = 0
    private var firstFocus = true
    /** Grid scroll position; RecyclerView's own estimate is unreliable for grids. */
    private var scrollY = 0
    private var shownBackground: String? = null
    private var glassExtreme = false
    private var glassAppearance = ""
    @Volatile private var refreshing = false
    private var bgPhoto: android.graphics.drawable.Drawable? = null
    private var sw = 0
    private var sh = 0

    /** Liquid Glass slab behind the first row, the "dock". */
    private lateinit var dock: ImageView
    private var dockShadow: ImageView? = null
    private lateinit var frost: ImageView
    private lateinit var scrollGlass: FrostGlass
    private val dockRect = Rect()
    private var dockRadius = 0f
    /** The glass baked over the home picture, kept for when the shelf goes away. */
    @Volatile private var homeDock: Bitmap? = null
    /** Extreme glass: the home picture's lenses, so leaving a Top Shelf needs no new preparation. */
    private var homeLenses: FrostGlass.Lenses? = null
    private var dockShown: Drawable? = null
    /** Last pre-31 video dock rebuild. API 31+ does not read this. */
    private var lastLegacyDockGlassMs = 0L
    private var lastLiveFrostMs = 0L

    /** Top Shelf: content of the focused first-row dock app filling the screen above the dock. */
    private lateinit var shelf: FrameLayout
    private lateinit var shelfPics: FrameLayout
    private var shelfFront: ImageView? = null
    private lateinit var shelfSource: TextView
    private lateinit var shelfTitle: TextView
    private lateinit var shelfMeta: TextView
    private lateinit var shelfHint: TextView
    private lateinit var shelfInfo: LinearLayout
    private lateinit var shelfDots: LinearLayout
    // A bounded queue prevents rapid D-pad presses from accumulating stale image work.
    private val net = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(4), ThreadPoolExecutor.DiscardOldestPolicy())
    private var shelfPkg: String? = null
    private var pendingShelf: String? = null
    private var shelfItems: List<Shelf.Item> = emptyList()
    private var shelfIndex = 0
    private var shelfPendingIndex = 0
    private var shelfConsumeExitKeyUp = -1
    private var shelfShown = false
    private var shelfBrowsing = false
    private var shelfGeneration = 0L
    private var shelfImageRequest = 0L
    private val shelfShow = Runnable { pendingShelf?.let { showShelf(it) } }
    private val shelfAdvance = Runnable {
        if (shelfShown && !shelfBrowsing && shelfItems.size > 1 && !control.isOpen) {
            requestShelfIndex(ShelfNavigation.step(shelfIndex, shelfItems.size, 1))
        }
    }

    /** Position being dragged in "edit home screen" mode, or -1. */
    private var moving = -1

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val pkg = i.data?.schemeSpecificPart
            repo.invalidate(pkg)
            reload(pkg)
        }
    }

    /**
     * In-app photo-frame idle timer. Always arm before the earliest applicable system dream
     * or screen-off deadline; otherwise Android may go to sleep before FrameActivity opens.
     * It only runs while Home is resumed, never over video/casting in another app.
     */
    private var idleToken = 0
    private var idleDueAt = 0L
    private var lastIdleLog: String? = null
    private var lastSuppressLog: String? = null
    private val idleFrame = Runnable { onIdleFired() }

    private fun screenSaverTimeout(): Long? =
        runCatching { Settings.System.getLong(contentResolver, "screen_saver_timeout") }.getOrNull()

    private fun screenOffTimeout(): Long? =
        runCatching { Settings.System.getLong(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) }.getOrNull()

    private fun readSchedule() = FrameIdlePolicy.schedule(
        prefs.frameScreensaver, prefs.frameIdleMinutes,
        if (IdleTimerTakeover.isActive(this)) null else screenSaverTimeout(),
        if (IdleTimerTakeover.isActive(this)) null else screenOffTimeout())

    private fun cancelIdle() {
        idleToken++
        main.removeCallbacks(idleFrame)
    }

    /** [resetDeadline] is for a real key, a fresh resume, or the screen coming back on. */
    private fun armIdle(resetDeadline: Boolean) {
        if (!resumed) return
        val now = SystemClock.elapsedRealtime()
        if (resetDeadline) HomeForeground.interact(now)
        val schedule = readSchedule()
        if (schedule.logLine != lastIdleLog) {
            lastIdleLog = schedule.logLine
            Log.i(FrameIdlePolicy.TAG, schedule.logLine)
        }
        main.removeCallbacks(idleFrame)
        val delay = schedule.delayMs
        if (delay == null) {
            idleDueAt = 0L
            idleToken++
            return
        }
        if (resetDeadline || idleDueAt == 0L) idleDueAt = now + delay
        idleToken++
        main.postDelayed(idleFrame, (idleDueAt - now).coerceAtLeast(0L))
    }

    private fun idleOverlays() = welcome != null || intro != null || sheets.isNotEmpty() || control.isOpen

    private fun idleSuppression(): FrameIdlePolicy.Suppression? {
        val now = SystemClock.elapsedRealtime()
        val hint = HomeForeground.mediaHint(now)
        val media = FrameIdleProbe.media(this, hint)
        return FrameIdlePolicy.suppression(
            resumed = resumed,
            focused = hasWindowFocus(),
            interactive = getSystemService(android.os.PowerManager::class.java).isInteractive,
            overlays = idleOverlays(),
            media = media,
        )
    }

    private fun noteSuppressed(detail: String) {
        if (detail == lastSuppressLog) return
        lastSuppressLog = detail
        Log.i(FrameIdlePolicy.TAG, "photo frame suppressed: $detail")
    }

    private fun onIdleFired() {
        val token = idleToken
        if (!resumed) return
        val blocked = idleSuppression()
        if (blocked != null) {
            noteSuppressed(blocked.detail)
            if (blocked.retry) main.postDelayed(idleFrame, FrameIdlePolicy.RETRY_MS)
            return
        }
        if (!prefs.adbAuthorized) {
            openFrameFromIdle(token)
            return
        }
        io.execute {
            val dump = LocalAdb.shell(applicationContext, "dumpsys window", 2500)
            val foreign = FrameIdlePolicy.foreignHoldScreen(dump, packageName)
            main.post {
                if (token != idleToken || !resumed) return@post
                val again = idleSuppression()
                if (again != null) {
                    noteSuppressed(again.detail)
                    if (again.retry) main.postDelayed(idleFrame, FrameIdlePolicy.RETRY_MS)
                    return@post
                }
                if (foreign == true) {
                    noteSuppressed("another window holds the screen on")
                    main.postDelayed(idleFrame, FrameIdlePolicy.RETRY_MS)
                    return@post
                }
                if (foreign == null) {
                    Log.i(FrameIdlePolicy.TAG, "frame idle: hold-screen window was not readable; other apps' FLAG_KEEP_SCREEN_ON may be invisible")
                }
                openFrameFromIdle(token)
            }
        }
    }

    private fun openFrameFromIdle(token: Int) {
        if (token != idleToken || !resumed || isFinishing) return
        val blocked = idleSuppression()
        if (blocked != null) {
            noteSuppressed(blocked.detail)
            if (blocked.retry) main.postDelayed(idleFrame, FrameIdlePolicy.RETRY_MS)
            return
        }
        lastSuppressLog = null
        Log.i(FrameIdlePolicy.TAG, "opening photo frame; ${lastIdleLog.orEmpty()}")
        openFrame()
    }

    private fun syncOwnPlayback() {
        HomeForeground.setOwnPlayback(wallpaperPlaying || intro != null, SystemClock.elapsedRealtime())
    }

    fun openFrame() {
        startActivity(Intent(this, FrameActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            refreshWeather()
            applyBackground()
            main.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tween.readScale(this)
        prefs = Prefs(this)
        glassExtreme = prefs.extremeGlass
        cols = prefs.dockColumns
        repo = AppRepo(this)
        tileStyleSeen = prefs.appTileStyle
        textScaleSeen = prefs.textScale
        buildViews()
        control = ControlCenter(this, root)
        // Covers the screen before its first frame, so the home screen is never seen ahead of it.
        val welcomePreview = intent.getBooleanExtra(Welcome.EXTRA_PREVIEW, false)
        intent.removeExtra(Welcome.EXTRA_PREVIEW)
        if (welcomePreview || !prefs.welcomeCompleted) playWelcome(preview = welcomePreview)
        else if (Intro.dueAfterBoot(this)) playIntro()
        reload()
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        registerReceiver(screenOn, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        KeeperService.start(this)
        if (!prefs.adbAuthorized && welcome == null) main.postDelayed({ askAdbAuthorization() }, if (intro != null) 8000L else 1500L)
    }

    // ------------------------------------------------------------------- intro

    // Cover on screen-off, before MIUI can show the retained home frame on wake.
    // Consume the pending wake in onResume OR SCREEN_ON, never both.
    // API 30 and below then wait for a lit display (Intro.startAfterDisplay). API 31+ does not.
    private var resumed = false
    private var wakeIntro = false
    private var wakeCover: View? = null

    private fun displayState(): Int = runCatching {
        getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.state
    }.getOrNull() ?: -1

    private fun logPower(event: String) {
        val interactive = runCatching { getSystemService(android.os.PowerManager::class.java).isInteractive }.getOrNull()
        val introMode = if (::prefs.isInitialized) prefs.intro else "-"
        BootLog.i("event=$event display=${BootIntroGate.displayLabel(displayState())} interactive=$interactive resumed=$resumed focused=${hasWindowFocus()} intro=$introMode")
    }

    private fun armWakeIntro() {
        if (prefs.intro != Prefs.INTRO_WAKE) return
        BootLog.i("wake armed")
        wakeIntro = true
        if (wakeCover == null) {
            wakeCover = View(this).apply {
                setBackgroundColor(Color.BLACK)
                translationZ = dp(401).toFloat()
                root.addView(this, FrameLayout.LayoutParams(-1, -1))
            }
        }
    }

    private fun consumeWakeIntro() {
        if (!wakeIntro) return
        BootLog.i("wake consume")
        wakeIntro = false
        if (prefs.intro == Prefs.INTRO_WAKE) playIntro(wake = true)
        wakeCover?.let { root.removeView(it) }
        wakeCover = null
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            logPower(if (i.action == Intent.ACTION_SCREEN_ON) "screen_on" else "screen_off")
            if (i.action == Intent.ACTION_SCREEN_OFF) {
                wallpaperPlaying = false
                videoWallpaper.stop()
                syncOwnPlayback()
                cancelIdle()
                armWakeIntro()
                return
            }
            if (resumed) armIdle(resetDeadline = true)
            if (resumed && !wakeIntro) applyBackground()
            if (!wakeIntro) return
            if (resumed) { consumeWakeIntro(); return }
            startActivity(Intent(c, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_WAKE, true))
        }
    }

    /** Plays the boot animation over the home screen; [preview] is the one started from settings. */
    fun playIntro(preview: Boolean = false, wake: Boolean = false) {
        if (intro != null || welcome != null) return
        wallpaperPlaying = false
        videoWallpaper.stop()
        syncOwnPlayback()
        if (!preview) prefs.introBoot = Intro.bootStamp(this)
        sheets.toList().forEach { it.dismiss() }
        control.close()
        BootLog.i("play wake=$wake preview=$preview delay=${if (wake) prefs.wakeDelay else 0} sdk=${Build.VERSION.SDK_INT}")
        intro = Intro(this, root, scene, prefs.introSound) {
            intro = null
            syncOwnPlayback()
            if (resumed) applyBackground()
            if (sheets.isEmpty() && !control.isOpen) focusAt(focusedPos)
        }.also {
            // API 31+ keeps the fixed delay from the wake signal. Older platforms wait until the
            // display is on: screen-on there does not mean the panel is showing a picture.
            if (Build.VERSION.SDK_INT >= 31) it.start(if (wake) prefs.wakeDelay.toLong() else 0)
            else it.startAfterDisplay(wake, if (wake) prefs.wakeDelay.toLong() else 0L)
        }
        syncOwnPlayback()
    }

    /** Independent of the user's boot video and sound preferences. */
    private fun playWelcome(preview: Boolean = false) {
        welcome?.let { it.close(); root.removeView(it) }
        intro?.finish()
        wallpaperPlaying = false
        videoWallpaper.stop()
        syncOwnPlayback()
        pauseShelfRotation()
        sheets.toList().forEach { it.dismiss() }
        control.close()
        scene.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        fun leave(continued: Boolean) {
            if (continued && !preview) {
                prefs.welcomeCompleted = true
                // Do not immediately play a second introduction on this same boot.
                prefs.introBoot = Intro.bootStamp(this)
            }
            val overlay = welcome ?: return
            welcome = null
            scene.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            overlay.animate().alpha(0f).setDuration(450).withEndAction {
                root.removeView(overlay)
                if (!isDestroyed && !isFinishing) {
                    if (resumed) applyBackground()
                    restoreGridFocus()
                    if (!preview && !prefs.adbAuthorized) main.postDelayed({ askAdbAuthorization() }, 550)
                }
            }.start()
        }
        welcome = Welcome(this, onContinue = { leave(true) }, onCancel = { leave(false) }).also {
            root.addView(it, FrameLayout.LayoutParams(-1, -1))
            it.requestFocus()
            if (resumed) it.resume()
        }
    }

    override fun onDestroy() {
        resumed = false
        cancelIdle()
        wallpaperPlaying = false
        syncOwnPlayback()
        videoWallpaper.destroy()
        cancelReturnCue()
        outboundLaunch = null
        unregisterReceiver(pkgReceiver)
        main.removeCallbacksAndMessages(null)
        control.destroy()
        scrollGlass.close()
        intro?.finish()
        welcome?.close()
        runCatching { unregisterReceiver(screenOn) }
        io.shutdown()
        net.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ layout

    private fun buildViews() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val match = { FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        scene = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        backdrop = FrameLayout(this)
        root.addView(scene, match())
        scene.addView(backdrop, match())
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        backdrop.addView(bg, match())
        videoWallpaper = VideoWallpaper(this, backdrop,
            canSample = { resumed && !shelfShown && (prefs.extremeGlass || !videoGlassFrames.busy) },
            onFrame = ::updateVideoGlass,
            onFrostSuspended = ::showVideoVeil)

        val dm = resources.displayMetrics
        sw = dm.widthPixels
        sh = dm.heightPixels
        // Proportions measured from a 1920×1080 tvOS 26 screenshot.
        val side = (sw * 0.06875f).toInt()
        val gap = (sw * 0.0292f).toInt()           // 56 px
        tileW = DockLayout.tileWidth(sw, side, gap, cols)
        tileH = if (prefs.appTileAspect == Prefs.TILE_TVOS) tileW * 3 / 5 else tileW * 9 / 16
        rowPitch = (sh * 0.2463f).toInt()          // 266 px
        // The dock sits at the bottom; only the top edge of the next row peeks out below it.
        val defaultTileWidth = DockLayout.tileWidth(sw, side, gap, DockLayout.DEFAULT_COLUMNS)
        val defaultTileHeight = if (prefs.appTileAspect == Prefs.TILE_TVOS) defaultTileWidth * 3 / 5 else defaultTileWidth * 9 / 16
        firstRowY = DockLayout.dockTop((sh * 0.744f).toInt(), defaultTileHeight, tileH) + dp(10)
        expandedRowY = (sh * 0.36f).toInt()

        // The dock hugs the first row: a focused tile (scaled 1.12) nearly touches its edges.
        val pad = dp(24)
        dockRect.set(side - pad, firstRowY - pad, sw - side + pad, firstRowY + tileH + pad)
        dockRadius = dp(11) + pad.toFloat()
        buildShelf(side)
        frost = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        scene.addView(frost, match())
        dock = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        scrollGlass = FrostGlass(dock, dockRadius)
        scene.addView(dock, FrameLayout.LayoutParams(dockRect.width(), dockRect.height()).apply {
            leftMargin = dockRect.left; topMargin = dockRect.top
        })
        // No frosted dock bar: the brand-coloured tiles float directly on the wallpaper.
        dock.visibility = View.GONE
        updateDockShadow(prefs.extremeGlass)

        // A persistent status row, also visible over Top Shelf and the expanded app grid.
        header = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        // The tvOS 26 clock this launcher started with: the date over a large thin clock, top left.
        bigDate = text(20f, bold = true).apply { setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.argb(60, 0, 0, 0)) }
        bigTime = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Fonts.inter(this@HomeActivity, 250)
            textSize = 118f
            includeFontPadding = false
            letterSpacing = -0.02f
            setShadowLayer(dp(12).toFloat(), 0f, 0f, Color.argb(50, 0, 0, 0))
        }
        bigClock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bigDate, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            addView(bigTime, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        }
        header.addView(bigClock, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = side - dp(4); topMargin = (sh * 0.197f).toInt()
        })
        clockText = text(14f, weight = 600).apply {
            setShadowLayer(dp(5).toFloat(), 0f, dp(1).toFloat(), Color.argb(140, 0, 0, 0))
        }
        val icons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            // The padding is the room a focused (enlarged) disc grows into; don't clip it away.
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        netButton = IconButton(this, R.drawable.ic_wifi) {
            runCatching { startActivity(tv.wifiIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        ccButton = IconButton(this, R.drawable.ic_toggles) { control.open() }
        netButton.contentDescription = I18n.text(R.string.i18n_homeactivity_001)
        ccButton.contentDescription = I18n.text(R.string.i18n_homeactivity_002)
        icons.addView(clockText, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        icons.addView(netButton, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(6) })
        icons.addView(ccButton, LinearLayout.LayoutParams(dp(28), dp(28)))
        avatar = IconButton(this, R.drawable.profile_avatar, portrait = true) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }.apply {
            id = View.generateViewId()
            contentDescription = I18n.text(R.string.i18n_homeactivity_003)
            visibility = if (prefs.showAvatar) View.VISIBLE else View.GONE
        }
        icons.addView(avatar, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(6) })
        header.addView(icons, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply {
            rightMargin = dp(32); topMargin = dp(26)
        })
        // Weather is opt-in. The compact translucent card sits below the status icons,
        // does not steal D-pad focus, and leaves the normal Dock layout unchanged.
        weatherCard = text(15f, Color.WHITE, weight = 500).apply {
            maxLines = 2
            setPadding(dp(17), dp(10), dp(17), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(45, 255, 255, 255))
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.argb(62, 255, 255, 255))
            }
            isFocusable = false
            visibility = if (prefs.weatherEnabled) View.VISIBLE else View.GONE
        }
        header.addView(weatherCard, FrameLayout.LayoutParams(dp(310), dp(69),
            Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(31); topMargin = dp(79)
        })
        listOf(netButton, ccButton, avatar).forEach { b ->
            b.setOnKeyListener { _, code, e ->
                if (e.action != KeyEvent.ACTION_DOWN) false else when {
                    code == KeyEvent.KEYCODE_DPAD_DOWN -> { focusAt(focusedPos); true }
                    code == KeyEvent.KEYCODE_DPAD_RIGHT && b == ccButton && avatar.visibility == View.VISIBLE -> { avatar.requestFocus(); true }
                    code == KeyEvent.KEYCODE_DPAD_LEFT && b == avatar -> { ccButton.requestFocus(); true }
                    else -> false
                }
            }
        }
        netButton.nextFocusRightId = View.generateViewId().also { ccButton.id = it }
        ccButton.nextFocusLeftId = View.generateViewId().also { netButton.id = it }
        ccButton.nextFocusRightId = avatar.id
        avatar.nextFocusLeftId = ccButton.id
        scene.addView(header, match())

        adapter = TileAdapter(repo, tileW, tileH, this).apply { setHasStableIds(true); dockCount = cols }
        grid = RecyclerView(this).apply {
            clipChildren = false
            clipToPadding = false
            itemAnimator?.changeDuration = 0
            // Room below the last row so it can scroll up to the expanded position.
            setPadding(side, firstRowY, side, sh - expandedRowY - tileH)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            overScrollMode = View.OVER_SCROLL_NEVER
            setItemViewCacheSize(30)
        }
        grid.layoutManager = object : GridLayoutManager(this, cols) {
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean,
            ): Boolean {
                // The dock row rests at the bottom; any other row is brought up to the expanded position.
                val row = getPosition(child) / cols
                val wantedOffset = if (row == 0) 0 else firstRowY + row * rowPitch - expandedRowY
                val dy = wantedOffset - scrollY
                if (dy != 0) parent.smoothScrollBy(0, dy, Motion.ease, 360)
                return true
            }
        }
        grid.adapter = adapter
        // Tiles update in place; the default cross-fade would swap in a second view and drop focus.
        (grid.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val col = parent.getChildAdapterPosition(view) % cols
                out.left = col * gap / cols
                out.right = gap - (col + 1) * gap / cols
                out.bottom = rowPitch - tileH
            }
        })
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // Measured from the rows themselves: layouts and jumps (reloads, scrollToPosition)
                // move the grid with dy = 0, so a running sum of dy drifts away from the truth.
                scrollY = gridOffset() ?: (scrollY + dy)
                val off = scrollY.toFloat()
                // Keep the glass with the first row and sample the wallpaper at its current position.
                val expansion = (off / (rowPitch * 0.7f)).coerceIn(0f, 1f)
                dock.translationY = -off
                dockShadow?.translationY = -off
                icons.translationY = -off
                weatherCard.alpha = 1f - expansion
                // Off-screen controls must also leave remote-control focus navigation.
                icons.visibility = if (off > 0f && off >= icons.bottom) View.INVISIBLE else View.VISIBLE
                dock.alpha = 1f
                frost.alpha = expansion
                scrollGlass.expansion = expansion
                if (off > 0f) { dock.setImageDrawable(scrollGlass); scrollGlass.invalidateSelf() }
                else dock.setImageDrawable(if (prefs.extremeGlass) scrollGlass else dockShown)
                // The large clock gives way to the rows coming up under it.
                if (!shelfShown) bigClock.alpha = 1f - expansion
            }
        })
        scene.addView(grid, match())
        header.bringToFront()
        setContentView(root)
    }

    /** Called by the home-screen minute ticker only; never does network work while off. */
    private fun refreshWeather() {
        if (!prefs.weatherEnabled) {
            weatherCard.visibility = View.GONE
            lastWeatherAttempt = 0L
            return
        }
        weatherCard.visibility = View.VISIBLE
        val name = prefs.weatherCity
        if (name.isBlank()) {
            weatherCard.text = I18n.text(R.string.weather_weather_pending)
            return
        }
        val city = Weather.City(name, prefs.weatherLatitude, prefs.weatherLongitude)
        Weather.cached(this, city)?.let { weatherCard.text = Weather.format(it, prefs.weatherFahrenheit) }
            ?: run { weatherCard.text = I18n.text(R.string.weather_weather_unavailable) }
        val now = System.currentTimeMillis()
        if (weatherLoading || now - lastWeatherAttempt < 2 * 60_000L) return
        weatherLoading = true
        lastWeatherAttempt = now
        io.execute {
            val observed = Weather.load(applicationContext, city)
            main.post {
                weatherLoading = false
                if (!isDestroyed && prefs.weatherEnabled && prefs.weatherCity == city.name) {
                    weatherCard.text = observed?.let { Weather.format(it, prefs.weatherFahrenheit) }
                        ?: I18n.text(R.string.weather_weather_unavailable)
                }
            }
        }
    }

    /**
     * Shows the gradient theme, or the photo for the current rotation slot (cross-fading when it
     * changes). Called every minute; the next photo is downloaded only when its turn comes.
     */
    private fun applyBackground() {
        val theme = prefs.background
        if (theme in VideoWallpaper.SOURCES) {
            showBackground("graphite", null)
            if (resumed && intro == null && welcome == null && getSystemService(android.os.PowerManager::class.java).isInteractive) {
                wallpaperPlaying = true
                videoWallpaper.start(theme)
            } else {
                wallpaperPlaying = false
                videoWallpaper.stop()
            }
            syncOwnPlayback()
            return
        }
        wallpaperPlaying = false
        videoWallpaper.stop()
        syncOwnPlayback()
        if (!Wallpapers.isPhoto(theme)) return showBackground(theme, null)
        if (refreshing) return
        refreshing = true
        io.execute {
            val walls = Wallpapers(this, Wallpapers.sourceFor(theme))
            runCatching { walls.refresh(); walls.advance(prefs.wallMinutes) }
            val photo = walls.current()
            refreshing = false
            main.post { if (!isDestroyed && prefs.background == theme) showBackground(theme, photo) }
        }
    }

    private fun showBackground(theme: String, photo: java.io.File?) {
        val key = if (Wallpapers.isPhoto(theme)) "$theme:${photo?.name}" else theme
        if (key == shownBackground) return
        shownBackground = key
        val modernDock = prefs.extremeGlass || DockGlassFallback.usesModernPipeline(Build.VERSION.SDK_INT)
        val lowEnd = Device.isLowEnd(this)
        val videoTheme = prefs.background in VideoWallpaper.SOURCES
        io.execute {
            val bmp = photo?.let { Wallpapers(this, Wallpapers.sourceFor(theme)).forHome(it, clearDock = prefs.extremeGlass) }
                ?: Backgrounds.render(if (Wallpapers.isPhoto(theme)) "green" else theme,
                    bottomShade = if (prefs.extremeGlass) 0.55f else 1f)
            // API 31+ is the existing full-resolution bake. Older TVs composite a
            // small software blur into a full-size mask. Video starts as a light
            // veil until a snapshot is ready; the TextureView keeps playing under it.
            val glass = (if (prefs.extremeGlass) bmp else if (modernDock) {
                LiquidGlass.bake(bmp, sw, sh, dockRect, dockRadius, extreme = prefs.extremeGlass,
                    darkTheme = isNight(), tintScale = prefs.glassTint(isNight()), solid = prefs.solidGlass(this))
            } else if (videoTheme) {
                DockGlassFallback.frosted(dockRect.width(), dockRect.height(), dockRadius)
            } else {
                DockGlassFallback.fromBackdrop(bmp, sw, sh, dockRect, dockRadius, video = false, lowEnd = lowEnd)
            }).also { it.prepareToDraw() }
            val blurred = Wallpapers.blurred(bmp, darken = 0.22f)
            // Extreme glass refracts a prepared lens. Build it here, with the picture,
            // so the dock changes in the same frame as the wallpaper instead of a
            // second background job later.
            val lenses = if (prefs.extremeGlass) runCatching { FrostGlass.prepare(blurred, bmp, sw) }.getOrNull() else null
            main.post {
                if (isDestroyed || shownBackground != key) return@post
                // A video bake may have overtaken this initial static fallback.
                val live = prefs.background in VideoWallpaper.SOURCES && videoWallpaper.frame != null
                if (!live) homeDock = glass
                val old = bgPhoto
                if (!live) {
                    homeLenses = lenses
                    frost.setImageBitmap(blurred)
                    scrollGlass.veil = !modernDock && videoTheme
                    scrollGlass.frostAlpha = 255
                    if (!scrollGlass.veil) {
                        // Same 1200 ms as the TransitionDrawable below, which the animator
                        // scale does not shorten either. Under a Top Shelf the dock keeps the
                        // shelf picture; leaving it applies [homeLenses].
                        if (lenses != null) { if (!shelfShown) scrollGlass.texture(lenses, if (old == null) 0L else 1200L) }
                        else scrollGlass.texture(blurred, bmp)
                    }
                    val fade = if (!modernDock && videoTheme) DockGlassFallback.FROST_FADE_MS else 1200
                    if (!shelfShown) setDock(glass, fade)
                }
                val next = android.graphics.drawable.BitmapDrawable(resources, bmp)
                if (old == null) {
                    bg.setImageDrawable(next)
                    bg.alpha = 0f
                    bg.animate().alpha(1f).setDuration(300).start()
                } else {
                    val fade = android.graphics.drawable.TransitionDrawable(arrayOf(old, next))
                    fade.isCrossFadeEnabled = true
                    bg.setImageDrawable(fade)
                    fade.startTransition(1200)
                    // Drop the old UHD bitmap reference after the fade. Otherwise the
                    // TransitionDrawable retains two full-size photos until the next rotation.
                    root.postDelayed({
                        if (!isDestroyed && bg.drawable === fade) bg.setImageDrawable(next)
                    }, 1250)
                }
                bgPhoto = next
                root.postDelayed({ if (!isDestroyed) control.refreshGlass() }, 1250)
            }
            // After the home picture is posted: Settings then opens with its
            // backdrop and glass already built (no-op on low-end TVs).
            SettingsActivity.warm(applicationContext)
        }
    }

    /** Small video snapshots feed the existing glass renderers; never decode 4K bitmaps per frame. */
    private fun updateVideoGlass(frame: Bitmap?) {
        if (frame == null) {
            lastLegacyDockGlassMs = 0L
            videoGlassFrames.invalidate()
            // A pause/restart must rebuild fallback textures if no video frame returns.
            shownBackground = null
            if (!isDestroyed && resumed && prefs.background in VideoWallpaper.SOURCES) {
                showBackground("graphite", null)
                control.refreshGlass()
            }
            return
        }
        if (prefs.extremeGlass) {
            if (isDestroyed || !resumed || shelfShown) return
            homeDock = frame
            scrollGlass.liveTexture(frame)
            if (dock.drawable !== scrollGlass) dock.setImageDrawable(scrollGlass)
            control.liveFrame(frame)
            val now = SystemClock.uptimeMillis()
            if (scrollY > 0 && now - lastLiveFrostMs >= 100L) {
                val ticket = videoGlassFrames.begin() ?: return
                lastLiveFrostMs = now
                val source = prefs.background
                io.execute {
                    val blurred = Wallpapers.blurred(frame, darken = 0.22f)
                    main.post {
                        val current = videoGlassFrames.finish(ticket)
                        if (current && !isDestroyed && resumed && !shelfShown && prefs.background == source) {
                            frost.setImageBitmap(blurred)
                            scrollGlass.softTexture(blurred)
                        }
                    }
                }
            }
            return
        }
        val ticket = videoGlassFrames.begin() ?: return
        val source = prefs.background
        val modernDock = prefs.extremeGlass || DockGlassFallback.usesModernPipeline(Build.VERSION.SDK_INT)
        if (!modernDock && (videoWallpaper.frostSuspended || !DockGlassFallback.shouldRefreshVideo(
                SystemClock.uptimeMillis(), lastLegacyDockGlassMs, videoWallpaper.weakForFrost,
            ))
        ) {
            videoGlassFrames.finish(ticket)
            return
        }
        if (!modernDock) lastLegacyDockGlassMs = SystemClock.uptimeMillis()
        val lowEnd = Device.isLowEnd(this)
        io.execute {
            val result = runCatching {
                if (modernDock) {
                    // Match the bake to the captured frame's resolution. Rendering a
                    // larger bitmap only stretched pixels that the snapshot did not have.
                    val scale = minOf(0.5f, frame.width / sw.toFloat())
                    val rect = Rect((dockRect.left * scale).toInt(), (dockRect.top * scale).toInt(),
                        (dockRect.right * scale).toInt(), (dockRect.bottom * scale).toInt())
                    val renderW = (sw * scale).toInt().coerceAtLeast(1)
                    val renderH = (sh * scale).toInt().coerceAtLeast(1)
                    // Live video already provides motion and color; a lighter frost
                    // and partial opacity keep more of that detail visible while the
                    // asynchronous glass texture catches up with the video.
                    val glass = (if (prefs.extremeGlass) frame else LiquidGlass.bake(frame, renderW, renderH, rect, dockRadius * scale,
                        frostStrength = 0.3f, glassOpacity = 0.5f, extreme = prefs.extremeGlass,
                        darkTheme = isNight(), tintScale = prefs.glassTint(isNight()),
                        solid = prefs.solidGlass(this), renderScale = scale))
                        .also { it.prepareToDraw() }
                    glass to Wallpapers.blurred(frame, darken = 0.22f)
                } else {
                    // Full-size mask, about 55% opaque, over the live TextureView.
                    val glass = DockGlassFallback.fromBackdrop(
                        frame, sw, sh, dockRect, dockRadius, video = true, lowEnd = lowEnd,
                    )
                    glass to Wallpapers.blurred(frame, darken = 0.22f)
                }
            }
            main.post {
                val current = videoGlassFrames.finish(ticket)
                if (!current || isDestroyed || !resumed || prefs.background != source) return@post
                result.getOrNull()?.let { (glass, blurred) ->
                    homeDock = glass
                    if (!shelfShown) {
                        frost.setImageBitmap(blurred)
                        if (modernDock) {
                            scrollGlass.veil = false
                            scrollGlass.frostAlpha = 255
                            scrollGlass.texture(blurred, frame)
                            // Periodic live frames replace directly; still photos retain their cross-fade.
                            setDock(glass, 0)
                        } else if (!videoWallpaper.frostSuspended) {
                            scrollGlass.veil = false
                            scrollGlass.frostAlpha = DockGlassFallback.VIDEO_FROST_ALPHA
                            scrollGlass.texture(blurred, frame)
                            setDock(glass, DockGlassFallback.FROST_FADE_MS)
                        }
                        control.refreshGlass()
                    }
                }
            }
        }
    }

    /** Pre-31 video whose snapshot was too slow: a light veil and rim, no frost. */
    private fun showVideoVeil() {
        if (isDestroyed || !::scrollGlass.isInitialized) return
        if (DockGlassFallback.usesModernPipeline(Build.VERSION.SDK_INT)) return
        scrollGlass.veil = true
        scrollGlass.frostAlpha = 255
        val w = dockRect.width().coerceAtLeast(1)
        val h = dockRect.height().coerceAtLeast(1)
        val radius = dockRadius
        io.execute {
            val veil = DockGlassFallback.frosted(w, h, radius)
            main.post {
                if (!isDestroyed && videoWallpaper.frostSuspended && !shelfShown) {
                    setDock(veil, DockGlassFallback.FROST_FADE_MS)
                }
            }
        }
    }

    private fun setDock(b: Bitmap, fadeMs: Int) {
        if (prefs.extremeGlass) {
            dock.setImageDrawable(scrollGlass)
            dockShown = scrollGlass
            return
        }
        val next = BitmapDrawable(resources, b)
        val old = dockShown
        if (old == null || fadeMs == 0) {
            dock.setImageDrawable(next)
        } else {
            val fade = TransitionDrawable(arrayOf(old, next)).apply { isCrossFadeEnabled = true }
            dock.setImageDrawable(fade)
            fade.startTransition(fadeMs)
        }
        dockShown = next
        if (scrollY > 0) dock.setImageDrawable(scrollGlass)
    }

    /** The Dock's depth shadow is only allocated for people who opt into extreme glass. */
    private fun isNight() = (resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun updateDockShadow(on: Boolean) {
        // Dock glass (and its depth shadow) are not shown; tiles float on the wallpaper.
        dockShadow?.visibility = View.GONE
    }

    // ------------------------------------------------------------------- shelf

    private fun buildShelf(side: Int) {
        shelf = FrameLayout(this).apply { alpha = 0f; visibility = View.INVISIBLE }
        shelfPics = FrameLayout(this)
        shelf.addView(shelfPics, FrameLayout.LayoutParams(-1, -1))
        val shadow = { t: TextView -> t.setShadowLayer(dp(10).toFloat(), 0f, dp(1).toFloat(), Color.argb(150, 0, 0, 0)) }
        shelfSource = text(16f, Color.argb(200, 255, 255, 255), weight = 600).also(shadow)
        shelfTitle = text(38f, weight = 700).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.08f)
        }.also(shadow)
        shelfMeta = text(17f, Color.argb(210, 255, 255, 255), weight = 500).apply { maxLines = 1 }.also(shadow)
        shelfHint = text(14f, Color.argb(205, 255, 255, 255), weight = 500).apply {
            visibility = View.GONE
        }.also(shadow)
        shelfInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(shelfSource)
            addView(shelfTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(shelfMeta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
            addView(shelfHint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(13) })
        }
        val above = sh - dockRect.top + dp(30)
        shelf.addView(shelfInfo, FrameLayout.LayoutParams((sw * 0.56f).toInt(), -2, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = side; bottomMargin = above
        })
        shelfDots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        shelf.addView(shelfDots, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = side; bottomMargin = above + dp(4)
        })
        backdrop.addView(shelf, FrameLayout.LayoutParams(-1, -1))
    }

    private fun shelfCustom() = Shelf.Custom(prefs.shelfCustomPkg, prefs.shelfCustomUrl)

    /** A delayed network response can never take over a newly focused Dock app. */
    private fun showShelf(pkg: String) {
        shelfPkg = pkg
        val session = ++shelfGeneration
        ++shelfImageRequest
        val custom = shelfCustom()
        net.execute {
            val items = Shelf.items(pkg, custom)
            val first = items.firstOrNull()?.let { Shelf.image(this, it.image) }
            val glass = first?.let { shelfGlass(it) }
            main.post {
                if (session != shelfGeneration || shelfPkg != pkg || !resumed ||
                    isDestroyed || control.isOpen || first == null || glass == null) return@post
                shelfItems = items
                shelfIndex = 0
                shelfPendingIndex = 0
                shelfBrowsing = false
                shelfSource.text = Shelf.sourceName(pkg)
                shelfDots.removeAllViews()
                repeat(items.size) {
                    shelfDots.addView(View(this).apply { background = rounded(Color.WHITE, dp(4).toFloat()) },
                        LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginStart = dp(7) })
                }
                showShelfItem(0, first, glass)
                setShelfVisible(true)
                updateShelfHint()
                resumeShelfRotation()
            }
        }
    }

    /** Image preparation happens on the network pool; focus and user requests win over stale work. */
    private fun requestShelfIndex(index: Int) {
        val pkg = shelfPkg ?: return
        if (!shelfShown || index !in shelfItems.indices) return
        val session = shelfGeneration
        val request = ++shelfImageRequest
        val item = shelfItems[index]
        shelfPendingIndex = index
        main.removeCallbacks(shelfAdvance)
        // Keep a retry timer even if a superseded request is dropped by the bounded queue.
        if (!shelfBrowsing) main.postDelayed(shelfAdvance, (SHELF_SECONDS + 2) * 1000L)
        net.execute {
            val bitmap = Shelf.image(this, item.image)
            val glass = bitmap?.let { shelfGlass(it) }
            main.post {
                if (!ShelfNavigation.accepts(session, shelfGeneration, request, shelfImageRequest,
                        pkg, shelfPkg, shelfShown && resumed && !isDestroyed, control.isOpen)) return@post
                if (bitmap != null && glass != null) showShelfItem(index, bitmap, glass)
                else shelfPendingIndex = shelfIndex
                if (!shelfBrowsing) resumeShelfRotation()
            }
        }
    }

    private fun updateShelfHint() {
        shelfHint.visibility = if (shelfShown && shelfItems.size > 1) View.VISIBLE else View.GONE
        shelfHint.text = getString(if (shelfBrowsing)
            R.string.tvos_shelf_select_hint else R.string.tvos_shelf_browse_hint)
    }

    /** Up enters the featured-content strip; left/right browse; OK opens; down/back returns. */
    private fun shelfKey(event: KeyEvent): Boolean {
        val app = adapter.apps.getOrNull(focusedPos) ?: return false
        if (!shelfShown || shelfItems.size < 2 || shelfPkg != app.pkg ||
            focusedPos >= cols || !grid.hasFocus() || control.isOpen || moving >= 0 || sheets.isNotEmpty()) return false
        val key = event.keyCode
        if (!shelfBrowsing) {
            if (key != KeyEvent.KEYCODE_DPAD_UP) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                shelfBrowsing = true
                shelfPendingIndex = shelfIndex
                ++shelfImageRequest
                main.removeCallbacks(shelfAdvance)
                updateShelfHint()
            }
            return true
        }
        when (key) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val delta = if (key == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    // An incoming request supersedes its predecessor; rapid presses remain responsive.
                    val next = ShelfNavigation.step(shelfPendingIndex, shelfItems.size, delta)
                    if (next >= 0) requestShelfIndex(next)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    shelfBrowsing = false
                    shelfPendingIndex = shelfIndex
                    shelfConsumeExitKeyUp = key
                    ++shelfImageRequest
                    updateShelfHint()
                    resumeShelfRotation()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    val intent = shelfDeepLink(app) ?: repo.launchIntent(app)
                    if (intent != null) try {
                        startActivity(intent)
                        noteExternalLaunch(app.pkg)
                        RecentApps.record(this, app.pkg)
                        rememberOpened(app.pkg)
                    } catch (_: Exception) {
                        Toast.makeText(this,
                            I18n.text(R.string.i18n_homeactivity_005, app.label), Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
        }
        return false
    }

    /** A Top Shelf picture's dock glass, made with the picture on its loader thread. */
    private class ShelfGlass(val dock: Bitmap?, val lenses: FrostGlass.Lenses?)

    private fun shelfGlass(b: Bitmap): ShelfGlass = runCatching {
        // The picture already carries the scrim (see Shelf.darken).
        // API 31+ keeps the original bake. Older TVs use the full-size mask.
        if (prefs.extremeGlass) ShelfGlass(b, FrostGlass.prepare(b, b, sw))
        else ShelfGlass((if (DockGlassFallback.usesModernPipeline(Build.VERSION.SDK_INT)) {
            LiquidGlass.bake(b, sw, sh, dockRect, dockRadius, extreme = false,
                darkTheme = isNight(), tintScale = prefs.glassTint(isNight()), solid = prefs.solidGlass(this))
        } else {
            DockGlassFallback.fromBackdrop(
                b, sw, sh, dockRect, dockRadius, video = false, lowEnd = Device.isLowEnd(this@HomeActivity),
            )
        }).also { it.prepareToDraw() }, null)
    }.getOrElse { ShelfGlass(null, null) }  // The picture still shows; the dock keeps its glass.

    private fun showShelfItem(i: Int, b: Bitmap, glass: ShelfGlass) {
        val item = shelfItems.getOrNull(i) ?: return
        shelfIndex = i
        shelfPendingIndex = i
        // The new picture fades in over the old one, settling from a slight zoom.
        val old = shelfFront
        // Interrupted cross-fades must not accumulate off-screen ImageViews or bitmap references.
        for (j in shelfPics.childCount - 1 downTo 0) {
            val view = shelfPics.getChildAt(j)
            if (view !== old) {
                view.animate().cancel()
                shelfPics.removeViewAt(j)
            }
        }
        old?.animate()?.cancel()
        val pic = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(b) }
        shelfPics.addView(pic, FrameLayout.LayoutParams(-1, -1))
        shelfFront = pic
        pic.alpha = 0f
        pic.scaleX = 1.04f; pic.scaleY = 1.04f
        pic.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(if (old == null) 0 else 700).setInterpolator(Motion.ease)
            .withEndAction {
                if (old != null) shelfPics.removeView(old)
                if (old != null) control.refreshGlass()
            }.start()
        shelfInfo.animate().cancel()
        shelfInfo.alpha = 0f
        shelfInfo.translationY = dp(10).toFloat()
        shelfTitle.text = item.title
        shelfMeta.text = item.meta
        shelfInfo.animate().alpha(1f).translationY(0f).setDuration(450).setStartDelay(150).start()
        for (k in 0 until shelfDots.childCount) shelfDots.getChildAt(k).alpha = if (k == i) 1f else 0.35f
        // The dock refracts the new picture from this frame on, fading with it: the
        // shelf itself fades in over 500 ms the first time, later pictures over 700 ms.
        val lenses = glass.lenses
        if (lenses != null && prefs.extremeGlass) {
            val ms = ((if (old == null) 500 else 700) * Tween.scale()).toLong()
            scrollGlass.texture(lenses, ms, if (old == null) android.view.animation.AccelerateDecelerateInterpolator() else Motion.ease)
        }
        glass.dock?.let { setDock(it, 600) }
        // Fetch the next picture ahead of time.
        shelfItems.getOrNull((i + 1) % shelfItems.size)?.let { n -> net.execute { Shelf.image(this, n.image) } }
    }

    private fun setShelfVisible(v: Boolean) {
        if (v == shelfShown) return
        shelfShown = v
        if (v) shelf.visibility = View.VISIBLE else bg.visibility = View.VISIBLE
        shelf.animate().cancel()
        // The large clock gives way to the shelf.
        bigClock.animate().alpha(if (v) 0f else dock.alpha).setDuration(350).start()
        shelf.animate().alpha(if (v) 1f else 0f).setDuration(if (v) 500 else 350)
            .withEndAction {
                // The shelf covers the whole screen: stop drawing the photo underneath it.
                if (shelfShown) bg.visibility = View.INVISIBLE
                if (!shelfShown) {
                    shelf.visibility = View.INVISIBLE
                    shelfPics.removeAllViews()
                    shelfFront = null
                }
                if (!isDestroyed) control.refreshGlass()
            }.start()
        if (!v) {
            if (prefs.extremeGlass) {
                val video = videoWallpaper.frame
                val lenses = homeLenses
                if (video == null && lenses != null) {
                    // Already prepared: back in step with the shelf's 350 ms fade-out.
                    scrollGlass.texture(lenses, (350 * Tween.scale()).toLong(),
                        android.view.animation.AccelerateDecelerateInterpolator())
                } else {
                    val image = video ?: (bgPhoto as? BitmapDrawable)?.bitmap
                    val soft = (frost.drawable as? BitmapDrawable)?.bitmap
                    if (image != null && soft != null) scrollGlass.texture(soft, image)
                }
            }
            homeDock?.let { setDock(it, 450) }
        }
    }

    private fun hideShelf() {
        ++shelfGeneration
        ++shelfImageRequest
        shelfBrowsing = false
        main.removeCallbacks(shelfShow)
        main.removeCallbacks(shelfAdvance)
        pendingShelf = null
        shelfPkg = null
        shelfItems = emptyList()
        shelfPendingIndex = 0
        setShelfVisible(false)
        shelfHint.visibility = View.GONE
    }

    fun pauseShelfRotation() {
        main.removeCallbacks(shelfShow)
        main.removeCallbacks(shelfAdvance)
    }

    fun resumeShelfRotation() {
        if (shelfShown && !shelfBrowsing && shelfItems.size > 1) {
            main.removeCallbacks(shelfAdvance)
            main.postDelayed(shelfAdvance, SHELF_SECONDS * 1000L)
        }
    }

    private fun updateClock() {
        val now = Date()
        clockText.text = SimpleDateFormat(if (prefs.clock24) "HH:mm" else "h:mm a", Locale.getDefault()).format(now)
        bigDate.text = SimpleDateFormat(I18n.text(R.string.i18n_homeactivity_004), Locale.getDefault()).format(now)
        // The large clock leaves out AM/PM, as tvOS did; at that size it reads as the time of day.
        bigTime.text = SimpleDateFormat(if (prefs.clock24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    }

    private fun applyClockStyle() {
        val style = prefs.clockStyle
        clockText.visibility = if (style == Prefs.CLOCK_CORNER) View.VISIBLE else View.GONE
        bigClock.visibility = if (style == Prefs.CLOCK_LARGE) View.VISIBLE else View.GONE
        updateClock()
    }

    /** The home scene alone, without the control center or any dialog; the glass controls sample it. */
    val sceneView: View get() = scene
    internal fun glassVideoFrame(): Bitmap? = if (shelfShown) null else videoWallpaper.frame

    /** An opaque wallpaper base prevents transitional scene alpha from baking black into glass. */
    fun drawGlassScene(canvas: Canvas): Boolean {
        val bitmap = videoWallpaper.frame ?: (bgPhoto as? BitmapDrawable)?.bitmap ?: return false
        val scale = maxOf(scene.width.toFloat() / bitmap.width, scene.height.toFloat() / bitmap.height)
        val left = (scene.width - bitmap.width * scale) / 2f
        val top = (scene.height - bitmap.height * scale) / 2f
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale),
            Paint(Paint.FILTER_BITMAP_FLAG))
        scene.draw(canvas)
        return true
    }

    private fun updateNetworkIcon() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (e: SecurityException) { null }
        val wired = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        netButton.setIcon(if (wired) R.drawable.ic_ethernet else R.drawable.ic_wifi)
        netButton.alpha = if (caps == null) 0.4f else 1f
    }

    /** Pins a TV input to the grid, or takes it off. */
    fun pinInput(i: Input, on: Boolean) {
        val enc = InputTile.encode(i)
        prefs.pinnedInputs = prefs.pinnedInputs.filter { InputTile.decode(it)?.uri != i.uri }.toSet() + if (on) setOf(enc) else emptySet()
        if (on) prefs.hidden = prefs.hidden - InputTile.key(i)
        reload()
    }

    fun isPinned(i: Input) = prefs.pinnedInputs.any { InputTile.decode(it)?.uri == i.uri }

    private fun reload(changed: String? = null, forceFocus: Boolean = false) {
        io.execute {
            val list = repo.orderedVisible()
            main.post {
                val focusedPkg = adapter.apps.getOrNull(focusedPos)?.pkg
                adapter.submit(list, changed)
                val idx = list.indexOfFirst { it.pkg == focusedPkg }.coerceAtLeast(0)
                grid.post {
                    // On first show the header buttons grab default focus; the grid should have it.
                    val headerOk = header.hasFocus() && !firstFocus
                    if ((forceFocus || (!grid.hasFocus() && !headerOk)) && sheets.isEmpty() && !control.isOpen) focusAt(idx)
                    firstFocus = false
                    // A new list can leave the grid between rows; put the focused row back where it belongs.
                    grid.post { grid.focusedChild?.let { grid.layoutManager?.requestChildRectangleOnScreen(grid, it, Rect(), false, true) } }
                }
            }
        }
    }

    /** How far the grid is scrolled from its resting place, worked out from the first laid-out tile. */
    private fun gridOffset(): Int? {
        val lm = grid.layoutManager ?: return null
        val child = grid.getChildAt(0) ?: return null
        val p = grid.getChildAdapterPosition(child).takeIf { it >= 0 } ?: return null
        return firstRowY + (p / cols) * rowPitch - lm.getDecoratedTop(child)
    }

    private fun focusAt(pos: Int) {
        if (welcome != null || adapter.itemCount == 0) return
        val p = pos.coerceIn(0, adapter.itemCount - 1)
        val vh = grid.findViewHolderForAdapterPosition(p)
        if (vh != null) vh.itemView.requestFocus() else {
            grid.scrollToPosition(p)
            grid.post { grid.findViewHolderForAdapterPosition(p)?.itemView?.requestFocus() }
        }
    }

    // ------------------------------------------------------------------- tiles

    override fun onTileFocused(pos: Int, cell: TileCell) {
        focusedPos = pos
        val pkg = adapter.apps.getOrNull(pos)?.pkg
        if (shelfBrowsing && pkg != shelfPkg) shelfBrowsing = false
        main.removeCallbacks(shelfShow)
        if (pos < cols && pkg != null && Shelf.has(pkg, shelfCustom()) && moving < 0) {
            if (pkg != shelfPkg || !shelfShown) {
                if (shelfShown) hideShelf()
                pendingShelf = pkg
                // Wait for focus to settle so sliding along the dock does not flicker.
                main.postDelayed(shelfShow, 300)
            }
        } else hideShelf()
    }

    override fun onTileClicked(pos: Int, cell: TileCell) {
        if (moving >= 0) { endMove(); return }
        val app = adapter.apps.getOrNull(pos) ?: return
        app.folder?.let { openFolder(it); return }
        app.input?.let { i -> io.execute { tv.setInput(i) }; return }
        launchTileApp(app, pos, cell)
    }

    private fun launchTileApp(app: AppEntry, pos: Int, cell: TileCell) {
        // The stock launcher (Xiaomi's PatchWall) can only be opened through the system Home action.
        if (HomeGuardService.isStockHome(app.pkg) && HomeGuardService.openStockLauncher()) {
            rememberOpened(app.pkg)
            return
        }
        val intent = shelfDeepLink(app) ?: repo.launchIntent(app) ?: return
        // tvOS-style: the incoming app window grows out of the selected tile/icon.
        val opts = ActivityOptions.makeScaleUpAnimation(cell.card, 0, 0, cell.card.width, cell.card.height)
        try {
            startActivity(intent, opts.toBundle())
            noteExternalLaunch(app.pkg)
            RecentApps.record(this, app.pkg)
            rememberOpened(app.pkg)
        } catch (e: Exception) {
            Toast.makeText(this, I18n.text(R.string.i18n_homeactivity_005, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * A user-supplied Top Shelf link may deep-link to the focused app, but only when
     * that *same package* resolves an HTTPS view intent. Never hand an untrusted
     * feed URL to a browser or an arbitrary third-party application.
     */
    private fun shelfDeepLink(app: AppEntry): Intent? {
        if (!shelfShown || shelfPkg != app.pkg || shelfIndex < 0) return null
        val link = shelfItems.getOrNull(shelfIndex)?.url ?: return null
        if (!link.startsWith("https://", ignoreCase = true) || !ShelfLimits.isHttpUrl(link)) return null
        val candidate = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(app.pkg)
        return candidate.takeIf { packageManager.resolveActivity(it, 0) != null }
    }

    /** A disabled recent-first preference preserves manual order, including folders. */
    private fun rememberOpened(pkg: String) {
        if (!prefs.recentFirst) return
        val visible = adapter.apps.map { it.pkg }
        prefs.order = RecentOrder.afterLaunch(pkg, visible, prefs.order, prefs.recentFirst)
        reload()
    }

    override fun onTileLongPressed(pos: Int, cell: TileCell) {
        if (moving >= 0) return
        val app = adapter.apps.getOrNull(pos) ?: return
        app.folder?.let { manageFolder(it, pos); return }
        app.input?.let { i ->
            showSheet(Sheet(root).title(app.label).message(null)
                .button(I18n.text(R.string.i18n_homeactivity_006)) { startMove(pos) }
                .button(I18n.text(R.string.i18n_homeactivity_007)) { pinInput(i, false) }
                .button(I18n.text(R.string.i18n_homeactivity_008)) {}, restoreFocusTo = pos)
            return
        }
        val s = Sheet(root).title(app.label).message(null)
            .button(I18n.text(R.string.i18n_homeactivity_009)) { startMove(pos) }
            .button(I18n.text(R.string.i18n_homeactivity_010)) { chooseFolder(app) }
            .button(I18n.text(R.string.i18n_homeactivity_011)) {
                prefs.hidden = prefs.hidden + app.pkg
                reload()
            }
        if (repo.isOwn(app.pkg)) {
            showSheet(s.button(I18n.text(R.string.i18n_homeactivity_012)) {}, restoreFocusTo = pos)
            return
        }
        s.button(getString(R.string.tile_source_choose)) {
            val modes = arrayOf(Prefs.TILE_AUTO, Prefs.TILE_BANNER, Prefs.TILE_ICON, Prefs.TILE_CLASSIC)
            val titles = arrayOf(R.string.tile_source_auto, R.string.tile_source_banner,
                R.string.tile_source_icon, R.string.tile_source_classic).map { getString(it) }.toTypedArray()
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.tile_source_choose) + " · " + app.label)
                .setSingleChoiceItems(titles, modes.indexOf(prefs.appTileSource(app.pkg)).coerceAtLeast(0)) { dialog, index ->
                    prefs.setAppTileSource(app.pkg, modes[index])
                    repo.invalidate(app.pkg)
                    reload(changed = app.pkg, forceFocus = true)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        s.button(I18n.text(R.string.i18n_homeactivity_013)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        if (!repo.isEssential(app.pkg)) s.button(I18n.text(R.string.i18n_homeactivity_014)) { confirmDelete(app, pos) }
        s.button(I18n.text(R.string.i18n_homeactivity_015)) {}
        showSheet(s, restoreFocusTo = pos)
    }

    /**
     * Deletes through local adb, which also removes preinstalled apps the system
     * won't let go of ("pm uninstall --user 0": the APK stays on the system
     * partition, so it can be restored from settings).
     */
    private fun foldersChanged() { repo.invalidate(); reload(forceFocus = true) }

    private fun folderName(initial: String, save: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            setSingleLine(); setText(initial); selectAll()
            filters = arrayOf(android.text.InputFilter.LengthFilter(30))
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(I18n.text(R.string.i18n_homeactivity_016)).setView(input)
            .setPositiveButton(I18n.text(R.string.i18n_homeactivity_017), null).setNegativeButton(I18n.text(R.string.i18n_homeactivity_018), null).create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) input.error = I18n.text(R.string.i18n_homeactivity_019)
                else { save(name); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun chooseFolder(app: AppEntry) {
        val store = Folders(this)
        val folders = store.read()
        android.app.AlertDialog.Builder(this).setTitle(I18n.text(R.string.i18n_homeactivity_020))
            .setItems((listOf(I18n.text(R.string.i18n_homeactivity_021)) + folders.map { it.name }).toTypedArray()) { _, index ->
                if (index == 0) folderName(I18n.text(R.string.i18n_homeactivity_022)) { name ->
                    val folder = store.create(name, app.pkg)
                    val order = adapter.apps.map { if (it.pkg == app.pkg) folder.id else it.pkg }
                    prefs.order = (order + prefs.order).distinct()
                    foldersChanged()
                } else { store.move(app.pkg, folders[index - 1].id); foldersChanged() }
            }.setNegativeButton(I18n.text(R.string.i18n_homeactivity_023), null).show()
    }

    private fun openFolder(folder: AppFolder) {
        val all = repo.allApps().associateBy { it.pkg }
        val apps = folder.members.filter { it !in prefs.hidden }.mapNotNull { all[it] }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(folder.name)
            .setItems(apps.map { it.label }.toTypedArray()) { _, index ->
                val app = apps[index]
                try {
                    val intent = repo.launchIntent(app)
                    if (intent != null) {
                        if (!HomeGuardService.isStockHome(app.pkg) || !HomeGuardService.openStockLauncher()) {
                            startActivity(intent)
                            noteExternalLaunch(app.pkg)
                        }
                        RecentApps.record(this, app.pkg)
                        rememberOpened(folder.id)
                    }
                } catch (_: Exception) { Toast.makeText(this, I18n.text(R.string.i18n_homeactivity_024, app.label), Toast.LENGTH_SHORT).show() }
            }.setNeutralButton(I18n.text(R.string.i18n_homeactivity_025)) { _, _ -> manageFolder(folder, focusedPos) }
            .setNegativeButton(I18n.text(R.string.i18n_homeactivity_026), null).create()
        if (apps.isEmpty()) dialog.setMessage(I18n.text(R.string.i18n_homeactivity_027))
        dialog.show()
    }

    private fun manageFolder(folder: AppFolder, pos: Int) {
        val store = Folders(this)
        android.app.AlertDialog.Builder(this).setTitle(folder.name)
            .setItems(arrayOf(I18n.text(R.string.i18n_homeactivity_028), I18n.text(R.string.i18n_homeactivity_029), I18n.text(R.string.i18n_homeactivity_030), I18n.text(R.string.i18n_homeactivity_031))) { _, index ->
                when (index) {
                    0 -> folderName(folder.name) { name ->
                        store.save(store.read().map { if (it.id == folder.id) it.copy(name = name) else it })
                        foldersChanged()
                    }
                    1 -> {
                        val all = repo.allApps().associateBy { it.pkg }
                        val members = store.read().find { it.id == folder.id }?.members.orEmpty()
                        android.app.AlertDialog.Builder(this).setTitle(I18n.text(R.string.i18n_homeactivity_032))
                            .setItems(members.map { all[it]?.label ?: it }.toTypedArray()) { _, i ->
                                store.move(members[i], null); foldersChanged()
                            }.setNegativeButton(I18n.text(R.string.i18n_homeactivity_033), null).show()
                    }
                    2 -> startMove(pos)
                    3 -> android.app.AlertDialog.Builder(this).setTitle(I18n.text(R.string.i18n_homeactivity_034, folder.name))
                        .setMessage(I18n.text(R.string.i18n_homeactivity_035))
                        .setPositiveButton(I18n.text(R.string.i18n_homeactivity_036)) { _, _ ->
                            store.save(store.read().filter { it.id != folder.id }); foldersChanged()
                        }.setNegativeButton(I18n.text(R.string.i18n_homeactivity_037), null).show()
                }
            }.setNegativeButton(I18n.text(R.string.i18n_homeactivity_038), null).show()
    }

    private fun confirmDelete(app: AppEntry, pos: Int) {
        val system = repo.isSystem(app.pkg)
        if (!prefs.adbAuthorized) {
            if (!system) {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            showSheet(Sheet(root).title(I18n.text(R.string.i18n_homeactivity_039))
                .message(I18n.text(R.string.i18n_homeactivity_040))
                .button(I18n.text(R.string.i18n_homeactivity_041)) { authorizeAdb() }
                .button(I18n.text(R.string.i18n_homeactivity_042)) {}, restoreFocusTo = pos)
            return
        }
        val msg = when {
            system -> I18n.text(R.string.i18n_homeactivity_043)
            repo.isLauncher(app.pkg) -> I18n.text(R.string.i18n_homeactivity_044)
            else -> I18n.text(R.string.i18n_homeactivity_045)
        }
        showSheet(Sheet(root).title(I18n.text(R.string.i18n_homeactivity_046, app.label)).message(msg)
            .button(I18n.text(R.string.i18n_homeactivity_047)) {
                io.execute {
                    val out = LocalAdb.shell(this, if (system) "pm uninstall -k --user 0 ${app.pkg}" else "pm uninstall ${app.pkg}", 15_000)
                    val ok = out?.contains("Success") == true
                    main.post {
                        if (ok) {
                            if (system) prefs.removed = prefs.removed + app.pkg
                            reload()
                        } else Toast.makeText(this, I18n.text(R.string.i18n_homeactivity_057, out?.trim()?.take(80) ?: I18n.text(R.string.i18n_homeactivity_058)), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .button(I18n.text(R.string.i18n_homeactivity_048)) {}, restoreFocusTo = pos)
    }

    override fun onTileKey(pos: Int, cell: TileCell, keyCode: Int): Boolean {
        val (dx, dy) = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            else -> return false
        }
        adapter.lastDx = dx
        adapter.lastDy = dy
        val n = adapter.itemCount
        val col = pos % cols
        val target = pos + dx + dy * cols
        val lastRow = (n - 1) / cols
        val atEdge = (dx < 0 && col == 0) || (dx > 0 && (col == cols - 1 || pos == n - 1)) ||
            (dy < 0 && pos < cols) || (dy > 0 && pos / cols == lastRow)

        if (moving >= 0) {
            if (!atEdge) {
                val to = target.coerceIn(0, n - 1)
                adapter.move(pos, to)
                moving = to
                grid.post { focusAt(to) }
            } else Motion.bump(cell.card, dx, dy)
            return true
        }
        if (dy < 0 && pos < cols) {
            // Up from the first row reaches the buttons in the top-right corner.
            ccButton.requestFocus()
            return true
        }
        if (atEdge) {
            Motion.bump(cell.card, dx, dy)
            return true
        }
        if (dy > 0 && target >= n) {
            // Partial last row: drop to its last tile like tvOS does.
            focusAt(n - 1)
            return true
        }
        return false
    }

    private fun startMove(pos: Int) {
        moving = pos
        adapter.movingPkg = adapter.apps[pos].pkg
        adapter.notifyItemChanged(pos)
        grid.post { focusAt(pos) }
        Toast.makeText(this, I18n.text(R.string.i18n_homeactivity_049), Toast.LENGTH_SHORT).show()
    }

    private fun endMove() {
        val pos = moving
        moving = -1
        adapter.movingPkg = null
        adapter.notifyItemChanged(pos)
        prefs.order = adapter.apps.map { it.pkg }
        grid.post { focusAt(pos) }
    }

    // ------------------------------------------------------------ overlays / keys

    fun showSheet(s: Sheet, restoreFocusTo: Int? = null): Sheet {
        sheets += s
        setGridBlocked(true)
        s.onDismiss {
            sheets -= s
            if (sheets.isEmpty() && control.isOpen) control.refocus()
            else if (sheets.isEmpty()) {
                setGridBlocked(false)
                focusAt(restoreFocusTo ?: focusedPos)
            }
        }
        return s.show()
    }

    /** Keeps D-pad focus from wandering from an overlay onto the screen behind it. */
    fun setGridBlocked(blocked: Boolean) {
        val mode = if (blocked) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        grid.descendantFocusability = mode
        header.descendantFocusability = mode
    }

    fun restoreGridFocus() {
        setGridBlocked(false)
        focusAt(focusedPos)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        armIdle(resetDeadline = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (intro != null) BootLog.i("event=focus has=$hasFocus display=${BootIntroGate.displayLabel(displayState())}")
        intro?.onWindowFocus(hasFocus)
        val now = SystemClock.elapsedRealtime()
        HomeForeground.windowFocus(hasFocus, now)
        if (!resumed) return
        if (!hasFocus) cancelIdle()
        else {
            if (HomeForeground.isDreamBounce(now)) armIdle(resetDeadline = false)
            else armIdle(resetDeadline = true)
            grid.post { revealReturnFocus() }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        armIdle(resetDeadline = true)
        welcome?.let { return it.key(event) }
        // Any key skips the intro to its closing zoom; nothing behind it reacts meanwhile.
        intro?.let { if (event.action == KeyEvent.ACTION_UP) it.skip(); return true }
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && code == shelfConsumeExitKeyUp) {
            shelfConsumeExitKeyUp = -1
            return true
        }
        if (shelfKey(event)) return true
        // APP_SWITCH is only handled when delivered to this Activity. Android often owns
        // Home/TV key globally; we do not intercept global system buttons.
        if (code == KeyEvent.KEYCODE_APP_SWITCH) {
            if (event.action == KeyEvent.ACTION_UP) {
                AppSwitcherRouter.open(this)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT &&
            sheets.isEmpty() && control.isOpen && avatar.isShown && control.atRightEdge()) {
            control.close()
            avatar.requestFocus()
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU)) {
            when {
                sheets.isNotEmpty() -> sheets.last().dismiss()
                control.isOpen -> control.close()
                moving >= 0 -> endMove()
                header.hasFocus() -> focusAt(focusedPos)
                else -> control.open()
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Welcome.EXTRA_PREVIEW, false)) {
            intent.removeExtra(Welcome.EXTRA_PREVIEW)
            playWelcome(preview = true)
            return
        }
        if (welcome != null) return
        if (intent.getBooleanExtra(Intro.EXTRA_PREVIEW, false)) { playIntro(preview = true); return }
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) {
            outboundLaunch = null
            return // consume only the pending wake; never re-arm
        }
        // Home pressed while already home: close everything and go back to the top.
        sheets.toList().forEach { it.dismiss() }
        control.close()
        if (moving >= 0) endMove()
        // Guard-routed returns: a user Home press from a Tigris-launched app must still
        // restore the launched tile; a stock-launcher bounce has no outbound launch.
        if (intent.getBooleanExtra(EXTRA_GUARD, false)) {
            val nowGuard = SystemClock.uptimeMillis()
            if (HomeReturnPolicy.canPreserveFocus(outboundLaunch, nowGuard,
                    homeAlreadyVisible = resumed, guardReturn = false, wakeReturn = false)) {
                previousHomeIntentAt = nowGuard
                return
            }
            outboundLaunch = null
            return
        }
        val now = SystemClock.uptimeMillis()
        // A launch performed by the App Switcher cannot be observed here; adopt it once.
        if (outboundLaunch == null) outboundLaunch = HomeReturnHandoff.take()
        if (HomeReturnPolicy.canPreserveFocus(outboundLaunch, now,
                homeAlreadyVisible = resumed, guardReturn = false, wakeReturn = false)) {
            // A HOME press from an app should not snap the Dock to position zero.
            // Remember it so a quick second Home can still open the switcher; then let
            // onResume/onWindowFocusChanged restore and settle the launched card.
            previousHomeIntentAt = now
            return
        }
        outboundLaunch = null
        val doubleHome = prefs.doubleHomeSwitcher &&
            DoubleHomePolicy.second(previousHomeIntentAt, now, allowed = !Guard.isXiaomi())
        previousHomeIntentAt = if (doubleHome) 0L else now
        if (doubleHome) {
            AppSwitcherRouter.open(this)
            return
        }
        focusedPos = 0
        // Scroll by our own offset: smoothScrollToPosition aligns to the padding and leaves the grid off its resting place.
        grid.smoothScrollBy(0, -scrollY, Motion.ease, 360)
        grid.postDelayed({ focusAt(0) }, 400)
    }

    override fun onStart() {
        super.onStart()
        Guard.ensure(this)
        main.postDelayed({ Guard.rebindIfStalled(this) }, 5_000)
    }

    override fun onResume() {
        super.onResume()
        IdleTimerTakeover.noteHomeResumed()
        IdleTimerTakeover.sync(this)
        // Rebuild only after Settings changes column count; order/folders remain in Prefs.
        val expectedH = if (prefs.appTileAspect == Prefs.TILE_TVOS) tileW * 3 / 5 else tileW * 9 / 16
        if (cols != prefs.dockColumns || tileH != expectedH ||
            kotlin.math.abs(textScaleSeen - prefs.textScale) > 0.001f) { recreate(); return }
        if (tileStyleSeen != prefs.appTileStyle) {
            tileStyleSeen = prefs.appTileStyle
            repo.invalidate()
            focusedPos = 0
            reload(forceFocus = true)
        }
        val appearance = "${prefs.extremeGlass}:${prefs.glassTint(isNight())}:${prefs.solidGlass(this)}:${isNight()}"
        if (glassAppearance != appearance) {
            glassAppearance = appearance
            glassExtreme = prefs.extremeGlass
            updateDockShadow(glassExtreme)
            shownBackground = null
        }
        prefs.observe(wallpaperChanges)
        avatar.visibility = if (prefs.showAvatar) View.VISIBLE else View.GONE
        avatar.setPortrait(SettingsActivity.avatarFile(this)?.let { SettingsActivity.decodeIcon(it, 96) })
        adapter.tvosFocus = prefs.focusTvos(this)
        applyClockStyle()
        control.applyStyle()
        // Rebuilds a Settings scene dropped under memory pressure or made stale by
        // a settings change; returns at once when it is still current.
        val app = applicationContext
        io.execute { SettingsActivity.warm(app) }
        if (getSystemService(android.os.PowerManager::class.java).isInteractive) consumeWakeIntro()
        intro?.onWindowFocus(hasWindowFocus())
        resumed = true
        logPower("resume")
        welcome?.resume()
        prefs.homeInFront = true
        HomeGuardService.onHomeResumed()
        val now = SystemClock.elapsedRealtime()
        HomeForeground.resume(now)
        if (HomeForeground.isDreamBounce(now)) armIdle(resetDeadline = false)
        else armIdle(resetDeadline = true)
        // The minute ticker already applies the wallpaper. A second call here
        // would schedule redundant photo/video preparation during app return.
        updateNetworkIcon()
        main.removeCallbacks(ticker)
        ticker.run()
        control.refresh()
        if (shelfShown && !control.isOpen) resumeShelfRotation()
        // Adopt a launch made by the App Switcher when returning here without a HOME intent
        // (e.g. the user pressed Back inside the launched app).
        if (outboundLaunch == null) outboundLaunch = HomeReturnHandoff.take()
        if (outboundLaunch != null) grid.post { revealReturnFocus() }
    }

    override fun onPause() {
        logPower("pause")
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
        outboundLaunch = HomeReturnPolicy.markPaused(outboundLaunch, interactive)
        cancelReturnCue()
        HomeGuardService.onHomePaused()
        resumed = false
        prefs.unobserve(wallpaperChanges)
        wallpaperPlaying = false
        videoWallpaper.stop()
        // Leaving for another app (or standby) ends the intro rather than leaving it half played.
        // A not-yet-started clip on API 30- is kept: Sony pauses Home while the panel is still dark.
        if (intro?.isWaitingForDisplay() == true) BootLog.i("pause during display wait")
        else intro?.finish()
        syncOwnPlayback()
        welcome?.pause()
        if (!getSystemService(android.os.PowerManager::class.java).isInteractive) armWakeIntro()
        resumed = false
        prefs.homeInFront = false
        HomeForeground.pause(SystemClock.elapsedRealtime())
        cancelIdle()
        main.removeCallbacks(ticker)
        main.removeCallbacks(shelfAdvance)
        ++shelfImageRequest
        shelfBrowsing = false
        shelfPendingIndex = shelfIndex
        if (::shelfHint.isInitialized) updateShelfHint()
        super.onPause()
    }

    private fun askAdbAuthorization() {
        if (prefs.adbAuthorized || isFinishing || !resumed || welcome != null) return
        showSheet(Sheet(root).title(I18n.text(R.string.i18n_homeactivity_050))
            .message(I18n.text(R.string.i18n_homeactivity_051))
            .button(I18n.text(R.string.i18n_homeactivity_052)) { authorizeAdb() }
            .button(I18n.text(R.string.i18n_homeactivity_053)) {})
    }

    private var adbAuthorizing = false

    fun authorizeAdb() {
        if (adbAuthorizing) return
        adbAuthorizing = true
        Toast.makeText(this, I18n.text(R.string.i18n_homeactivity_054), Toast.LENGTH_LONG).show()
        io.execute {
            val result = LocalAdb.authorize(applicationContext)
            main.post {
                adbAuthorizing = false
                if (isFinishing || isDestroyed) return@post
                if (result.authorized) Toast.makeText(this, result.description(), Toast.LENGTH_LONG).show()
                else showSheet(Sheet(root).title(I18n.text(R.string.adb_failure_title)).message(result.description())
                    .button(I18n.text(R.string.adb_retry)) { authorizeAdb() }
                    .button(getString(android.R.string.cancel)) {})
                control.refresh()
            }
        }
    }
}

/**
 * Darkens the shelf picture where text and the dock sit: along the bottom, to
 * the left behind the title, and a little at the top behind the corner buttons.
 */
class ShelfScrim : Drawable() {
    private val p = Paint()

    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        p.shader = LinearGradient(0f, h * 0.3f, 0f, h, Color.TRANSPARENT, Color.argb(215, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = LinearGradient(0f, 0f, w * 0.62f, 0f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.2f, Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Round glyph button for the top-right corner; fills white when focused. */
class IconButton(ctx: Context, icon: Int, private val portrait: Boolean = false, onClick: () -> Unit) : FrameLayout(ctx) {
    private val img = ImageView(ctx).apply {
        setImageResource(icon)
        if (portrait) { scaleType = ImageView.ScaleType.CENTER_CROP; clipRounded(ctx.dp(12).toFloat()) }
    }
    private val disc = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
    }

    init {
        isFocusable = true
        isClickable = true
        background = disc
        val size = ctx.dp(if (portrait) 24 else 16)
        addView(img, LayoutParams(size, size, Gravity.CENTER))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { _, f ->
            disc.setColor(if (f) Color.WHITE else Color.TRANSPARENT)
            if (!portrait) img.setColorFilter(if (f) Color.BLACK else Color.WHITE)
            tweenScale(if (f) 1.1f else 1f, 150)
        }
    }

    fun setPortrait(bitmap: Bitmap?) {
        if (bitmap == null) img.setImageResource(R.drawable.profile_avatar) else img.setImageBitmap(bitmap)
    }

    fun setIcon(res: Int) = img.setImageResource(res)
}
