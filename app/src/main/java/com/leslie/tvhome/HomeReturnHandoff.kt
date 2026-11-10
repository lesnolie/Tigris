package com.leslie.tvhome

import android.os.SystemClock

/**
 * One-shot handoff for an app launch performed by a secondary Tigris surface (the
 * App Switcher) rather than by [HomeActivity] itself. `HomeActivity` cannot observe a
 * `startActivity` that another Activity performed, so it adopts this record on the next
 * genuine return and applies the same return-cue policy.
 *
 * The record is already marked `leftHome = true` because the caller knows the user is
 * leaving the desktop, and it is consumed at most once so it can never fire twice.
 * Wall-clock changes and reboots must not reactivate an old launch: uses uptime like
 * [HomeReturnPolicy].
 */
internal object HomeReturnHandoff {
    @Volatile private var pending: HomeReturnPolicy.Launch? = null

    /** Record a launch made outside [HomeActivity]; consumed by the next return. */
    fun set(pkg: String) {
        if (pkg.isNotEmpty()) {
            pending = HomeReturnPolicy.Launch(pkg, SystemClock.uptimeMillis(), leftHome = true)
        }
    }

    /** Consume the pending record so it can trigger at most one return cue. */
    fun take(): HomeReturnPolicy.Launch? {
        val value = pending
        pending = null
        return value
    }

    fun clear() {
        pending = null
    }
}
