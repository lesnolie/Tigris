package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMaterialTest {
    private fun solid(argb: Int, size: Int = 64): IntArray = IntArray(size * size) { argb }

    @Test
    fun brightBackdropChoosesLightGlassAndDarkInk() {
        val tone = GlassMaterial.analyze(
            solid(0xffece3d4.toInt()), 64, 64, 0, 0, 64, 64,
        )
        assertTrue(tone.light)
        assertEquals(GlassMaterial.tone(tone.luminance, tone.complexity, tone.average).ink, tone.ink)
        assertTrue(GlassMaterial.luminance(tone.ink) < 0.2f)
    }

    @Test
    fun darkBackdropChoosesDarkGlassAndLightInk() {
        val tone = GlassMaterial.analyze(
            solid(0xff121826.toInt()), 64, 64, 0, 0, 64, 64,
        )
        assertFalse(tone.light)
        assertTrue(GlassMaterial.luminance(tone.ink) > 0.85f)
    }

    @Test
    fun busyBackdropGetsMoreTintThanSmoothBackdrop() {
        val smooth = GlassMaterial.analyze(
            solid(0xffb8b8b8.toInt()), 64, 64, 0, 0, 64, 64,
        )
        val busy = IntArray(64 * 64) { index ->
            val x = index % 64
            val y = index / 64
            if ((x / 4 + y / 4) % 2 == 0) 0xfff4ead8.toInt() else 0xff102030.toInt()
        }
        val detailed = GlassMaterial.analyze(busy, 64, 64, 0, 0, 64, 64)
        assertTrue(detailed.complexity > smooth.complexity)
        assertTrue(
            GlassMaterial.tintAlpha(detailed.light, detailed.complexity) >
                GlassMaterial.tintAlpha(smooth.light, smooth.complexity),
        )
    }

    @Test
    fun tintAlphaStaysInsideTheReferenceRange() {
        assertEquals(0.06f, GlassMaterial.tintAlpha(true, 0f), 0.001f)
        assertEquals(0.22f, GlassMaterial.tintAlpha(true, 1f), 0.001f)
        assertEquals(0.16f, GlassMaterial.tintAlpha(false, 0f), 0.001f)
        assertEquals(0.38f, GlassMaterial.tintAlpha(false, 1f), 0.001f)
    }

    @Test
    fun inkUsesFixedTokensBeforeCompositingWithTheBackdrop() {
        val warm = GlassMaterial.analyze(solid(0xffffe0c0.toInt()), 64, 64, 0, 0, 64, 64)
        val cool = GlassMaterial.analyze(solid(0xffc0e0ff.toInt()), 64, 64, 0, 0, 64, 64)
        assertTrue(warm.light)
        assertTrue(cool.light)
        assertEquals(warm.ink, cool.ink)
        assertEquals(0xdb000000.toInt(), warm.ink)
    }

    @Test
    fun samplingRectIsClampedToTheBitmap() {
        val tone = GlassMaterial.analyze(
            solid(0xff808080.toInt(), 16), 16, 16, -50, -50, 999, 999,
        )
        assertEquals(0.5f, tone.luminance, 0.02f)
    }
}
