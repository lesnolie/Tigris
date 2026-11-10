package com.leslie.tvhome

/** Optional gesture never overrides first Home press; uses monotonic uptime. */
internal object DoubleHomePolicy {
    private const val MIN_GAP_MS = 70L
    private const val MAX_GAP_MS = 520L

    fun second(previous: Long, now: Long, allowed: Boolean): Boolean =
        allowed && previous > 0 && now > previous &&
            now - previous in MIN_GAP_MS..MAX_GAP_MS
}
