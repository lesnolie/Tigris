package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class VideoGlassFramesTest {
    @Test fun onlyOneGlassBakeCanBePending() {
        val p = VideoGlassFrames()
        val first = p.begin()!!
        repeat(100) { assertNull(p.begin()) }
        assertTrue(p.finish(first))
        assertFalse(p.busy)
    }

    @Test fun switchingVideoOrPausingRejectsLateResult() {
        val p = VideoGlassFrames()
        val old = p.begin()!!
        p.invalidate()
        assertFalse(p.finish(old))
        val fresh = p.begin()!!
        assertFalse(p.finish(old))
        assertTrue(p.finish(fresh))
    }
}
