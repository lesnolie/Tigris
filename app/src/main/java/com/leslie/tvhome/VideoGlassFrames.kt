package com.leslie.tvhome

/** One bake at a time; source changes/pause invalidate work already running off-thread. */
internal class VideoGlassFrames {
    private var generation = 0L
    private var sequence = 0L
    private var pending: Long? = null
    private var pendingGeneration = 0L
    val busy: Boolean get() = pending != null

    fun begin(): Long? {
        if (busy) return null
        pendingGeneration = generation
        return (++sequence).also { pending = it }
    }

    fun invalidate() { generation++ }

    fun finish(ticket: Long): Boolean {
        if (pending != ticket) return false
        pending = null
        return pendingGeneration == generation
    }
}
