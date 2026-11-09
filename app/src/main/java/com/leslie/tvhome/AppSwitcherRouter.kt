package com.leslie.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Prefer real Android Recents only when Tigris's existing, user-enabled accessibility
 * service is connected and Android accepts the global Recents action.
 * No privileged APIs, ADB injection, brand allowlist, or automatic accessibility enablement.
 */
internal object AppSwitcherRouter {
    fun open(ctx: Context): Boolean {
        if (HomeGuardService.tryOpenNativeRecents()) return true
        val fallback = Intent(ctx, AppSwitcherActivity::class.java)
        if (ctx !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(fallback); true }.getOrDefault(false)
    }
}
