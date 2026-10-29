package com.leslie.tvhome

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Talks to the TV's own adbd on 127.0.0.1:5555 so a normal app can run
 * shell-privileged commands (force-stop, shutdown). The first connection
 * makes the TV show I18n.text(R.string.i18n_cleaner_001); after I18n.text(R.string.i18n_cleaner_002) it is silent.
 */
object LocalAdb {
    private const val TAG = "LocalAdb"

    @Synchronized
    private fun keys(ctx: Context): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (priv.exists() != pub.exists()) throw java.io.IOException("Incomplete existing ADB key pair")
        if (!priv.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /**
     * Runs [cmd] in the adb shell. Returns null when adbd is unreachable or
     * the key is not authorized yet. [waitAuthMs] is how long to wait for the
     * user to accept the authorization prompt.
     */
    fun shell(ctx: Context, cmd: String, waitAuthMs: Int = 4000): String? {
        return execute(ctx, cmd, waitAuthMs).response?.allOutput
    }

    /** Only this explicit user action reports a failed connection as a lost current authorization. */
    internal fun authorize(ctx: Context): AdbAuthorization {
        val result = execute(ctx, "echo tigris-adb-ready", 60_000)
        val good = result.response?.let { it.exitCode == 0 && it.output.trim() == "tigris-adb-ready" } == true
        Prefs(ctx).adbAuthorized = good
        return AdbAuthorization(if (good) null else result.failure ?: AdbFailure.SHELL)
    }

    private data class ShellResult(val response: dadb.AdbShellResponse? = null, val failure: AdbFailure? = null)

    private fun execute(ctx: Context, cmd: String, waitAuthMs: Int): ShellResult {
        val prefs = Prefs(ctx)
        var stage = AdbStage.KEYS
        return try {
            val pair = keys(ctx)
            stage = AdbStage.HANDSHAKE
            Dadb.create("127.0.0.1", 5555, pair, connectTimeout = 3000, socketTimeout = waitAuthMs).use { d ->
                stage = AdbStage.SHELL
                ShellResult(response = d.shell(cmd)).also { prefs.adbAuthorized = true }
            }
        } catch (e: Exception) {
            val reason = AdbFailure.classify(stage, e)
            // Do not copy commands, keys, pairing codes or arbitrary server output into diagnostics.
            Log.w(TAG, "local ADB failed: stage=$stage reason=$reason type=${e.javaClass.simpleName}")
            ShellResult(failure = reason)
        }
    }

    /** Fire-and-forget variant for commands that kill the connection (shutdown). */
    fun shellAsync(ctx: Context, cmd: String) {
        Thread { shell(ctx.applicationContext, cmd, 60_000) }.start()
    }
}

object Cleaner {
    data class Result(val beforeMb: Long, val afterMb: Long, val stopped: Int, val viaAdb: Boolean, val failed: Int = 0, val skipped: Boolean = false) {
        val freedMb get() = (afterMb - beforeMb).coerceAtLeast(0)
        fun description(): String = when {
            skipped -> I18n.text(R.string.i18n_cleaner_003)
            viaAdb -> I18n.text(R.string.i18n_cleaner_004, freedMb, stopped, failed)
            else -> I18n.text(R.string.i18n_cleaner_005, freedMb)
        }
    }

    fun availMb(ctx: Context): Long = memInfo(ctx).availMem / 1024 / 1024
    fun totalMb(ctx: Context): Long = memInfo(ctx).totalMem / 1024 / 1024

    private fun memInfo(ctx: Context) = ActivityManager.MemoryInfo().also {
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }

    /**
     * Manual cleanup stops unprotected third-party apps. Automatic cleanup only requests
     * cached-process cleanup; missing foreground evidence skips automatic work.
     * Blocking; call off the main thread.
     */
    fun clean(ctx: Context, spare: String? = null, automatic: Boolean = false): Result {
        val prefs = Prefs(ctx)
        val before = availMb(ctx)
        val foreground = spare ?: foregroundPackage(ctx)
        // Automatic cleanup never force-stops apps: downloads/casting may be active without a visible window.
        val mode = CleanupPolicy.mode(automatic, foreground)
        if (mode == CleanupPolicy.Mode.SKIP) return Result(before, before, 0, false, skipped = true)
        val keep = prefs.keep + setOfNotNull(ctx.packageName, foreground) + protectedServices(ctx)
        var stopped = 0
        var failed = 0
        var viaAdb = false
        val list = if (mode != CleanupPolicy.Mode.DEEP) null else LocalAdb.shell(ctx, "pm list packages -3")
        if (list != null) {
            val targets = CleanupPolicy.targets(list.lineSequence()
                .filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toList(), keep)
            viaAdb = true
            for (pkg in targets) {
                val marker = "TIGRIS_STOP_OK"
                val out = LocalAdb.shell(ctx, "am force-stop $pkg && echo $marker", 4000)
                if (CleanupPolicy.succeeded(out, marker)) stopped++ else failed++
            }
        } else {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val packages = ctx.packageManager.getInstalledApplications(0)
                .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { it.packageName }
            for (pkg in CleanupPolicy.targets(packages, keep)) am.killBackgroundProcesses(pkg)
        }
        Thread.sleep(1500)
        val r = Result(before, availMb(ctx), stopped, viaAdb, failed)
        prefs.lastClean = SimpleDateFormat(I18n.text(R.string.i18n_cleaner_006), Locale.CHINA).format(Date()) +
            "，${r.description()}"
        return r
    }

    private fun protectedServices(ctx: Context): Set<String> {
        val r = ctx.contentResolver
        val names = listOf("enabled_accessibility_services", "enabled_notification_listeners", "default_input_method")
        val keep = names.flatMap { android.provider.Settings.Secure.getString(r, it).orEmpty().split(':') }
            .mapNotNull { android.content.ComponentName.unflattenFromString(it)?.packageName }.toMutableSet()
        val am = ctx.getSystemService(ActivityManager::class.java)
        am.runningAppProcesses.orEmpty().filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE }
            .forEach { keep.addAll(it.pkgList.orEmpty()) }
        runCatching {
            ctx.getSystemService(android.media.session.MediaSessionManager::class.java)
                .getActiveSessions(android.content.ComponentName(ctx, RevivalListener::class.java))
                .forEach { keep.add(it.packageName) }
        }.onFailure {
            // No reliable playback inventory: keep all third-party apps out of destructive cleanup.
            keep.addAll(ctx.packageManager.getInstalledApplications(0).map { it.packageName })
        }
        return keep
    }

    fun foregroundPackage(ctx: Context): String? =
        LocalAdb.shell(ctx, "dumpsys activity activities")?.let(CleanupPolicy::foreground)

    fun shutdown(ctx: Context) = LocalAdb.shellAsync(ctx, "svc power shutdown")
    fun reboot(ctx: Context) = LocalAdb.shellAsync(ctx, "svc power reboot")
    fun sleep(ctx: Context) = LocalAdb.shellAsync(ctx, "input keyevent KEYCODE_SLEEP")
}
