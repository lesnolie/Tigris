package com.leslie.tvhome

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Our visible UI stays started while a system dream attaches and takes window focus. */
internal object ScreensaverScope : Application.ActivityLifecycleCallbacks {
    private val visible = mutableSetOf<Activity>()
    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)
    fun coversTigrisUi() = visible.any { !it.isFinishing && !it.isDestroyed }

    override fun onActivityStarted(activity: Activity) {
        // FrameActivity already owns its bounded keep-screen-on period. Never count DreamActivity.
        if (activity is HomeActivity || activity is SettingsActivity || activity is UpdateActivity ||
            activity is WallpaperActivity) visible += activity
    }
    override fun onActivityStopped(activity: Activity) { visible -= activity }
    override fun onActivityDestroyed(activity: Activity) { visible -= activity }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
