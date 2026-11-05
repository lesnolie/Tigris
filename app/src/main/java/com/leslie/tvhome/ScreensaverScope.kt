package com.leslie.tvhome

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock

/**
 * A started but long-paused Settings/Home activity may be underneath a full-screen cast.
 * Count only resumed Tigris UI, with a short pause grace for Android's dream transition.
 */
internal object ScreensaverScope : Application.ActivityLifecycleCallbacks {
    private val resumed = mutableSetOf<Activity>()
    private var lastOwnUiPausedAt = Long.MIN_VALUE
    const val DREAM_ATTACH_GRACE_MS = 2_500L

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    fun coversTigrisUi(now: Long = SystemClock.elapsedRealtime()): Boolean =
        resumed.any { !it.isFinishing && !it.isDestroyed } ||
            (lastOwnUiPausedAt != Long.MIN_VALUE &&
                now - lastOwnUiPausedAt in 0..DREAM_ATTACH_GRACE_MS)

    private fun tracked(activity: Activity) =
        activity is HomeActivity || activity is SettingsActivity || activity is UpdateActivity ||
            activity is WallpaperActivity

    override fun onActivityResumed(activity: Activity) {
        if (tracked(activity)) resumed += activity
    }
    override fun onActivityPaused(activity: Activity) {
        if (tracked(activity) && resumed.remove(activity))
            lastOwnUiPausedAt = SystemClock.elapsedRealtime()
    }
    override fun onActivityStopped(activity: Activity) { resumed -= activity }
    override fun onActivityDestroyed(activity: Activity) { resumed -= activity }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
