package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateRecoveryTest {
    @Test fun rebootWithAnExistingSessionMustRecommitRatherThanReplayTheOldActivity() {
        assertEquals(UpdateRecovery.Action.RESUME, UpdateRecovery.action("pending", false, 13, 14, 42, 42))
    }
    @Test fun missingOrUnrelatedSessionMustOfferFreshRetry() {
        for (session in listOf(null, 43)) assertEquals(UpdateRecovery.Action.RETRY,
            UpdateRecovery.action("pending", false, 13, 14, 42, session))
        assertEquals(UpdateRecovery.Action.RETRY, UpdateRecovery.action("pending", false, 13, null, -1, null))
    }
    @Test fun installedTargetCompletesEvenWhenTheResultCallbackWasLost() {
        for (installed in listOf(14L, 15L)) assertEquals(UpdateRecovery.Action.COMPLETE,
            UpdateRecovery.action("pending", false, installed, 14, 42, null))
    }
    @Test fun processDeathDuringDownloadOrStagingCannotLeaveAPermanentSpinner() {
        for (phase in listOf("download", "verify", "stage")) assertEquals(UpdateRecovery.Action.RETRY,
            UpdateRecovery.action(phase, false, 13, 14, 42, 42))
    }
    @Test fun returningToThePageMustNotCancelALiveDownload() {
        for (phase in listOf("download", "verify", "stage", "pending")) assertEquals(UpdateRecovery.Action.NONE,
            UpdateRecovery.action(phase, true, 13, 14, 42, 42))
    }
    @Test fun successfulExternalUpgradeClearsAnOldFailedTask() {
        assertEquals(UpdateRecovery.Action.COMPLETE,
            UpdateRecovery.action("failed", false, 27L, 26L, -1, null))
    }

    @Test fun finishedFailedOrIdleTasksAreNotAutomaticallyRestarted() {
        for (phase in listOf("idle", "done", "failed")) assertEquals(UpdateRecovery.Action.NONE,
            UpdateRecovery.action(phase, false, 13, 14, 42, 42))
    }
}
