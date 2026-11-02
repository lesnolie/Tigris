package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class AppUpdateCardStateTest {
    private fun state(installed: Boolean, optional: Boolean, checked: Boolean = false,
                      available: Boolean = false, phase: String = "idle",
                      confirmation: Boolean = false) =
        AppUpdateCardState.Snapshot(installed, optional, checked, available, phase, confirmation)

    @Test fun freshUsersSeeOnlyGetForUninstalledOptionalApps() {
        assertEquals(AppUpdateCardState.Action.GET, AppUpdateCardState.action(state(false, true)))
        assertTrue(AppUpdateCardState.actionable(AppUpdateCardState.Action.GET))
        assertEquals(AppUpdateCardState.Action.OPEN, AppUpdateCardState.action(state(true, true, true)))
    }

    @Test fun updateWinsOverOpenAndLatestIsNotAnAction() {
        assertEquals(AppUpdateCardState.Action.UPDATE,
            AppUpdateCardState.action(state(true, true, true, true)))
        assertEquals(AppUpdateCardState.Action.CHECK,
            AppUpdateCardState.action(state(true, false)))
        assertEquals(AppUpdateCardState.Action.CURRENT,
            AppUpdateCardState.action(state(true, false, true)))
        assertFalse(AppUpdateCardState.actionable(AppUpdateCardState.Action.CURRENT))
    }

    @Test fun pendingInstallHasOneContextualAction() {
        assertEquals(AppUpdateCardState.Action.CONFIRM,
            AppUpdateCardState.action(state(false, true, phase="pending", confirmation=true)))
        assertEquals(AppUpdateCardState.Action.WAITING,
            AppUpdateCardState.action(state(false, true, phase="pending")))
        assertEquals(AppUpdateCardState.Action.RETRY,
            AppUpdateCardState.action(state(false, true, phase="failed")))
    }

    @Test fun downloadAndVerifyNeverShowAnUnrelatedAction() {
        for (phase in listOf("download", "verify", "stage")) {
            val action=AppUpdateCardState.action(state(true, true, true, true, phase))
            assertFalse(AppUpdateCardState.actionable(action))
        }
    }
}
