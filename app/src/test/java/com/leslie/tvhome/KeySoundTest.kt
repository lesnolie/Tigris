package com.leslie.tvhome

import android.media.AudioManager
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

/** The home screen must stay silent unless the user asked for the sound and the move was real. */
class KeySoundTest {
    private fun play(
        enabled: Boolean = true,
        systemSoundsOn: Boolean = true,
        code: Int = KeyEvent.KEYCODE_DPAD_DOWN,
        repeatCount: Int = 0,
        focusMoved: Boolean = true,
        handled: Boolean = true,
    ) = KeySound.shouldPlay(enabled, systemSoundsOn, code, repeatCount, focusMoved, handled)

    @Test fun silentUntilTheUserOptsIn() {
        assertFalse(play(enabled = false))
        assertTrue(play(enabled = true))
    }

    @Test fun systemTouchSoundsSwitchWins() {
        assertFalse(play(systemSoundsOn = false))
    }

    @Test fun hittingTheEdgeOfTheGridIsSilent() {
        assertFalse(play(code = KeyEvent.KEYCODE_DPAD_RIGHT, focusMoved = false))
        assertTrue(play(code = KeyEvent.KEYCODE_DPAD_RIGHT, focusMoved = true))
    }

    @Test fun selectFollowsWhetherAnythingTookThePress() {
        assertFalse(play(code = KeyEvent.KEYCODE_DPAD_CENTER, handled = false))
        assertTrue(play(code = KeyEvent.KEYCODE_DPAD_CENTER, handled = true))
    }

    @Test fun holdingADirectionClicksOnlyOnce() {
        assertTrue(play(repeatCount = 0))
        assertFalse(play(repeatCount = 1))
        assertFalse(play(repeatCount = 7))
    }

    @Test fun keysWithoutAnEffectStaySilent() {
        assertNull(KeySound.effectFor(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(play(code = KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(play(code = KeyEvent.KEYCODE_HOME))
    }

    @Test fun eachDirectionKeepsItsOwnEffect() {
        assertEquals(AudioManager.FX_FOCUS_NAVIGATION_LEFT, KeySound.effectFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, KeySound.effectFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(AudioManager.FX_FOCUS_NAVIGATION_UP, KeySound.effectFor(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(AudioManager.FX_FOCUS_NAVIGATION_DOWN, KeySound.effectFor(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(AudioManager.FX_KEY_CLICK, KeySound.effectFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(AudioManager.FX_KEY_CLICK, KeySound.effectFor(KeyEvent.KEYCODE_BACK))
    }
}
