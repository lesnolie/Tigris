package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class StockHomeActivitiesTest {
    @Test fun sonyAliasIncludesTheClassReportedByWindowEvents() {
        assertEquals(setOf("com.google.android.tvlauncher.HomeScreen", "com.google.android.tvlauncher.MainActivity"),
            StockHomeActivities.classes("com.google.android.tvlauncher", ".HomeScreen", ".MainActivity"))
    }
    @Test fun ordinaryActivitiesRemainUnchanged() {
        assertEquals(setOf("vendor.home.Main"), StockHomeActivities.classes("vendor.home", "vendor.home.Main", null))
    }
    @Test fun blanksCannotMatchArbitraryDialogs() {
        assertTrue(StockHomeActivities.classes("vendor.home", "", null).isEmpty())
        assertFalse("vendor.home.PairingDialog" in StockHomeActivities.classes("vendor.home", ".Home", ".Main"))
    }
    @Test fun shortClassNamesAreQualifiedAndDuplicatesRemoved() {
        assertEquals(setOf("vendor.home.Main"), StockHomeActivities.classes("vendor.home", "Main", ".Main"))
    }
}
