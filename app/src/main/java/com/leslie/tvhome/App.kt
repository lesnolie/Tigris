package com.leslie.tvhome

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreensaverScope.register(this)
        I18n.initialize(this)
        Updates.schedule(this)
        val sp = Updates.prefs(this)
        if (System.currentTimeMillis() - sp.getLong("lastLaunchCheck", 0) > 6 * 60 * 60 * 1000L) {
            sp.edit().putLong("lastLaunchCheck", System.currentTimeMillis()).apply()
            Updates.executor.execute { Updates.check(this) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Prebuilt Settings scenes are a convenience; give them back under pressure.
        // Not on UI_HIDDEN: that is every app launch from Home, not memory pressure.
        @Suppress("DEPRECATION")
        if (level in TRIM_MEMORY_RUNNING_LOW..TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND)
            SettingsActivity.trimMemory()
    }
}
