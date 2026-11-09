package com.leslie.tvhome

/** Pure, deterministic ordering shared by the instant recent-app view and background usage scan. */
internal object RecentAppOrder {
    private const val MAX_VISIBLE = 12

    /** Prefer observed foreground activity; fall back to Tigris launch history.
     * Never include apps that are no longer installed or are not real launchable apps. */
    fun merge(observed: List<String>, saved: List<String>, available: Set<String>): List<String> =
        (observed + saved).asSequence()
            .filter { it in available }
            .distinct()
            .take(MAX_VISIBLE)
            .toList()
}
