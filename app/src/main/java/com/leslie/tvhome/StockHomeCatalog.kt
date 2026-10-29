package com.leslie.tvhome

/**
 * Which activities count as a stock launcher's home screen.
 *
 * Portions adapted from Aerial (https://github.com/pengchujin/aerial-tv), MIT License,
 * Copyright (c) 2026 pengchujin. Activity-alias targets match the approach in Tigris PR #61.
 *
 * Standard CATEGORY_HOME misses Sony's alias target, Coocaa's HOME.CC / LITE_HOME filters,
 * and PatchWall screens the firmware shows without a HOME filter (StrategicLayerActivity).
 * TV players that also declare HOME stay out, unless the system actually resolves Home to them,
 * so an HDMI switch is not taken for the launcher.
 */
internal data class HomeCandidate(
    val packageName: String,
    val name: String,
    val targetActivity: String? = null,
    val priority: Int = 0,
    val categories: Set<String> = emptySet(),
)

internal data class StockHomeSet(
    val homes: Map<String, Set<String>>,
    /** Negative-priority fallbacks (setup wizard, FallbackHome). Seeing one does not mean it is still in front. */
    val fallbacks: Set<Pair<String, String>>,
)

internal object StockHomeCatalog {
    val inputPlayers: Set<String> = setOf(
        "com.tcl.tv",
        "com.xiaomi.mitv.tvplayer",
        "com.skyworth.tv",
        "com.hisilicon.tvui",
    )

    /** Packages that declare HOME but are not a launcher to keep away from. */
    val notLaunchers: Set<String> = setOf("com.stv.appdaemon", "com.letv.lekids")

    /**
     * Launcher screens a firmware starts by component with no HOME filter.
     * PatchWall's StrategicLayerActivity is what stays up when Tigris is killed.
     * BootActivity is not listed: it is only a boot-window cover, handled by the guard.
     */
    val unfilteredHomes: Map<String, List<String>> = mapOf(
        "com.mitv.tvhome" to listOf("com.mitv.tvhome.StrategicLayerActivity"),
        "com.jamdeo.tv.vod" to listOf("com.ju.main.launcher.LauncherActivity"),
        "com.coocaa.aigc.home" to listOf("com.coocaa.aigc.home.home.rootpage.HomeActivity"),
    )

    /** Negative-priority activities a firmware really starts. */
    val lowPriorityHomes: Map<String, String> = mapOf(
        "com.keylab.speech.view.vidaa" to "com.ju.secondscreen.HomeActivity",
    )

    const val PATCHWALL = "com.mitv.tvhome"
    const val PATCHWALL_BOOT = "com.mitv.tvhome.BootActivity"
    const val VIDAA_LAUNCHER = "com.jamdeo.tv.vod"

    fun isVendorHomeCategory(category: String): Boolean =
        category.startsWith("android.intent.category.HOME.") ||
            (category.endsWith("_HOME") && category != "android.intent.category.SECONDARY_HOME")

    fun collect(
        ours: String,
        resolvedPackage: String?,
        homeIntent: List<HomeCandidate>,
        mainIntent: List<HomeCandidate>,
        activityExists: (String, String) -> Boolean = { _, _ -> true },
    ): StockHomeSet {
        val vendor = mainIntent.filter { candidate -> candidate.categories.any(::isVendorHomeCategory) }
        val fallbacks = homeIntent
            .filter { it.packageName != ours && it.priority < 0 && !isLowPriority(it) }
            .flatMap { candidate ->
                StockHomeActivities.classes(candidate.packageName, candidate.name, candidate.targetActivity)
                    .map { candidate.packageName to it }
            }
            .toSet()
        val selected = (homeIntent + vendor)
            .filter { it.packageName != ours }
            .filter { it.priority >= 0 || isLowPriority(it) }
            .filter { it.packageName !in inputPlayers || it.packageName == resolvedPackage }
            .filter { it.packageName !in notLaunchers }
        val grouped = selected.groupBy { it.packageName }.mapValues { (pkg, items) ->
            items.flatMap { StockHomeActivities.classes(pkg, it.name, it.targetActivity) }.toSet()
        }
        val extras = unfilteredHomes.mapValues { (pkg, classes) ->
            classes.filter { activityExists(pkg, it) }.toSet()
        }.filterValues { it.isNotEmpty() }
        val homes = (grouped.keys + extras.keys).associateWith { pkg ->
            grouped[pkg].orEmpty() + extras[pkg].orEmpty()
        }.filterValues { it.isNotEmpty() }
        return StockHomeSet(homes, fallbacks)
    }

    private fun isLowPriority(candidate: HomeCandidate): Boolean {
        val expected = lowPriorityHomes[candidate.packageName] ?: return false
        return expected == candidate.name ||
            expected in StockHomeActivities.classes(candidate.packageName, candidate.name, candidate.targetActivity)
    }
}
