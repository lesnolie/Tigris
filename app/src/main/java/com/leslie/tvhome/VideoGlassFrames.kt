package com.leslie.tvhome

/** A stale or overlapping video frame may never overwrite a newer glass texture. */
internal class VideoGlassFrames {
    private var generation = 0L
    private var sequence = 0L
    private var pending: Long? = null
    private var pendingGeneration = 0L
    val busy: Boolean get() = pending != null

    @Synchronized fun begin(): Long? {
        if (busy) return null
        pendingGeneration = generation
        return (++sequence).also { pending = it }
    }

    @Synchronized fun invalidate() { generation++ }

    @Synchronized fun finish(ticket: Long): Boolean {
        if (pending != ticket) return false
        pending = null
        return pendingGeneration == generation
    }
}
