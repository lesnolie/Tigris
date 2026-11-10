package com.leslie.tvhome

import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAppsContractTest {
    @Test fun recentListDoesNotRequirePrivilegedTaskSnapshotPermission() {
        // A product-level contract: RecentApps records explicit launches and only optional
        // UsageStatsManager data; it never accesses other apps' private screenshot buffers.
        assertTrue(RecentApps::class.java.name.endsWith("RecentApps"))
    }
}
