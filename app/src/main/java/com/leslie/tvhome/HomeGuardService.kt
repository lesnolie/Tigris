package com.leslie.tvhome

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.leslie.tvhome.tv.CoocaaAdapter

/**
 * Brings Tigris back when a stock launcher takes the screen, without disabling that launcher.
 *
 * Portions adapted from Aerial (https://github.com/pengchujin/aerial-tv), MIT License,
 * Copyright (c) 2026 pengchujin, and from open-tv (https://github.com/luckfunc/open-tv),
 * MIT License, Copyright (c) 2026 luckfunc:
 * the Home-key broadcast, Xiaomi's pause bounce, connect/wake/boot checks, vendor HOME
 * categories, usage stats, a deaf binding, and the optional persistent key filter.
 *
 * A real Home press is never rate-limited. Automatic checks (boot, wake, revival, usage
 * stats, and stock-home windows during the boot window) are. HDMI-CEC at power-on is left
 * alone unless the front is actually a stock home. Coocaa Android 11, VIDAA volume and a
 * connected gamepad do not take the whole key stream.
 */
class HomeGuardService : AccessibilityService() {
    private var stockHomes: Map<String, Set<String>> = emptyMap()
    private var fallbackHomes: Set<Pair<String, String>> = emptySet()
    private var allowStockUntil = 0L
    private var ownHomeAt = Long.MIN_VALUE / 2
    private var lastWindow: Pair<String, String>? = null
    private val takeoverPolicy = HomeTakeoverPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private var takeOverReason = ""
    private var classifyOnTakeover = false
    private var temporaryFilter = false
    private var sawEvent = false
    private var screenOff = false
    private var gamepadAttached = false
    private var broughtBackAt = Long.MIN_VALUE / 2
    private var homeInTigrisAt = Long.MIN_VALUE / 2
    private var windowsSeen = 0L
    private var windowsAtHome = 0L
    private var homePressedAt = Long.MIN_VALUE / 2
    private var foregroundTakenAt = Long.MIN_VALUE / 2

    private val takeOver = Runnable { takeOverIfSettled(takeOverReason) }

    private val bootRecheck = Runnable {
        if (SystemClock.elapsedRealtime() >= HomeTakeoverPolicy.BOOT_WINDOW_MS) return@Runnable
        if (stockHomes.isEmpty()) refreshStockHomes()
        scheduleAutomatic("looking again after boot")
    }

    private val foregroundCheck = object : Runnable {
        override fun run() {
            handler.postDelayed(this, FOREGROUND_POLL_MS)
            if (screenOff || !Guard.guardOn(this@HomeGuardService)) return
            if (SystemClock.uptimeMillis() < allowStockUntil) return
            val found = foreground(FOREGROUND_LOOKBACK_MS) ?: return
            val (window, at) = found
            if (at <= foregroundTakenAt) return
            val (pkg, cls) = window
            val bootCover = pkg == StockHomeCatalog.PATCHWALL && cls == StockHomeCatalog.PATCHWALL_BOOT &&
                SystemClock.elapsedRealtime() < HomeTakeoverPolicy.BOOT_WINDOW_MS
            if (cls !in stockHomes[pkg].orEmpty() && !bootCover) return
            foregroundTakenAt = at
            if (SystemClock.uptimeMillis() - broughtBackAt < 1_000) return
            val now = SystemClock.elapsedRealtime()
            takeoverPolicy.observe(HomeTakeoverPolicy.Window.STOCK_HOME, now - HomeTakeoverPolicy.SETTLE_MS)
            if (!requestTakeover(now, HomeTakeoverPolicy.Kind.AUTOMATIC)) return
            Log.i(TAG, "stock launcher $pkg in front by usage stats; bringing Tigris back")
            goHome()
        }
    }

    private val deafCheck = Runnable {
        if (stockHomes.isEmpty()) refreshStockHomes()
        if (!DeafGuardPolicy.shouldRestart(Build.VERSION.SDK_INT, sawEvent, deafRetried)) return@Runnable
        deafRetried = true
        Log.i(TAG, "no window change since bound; switching accessibility off and on")
        Guard.restartAccessibility(this)
    }

    private val frontAfterHome = Runnable { lookAfterHome() }

    private fun lookAfterHome() {
        if (windowsSeen != windowsAtHome) return
        val front = frontByUsage()
        if (front == null || front.first !in stockHomes) {
            if (deafAfterHome && SystemClock.uptimeMillis() - homePressedAt < FRONT_AFTER_HOME_MS) {
                handler.postDelayed(frontAfterHome, DEAF_LOOK_MS)
            }
            return
        }
        Log.i(TAG, "Home pressed, no window change since, and usage stats say ${front.first} is in front")
        lastWindow = front
        deafAfterHome = true
        takeUserHome("Home key without a window change")
        if (!deafAfterHomeRestarted) {
            deafAfterHomeRestarted = true
            Log.i(TAG, "no window change for that Home press; switching accessibility off and on")
            Guard.restartAccessibility(this)
        }
    }

    private val userUnlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshStockHomes()
            filterKeys(false)
            Guard.ensure(this@HomeGuardService)
            val skip = when {
                SystemClock.elapsedRealtime() >= HomeTakeoverPolicy.BOOT_WINDOW_MS -> "outside boot window"
                getSystemService(PowerManager::class.java)?.isInteractive != true -> "screen off"
                else -> null
            }
            if (skip == null) scheduleAutomatic("user unlocked")
            else Log.i(TAG, "user unlocked; not taking over Home ($skip)")
        }
    }

    private val screenWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOff = intent.action == Intent.ACTION_SCREEN_OFF
            filterKeys(temporaryFilter)
            if (screenOff) {
                handler.removeCallbacks(takeOver)
                return
            }
            // The factory launcher is often put back during standby, with no window event on wake.
            // Wait, then act only if that launcher is still what is in front. An HDMI-CEC input is left.
            scheduleAutomatic("screen on")
        }
    }

    private val homeKey = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("reason") != "homekey") return
            if (SystemClock.uptimeMillis() - ownHomeAt < OWN_HOME_MS) return
            if (backOnPause) noteHomeInTigris()
            val front = lastWindow?.first
            if (front != null && front !in stockHomes) {
                windowsAtHome = windowsSeen
                homePressedAt = SystemClock.uptimeMillis()
                handler.removeCallbacks(frontAfterHome)
                handler.postDelayed(frontAfterHome, if (deafAfterHome) DEAF_LOOK_MS else FRONT_AFTER_HOME_MS)
                return
            }
            if (SystemClock.uptimeMillis() - broughtBackAt < 1_000) return
            Log.i(TAG, "Home pressed with ${front ?: "nothing seen yet"} in front")
            takeUserHome("homekey")
        }
    }

    private val gamepadListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshGamepads()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshGamepads()
        override fun onInputDeviceChanged(deviceId: Int) = refreshGamepads()
    }

    override fun onServiceConnected() {
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerReceiver(userUnlocked, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        }
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        screenOff = !interactive
        val elapsed = SystemClock.elapsedRealtime()
        val fastBoot = Guard.isUserUnlocked(this) && elapsed < HomeTakeoverPolicy.BOOT_WINDOW_MS && interactive
        if (fastBoot) {
            scheduleAutomatic("bound shortly after boot")
            scheduleBootRechecks()
        }
        refreshStockHomes()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            runCatching { getSystemService(InputManager::class.java)?.registerInputDeviceListener(gamepadListener, handler) }
            refreshGamepads()
        }
        filterKeys(false)
        registerReceiver(screenWatch, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) })
        val homeFilter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(homeKey, homeFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(homeKey, homeFilter)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) handler.postDelayed(deafCheck, DEAF_AFTER_MS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || StockHomeCatalog.VIDAA_LAUNCHER in stockHomes) {
            if (Guard.usageStatsAllowed(this)) handler.post(foregroundCheck)
            else Log.i(TAG, "usage stats not allowed; not watching the front by them")
        }
        if (!Guard.isUserUnlocked(this)) {
            Log.i(TAG, "bound while user locked; deferred takeover until unlock")
            return
        }
        val revived = elapsed - Guard.lastRevivedAt < 60_000 && !justUpdated()
        val diedInFront = runCatching { Prefs(this).homeInFront }.getOrDefault(false) &&
            elapsed - android.os.Process.getStartElapsedRealtime() < 60_000
        if (!fastBoot && (elapsed < HomeTakeoverPolicy.BOOT_WINDOW_MS || revived || diedInFront) && interactive) {
            scheduleAutomatic(
                when {
                    revived -> "re-enabled after being switched off"
                    diedInFront -> "back after Tigris died in front"
                    else -> "bound shortly after boot"
                },
            )
            if (elapsed < HomeTakeoverPolicy.BOOT_WINDOW_MS) scheduleBootRechecks()
        } else if (!fastBoot) {
            Log.i(TAG, "connected; waiting for a stock HOME window or a Home press")
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenWatch) }
        runCatching { unregisterReceiver(homeKey) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching { unregisterReceiver(userUnlocked) }
        runCatching { getSystemService(InputManager::class.java)?.unregisterInputDeviceListener(gamepadListener) }
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Guard.guardOn(this)) return false
        if (!HomeKeyCodes.isHome(event.keyCode, Guard.isXiaomi())) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            Log.i(TAG, "Home key taken before the system saw it")
            takeUserHome("Home key")
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        sawEvent = true
        deafAfterHome = false
        windowsSeen++
        if (!Guard.guardOn(this) || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Overlays are not what is in front. TCL's voice assistant at boot must not cancel a takeover.
        if (!isActivity(pkg, cls)) return
        val before = lastWindow?.first
        lastWindow = pkg to cls
        val now = SystemClock.elapsedRealtime()
        val stockHome = cls in stockHomes[pkg].orEmpty()
        takeoverPolicy.observe(
            if (stockHome) HomeTakeoverPolicy.Window.STOCK_HOME else HomeTakeoverPolicy.Window.OTHER,
            now,
        )
        handler.removeCallbacks(takeOver)
        val allowed = SystemClock.uptimeMillis() < allowStockUntil
        if (pkg in stockHomes) {
            filterKeys(allowed)
            if (!stockHome || allowed) return
            if (before in StockHomeCatalog.inputPlayers) {
                Log.i(TAG, "stock home $pkg came up during an input switch; looking again shortly")
                classifyOnTakeover = false
                takeOverOnceSettled("after an input switch")
                return
            }
            if (now < HomeTakeoverPolicy.BOOT_WINDOW_MS) {
                Log.i(TAG, "stock HOME during boot: $pkg/$cls; waiting for it to settle")
                classifyOnTakeover = false
                takeOverOnceSettled("stock HOME window")
                return
            }
            if (!requestTakeover(now, HomeTakeoverPolicy.Kind.WINDOW)) return
            Log.i(TAG, "stock home $pkg came to the front; bringing Tigris back")
            goHome()
            return
        }
        filterKeys(false)
        if (allowed && pkg != packageName && isApp(pkg)) allowStockUntil = 0L
    }

    private fun scheduleAutomatic(reason: String) {
        classifyOnTakeover = true
        takeoverPolicy.observe(HomeTakeoverPolicy.Window.UNKNOWN, SystemClock.elapsedRealtime())
        takeOverOnceSettled(reason)
    }

    private fun scheduleBootRechecks() {
        handler.removeCallbacks(bootRecheck)
        HomeTakeoverPolicy.BOOT_RECHECKS_MS.forEach { handler.postDelayed(bootRecheck, it) }
    }

    private fun takeOverOnceSettled(reason: String) {
        takeOverReason = reason
        handler.removeCallbacks(takeOver)
        handler.postDelayed(takeOver, HomeTakeoverPolicy.SETTLE_MS)
    }

    private fun takeOverIfSettled(reason: String) {
        if (!Guard.guardOn(this)) return
        if (classifyOnTakeover) classifyFront(reason)
        val kind = if (reason == "stock HOME window") HomeTakeoverPolicy.Kind.WINDOW else HomeTakeoverPolicy.Kind.AUTOMATIC
        if (!requestTakeover(SystemClock.elapsedRealtime(), kind)) return
        Log.i(TAG, "$reason: stock HOME still in front; bringing Tigris back")
        goHome()
    }

    /**
     * Fills in the front when no window has been seen: usage stats, else unknown.
     * Unknown does not become "the stock launcher". That was pulling viewers out of HDMI
     * when the input raised no window and usage stats were silent.
     */
    private fun classifyFront(reason: String) {
        if (stockHomes.isEmpty()) refreshStockHomes()
        val now = SystemClock.elapsedRealtime()
        val seen = lastWindow?.takeUnless { it in fallbackHomes }
        val front = when {
            seen == null -> frontByUsage()?.also { Log.i(TAG, "$reason; usage stats say ${it.first} is in front") }
            seen.first == packageName && !homeResumed -> coveredByUsage(seen) ?: seen
            else -> seen
        }
        if (front == null && Guard.revivedOften(this)) {
            Log.i(TAG, "$reason, but Tigris keeps being stopped and the front is unknown; leaving it")
            takeoverPolicy.observe(HomeTakeoverPolicy.Window.UNKNOWN, now)
            return
        }
        val stock = front != null && (
            front.second in stockHomes[front.first].orEmpty() ||
                (front.first == StockHomeCatalog.PATCHWALL && front.second == StockHomeCatalog.PATCHWALL_BOOT &&
                    now < HomeTakeoverPolicy.BOOT_WINDOW_MS)
            )
        takeoverPolicy.observe(
            when {
                stock -> HomeTakeoverPolicy.Window.STOCK_HOME
                front == null -> HomeTakeoverPolicy.Window.UNKNOWN
                else -> HomeTakeoverPolicy.Window.OTHER
            },
            if (stock) now - HomeTakeoverPolicy.SETTLE_MS else now,
        )
        if (front != null && !stock) Log.i(TAG, "$reason with ${front.first} in front; leaving it there")
    }

    private fun coveredByUsage(window: Pair<String, String>): Pair<String, String>? {
        if (window.first != packageName || homeResumed || !Guard.usageStatsAllowed(this)) return null
        val front = frontByUsage() ?: return null
        if (front.second !in stockHomes[front.first].orEmpty()) return null
        Log.i(TAG, "Tigris is not resumed and usage stats say ${front.first} is in front")
        return front
    }

    private fun requestTakeover(now: Long, kind: HomeTakeoverPolicy.Kind): Boolean {
        val awake = getSystemService(PowerManager::class.java)?.isInteractive == true
        val visiting = SystemClock.uptimeMillis() < allowStockUntil
        return takeoverPolicy.request(
            now, true, awake, visiting, kind,
            SystemClock.elapsedRealtime() < HomeTakeoverPolicy.BOOT_WINDOW_MS,
        )
    }

    private fun takeUserHome(reason: String) {
        if (!Guard.guardOn(this)) return
        val awake = getSystemService(PowerManager::class.java)?.isInteractive != false
        if (!takeoverPolicy.request(
                SystemClock.elapsedRealtime(), true, awake, false,
                HomeTakeoverPolicy.Kind.USER_HOME, false,
            )
        ) return
        allowStockUntil = 0L
        Log.i(TAG, "$reason; bringing Tigris back")
        goHome()
    }

    private fun goHome() {
        if (!Guard.isUserUnlocked(this)) return
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) {
            Log.i(TAG, "screen off; leaving Home to the screen-on check")
            return
        }
        broughtBackAt = SystemClock.uptimeMillis()
        runCatching { startActivity(HomeActivity.bringBack(this)) }
            .onFailure { Log.w(TAG, "could not bring the home screen back", it) }
    }

    private fun noteHomeInTigris() {
        val now = SystemClock.uptimeMillis()
        if (!homeResumed && now - broughtBackAt > 1_000) return
        homeInTigrisAt = now
    }

    private val backOnPause by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { packageManager.getPackageInfo("com.mitv.tvhome", 0) }.isSuccess
    }

    private val passVolumeKeys by lazy {
        listOf("com.hisense.vidaaassistant", "com.jamdeo.tv.vod").any {
            runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess
        }
    }

    private fun filterKeys(stockVisit: Boolean) {
        temporaryFilter = stockVisit
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val persistent = Guard.guardOn(this) && !Guard.isXiaomi() &&
            runCatching { Prefs(this).persistentHomeFilter }.getOrDefault(false)
        val request = HomeKeyFilterPolicy.shouldRequestFilter(
            Build.VERSION.SDK_INT,
            CoocaaAdapter.isCoocaa(),
            stockVisit,
            persistent = persistent,
            isXiaomi = Guard.isXiaomi(),
            passVolumeKeys = passVolumeKeys,
            gamepadGame = gamepadGame(),
            screenOff = screenOff,
        )
        val want = if (request) info.flags or flag else info.flags and flag.inv()
        if (want == info.flags) return
        info.flags = want
        serviceInfo = info
        Log.i(TAG, if (request) "filtering keys" else "stopped filtering keys")
    }

    private fun gamepadGame(): Boolean {
        if (!gamepadAttached || homeResumed) return false
        val front = lastWindow?.first ?: return false
        return front != packageName && front !in stockHomes
    }

    private fun refreshGamepads() {
        gamepadAttached = runCatching {
            InputDevice.getDeviceIds().any { id ->
                InputDevice.getDevice(id)?.let {
                    !it.isVirtual && !it.name.orEmpty().contains("virtual", ignoreCase = true) &&
                        it.supportsSource(InputDevice.SOURCE_GAMEPAD)
                } == true
            }
        }.getOrDefault(false)
        filterKeys(temporaryFilter)
    }

    private fun foreground(lookbackMs: Long): Pair<Pair<String, String>, Long>? = runCatching {
        val now = System.currentTimeMillis()
        val events = getSystemService(UsageStatsManager::class.java)?.queryEvents(now - lookbackMs, now) ?: return null
        var last: Pair<Pair<String, String>, Long>? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && !event.className.isNullOrEmpty()) {
                last = (event.packageName to event.className!!) to event.timeStamp
            }
        }
        last
    }.getOrNull()

    private fun frontByUsage(): Pair<String, String>? {
        if (!Guard.usageStatsAllowed(this)) return null
        return foreground(UNKNOWN_FRONT_LOOKBACK_MS)?.first?.takeIf { it.first != packageName }
    }

    private val activityCache = HashMap<Pair<String, String>, Boolean>()

    private fun isActivity(pkg: String, cls: String): Boolean = activityCache.getOrPut(pkg to cls) {
        runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
    }

    private fun isApp(pkg: String): Boolean =
        packageManager.getLeanbackLaunchIntentForPackage(pkg) != null ||
            packageManager.getLaunchIntentForPackage(pkg) != null

    private fun refreshStockHomes() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val standard = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).map { it.toCandidate() }
        val vendor = runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN), PackageManager.GET_RESOLVED_FILTER)
                .map { it.toCandidate() }
        }.getOrDefault(emptyList())
        val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val found = StockHomeCatalog.collect(packageName, resolved, standard, vendor) { pkg, cls -> isActivity(pkg, cls) }
        stockHomes = found.homes
        fallbackHomes = found.fallbacks
    }

    private fun ResolveInfo.toCandidate(): HomeCandidate {
        val categories = buildSet {
            val intentFilter = filter ?: return@buildSet
            val names = intentFilter.categoriesIterator() ?: return@buildSet
            while (names.hasNext()) add(names.next())
        }
        return HomeCandidate(
            activityInfo.packageName,
            activityInfo.name,
            activityInfo.targetActivity,
            priority,
            categories,
        )
    }

    private fun justUpdated(): Boolean = runCatching {
        System.currentTimeMillis() - packageManager.getPackageInfo(packageName, 0).lastUpdateTime < 2 * 60_000
    }.getOrDefault(false)

    private fun pressHomeForStock(): Boolean {
        ownHomeAt = SystemClock.uptimeMillis()
        allowStockUntil = SystemClock.uptimeMillis() + 60 * 60 * 1000L
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    companion object {
        private const val TAG = "TigrisGuard"
        private const val DEAF_AFTER_MS = 20_000L
        private const val FRONT_AFTER_HOME_MS = 1_500L
        private const val DEAF_LOOK_MS = 150L
        private const val FOREGROUND_POLL_MS = 1_000L
        private const val FOREGROUND_LOOKBACK_MS = 10_000L
        private const val UNKNOWN_FRONT_LOOKBACK_MS = 6 * 60 * 60 * 1000L
        private const val OWN_HOME_MS = 1_500L
        private const val HOME_PAUSE_MS = 400L

        @Volatile private var instance: HomeGuardService? = null
        @Volatile private var homeResumed = false
        @Volatile private var deafRetried = false
        @Volatile private var deafAfterHomeRestarted = false
        @Volatile private var deafAfterHome = false

        val isRunning: Boolean get() = instance != null

        fun isStockHome(pkg: String): Boolean = instance?.stockHomes?.containsKey(pkg) == true

        fun openStockLauncher(): Boolean {
            val service = instance ?: return false
            return service.pressHomeForStock()
        }

        fun onHomeResumed() {
            homeResumed = true
            val service = instance ?: return
            service.allowStockUntil = 0L
            service.filterKeys(false)
        }

        /** True when the pause is only Xiaomi's Home bounce and Tigris is being brought back. */
        fun onHomePaused(): Boolean {
            homeResumed = false
            val service = instance ?: return false
            service.filterKeys(false)
            if (service.backOnPause && SystemClock.uptimeMillis() - service.homeInTigrisAt < HOME_PAUSE_MS) {
                service.homeInTigrisAt = Long.MIN_VALUE / 2
                Log.i(TAG, "Tigris paused for Home; bringing it back before the stock launcher shows")
                service.takeUserHome("pause")
                return true
            }
            return false
        }

        fun onFilterChanged() {
            val service = instance ?: return
            service.filterKeys(service.temporaryFilter)
        }

        fun onBootCompleted(): Boolean {
            val service = instance ?: return false
            service.handler.post {
                if (service.lastWindow?.first == service.packageName && homeResumed) return@post
                if (service.getSystemService(PowerManager::class.java)?.isInteractive == true) {
                    service.scheduleAutomatic("boot completed")
                    service.scheduleBootRechecks()
                }
            }
            return true
        }
    }
}
