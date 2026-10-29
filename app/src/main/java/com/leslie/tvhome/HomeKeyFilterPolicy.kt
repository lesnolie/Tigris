package com.leslie.tvhome

/**
 * Decide whether the home guard should request ALL remote-control key events.
 *
 * Android <= 11 normally needs Home interception to avoid a long background-activity
 * launch delay. Coocaa / Skyworth Android 11 firmware reported in issue #15 consumes
 * Home before AccessibilityService.onKeyEvent; asking for the entire key stream instead
 * risks losing a volume KEY_UP and causing runaway volume changes.
 *
 * VIDAA handles volume in firmware that never sees keys which went through the filter.
 * A game played with a gamepad must not have every pad event injected again (Aerial #774).
 * The screen-off case leaves the power key to the system (Aerial #371).
 *
 * On Android 12+ the optional persistent filter (open-tv, MIT) keeps filtering outside the
 * stock launcher. Xiaomi does not use it: Home there is answered when Tigris pauses, and
 * filtering drops volume-key repeats.
 *
 * Persistent-filter behavior is adapted from open-tv (https://github.com/luckfunc/open-tv),
 * MIT License, Copyright (c) 2026 luckfunc.
 */
internal object HomeKeyFilterPolicy {
    fun shouldRequestFilter(
        apiLevel: Int,
        isCoocaa: Boolean,
        homeFilterRequested: Boolean,
        persistent: Boolean = false,
        isXiaomi: Boolean = false,
        passVolumeKeys: Boolean = false,
        gamepadGame: Boolean = false,
        screenOff: Boolean = false,
    ): Boolean {
        if (screenOff || passVolumeKeys || gamepadGame) return false
        if (isCoocaa && apiLevel == 30) return false // Android 11 / API 30
        // Xiaomi from Android 12 answers Home on pause. The setting stays off there.
        if (isXiaomi && apiLevel >= 31) return false
        if (apiLevel <= 30) return true
        return homeFilterRequested || persistent
    }
}

/** Xiaomi sticks report the house button as ALL_APPS. Other brands keep that key for the app list. */
internal object HomeKeyCodes {
    const val KEYCODE_HOME = 3
    const val KEYCODE_ALL_APPS = 284

    fun isHome(keyCode: Int, isXiaomi: Boolean): Boolean =
        keyCode == KEYCODE_HOME || (isXiaomi && keyCode == KEYCODE_ALL_APPS)
}

/** Up to Android 6 a bound guard that never sees a window is switched off and on once. */
internal object DeafGuardPolicy {
    fun shouldRestart(apiLevel: Int, sawEvent: Boolean, alreadyRetried: Boolean): Boolean =
        apiLevel <= 23 && !sawEvent && !alreadyRetried
}

/**
 * Counts process revivals so an unknown front is left alone when the firmware keeps stopping Tigris.
 * Adapted from Aerial's GuardSetup.noteRevival / revivedOften.
 */
internal object RevivalBudget {
    const val WINDOW_MS = 10 * 60 * 1000L
    const val BOOT_START_MS = 2 * 60 * 1000L

    fun shouldCount(
        now: Long,
        elapsedRealtime: Long,
        screenInteractive: Boolean,
        packageUpdatedAt: Long,
        alreadyNoted: Boolean,
    ): Boolean {
        if (alreadyNoted || !screenInteractive || elapsedRealtime < BOOT_START_MS) return false
        if (now - packageUpdatedAt in 0..BOOT_START_MS) return false
        return true
    }

    fun often(stamps: List<Long>, now: Long, bootedAt: Long): Boolean =
        stamps.count { now - it in 0..WINDOW_MS && it >= bootedAt } >= 3
}
