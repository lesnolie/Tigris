package com.leslie.tvhome

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.leslie.tvhome.tv.CoocaaAdapter
import com.leslie.tvhome.tv.TclAdapter
import com.leslie.tvhome.tv.XiaomiAdapter

/**
 * Keeping Tigris the home screen on firmwares that pin Home to their own launcher, without
 * disabling any system app. Ported from Aerial (github.com/pengchujin/aerial-tv, MIT), whose
 * per-model notes record what these firmwares do:
 *
 * - Xiaomi PatchWall accepts `set-home-activity` and the HOME role, yet Home still resolves to
 *   `com.mitv.tvhome` (a whitelist only the system can write). Disabling PatchWall leaves the TV
 *   on a black screen, because the fallback home refuses third-party launchers too.
 * - Xiaomi force-stops every third-party app on standby, and Android then drops the app's
 *   accessibility services from the enabled list. It also switches ADB debugging off on wake.
 * - TCL refuses any APK that declares HOME unless TCL signed it (the "guard" flavor declares
 *   none), and blocks third-party apps from starting at boot unless two TCL app ops allow it.
 *
 * The pieces:
 * 1. [HomeGuardService], an accessibility service that brings Tigris back the moment the stock
 *    launcher comes to the front. It sees window changes only, never screen content.
 * 2. [RevivalListener], a notification listener that reads no notifications. Android rebinds a
 *    listener about half a second after its process is killed, which restarts Tigris; Tigris then
 *    switches the guard back on, which needs WRITE_SECURE_SETTINGS.
 * 3. WRITE_SECURE_SETTINGS and the listener approval come from adb (a computer, AGENTS.md) or from
 *    the TV's own adb through [LocalAdb] ([grantViaLocalAdb]).
 *
 * All of it is off unless [Prefs.guard] is on. On TVs where Tigris is the real home (Sony, stock
 * Android TV) it is not needed.
 */
object Guard {
    private const val TAG = "TigrisGuard"
    private var recovery: ServiceRecovery? = null

    /** elapsedRealtime of the last time the guard had to be switched back on. */
    @Volatile
    var lastRevivedAt = Long.MIN_VALUE / 2
        private set

    fun hasSecureSettings(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun service(ctx: Context) = ComponentName(ctx, HomeGuardService::class.java).flattenToString()

    /** Xiaomi switches ADB off on every wake, so "keep ADB on" is offered there only. */
    fun isXiaomi() = XiaomiAdapter.isXiaomi()

    private fun isTcl() = TclAdapter.isTcl()

    /**
     * Whether this TV needs the guard: Xiaomi and TCL (Xiaomi reports Tigris as the default home
     * while still opening PatchWall), any TV where Tigris is not the default home (including the
     * guard build, which has no HOME filter), or the guard is already on.
     */
    fun needed(ctx: Context) = Prefs(ctx).guard || isXiaomi() || isTcl() || !isDefaultHome(ctx)

    fun isDefaultHome(ctx: Context): Boolean =
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName == ctx.packageName

    /**
     * What a computer would grant over adb, done through the TV's own adb instead. Blocking.
     * The two TCL app ops let Tigris start at boot there (TclAppBootManager refuses third-party
     * apps otherwise); other firmwares don't know them and refuse, which is fine.
     */
    fun grantViaLocalAdb(ctx: Context): Boolean {
        val p = ctx.packageName
        val script = listOf(
            "pm grant $p ${Manifest.permission.WRITE_SECURE_SETTINGS}",
            "cmd notification allow_listener $p/$p.RevivalListener",
            "appops set $p SYSTEM_ALERT_WINDOW allow",
            "appops set $p APP_AUTO_START allow",
            "appops set $p APP_ASSOC_START allow",
        ).joinToString("; ") + "; echo done"
        LocalAdb.shell(ctx, script, 10_000) ?: return false
        return hasSecureSettings(ctx)
    }

    /** Turns the guard on or off for good. Returns false when the permission to do so is missing. */
    fun setEnabled(ctx: Context, on: Boolean): Boolean {
        recovery?.cancel()
        Prefs(ctx).guard = on
        if (!hasSecureSettings(ctx)) return false
        if (on) ensure(ctx) else runCatching {
            val me = service(ctx)
            val r = ctx.contentResolver
            val others = enabledServices(ctx).filter { ComponentName.unflattenFromString(it)?.flattenToString() != me }
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
        }
        return true
    }

    private fun enabledServices(ctx: Context) =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() }

    /** Puts back whatever the firmware switched off: ADB, the photo-frame screensaver, the guard. */
    fun ensure(ctx: Context) {
        ensureAdb(ctx)
        if (!hasSecureSettings(ctx)) return
        keepScreensaver(ctx)
        if (!Prefs(ctx).guard) return
        val me = service(ctx)
        val entries = enabledServices(ctx)
        runCatching {
            if (entries.none { ComponentName.unflattenFromString(it)?.flattenToString() == me }) {
                lastRevivedAt = SystemClock.elapsedRealtime()
                // Appended, so the firmware's own services (小爱, TCL's 小T助手) stay on.
                Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (entries + me).joinToString(":"))
                Log.i(TAG, "home guard had been switched off; turned it back on")
            }
            Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.onFailure { Log.w(TAG, "could not switch the home guard on", it) }
    }

    fun ensureAdb(ctx: Context) {
        if (!Prefs(ctx).keepAdb || !hasSecureSettings(ctx)) return
        val r = ctx.contentResolver
        if (Settings.Global.getInt(r, Settings.Global.ADB_ENABLED, 0) == 1) return
        runCatching {
            Settings.Global.putInt(r, Settings.Global.ADB_ENABLED, 1)
            Log.i(TAG, "ADB debugging had been switched off; turned it back on")
        }
    }

    /** TCL writes its own screensaver back at boot; the photo frame takes its place again. */
    private fun keepScreensaver(ctx: Context) {
        if (!Prefs(ctx).frameScreensaver) return
        val r = ctx.contentResolver
        val mine = ComponentName(ctx, PhotoDream::class.java)
        val current = Settings.Secure.getString(r, "screensaver_components").orEmpty()
        if (ComponentName.unflattenFromString(current) == mine) return
        runCatching {
            Settings.Secure.putString(r, "screensaver_components", mine.flattenToString())
            Log.i(TAG, "the screensaver was $current; put the photo frame back")
        }
    }

    /**
     * Enabled but not running: seen on TCL after an update or a crash, where Android keeps the
     * setting but never binds the service again. Writing the list without it and then with it
     * makes Android bind it. Two writes in a row are coalesced, hence the pause.
     */
    fun rebindIfStalled(ctx: Context) {
        if (HomeGuardService.isRunning || !Prefs(ctx).guard || !hasSecureSettings(ctx)) return
        val app = ctx.applicationContext
        val repair = recovery ?: ServiceRecovery(
            service(app),
            read = { enabledServices(app).mapNotNull { ComponentName.unflattenFromString(it)?.flattenToString() } },
            write = { Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, it.joinToString(":")) },
            allowed = { Prefs(app).guard && hasSecureSettings(app) },
            bound = { HomeGuardService.isRunning },
            later = { action -> Handler(Looper.getMainLooper()).postDelayed({
                runCatching { action() }.onFailure { Log.w(TAG, "guard rebind failed", it) }
            }, 1500) },
        ).also { recovery = it }
        runCatching { repair.repair() }.onFailure { Log.w(TAG, "guard repair failed", it) }
    }
}

/**
 * 1. When the stock launcher's home activity comes to the front (the Home key, an app exiting to
 *    it, the TV waking into it), Tigris is brought back on top. On Xiaomi the user sees PatchWall
 *    for about half a second.
 * 2. The stock launcher can still be opened on purpose from its tile. While it is in front the Home
 *    key is taken before the system sees it and answered with Tigris.
 *
 * Key filtering is on only in that second case (and always up to Android 11, see [filterKeys]).
 * With it on every key press detours through this service, and some apps then see a press as held
 * and move twice (云视听小电视 on Xiaomi, reported to Aerial).
 */
class HomeGuardService : AccessibilityService() {
    /** HOME activities that are not ours: package to class names. */
    private var stockHomes: Map<String, Set<String>> = emptyMap()
    private var allowStockUntil = 0L
    /** Tracks confirmed windows and bounds automatic takeover attempts. */
    private val takeoverPolicy = HomeTakeoverPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private var takeOverReason = ""
    private val takeOver = Runnable { takeOverIfStockInFront(takeOverReason) }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Firmwares put their own launcher back in front during standby, and no window change follows.
            takeoverPolicy.observe(HomeTakeoverPolicy.Window.UNKNOWN, SystemClock.elapsedRealtime())
            handler.removeCallbacks(takeOver)
        }
    }

    override fun onServiceConnected() {
        instance = this
        refreshStockHomes()
        filterKeys(false)
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        // Wait for a real window event: binding does not prove what is on screen.
        Log.i(TAG, "connected; waiting for a confirmed stock HOME window")
    }

    /**
     * Waits a moment and takes Home only if nothing but the stock launcher came to the front. At
     * power-on an HDMI-CEC source switches the TV to its input just as the guard is bound again;
     * taking Home at once bounced the viewer straight out of the input (Aerial, Xiaomi MFTR0).
     */
    private fun takeOverOnceSettled(reason: String) {
        takeOverReason = reason
        handler.removeCallbacks(takeOver)
        handler.postDelayed(takeOver, HomeTakeoverPolicy.SETTLE_MS)
    }

    private fun takeOverIfStockInFront(reason: String) {
        val awake = getSystemService(PowerManager::class.java)?.isInteractive == true
        if (!takeoverPolicy.request(SystemClock.elapsedRealtime(), Prefs(this).guard,
                awake, SystemClock.uptimeMillis() < allowStockUntil)) return
        Log.i(TAG, "$reason: confirmed stock HOME; bringing Tigris back")
        goHome()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    /**
     * Android 11 and earlier normally intercept Home to avoid delayed activity launches.
     * On Coocaa / Skyworth Android 11, however, the firmware consumes Home before
     * onKeyEvent, so filtering other keys can interfere with volume KEY_UP (issue #15).
     * That device uses the existing window-based home takeover instead.
     */
    private fun filterKeys(on: Boolean) {
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val request = HomeKeyFilterPolicy.shouldRequestFilter(
            Build.VERSION.SDK_INT, CoocaaAdapter.isCoocaa(), on,
        )
        val want = if (request) info.flags or flag else info.flags and flag.inv()
        if (want == info.flags) return
        info.flags = want
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs(this).guard) return false
        if (event.keyCode != KeyEvent.KEYCODE_HOME || Guard.isDefaultHome(this)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) goHome()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Prefs(this).guard || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Dialogs and overlays cancel a pending takeover too; never act on stale HOME evidence.
        val stockHome = stockHomes[pkg]?.contains(cls) == true
        takeoverPolicy.observe(if (stockHome) HomeTakeoverPolicy.Window.STOCK_HOME
            else HomeTakeoverPolicy.Window.OTHER, SystemClock.elapsedRealtime())
        handler.removeCallbacks(takeOver)
        val allowed = SystemClock.uptimeMillis() < allowStockUntil
        if (pkg in stockHomes) {
            filterKeys(allowed)
            if (cls !in stockHomes.getValue(pkg) || allowed) return
            Log.i(TAG, "stock HOME observed: $pkg/$cls; waiting for it to settle")
            takeOverOnceSettled("stock HOME window")
            return
        }
        filterKeys(false)
        // Leaving the stock launcher for a real app ends the visit: Home goes to Tigris again.
        if (allowed && pkg != packageName && isApp(pkg)) allowStockUntil = 0L
    }

    private fun isApp(pkg: String): Boolean =
        packageManager.getLeanbackLaunchIntentForPackage(pkg) != null || packageManager.getLaunchIntentForPackage(pkg) != null

    private fun refreshStockHomes() {
        stockHomes = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )
            // Negative priorities are fallbacks shown only while nothing else can be Home.
            .filter { it.activityInfo.packageName != packageName && it.priority >= 0 }
            .groupBy({ it.activityInfo.packageName }, { it.activityInfo.name })
            .mapValues { it.value.toSet() }
    }

    private fun goHome() {
        runCatching { startActivity(HomeActivity.bringBack(this)) }
            .onFailure { Log.w(TAG, "could not bring the home screen back", it) }
    }

    companion object {
        private const val TAG = "TigrisGuard"

        @Volatile
        private var instance: HomeGuardService? = null

        val isRunning: Boolean get() = instance != null

        fun isStockHome(pkg: String): Boolean = instance?.stockHomes?.containsKey(pkg) == true

        /**
         * Opens the stock launcher on purpose, from its tile. Xiaomi's launcher activities require
         * a signature permission, so an app cannot start them; the system Home action can.
         */
        fun openStockLauncher(): Boolean {
            val s = instance ?: return false
            s.allowStockUntil = SystemClock.uptimeMillis() + 60 * 60 * 1000L
            return s.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        /** The home screen is in front again: the stock launcher is fair game. */
        fun onHomeResumed() {
            val s = instance ?: return
            s.allowStockUntil = 0L
            s.filterKeys(false)
        }
    }
}

/**
 * Brings the guard back after the firmware kills Tigris. Android drops a force-stopped app's
 * accessibility services for good, but keeps a notification listener approved and binds it again
 * shortly after its process dies. No notification is read or touched.
 */
class RevivalListener : NotificationListenerService() {
    override fun onListenerConnected() {
        Log.i("TigrisGuard", "revived by the notification listener binding")
        Guard.ensure(this)
        runCatching { KeeperService.start(this) }
        Handler(Looper.getMainLooper()).postDelayed({ Guard.rebindIfStalled(this) }, 5_000)
    }
}

/**
 * Options set from a computer over adb:
 *   adb shell am broadcast -a com.leslie.tvhome.CONFIG -n com.leslie.tvhome/.ConfigReceiver --ez guard true
 *   ... --ez keep_adb true
 * The manifest requires the sender to hold WRITE_SECURE_SETTINGS, which the adb shell has and
 * ordinary apps cannot get.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val prefs = Prefs(c)
        if (i.hasExtra("keep_adb")) prefs.keepAdb = i.getBooleanExtra("keep_adb", false)
        if (i.hasExtra("guard")) Guard.setEnabled(c, i.getBooleanExtra("guard", false))
        // Looks (主屏幕设置 → 外观 / 开机动画), mostly for agents setting a TV up.
        i.getStringExtra("cc_style")?.let { prefs.ccStyle = it }
        i.getStringExtra("focus_style")?.let { prefs.focusStyle = it }
        i.getStringExtra("clock_style")?.let { prefs.clockStyle = it }
        if (i.hasExtra("clock_24")) prefs.clock24 = i.getBooleanExtra("clock_24", false)
        i.getStringExtra("intro")?.let { prefs.intro = it; prefs.introBoot = Intro.bootStamp(c) }
        i.getStringExtra("intro_source")?.let { prefs.introSource = it }
        i.getStringExtra("background")?.takeIf { it in Wallpapers.SOURCES || it in Backgrounds.THEMES || it in VideoWallpaper.SOURCES }?.let {
            prefs.background = it
            Wallpapers.trimExcept(c, it)
        }
        if (i.hasExtra("settings_bg")) prefs.settingsBg = i.getBooleanExtra("settings_bg", true)
        if (i.hasExtra("own_icon")) prefs.ownIcon = i.getBooleanExtra("own_icon", true)
        if (i.hasExtra("intro_sound")) prefs.introSound = i.getBooleanExtra("intro_sound", true)
        if (i.hasExtra("wake_delay")) prefs.wakeDelay = i.getIntExtra("wake_delay", 5000)
        Guard.ensure(c)
        resultData = "guard=${prefs.guard} keep_adb=${prefs.keepAdb} secure_settings=${Guard.hasSecureSettings(c)} " +
            "background=${prefs.background} cc_style=${prefs.ccStyle} focus_style=${prefs.focusStyle} clock_style=${prefs.clockStyle} clock_24=${prefs.clock24} " +
            "intro=${prefs.intro} intro_source=${prefs.introSource} wake_delay=${prefs.wakeDelay} own_icon=${prefs.ownIcon} settings_bg=${prefs.settingsBg} low_end=${Device.isLowEnd(c)}"
    }
}
