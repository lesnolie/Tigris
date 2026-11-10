package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class HomeReturnPolicyTest {
    @Test fun noAnimationOnColdStartOrWithoutLeaving() {
        assertFalse(HomeReturnPolicy.canReveal(null, 1000, true, false, false))
        assertFalse(HomeReturnPolicy.canReveal(HomeReturnPolicy.Launch("a", 100), 1000, true, false, false))
    }

    @Test fun returningFromAnExplicitAppLaunchRestoresTheOriginalDockState() {
        val launched = HomeReturnPolicy.markPaused(HomeReturnPolicy.Launch("movie", 100), true)
        assertTrue(HomeReturnPolicy.canPreserveFocus(launched, 500, false, false, false))
        assertTrue(HomeReturnPolicy.canReveal(launched, 500, true, false, false))
        assertFalse(HomeReturnPolicy.canPreserveFocus(launched, 500, true, false, false))
    }

    @Test fun systemWakeGuardAndOverlaysNeverTriggerTheReturnCue() {
        val launch = HomeReturnPolicy.Launch("app", 100, true)
        assertFalse(HomeReturnPolicy.canPreserveFocus(launch, 101, false, true, false))
        assertFalse(HomeReturnPolicy.canPreserveFocus(launch, 101, false, false, true))
        assertFalse(HomeReturnPolicy.canReveal(launch, 101, false, false, false))
        assertFalse(HomeReturnPolicy.canReveal(launch, 101, true, true, false))
        assertFalse(HomeReturnPolicy.canReveal(launch, 101, true, false, true))
        assertNull(HomeReturnPolicy.markPaused(launch, false))
    }

    @Test fun expiryAndMonotonicClockProtectAgainstStaleTransitions() {
        val launch = HomeReturnPolicy.Launch("app", 100, true)
        assertTrue(HomeReturnPolicy.canReveal(launch, 100 + HomeReturnPolicy.MAX_AWAY_MS,
            true, false, false))
        assertFalse(HomeReturnPolicy.canReveal(launch, 101 + HomeReturnPolicy.MAX_AWAY_MS,
            true, false, false))
        assertFalse(HomeReturnPolicy.canReveal(launch, 99, true, false, false))
    }

    @Test fun lowEndDevicesGetShorterSmallerCue() {
        assertTrue(HomeReturnPolicy.settleDurationMs(true) < HomeReturnPolicy.settleDurationMs(false))
        assertTrue(HomeReturnPolicy.liftDp(true) < HomeReturnPolicy.liftDp(false))
    }
}
