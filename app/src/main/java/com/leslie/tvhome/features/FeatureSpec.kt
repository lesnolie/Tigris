package com.leslie.tvhome.features

/** Brand-independent extension contract. Owners control lifecycle; no plugin loader/service. */
data class FeatureSpec(
    val id: String,
    val name: String,
    val purpose: String,
    val capabilities: Set<String>,
    val minSdk: Int = 28,
    val settings: Set<String>,
    val defaultEnabled: Boolean,
    val permissions: Set<String>,
    val fallback: String,
    val lifecycle: String,
    val testMethod: String,
) {
    fun supported(sdk: Int, available: Set<String>): Boolean =
        sdk >= minSdk && available.containsAll(capabilities)
}

/** Existing implementations remain the owners of their rendering, cache and settings. */
object Features {
    val weather = FeatureSpec(
        id = "weather", name = "Weather", purpose = "Optional city forecast",
        capabilities = emptySet(), settings = setOf("weather_enabled", "weather_city",
            "weather_latitude", "weather_longitude", "weather_fahrenheit"),
        defaultEnabled = false, permissions = setOf("android.permission.INTERNET"),
        fallback = "Use cached forecast; show unavailable without a usable cache",
        lifecycle = "HomeActivity refresh only while enabled; ignore callbacks after destruction",
        testMethod = "FeatureSpecTest; enable/disable, offline cache and destroy during request",
    )
    val liquidGlass = FeatureSpec(
        id = "liquid-glass", name = "Liquid Glass", purpose = "Control Center appearance",
        capabilities = emptySet(), settings = setOf("cc_style"), defaultEnabled = true,
        permissions = emptySet(), fallback = "Existing auto mode selects panel on low-end hardware",
        lifecycle = "ControlCenter owns views and animations; close cancels its view lifecycle",
        testMethod = "Build both flavors; compare auto/glass/panel on hardware",
    )
    val dock = FeatureSpec(
        id = "dock", name = "Dock", purpose = "Launch and arrange applications",
        capabilities = emptySet(), settings = setOf("focus_style"), defaultEnabled = true,
        permissions = emptySet(), fallback = "Existing simple focus animation",
        lifecycle = "HomeActivity owns tiles; preserve existing layout and folder persistence",
        testMethod = "RecentOrderTest; hardware navigation, folders and saved layout",
    )
    val hdmi = FeatureSpec(
        id = "hdmi", name = "HDMI", purpose = "Select a TV input through TvControls",
        capabilities = setOf("tv.inputs"), settings = emptySet(), defaultEnabled = true,
        permissions = emptySet(), fallback = "Unavailable when adapter cannot expose inputs",
        lifecycle = "ControlCenter reads adapter off the UI thread; owner releases views",
        testMethod = "Both-flavor build; physical input switch and return HOME",
    )
    val all = listOf(weather, liquidGlass, dock, hdmi)
}
