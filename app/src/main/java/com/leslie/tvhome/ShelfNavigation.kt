package com.leslie.tvhome

/** Pure Top Shelf navigation and asynchronous result gating; no Android dependencies. */
internal object ShelfNavigation {
    /** Wrap left and right without ever producing an invalid item index. */
    fun step(current: Int, count: Int, delta: Int): Int {
        if (count <= 0) return -1
        return Math.floorMod(current + delta, count)
    }

    /** Never apply an old focus session, superseded image or hidden shelf response. */
    fun accepts(
        session: Long,
        currentSession: Long,
        request: Long,
        latestRequest: Long,
        expectedPackage: String,
        focusedPackage: String?,
        visible: Boolean,
        blocked: Boolean,
    ): Boolean =
        session == currentSession && request == latestRequest &&
            expectedPackage == focusedPackage && visible && !blocked
}
