package com.leslie.tvhome

import android.content.Context
import android.content.SharedPreferences

/**
 * What the TV can afford. Low-end TVs (little memory, or Android 9/10, which ship on older chips)
 * get the cheaper look by default; every such default can be changed in 主屏幕设置.
 */
object Device {
    @Volatile private var lowEnd: Boolean? = null

    fun isLowEnd(ctx: Context): Boolean = lowEnd ?: run {
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        val mem = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        (am.isLowRamDevice || mem.totalMem < 2_500L * 1024 * 1024 || android.os.Build.VERSION.SDK_INT < 30 ||
            Runtime.getRuntime().availableProcessors() < 4).also { lowEnd = it }
    }
}

/** All user settings in one place. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

    /** Show the default profile portrait beside the home-screen clock and controls. */
    var showAvatar: Boolean
        get() = sp.getBoolean("show_avatar", true)
        set(v) = sp.edit().putBoolean("show_avatar", v).apply()

    var order: List<String>
        get() = sp.getString("order", "")!!.split(',').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("order", v.joinToString(",")).apply()

    /** New installs and upgrades start in fixed manual order; the user can opt in. */
    var recentFirst: Boolean
        get() = sp.getBoolean("recent_first", false)
        set(v) = sp.edit().putBoolean("recent_first", v).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** Packages that memory cleanup never stops (casting receivers etc.). */
    var keep: Set<String>
        get() {
            val saved = sp.getStringSet("keep", null)?.toSet() ?: return DEFAULT_KEEP
            // Defaults added in a later version join a list the user already saved, once.
            val seen = sp.getStringSet("keep_defaults", null) ?: KEEP_UNTRACKED
            val added = DEFAULT_KEEP - seen
            if (added.isEmpty()) return saved
            val merged = saved + added
            sp.edit().putStringSet("keep", merged).putStringSet("keep_defaults", DEFAULT_KEEP).apply()
            return merged
        }
        set(v) = sp.edit().putStringSet("keep", v).putStringSet("keep_defaults", DEFAULT_KEEP).apply()

    var cleanOnStandby: Boolean
        get() = sp.getBoolean("clean_on_standby", false)
        set(v) = sp.edit().putBoolean("clean_on_standby", v).apply()

    /** Hours between automatic cleanups; 0 = off. */
    var cleanIntervalHours: Int
        get() = sp.getInt("clean_interval", 0)
        set(v) = sp.edit().putInt("clean_interval", v).apply()

    var shutdownOnPowerKey: Boolean
        get() = sp.getBoolean("shutdown_on_power", false)
        set(v) = sp.edit().putBoolean("shutdown_on_power", v).apply()

    /** One of [Backgrounds.THEMES] keys, or a rotating photo source: "daily" (Bing) or "anime" (Wallhaven). */
    var background: String
        get() = sp.getString("background", "daily")!!
        set(v) = sp.edit().putString("background", v).apply()

    /** Minutes between background photo changes. */
    var wallMinutes: Int
        get() = sp.getInt("wall_minutes", 30)
        set(v) = sp.edit().putInt("wall_minutes", v).apply()

    /** Image processing resolution: auto (safe), 1080p, or force UHD on capable TVs. */
    var wallQuality: String
        get() = sp.getString("wall_quality", MediaQuality.AUTO)!!
        set(v) = sp.edit().putString("wall_quality", v).apply()

    /** Aerial stream preference; never bypass the hardware decoder capability check. */
    var aerialQuality: String
        get() = sp.getString("aerial_quality", MediaQuality.AUTO)!!
        set(v) = sp.edit().putString("aerial_quality", v).apply()

    /** Seconds each photo stays up in the photo frame. */
    var frameSeconds: Int
        get() = sp.getInt("frame_seconds", 30)
        set(v) = sp.edit().putInt("frame_seconds", v).apply()

    /** The photo frame is the system screensaver (instead of Sony's). */
    var frameScreensaver: Boolean
        get() = sp.getBoolean("frame_screensaver", false)
        set(v) = sp.edit().putBoolean("frame_screensaver", v).apply()

    /** Preinstalled apps removed for this user from the long-press menu; restorable in settings. */
    var removed: Set<String>
        get() = sp.getStringSet("removed", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("removed", v).apply()

    var adbAuthorized: Boolean
        get() = sp.getBoolean("adb_ok", false)
        set(v) = sp.edit().putBoolean("adb_ok", v).apply()

    var lastClean: String
        get() = sp.getString("last_clean", "")!!
        set(v) = sp.edit().putString("last_clean", v).apply()

    /** The screensaver before the photo frame took over, put back when it is turned off. */
    var prevScreensaver: String
        get() = sp.getString("prev_screensaver", "")!!
        set(v) = sp.edit().putString("prev_screensaver", v).apply()

    /** What the photo frame / screensaver shows: [SAVER_PHOTOS] or [SAVER_AERIAL]. */
    var saverMode: String
        get() = sp.getString("saver_mode", SAVER_PHOTOS)!!
        set(v) = sp.edit().putString("saver_mode", v).apply()

    /** Keep the home screen in front on firmwares that pin Home to their own launcher (Guard.kt). */
    var guard: Boolean
        get() = sp.getBoolean("guard", false)
        set(v) = sp.edit().putBoolean("guard", v).apply()

    /** Switch ADB debugging back on whenever the firmware turns it off (Xiaomi does on wake). */
    var keepAdb: Boolean
        get() = sp.getBoolean("keep_adb", false)
        set(v) = sp.edit().putBoolean("keep_adb", v).apply()

    /** Kept on disk while the home screen is in front, so a new process can tell how the last one ended. */
    var homeInFront: Boolean
        get() = sp.getBoolean("home_in_front", false)
        set(v) = sp.edit().putBoolean("home_in_front", v).apply()

    /** Picture links for the "custom" background, one per line (an image, or an API that returns one). */
    var customWallUrls: String
        get() = sp.getString("custom_urls", "")!!
        set(v) = sp.edit().putString("custom_urls", v).apply()

    /** TV inputs pinned to the home grid, as [InputTile.key] strings. */
    var pinnedInputs: Set<String>
        get() = sp.getStringSet("pinned_inputs", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("pinned_inputs", v).apply()

    /** 24-hour clock ("14:05") instead of "2:05 PM". */
    var clock24: Boolean
        get() = sp.getBoolean("clock_24", false)
        set(v) = sp.edit().putBoolean("clock_24", v).apply()

    /** [CLOCK_CORNER] (small, top right), [CLOCK_LARGE] (date and a large clock top left, as before v1.9) or [CLOCK_OFF]. */
    var clockStyle: String
        get() = sp.getString("clock_style", CLOCK_CORNER)!!
        set(v) = sp.edit().putString("clock_style", v).apply()

    /** [AUTO], [CC_GLASS] or [CC_PANEL]; see [ccGlass]. */
    var ccStyle: String
        get() = sp.getString("cc_style", AUTO)!!
        set(v) = sp.edit().putString("cc_style", v).apply()

    /** The Control Center uses floating glass controls rather than one plain panel. */
    fun ccGlass(ctx: Context) = when (ccStyle) { CC_GLASS -> true; CC_PANEL -> false; else -> !Device.isLowEnd(ctx) }

    /** [AUTO], [FOCUS_TVOS] (tilt and a gliding highlight) or [FOCUS_SIMPLE] (scale and shadow only). */
    var focusStyle: String
        get() = sp.getString("focus_style", AUTO)!!
        set(v) = sp.edit().putString("focus_style", v).apply()

    fun focusTvos(ctx: Context) = when (focusStyle) { FOCUS_TVOS -> true; FOCUS_SIMPLE -> false; else -> !Device.isLowEnd(ctx) }

    /** When the boot animation plays: [INTRO_OFF], [INTRO_BOOT] (after a cold boot) or [INTRO_WAKE] (also on wake from standby). */
    var intro: String
        get() = sp.getString("intro", INTRO_BOOT)!!
        set(v) = sp.edit().putString("intro", v).apply()

    /** What plays: uploaded or bundled video ([INTRO_VIDEO]), or the Tigris wordmark ([INTRO_WORDMARK]). */
    var introSource: String
        get() = sp.getString("intro_source", INTRO_VIDEO)!!
        set(v) = sp.edit().putString("intro_source", v).apply()

    /** The settings icon: the user's own picture when there is one (true), or the built-in gear. */
    var ownIcon: Boolean
        get() = sp.getBoolean("own_icon", true)
        set(v) = sp.edit().putBoolean("own_icon", v).apply()

    /** The settings page on its uploaded picture ([SettingsActivity.backdropFile]) rather than plain dark. */
    var settingsBg: Boolean
        get() = sp.getBoolean("settings_bg", true)
        set(v) = sp.edit().putBoolean("settings_bg", v).apply()

    /**
     * How long the screen stays black on wake before the intro starts. Many panels (OLED especially)
     * light up seconds after Android reports the screen on, and nothing tells the app when.
     */
    var wakeDelay: Int
        get() = sp.getInt("wake_delay", 5000)
        set(v) = sp.edit().putInt("wake_delay", v.coerceIn(0, 15_000)).apply()

    var introSound: Boolean
        get() = sp.getBoolean("intro_sound", true)
        set(v) = sp.edit().putBoolean("intro_sound", v).apply()

    /** When the boot animation last played after a boot ([Intro.bootStamp]), so it plays once per boot. */
    var introBoot: Long
        get() = sp.getLong("intro_boot", 0L)
        set(v) = sp.edit().putLong("intro_boot", v).apply()

    companion object {
        const val SAVER_PHOTOS = "photos"
        const val SAVER_AERIAL = "aerial"

        const val AUTO = "auto"
        const val CLOCK_CORNER = "corner"
        const val CLOCK_LARGE = "large"
        const val CLOCK_OFF = "off"
        const val CC_GLASS = "glass"
        const val CC_PANEL = "panel"
        const val FOCUS_TVOS = "tvos"
        const val FOCUS_SIMPLE = "simple"
        const val INTRO_OFF = "off"
        const val INTRO_BOOT = "boot"
        const val INTRO_WAKE = "wake"
        const val INTRO_VIDEO = "video"
        const val INTRO_WORDMARK = "wordmark"

        val DEFAULT_KEEP = setOf(
            "com.dangbei.screencast",
            "com.weekd.miracastreceiver",
            "com.xiaodianshi.tv.yst",
            "dev.vodik7.atvtoolsserver",
        )

        /** The defaults from before new ones were tracked; a list saved then has already seen these. */
        private val KEEP_UNTRACKED = setOf("com.dangbei.screencast", "com.xiaodianshi.tv.yst", "dev.vodik7.atvtoolsserver")

        /** Initial grid order on first run; everything else follows alphabetically. */
        val DEFAULT_ORDER = listOf(
            "com.xiaodianshi.tv.yst",
            "com.ktcp.video",
            "com.starcor.mango",
            "com.liskovsoft.smarttubetv.beta",
            "com.douyu.xl.douyutv",
            "com.huya.nftv",
            "com.fongmi.android.tv",
            "com.github.tvbox.osc.tk",
            "org.xbmc.kodi",
            "com.quark.yun.tv",
            "com.dangbei.screencast",
            "tv.twitch.android.app",
            "com.limelight",
            "com.limelight.qiin",
            "com.android.tv.settings",
        )
    }
}
