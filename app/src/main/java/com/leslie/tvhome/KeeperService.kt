package com.leslie.tvhome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import java.util.concurrent.Executors

/**
 * Keeps the launcher process alive, cleans memory when the TV goes to
 * standby (the remote's power key only turns the screen off) and on a timer.
 */
class KeeperService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (prefs.shutdownOnPowerKey && prefs.adbAuthorized) {
                Cleaner.shutdown(applicationContext)
            } else if (prefs.cleanOnStandby) {
                runClean(spareForeground = false)
            }
        }
    }

    /** Xiaomi switches ADB debugging off on wake; with [Prefs.keepAdb] it goes straight back on. */
    private val adbObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = Guard.ensureAdb(this@KeeperService)
    }

    private val periodic = object : Runnable {
        override fun run() {
            runClean(spareForeground = true)
            schedule()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, I18n.text(R.string.i18n_keeperservice_001), NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_memory)
            .setContentTitle(I18n.text(R.string.i18n_keeperservice_002))
            .setContentText(I18n.text(R.string.i18n_keeperservice_003))
            .build())
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, adbObserver)
        Guard.ensure(this)
        // START_STICKY may revive us without rebinding the enabled accessibility service.
        main.postDelayed({ Guard.rebindIfStalled(this) }, 5_000)
        schedule()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESCHEDULE) schedule()
        return START_STICKY
    }

    private fun schedule() {
        main.removeCallbacks(periodic)
        val h = prefs.cleanIntervalHours
        if (h > 0) main.postDelayed(periodic, h * 3_600_000L)
    }

    private fun runClean(spareForeground: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvhome:clean").apply { acquire(30_000) }
        worker.execute {
            try {
                val fg = if (spareForeground) Cleaner.foregroundPackage(this) else null
                Cleaner.clean(this, fg, automatic = true)
            } finally {
                if (wl.isHeld) wl.release()
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        contentResolver.unregisterContentObserver(adbObserver)
        main.removeCallbacksAndMessages(null)
        worker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CH = "keeper"
        const val ACTION_RESCHEDULE = "reschedule"

        fun start(ctx: Context, action: String? = null) {
            ctx.startForegroundService(Intent(ctx, KeeperService::class.java).setAction(action))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val action = i.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Direct Boot: credential storage is still locked. The guard catches up on unlock.
        if (!Guard.isUserUnlocked(c)) return
        KeeperService.start(c)
        Guard.ensure(c)
        // A running guard looks at what is in front before taking Home, so HDMI-CEC is left alone.
        // Boot alone, with no stock window and no usage-stats evidence, does not start an activity.
        if (action == Intent.ACTION_BOOT_COMPLETED) HomeGuardService.onBootCompleted()
    }
}
