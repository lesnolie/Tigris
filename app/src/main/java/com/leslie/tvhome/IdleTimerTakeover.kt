package com.leslie.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/** One timer setting, with compare-and-restore ownership of standard Android keys only. */
internal object IdleTimerTakeover {
    private const val TAG = "TigrisIdle"
    @Volatile private var guardedHomeVerified = false
    fun isActive(ctx: Context) = ctx.getSharedPreferences("idle_timer_takeover", Context.MODE_PRIVATE)
        .getBoolean("active", false)

    fun noteHomeResumed() {
        if (HomeGuardService.recentlyBroughtHome) guardedHomeVerified = true
    }

    fun ownsHome(ctx: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val native = ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == ctx.packageName
        // These firmwares can report a default HOME that their remote does not honor.
        return (native && !Guard.isXiaomi() && !Guard.isTcl()) ||
            (guardedHomeVerified && Prefs(ctx).guard && HomeGuardService.isRunning)
    }

    @Synchronized fun sync(ctx: Context) {
        if (!Guard.isUserUnlocked(ctx)) return
        val prefs = Prefs(ctx)
        val store = ctx.getSharedPreferences("idle_timer_takeover", Context.MODE_PRIVATE)
        fun read(key: String): String? = if (key == "screen_off_timeout")
            Settings.System.getString(ctx.contentResolver, key)
        else Settings.Secure.getString(ctx.contentResolver, key)
        fun write(key: String, value: String?): Boolean = if (key == "screen_off_timeout")
            Settings.System.putString(ctx.contentResolver, key, value)
        else Settings.Secure.putString(ctx.contentResolver, key, value)
        if (!Guard.hasSecureSettings(ctx) || !Settings.System.canWrite(ctx)) {
            store.edit().putBoolean("active", false).commit()
            return
        }
        val takeoverReady = ownsHome(ctx) && prefs.adbAuthorized && Guard.usageStatsAllowed(ctx)
        // New installs opt in only after takeover. Preserve an existing explicit opt-out.
        if (prefs.unifiedIdle && takeoverReady && !prefs.frameScreensaverConfigured) prefs.frameScreensaver = true
        val enabled = prefs.unifiedIdle && prefs.frameScreensaver && takeoverReady
        val transaction = IdleTimerTransaction(::read, ::write, object : IdleTimerTransaction.Journal {
            override fun entries() = store.getStringSet("keys", emptySet()).orEmpty().associateWith {
                IdleTimerTransaction.Saved(store.getString("original_$it", null), store.getString("applied_$it", "")!!)
            }
            override fun save(key: String, value: IdleTimerTransaction.Saved): Boolean = store.edit()
                .putStringSet("keys", store.getStringSet("keys", emptySet()).orEmpty() + key)
                .putString("original_$key", value.original).putString("applied_$key", value.applied).commit()
            override fun forget(key: String): Boolean = store.edit()
                .putStringSet("keys", store.getStringSet("keys", emptySet()).orEmpty() - key)
                .remove("original_$key").remove("applied_$key").commit()
        })
        store.edit().putBoolean("active", false).commit()
        if (!enabled) {
            runCatching { transaction.restore() }.onFailure { Log.w(TAG, "timer restore failed", it) }
            return
        }
        val desired = linkedMapOf(
            "screensaver_components" to "${ctx.packageName}/${PhotoDream::class.java.name}",
            "screensaver_enabled" to "1",
            "screensaver_activate_on_sleep" to "1",
            "screen_off_timeout" to (prefs.frameIdleMinutes * 60_000L).toString(),
            "sleep_timeout" to "-1",
        )
        // Attentive timeout is not implemented on every Android TV. Do not invent a vendor key.
        if (read("attentive_timeout") != null) desired["attentive_timeout"] = "-1"
        val ok = runCatching { transaction.apply(desired) }.onFailure {
            Log.w(TAG, "timer takeover failed; restoring owned settings", it)
            runCatching { transaction.restore() }
        }.getOrDefault(false)
        store.edit().putBoolean("active", ok).commit()
        Log.i(TAG, "unified idle timer active=$ok minutes=${prefs.frameIdleMinutes}")
    }
}
