package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class StockHomeCatalogTest {
    private val ours = "com.leslie.tvhome"

    @Test fun sonyAliasTargetCountsAsTheLauncherHome() {
        val set = StockHomeCatalog.collect(
            ours, ours,
            homeIntent = listOf(HomeCandidate("com.google.android.tvlauncher", ".HomeScreen", ".MainActivity")),
            mainIntent = emptyList(),
        )
        val classes = set.homes.getValue("com.google.android.tvlauncher")
        assertTrue(classes.contains("com.google.android.tvlauncher.HomeScreen"))
        assertTrue(classes.contains("com.google.android.tvlauncher.MainActivity"))
    }

    @Test fun vendorHomeCategoriesIncludeCoocaaFiltersAndSkipSecondaryHome() {
        assertTrue(StockHomeCatalog.isVendorHomeCategory("android.intent.category.HOME.CC"))
        assertTrue(StockHomeCatalog.isVendorHomeCategory("smartdevice.intent.category.LITE_HOME"))
        assertFalse(StockHomeCatalog.isVendorHomeCategory("android.intent.category.SECONDARY_HOME"))
        assertFalse(StockHomeCatalog.isVendorHomeCategory("android.intent.category.HOME"))
        val set = StockHomeCatalog.collect(
            ours, ours,
            homeIntent = emptyList(),
            mainIntent = listOf(
                HomeCandidate("com.coocaa.launcher", "com.coocaa.launcher.Home", categories = setOf("android.intent.category.HOME.CC")),
                HomeCandidate("com.coocaa.lite", "com.coocaa.lite.Lite", categories = setOf("smartdevice.intent.category.LITE_HOME")),
                HomeCandidate("com.android.settings", "com.android.settings.Fallback", categories = setOf("android.intent.category.SECONDARY_HOME")),
            ),
            activityExists = { _, _ -> false },
        )
        assertEquals(setOf("com.coocaa.launcher", "com.coocaa.lite"), set.homes.keys)
    }

    @Test fun patchwallLeftoverActivityIsIncludedWhenPresent() {
        val present = StockHomeCatalog.collect(ours, ours, emptyList(), emptyList()) { _, cls ->
            cls == "com.mitv.tvhome.StrategicLayerActivity"
        }
        assertTrue("com.mitv.tvhome.StrategicLayerActivity" in present.homes.getValue("com.mitv.tvhome"))
        val absent = StockHomeCatalog.collect(ours, ours, emptyList(), emptyList()) { _, _ -> false }
        assertFalse(absent.homes.containsKey("com.mitv.tvhome"))
    }

    @Test fun inputPlayersAreNotStockHomesUnlessHomeResolvesToThem() {
        val player = HomeCandidate("com.tcl.tv", "com.tcl.tv.TvActivity", priority = 1)
        val excluded = StockHomeCatalog.collect(ours, "com.tcl.cyberui", listOf(player), emptyList())
        assertFalse(excluded.homes.containsKey("com.tcl.tv"))
        val resolved = StockHomeCatalog.collect(ours, "com.tcl.tv", listOf(player), emptyList())
        assertTrue(resolved.homes.containsKey("com.tcl.tv"))
    }

    @Test fun negativePriorityFallbacksAreNotHomesExceptTheKnownVidaaActivity() {
        val set = StockHomeCatalog.collect(
            ours, ours,
            homeIntent = listOf(
                HomeCandidate("com.android.provision", "com.android.provision.DefaultActivity", priority = -1),
                HomeCandidate("com.keylab.speech.view.vidaa", "com.ju.secondscreen.HomeActivity", priority = -150),
            ),
            mainIntent = emptyList(),
        )
        assertFalse(set.homes.containsKey("com.android.provision"))
        assertTrue("com.android.provision" to "com.android.provision.DefaultActivity" in set.fallbacks)
        assertTrue(set.homes.getValue("com.keylab.speech.view.vidaa").contains("com.ju.secondscreen.HomeActivity"))
    }

    @Test fun ourPackageAndNonLaunchersAreLeftOut() {
        val set = StockHomeCatalog.collect(
            ours, ours,
            homeIntent = listOf(
                HomeCandidate(ours, "com.leslie.tvhome.HomeActivity"),
                HomeCandidate("com.stv.appdaemon", "com.stv.appdaemon.DaemonActivity"),
            ),
            mainIntent = emptyList(),
        )
        assertFalse(set.homes.containsKey(ours))
        assertFalse(set.homes.containsKey("com.stv.appdaemon"))
    }
}
