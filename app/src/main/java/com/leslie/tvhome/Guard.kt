package com.leslie.tvhome

import android.Manifest
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
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

    /** Credential-backed preferences and accounts are unreadable until the user is unlocked. */
    fun isUserUnlocked(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            ctx.getSystemService(UserManager::class.java)?.isUserUnlocked != false

    fun guardOn(ctx: Context): Boolean =
        isUserUnlocked(ctx) && runCatching { Prefs(ctx).guard }.getOrDefault(false)

    fun usageStatsAllowed(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * What a computer would grant over adb, done through the TV's own adb instead. Blocking.
     * TCL's autostart ops use either name; other firmwares don't know them and refuse, which is fine.
     * GET_USAGE_STATS lets the guard see a launcher that raises no window. ACCESS_RESTRICTED_SETTINGS
     * is what Android 13 requires before a sideloaded accessibility service may be enabled.
     */
    fun grantViaLocalAdb(ctx: Context): Boolean {
        val p = ctx.packageName
        val script = listOf(
            "pm grant $p ${Manifest.permission.WRITE_SECURE_SETTINGS}",
            "pm grant $p android.permission.PACKAGE_USAGE_STATS",
            "cmd notification allow_listener $p/$p.RevivalListener",
            "appops set $p SYSTEM_ALERT_WINDOW allow",
            "appops set $p WRITE_SETTINGS allow",
            "appops set $p GET_USAGE_STATS allow",
            "appops set $p android:get_usage_stats allow",
            "appops set $p ACCESS_RESTRICTED_SETTINGS allow",
            "appops set $p APP_AUTO_START allow",
            "appops set $p APP_ASSOC_START allow",
            "appops set $p AUTO_START allow",
            "appops set $p ASSOC_START allow",
        ).joinToString("; ") + "; echo done"
        LocalAdb.shell(ctx, script, 10_000) ?: return false
        return hasSecureSettings(ctx)
    }

    /** Turns the guard on or off for good. Returns false when the permission to do so is missing. */
    fun setEnabled(ctx: Context, on: Boolean): Boolean {
        if (!isUserUnlocked(ctx)) return false
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
    fun ensure(ctx: Context): Boolean {
        if (!isUserUnlocked(ctx)) return false
        ensureAdb(ctx)
        if (!hasSecureSettings(ctx)) return false
        keepScreensaver(ctx)
        if (!Prefs(ctx).guard) return false
        RevivalSync.ensure(ctx)
        val me = service(ctx)
        val entries = enabledServices(ctx)
        return runCatching {
            if (entries.none { ComponentName.unflattenFromString(it)?.flattenToString() == me }) {
                lastRevivedAt = SystemClock.elapsedRealtime()
                // Appended, so the firmware's own services (小爱, TCL's 小T助手) stay on.
                Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (entries + me).joinToString(":"))
                Log.i(TAG, "home guard had been switched off; turned it back on")
            }
            Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            entries.none { ComponentName.unflattenFromString(it)?.flattenToString() == me }
        }.onFailure { Log.w(TAG, "could not switch the home guard on", it) }.getOrDefault(false)
    }

    /** Whether the firmware has stopped Tigris often enough that an unknown front should be left alone. */
    fun revivedOften(ctx: Context): Boolean {
        if (!isUserUnlocked(ctx)) return false
        val now = System.currentTimeMillis()
        return RevivalBudget.often(revivalStamps(ctx), now, now - SystemClock.elapsedRealtime())
    }

    private var revivalNoted = false

    fun noteRevival(ctx: Context) {
        if (!isUserUnlocked(ctx)) return
        val now = System.currentTimeMillis()
        val interactive = ctx.getSystemService(PowerManager::class.java)?.isInteractive != false
        val updated = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        if (!RevivalBudget.shouldCount(now, SystemClock.elapsedRealtime(), interactive, updated, revivalNoted)) return
        revivalNoted = true
        val stamps = revivalStamps(ctx).filter { now - it in 0..RevivalBudget.WINDOW_MS } + now
        Prefs(ctx).revivals = stamps.joinToString(",")
    }

    private fun revivalStamps(ctx: Context): List<Long> =
        Prefs(ctx).revivals.split(',').mapNotNull { it.toLongOrNull() }

    /**
     * Switches the guard out of the accessibility list and back, so a deaf binding is replaced.
     * From Android 7, toggling the global accessibility switch does not rebind services.
     */
    fun restartAccessibility(ctx: Context) {
        if (!hasSecureSettings(ctx)) return
        val me = service(ctx)
        val resolver = ctx.contentResolver
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            fun others() = enabledServices(ctx).filter { ComponentName.unflattenFromString(it)?.flattenToString() != me }
            runCatching {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others().joinToString(":"))
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching {
                        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others() + me).joinToString(":"))
                    }.onFailure { Log.w(TAG, "could not put the home guard back", it) }
                }, 1500)
            }.onFailure { Log.w(TAG, "could not restart the home guard", it) }
            return
        }
        runCatching {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1) }
                    .onFailure { Log.w(TAG, "could not switch accessibility back on", it) }
            }, 1500)
        }.onFailure { Log.w(TAG, "could not switch accessibility off", it) }
    }

    fun ensureAdb(ctx: Context) {
        if (!isUserUnlocked(ctx) || !Prefs(ctx).keepAdb || !hasSecureSettings(ctx)) return
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
 * Brings the guard back after the firmware kills Tigris. Android drops a force-stopped app's
 * accessibility services for good, but keeps a notification listener approved and binds it again
 * shortly after its process dies. No notification is read or touched. On low-RAM Android 9 the
 * listener never binds; [RevivalSync] is the other way back.
 */
class RevivalListener : NotificationListenerService() {
    override fun onListenerConnected() {
        Log.i("TigrisGuard", "revived by the notification listener binding")
        val broughtBack = Guard.ensure(this)
        if (broughtBack) Guard.noteRevival(this)
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
        if (i.hasExtra("home_filter")) {
            val on = i.getBooleanExtra("home_filter", false) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Guard.isXiaomi()
            prefs.persistentHomeFilter = on
            HomeGuardService.onFilterChanged()
        }
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
        resultData = "guard=${prefs.guard} keep_adb=${prefs.keepAdb} home_filter=${prefs.persistentHomeFilter} secure_settings=${Guard.hasSecureSettings(c)} " +
            "background=${prefs.background} cc_style=${prefs.ccStyle} focus_style=${prefs.focusStyle} clock_style=${prefs.clockStyle} clock_24=${prefs.clock24} " +
            "intro=${prefs.intro} intro_source=${prefs.introSource} wake_delay=${prefs.wakeDelay} own_icon=${prefs.ownIcon} settings_bg=${prefs.settingsBg} low_end=${Device.isLowEnd(c)}"
    }
}
