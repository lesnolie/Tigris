package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class NativeRecentsPolicyTest {
    @Test fun disconnectedAccessibilityAlwaysUsesLocalFallback() {
        assertFalse(NativeRecentsPolicy.canAttempt(false, 29, true))
        assertFalse(NativeRecentsPolicy.canAttempt(false, 30, true))
        assertFalse(NativeRecentsPolicy.canAttempt(false, 36, true))
    }

    @Test fun android11AndLaterRequireReportedRecentsAction() {
        assertFalse(NativeRecentsPolicy.canAttempt(true, 30, false))
        assertFalse(NativeRecentsPolicy.canAttempt(true, 36, false))
        assertTrue(NativeRecentsPolicy.canAttempt(true, 30, true))
        assertTrue(NativeRecentsPolicy.canAttempt(true, 36, true))
    }

    @Test fun android9And10MayTryAndUseResultToDecideFallback() {
        assertTrue(NativeRecentsPolicy.canAttempt(true, 28, false))
        assertTrue(NativeRecentsPolicy.canAttempt(true, 29, false))
    }
}
