package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class HomeKeyFilterPolicyTest {
    @Test fun coocaaAndroid11NeverFiltersRemoteKeys() {
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(30, true, false))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(30, true, true))
    }

    @Test fun otherAndroid11DevicesKeepExistingBehavior() {
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(30, false, false))
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(30, false, true))
    }

    @Test fun coocaaAndroid9And10PreserveEarlierPolicy() {
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(28, true, false))
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(29, true, false))
    }

    @Test fun newerAndroidVersionsPreserveOnDemandFiltering() {
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(31, true, false))
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(31, true, true))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(31, false, false))
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(31, false, true))
    }

    @Test fun persistentFilterIsAndroid12AndOffOnXiaomi() {
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(31, false, false, persistent = true))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(31, false, false, persistent = true, isXiaomi = true))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(31, false, true, persistent = true, isXiaomi = true))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(30, true, false, persistent = true))
    }

    @Test fun volumePassthroughGamepadAndScreenOffNeverFilter() {
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(28, false, true, passVolumeKeys = true))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(30, false, true, gamepadGame = true))
        assertFalse(HomeKeyFilterPolicy.shouldRequestFilter(31, false, true, persistent = true, screenOff = true))
        assertTrue(HomeKeyFilterPolicy.shouldRequestFilter(30, false, false, gamepadGame = false, screenOff = false))
    }
}

class HomeKeyCodesTest {
    @Test fun allAppsIsHomeOnlyOnXiaomi() {
        assertTrue(HomeKeyCodes.isHome(HomeKeyCodes.KEYCODE_HOME, false))
        assertTrue(HomeKeyCodes.isHome(HomeKeyCodes.KEYCODE_ALL_APPS, true))
        assertFalse(HomeKeyCodes.isHome(HomeKeyCodes.KEYCODE_ALL_APPS, false))
        assertFalse(HomeKeyCodes.isHome(24, true))
    }
}

class DeafGuardPolicyTest {
    @Test fun onlyASilentAndroid6BindingRestartsOnce() {
        assertTrue(DeafGuardPolicy.shouldRestart(23, sawEvent = false, alreadyRetried = false))
        assertFalse(DeafGuardPolicy.shouldRestart(23, sawEvent = true, alreadyRetried = false))
        assertFalse(DeafGuardPolicy.shouldRestart(23, sawEvent = false, alreadyRetried = true))
        assertFalse(DeafGuardPolicy.shouldRestart(24, sawEvent = false, alreadyRetried = false))
    }
}

class RevivalBudgetTest {
    @Test fun bootUpdateAndScreenOffDoNotCount() {
        assertFalse(RevivalBudget.shouldCount(10_000, 30_000, true, 0, false))
        assertFalse(RevivalBudget.shouldCount(200_000, 200_000, false, 0, false))
        assertFalse(RevivalBudget.shouldCount(200_000, 200_000, true, 150_000, false))
        assertFalse(RevivalBudget.shouldCount(200_000, 200_000, true, 0, true))
        assertTrue(RevivalBudget.shouldCount(200_000, 200_000, true, 0, false))
    }

    @Test fun threeRevivalsSinceBootCountAsOften() {
        val now = 1_000_000L
        val bootedAt = now - 60_000
        assertFalse(RevivalBudget.often(listOf(now - 1_000, now - 2_000), now, bootedAt))
        assertTrue(RevivalBudget.often(listOf(now - 1_000, now - 2_000, now - 3_000, bootedAt - 5_000), now, bootedAt))
    }
}
