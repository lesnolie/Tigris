package com.leslie.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Captures the foreground app's screen through Tigris's own accessibility service
 * (`canTakeScreenshot`, Android 11+) and keeps the newest thumbnail per package in
 * app-private storage. The app switcher shows these as real screenshots.
 *
 * A plain third-party launcher cannot read the system's task snapshots, so a whole-screen
 * capture through the user-enabled accessibility service is the only non-privileged way
 * to get a genuine per-app image. Refreshes are bounded (one frame every few seconds
 * while a third-party app is in front) and nothing leaves the device.
 */
internal object ShotCapture {
    const val DIR = "app_shots"
    private const val CAPTURE_DELAY_MS = 900L
    private const val MIN_INTERVAL_MS = 1200L
    private const val REFRESH_MS = 4000L
    private const val MAX_KEEP = 12
    private const val TARGET_WIDTH = 1280

    @Volatile private var service: AccessibilityService? = null
    @Volatile private var currentApp: String? = null
    private var lastAt = 0L
    private var looping = false
    private val handler = Handler(Looper.getMainLooper())

    // Stored in the app cache so it is treated as disposable (system may clear it) and
    // never accumulates: one file per package, hard-capped by [MAX_KEEP], deleted on removal.
    private fun dir(ctx: Context) = File(ctx.cacheDir, DIR).apply { mkdirs() }

    private fun file(ctx: Context, pkg: String) = File(dir(ctx), ShelfImageCacheKey.name(pkg))

    fun has(ctx: Context, pkg: String): Boolean = runCatching { file(ctx, pkg).length() > 0L }.getOrDefault(false)

    fun load(ctx: Context, pkg: String): Bitmap? = runCatching {
        val f = file(ctx, pkg)
        if (f.length() > 0L) BitmapFactory.decodeFile(f.absolutePath) else null
    }.getOrNull()

    fun clear(ctx: Context, pkg: String) {
        runCatching { file(ctx, pkg).delete() }
    }

    private val initial = Runnable {
        val s = service
        val p = currentApp
        if (s != null && p != null) ShotCaptureApi30.capture(s, p)
    }

    private val loop = object : Runnable {
        override fun run() {
            val s = service
            val p = currentApp
            if (s != null && p != null &&
                s.getSystemService(PowerManager::class.java)?.isInteractive == true) {
                lastAt = SystemClock.uptimeMillis()
                ShotCaptureApi30.capture(s, p)
            }
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    /** Called on every window-state change; cheap, most calls return immediately. */
    fun onForeground(svc: AccessibilityService, pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (pkg.isEmpty() || pkg == svc.packageName || pkg.startsWith("com.android.systemui")) {
            // Back to our own launcher / system UI: stop refreshing foreign app shots.
            currentApp = null
            return
        }
        service = svc
        currentApp = pkg
        if (!looping) {
            looping = true
            handler.postDelayed(loop, REFRESH_MS)
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastAt >= MIN_INTERVAL_MS) {
            lastAt = now
            Log.i("TigrisShot", "schedule pkg=$pkg")
            handler.removeCallbacks(initial)
            handler.postDelayed(initial, CAPTURE_DELAY_MS)
        }
    }

    /** Persist one captured frame (called from the API-30 callback only). */
    fun store(svc: AccessibilityService, pkg: String, src: Bitmap) {
        val software = try { src.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null } ?: return
        val w = TARGET_WIDTH
        val h = (software.height.toFloat() * w / software.width).toInt().coerceAtLeast(1)
        val scaled = try { Bitmap.createScaledBitmap(software, w, h, true) } catch (_: Exception) { software }
        try {
            val f = file(svc, pkg)
            val tmp = File(f.parentFile, f.name + ".part")
            FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (_: Exception) {
        } finally {
            if (scaled !== software) runCatching { scaled.recycle() }
            runCatching { software.recycle() }
        }
        Log.i("TigrisShot", "saved pkg=$pkg ${w}x$h")
        prune(svc)
    }

    private fun prune(ctx: Context) {
        runCatching {
            dir(ctx).listFiles()?.filter { it.extension == "jpg" }
                ?.sortedByDescending { it.lastModified() }?.drop(MAX_KEEP)?.forEach { it.delete() }
        }
    }
}

/** Kept separate so the API-30 screenshot types are only loaded on Android 11+. */
internal object ShotCaptureApi30 {
    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    fun capture(service: AccessibilityService, pkg: String) {
        try {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            if (hw != null) ShotCapture.store(service, pkg, hw)
                        } catch (_: Exception) {
                        } finally {
                            runCatching { result.hardwareBuffer.close() }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w("TigrisShot", "takeScreenshot failed code=$errorCode pkg=$pkg")
                    }
                })
        } catch (e: Exception) {
            Log.w("TigrisShot", "takeScreenshot exception pkg=$pkg", e)
        }
    }
}
