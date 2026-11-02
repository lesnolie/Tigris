package com.leslie.tvhome

import android.os.SystemClock
import android.util.Log

/**
 * When the boot animation may start.
 *
 * API 31 and newer keep the historical timer: boot starts immediately and wake waits
 * [wakeDelayMs] from the moment wake is consumed, even if the display is still off.
 * Android 9–11 (the Sony X9000H is API 28) can report screen-on while the panel is dark,
 * and a TextureView that is fed then keeps playing audio but never presents a picture.
 * Those versions wait until the display is [DISPLAY_ON], the window has focus and one
 * frame has been drawn, and only then apply the user's wake settle.
 */
internal object BootIntroGate {
    /** [android.view.Display.STATE_ON]. Duplicated so the decision stays an integer comparison. */
    const val DISPLAY_ON = 2

    /** Start anyway once this long has passed without a ready display, so the cover cannot stick. */
    const val GIVE_UP_MS = 15_000L

    enum class Action { WAIT, START, START_UNREADY }

    data class Decision(val action: Action, val delayMs: Long)

    fun decide(
        sdk: Int,
        wake: Boolean,
        wakeDelayMs: Long,
        displayState: Int,
        hasWindowFocus: Boolean,
        frameDrawn: Boolean,
        nowMs: Long,
        readySinceMs: Long?,
        armedAtMs: Long,
    ): Decision {
        if (sdk >= 31) return Decision(Action.START, if (wake) wakeDelayMs.coerceAtLeast(0L) else 0L)
        val ready = displayState == DISPLAY_ON && hasWindowFocus && frameDrawn && readySinceMs != null
        if (!ready) {
            return if (nowMs - armedAtMs >= GIVE_UP_MS) Decision(Action.START_UNREADY, 0L)
            else Decision(Action.WAIT, 0L)
        }
        if (!wake) return Decision(Action.START, 0L)
        val left = wakeDelayMs.coerceAtLeast(0L) - (nowMs - readySinceMs!!)
        return if (left <= 0L) Decision(Action.START, 0L) else Decision(Action.WAIT, left)
    }

    /**
     * API 31+ starts as soon as a surface exists, including a 0×0 buffer (historical).
     * Older APIs wait for a real size: a zero buffer on API 28 accepts audio and drops the picture.
     */
    fun surfaceCanPlay(sdk: Int, width: Int, height: Int): Boolean =
        sdk >= 31 || (width > 0 && height > 0)

    fun displayLabel(state: Int): String = when (state) {
        0 -> "UNKNOWN"
        1 -> "OFF"
        DISPLAY_ON -> "ON"
        3 -> "DOZE"
        4 -> "DOZE_SUSPEND"
        5 -> "VR"
        6 -> "ON_SUSPEND"
        else -> "STATE_$state"
    }
}

/** Logcat tag for wake and boot-animation timing. Filter with `adb logcat -s TigrisBoot`. */
internal object BootLog {
    const val TAG = "TigrisBoot"

    fun i(message: String) {
        Log.i(TAG, "$message t=${SystemClock.elapsedRealtime()}")
    }
}
