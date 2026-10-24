package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQualityTest {
    private val avc = "https://sylvan.apple.com/Videos/SAMPLE_SDR_2K_AVC.mov"
    private val hevc = "https://sylvan.apple.com/Aerials/2x/Videos/SAMPLE_SDR_2K_HEVC.mov"

    @Test fun twoGigabyteClassTvCanUseUhdWhenMemoryIsAvailable() {
        val mib = 1024L * 1024
        assertTrue(MediaQuality.hasUhdWallpaperBudget(
            lowRam = false, lowMemory = false, totalBytes = 1900 * mib,
            availableBytes = 600 * mib, heapMb = 192
        ))
        assertFalse(MediaQuality.hasUhdWallpaperBudget(
            lowRam = true, lowMemory = false, totalBytes = 1900 * mib,
            availableBytes = 600 * mib, heapMb = 192
        ))
        assertFalse(MediaQuality.hasUhdWallpaperBudget(
            lowRam = false, lowMemory = true, totalBytes = 1900 * mib,
            availableBytes = 600 * mib, heapMb = 192
        ))
        assertFalse(MediaQuality.hasUhdWallpaperBudget(
            lowRam = false, lowMemory = false, totalBytes = 1900 * mib,
            availableBytes = 128 * mib, heapMb = 192
        ))
        assertFalse(MediaQuality.hasUhdWallpaperBudget(
            lowRam = false, lowMemory = false, totalBytes = 1900 * mib,
            availableBytes = 600 * mib, heapMb = 128
        ))
    }

    @Test fun uhdHevcPreferredButRetainsTwoFallbacks() {
        assertEquals(listOf(
            "https://sylvan.apple.com/Aerials/2x/Videos/SAMPLE_SDR_4K_HEVC.mov",
            hevc, avc,
        ), MediaQuality.aerialCandidates(avc, hevc, true, true))
    }

    @Test fun incompatibleDecoderUsesAvcOnly() {
        assertEquals(listOf(avc), MediaQuality.aerialCandidates(avc, hevc, true, false))
    }

    @Test fun compatible2kUsesHevcThenAvc() {
        assertEquals(listOf(hevc, avc), MediaQuality.aerialCandidates(avc, hevc, false, true))
    }

    @Test fun no4kSubstitutionForNonstandardUrl() {
        val special = "https://example.org/not_a_2k_variant.mov"
        assertEquals(listOf(special, avc), MediaQuality.aerialCandidates(avc, special, true, true))
    }

    @Test fun missingHevcFallsBackToAvc() {
        assertEquals(listOf(avc), MediaQuality.aerialCandidates(avc, null, true, true))
    }
}
