package com.leslie.tvhome

/**
 * Return-to-Home is an optional, local visual cue. It is never presented as a
 * reverse animation of another app's window, which a third-party launcher
 * cannot reliably control.
 *
 * Wall-clock changes and reboots must not reactivate an old launch: use uptime.
 */
internal object HomeReturnPolicy {
    const val MAX_AWAY_MS = 6L * 60 * 60 * 1000

    data class Launch(val pkg: String, val atUptimeMs: Long, val leftHome: Boolean = false)

    fun markPaused(launch: Launch?, interactive: Boolean): Launch? =
        if (interactive) launch?.copy(leftHome = true) else null

    fun canPreserveFocus(launch: Launch?, nowUptimeMs: Long, homeAlreadyVisible: Boolean,
                         guardReturn: Boolean, wakeReturn: Boolean): Boolean =
        !homeAlreadyVisible && !guardReturn && !wakeReturn &&
            ageValid(launch, nowUptimeMs) && launch?.leftHome == true

    fun canReveal(launch: Launch?, nowUptimeMs: Long, interactive: Boolean,
                  overlays: Boolean, guardReturn: Boolean): Boolean =
        ageValid(launch, nowUptimeMs) && launch?.leftHome == true &&
            interactive && !overlays && !guardReturn

    private fun ageValid(launch: Launch?, now: Long): Boolean =
        launch != null && launch.atUptimeMs > 0 && now >= launch.atUptimeMs &&
            now - launch.atUptimeMs <= MAX_AWAY_MS

    fun settleDurationMs(lowEnd: Boolean): Long = if (lowEnd) 130L else 210L
    fun liftDp(lowEnd: Boolean): Int = if (lowEnd) 5 else 10
}
