package com.leslie.tvhome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerButtonShutdownPolicyTest {
    private fun dump(reason: String, time: String = "10000", state: String = "Asleep") = """
        Power Manager State:
          mWakefulness=$state
          mLastSleepTime=$time (100 ms ago)
          mLastSleepReason=$reason
    """.trimIndent()

    @Test fun onlyARecentConfirmedPowerButtonSleepCanShutdown() {
        assertTrue(PowerButtonShutdownPolicy.decide(dump("power_button"), 10_100, false).shutdown)
        assertTrue(PowerButtonShutdownPolicy.decide(dump("power_button", state="Dozing"), 10_100, false).shutdown)
    }
    @Test fun idleScreensaverHdmiAndApplicationSleepNeverBecomeShutdown() {
        for (reason in listOf("timeout", "inattentive", "application", "sleep_button", "hdmi", "unknown"))
            assertFalse(reason, PowerButtonShutdownPolicy.decide(dump(reason), 10_100, false).shutdown)
    }
    @Test fun wakingUpInvalidatesEvenARecentPowerButtonReason() {
        assertFalse(PowerButtonShutdownPolicy.decide(dump("power_button"), 10_100, true).shutdown)
        assertFalse(PowerButtonShutdownPolicy.decide(dump("power_button",state="Awake"), 10_100, false).shutdown)
        assertFalse(PowerButtonShutdownPolicy.decide(dump("power_button",state="Dreaming"), 10_100, false).shutdown)
    }
    @Test fun missingAmbiguousOrOldSleepEvidenceFailsClosed() {
        for (value in listOf(null, "", "mLastSleepReason=power_button", dump("power_button", "0"),
            dump("power_button", "999999"), dump("power_button", "1"), dump("power_button", "bad")))
            assertFalse(PowerButtonShutdownPolicy.decide(value, 10_100, false).shutdown)
    }
}
