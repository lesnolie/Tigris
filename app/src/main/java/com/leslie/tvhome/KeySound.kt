package com.leslie.tvhome

import android.media.AudioManager
import android.view.KeyEvent

/**
 * Decides whether a key press earns a click, kept free of Android state so it can be tested.
 *
 * The home screen is opt-in territory: many TVs already click on their own, so two sounds would
 * overlap. Everything here assumes the feature is off until the user turns it on.
 */
object KeySound {
    /** The platform effect for a key, or null for keys that never click. */
    fun effectFor(code: Int): Int? = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEY_CLICK
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> AudioManager.FX_KEY_CLICK
        else -> null
    }

    fun isDirection(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
    }

    fun isSelect(code: Int): Boolean =
        code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER

    /**
     * A direction clicks only when focus actually landed somewhere else, so hitting the edge of
     * the grid stays silent; select clicks only when something took the press. Repeats never
     * click, or holding a direction would machine-gun the sound.
     */
    fun shouldPlay(
        enabled: Boolean,
        systemSoundsOn: Boolean,
        code: Int,
        repeatCount: Int,
        focusMoved: Boolean,
        handled: Boolean,
    ): Boolean {
        if (!enabled || !systemSoundsOn || repeatCount != 0) return false
        if (effectFor(code) == null) return false
        return when {
            isDirection(code) -> focusMoved
            isSelect(code) -> handled
            else -> false
        }
    }
}
