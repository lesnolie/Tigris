package com.leslie.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Tigris now renders real per-app screenshots itself, so its own switcher is preferred.
 * The native global Recents action is only a fallback if the local activity cannot start
 * (Android TV's GLOBAL_ACTION_RECENTS is a no-op on several OEMs, including Sony).
 * No privileged APIs, ADB injection, brand allowlist, or automatic accessibility enabling.
 */
internal object AppSwitcherRouter {
    fun open(ctx: Context): Boolean {
        val intent = Intent(ctx, AppSwitcherActivity::class.java)
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
        if (opened) return true
        return HomeGuardService.tryOpenNativeRecents()
    }
}
