package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppOrderTest {
    @Test fun foregroundEventsWinButSavedAppsRemainAvailable() {
        assertEquals(listOf("b", "a", "c"),
            RecentAppOrder.merge(listOf("b", "a"), listOf("a", "c"), setOf("a", "b", "c")))
    }

    @Test fun removesUninstalledPackagesAndDuplicates() {
        assertEquals(listOf("installed", "another"),
            RecentAppOrder.merge(
                listOf("missing", "installed", "installed"),
                listOf("missing", "another", "installed"),
                setOf("installed", "another")))
    }

    @Test fun capsCardsAtTwelveWithoutDroppingTheirOrdering() {
        val apps = (0 until 30).map { "pkg$it" }
        assertEquals(apps.take(12),
            RecentAppOrder.merge(apps, emptyList(), apps.toSet()))
    }

    @Test fun noUsagePermissionStillShowsSavedHistory() {
        assertEquals(listOf("last", "older"),
            RecentAppOrder.merge(emptyList(), listOf("last", "older"), setOf("last", "older")))
    }

    @Test fun emptyAvailableAppListIsSafe() {
        assertEquals(emptyList<String>(),
            RecentAppOrder.merge(listOf("old"), listOf("new"), emptySet()))
    }
}
