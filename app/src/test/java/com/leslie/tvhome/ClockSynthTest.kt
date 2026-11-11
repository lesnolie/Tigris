package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClockSynthTest {
    @Test fun everySoundRendersAudibleBoundedSamples() {
        for (sound in ClockSynth.Sound.values()) {
            val samples = ClockSynth.render(sound)
            assertTrue("${sound.file} is empty", samples.isNotEmpty())
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("${sound.file} is silent", peak > 1000)
            assertTrue("${sound.file} clips", peak < Short.MAX_VALUE)
        }
    }

    @Test fun ticksAreShortEnoughForOnePerSecond() {
        for (sound in listOf(ClockSynth.Sound.TICK_SOFT, ClockSynth.Sound.FLAP, ClockSynth.Sound.TICK_DIGITAL, ClockSynth.Sound.TICK_NEON))
            assertTrue(ClockSynth.render(sound).size < ClockSynth.RATE / 5)
    }

    /** The buzz follows the tubes: silent before they strike, silent in the dark stutter, on when lit. */
    @Test fun neonStrikeFollowsTheFlicker() {
        val s = ClockSynth.render(ClockSynth.Sound.NEON_STRIKE)
        fun loudness(fromMs: Int, toMs: Int) =
            (fromMs * ClockSynth.RATE / 1000 until toMs * ClockSynth.RATE / 1000).maxOf { abs(s[it].toInt()) }
        assertEquals(0, loudness(0, 70))
        assertTrue(loudness(90, 135) > 4 * loudness(160, 250))
        assertTrue(loudness(600, 850) > 3000)
    }

    @Test fun wavHeaderDescribesMono16BitPcm() {
        val wav = ClockSynth.wav(ShortArray(10))
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(44 + 20, wav.size)
    }
}
