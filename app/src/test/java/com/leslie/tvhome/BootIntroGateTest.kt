package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootIntroGateTest {
    private fun decide(
        sdk: Int,
        wake: Boolean,
        wakeDelayMs: Long,
        displayState: Int,
        hasWindowFocus: Boolean = true,
        frameDrawn: Boolean = true,
        nowMs: Long = 20_000L,
        readySinceMs: Long? = 20_000L,
        armedAtMs: Long = 0L,
    ) = BootIntroGate.decide(
        sdk, wake, wakeDelayMs, displayState, hasWindowFocus, frameDrawn, nowMs, readySinceMs, armedAtMs,
    )

    @Test fun api31KeepsTheFixedTimerEvenWhenTheDisplayIsOff() {
        val wake = decide(31, wake = true, wakeDelayMs = 5_000, displayState = 1, hasWindowFocus = false, frameDrawn = false, nowMs = 0, readySinceMs = null)
        assertEquals(BootIntroGate.Action.START, wake.action)
        assertEquals(5_000L, wake.delayMs)

        val boot = decide(36, wake = false, wakeDelayMs = 5_000, displayState = 6, hasWindowFocus = false, frameDrawn = false, nowMs = 0, readySinceMs = null)
        assertEquals(BootIntroGate.Action.START, boot.action)
        assertEquals(0L, boot.delayMs)
    }

    @Test fun api28WaitsForDisplayOnFocusAndAFrame() {
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, displayState = 1, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, displayState = 3, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, displayState = 4, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, displayState = 6, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, BootIntroGate.DISPLAY_ON, hasWindowFocus = false, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, BootIntroGate.DISPLAY_ON, frameDrawn = false, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(28, true, 5_000, BootIntroGate.DISPLAY_ON, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
        assertEquals(BootIntroGate.Action.WAIT, decide(30, true, 5_000, displayState = 1, nowMs = 1_000, readySinceMs = null, armedAtMs = 0).action)
    }

    @Test fun wakeSettleStartsWhenTheDisplayIsReady() {
        val settling = decide(28, true, 5_000, BootIntroGate.DISPLAY_ON, nowMs = 10_000, readySinceMs = 8_000, armedAtMs = 1_000)
        assertEquals(BootIntroGate.Action.WAIT, settling.action)
        assertEquals(3_000L, settling.delayMs)

        val ready = decide(28, true, 5_000, BootIntroGate.DISPLAY_ON, nowMs = 13_000, readySinceMs = 8_000, armedAtMs = 1_000)
        assertEquals(BootIntroGate.Action.START, ready.action)
        assertEquals(0L, ready.delayMs)
    }

    @Test fun bootDoesNotAddTheWakeSettleOnceTheDisplayIsReady() {
        val boot = decide(28, wake = false, wakeDelayMs = 5_000, BootIntroGate.DISPLAY_ON, nowMs = 4_000, readySinceMs = 4_000, armedAtMs = 3_500)
        assertEquals(BootIntroGate.Action.START, boot.action)
        assertEquals(0L, boot.delayMs)
    }

    @Test fun zeroWakeDelayStartsAsSoonAsTheDisplayIsReady() {
        val now = decide(29, true, 0, BootIntroGate.DISPLAY_ON, nowMs = 9_000, readySinceMs = 8_000, armedAtMs = 1_000)
        assertEquals(BootIntroGate.Action.START, now.action)
        assertEquals(0L, now.delayMs)
    }

    @Test fun giveUpStartsAfterTheCapWithoutAReadyDisplay() {
        val early = decide(28, true, 5_000, displayState = 1, nowMs = 14_999, readySinceMs = null, armedAtMs = 0)
        assertEquals(BootIntroGate.Action.WAIT, early.action)
        val late = decide(28, true, 5_000, displayState = 6, hasWindowFocus = false, frameDrawn = false, nowMs = 15_000, readySinceMs = null, armedAtMs = 0)
        assertEquals(BootIntroGate.Action.START_UNREADY, late.action)
        assertEquals(0L, late.delayMs)
    }

    @Test fun oldApisRefuseAZeroSurfaceAndApi31DoesNot() {
        assertFalse(BootIntroGate.surfaceCanPlay(28, 0, 0))
        assertFalse(BootIntroGate.surfaceCanPlay(30, 1920, 0))
        assertTrue(BootIntroGate.surfaceCanPlay(28, 1920, 1080))
        assertTrue(BootIntroGate.surfaceCanPlay(31, 0, 0))
    }

    @Test fun displayLabelsMatchTheLogLines() {
        assertEquals("OFF", BootIntroGate.displayLabel(1))
        assertEquals("ON", BootIntroGate.displayLabel(2))
        assertEquals("DOZE", BootIntroGate.displayLabel(3))
        assertEquals("DOZE_SUSPEND", BootIntroGate.displayLabel(4))
        assertEquals("ON_SUSPEND", BootIntroGate.displayLabel(6))
        assertEquals("STATE_-1", BootIntroGate.displayLabel(-1))
    }
}
