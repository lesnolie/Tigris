package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class VideoGlassFramesTest {
    @Test fun slowBakeCannotQueueEveryVideoFrame() {
        val frames = VideoGlassFrames()
        val ticket = frames.begin()!!
        repeat(120) { assertNull(frames.begin()) }
        assertTrue(frames.finish(ticket))
        assertFalse(frames.busy)
        assertNotNull(frames.begin())
    }

    @Test fun switchingToPhotoWhileBakeRunsRejectsOldVideoResult() {
        val frames = VideoGlassFrames()
        val video = frames.begin()!!
        frames.invalidate()
        assertFalse(frames.finish(video))
        val next = frames.begin()!!
        assertTrue(frames.finish(next))
    }

    @Test fun pauseAndNewVideoDoNotAllowLateCallbackToClearNewWork() {
        val frames = VideoGlassFrames()
        val old = frames.begin()!!
        frames.invalidate()
        assertFalse(frames.finish(old))
        val current = frames.begin()!!
        assertFalse(frames.finish(old))
        assertTrue(frames.busy)
        assertTrue(frames.finish(current))
    }

    @Test fun duplicateCompletionCannotConsumeNextFrameOfSameVideo() {
        val frames = VideoGlassFrames()
        val first = frames.begin()!!
        assertTrue(frames.finish(first))
        val next = frames.begin()!!
        assertFalse(frames.finish(first))
        assertTrue(frames.busy)
        assertTrue(frames.finish(next))
    }
}
