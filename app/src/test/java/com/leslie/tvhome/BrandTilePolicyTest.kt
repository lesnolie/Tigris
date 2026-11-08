package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class BrandTilePolicyTest {
    @Test fun respectsForcedClassicAndOriginalIconOverrides() {
        assertEquals(BrandTilePolicy.Source.CLASSIC, BrandTilePolicy.select(true, "auto", true, true, true))
        assertEquals(BrandTilePolicy.Source.CLASSIC, BrandTilePolicy.select(false, "classic", true, true, true))
        assertEquals(BrandTilePolicy.Source.ICON, BrandTilePolicy.select(false, "icon", true, false, true))
        assertEquals(BrandTilePolicy.Source.ADAPTIVE, BrandTilePolicy.select(false, "icon", true, true, true))
    }
    @Test fun bannerAdaptiveIconFallbackAndUnknownAppAlwaysResolve() {
        assertEquals(BrandTilePolicy.Source.BANNER, BrandTilePolicy.select(false, "auto", true, false, true))
        assertEquals(BrandTilePolicy.Source.ADAPTIVE, BrandTilePolicy.select(false, "auto", false, true, true))
        assertEquals(BrandTilePolicy.Source.ICON, BrandTilePolicy.select(false, "auto", false, false, true))
        assertEquals(BrandTilePolicy.Source.PLACEHOLDER, BrandTilePolicy.select(false, "auto", false, false, false))
    }
    @Test fun bannerQualityRequiresSufficientPixelsAndReasonableAspect() {
        assertFalse(BrandTilePolicy.goodBanner(96, 54))
        assertFalse(BrandTilePolicy.goodBanner(1000, 150))
        assertTrue(BrandTilePolicy.goodBanner(1280, 720))
        assertTrue(BrandTilePolicy.goodBanner(800, 480))
    }
    @Test fun onlyVerifiedForegroundsCanParallax() {
        assertTrue(BrandTilePolicy.validForeground(1800, 10000, false))
        assertFalse(BrandTilePolicy.validForeground(9000, 10000, false))
        assertFalse(BrandTilePolicy.validForeground(1700, 10000, true))
        assertFalse(BrandTilePolicy.validForeground(0, 0, false))
    }
    @Test fun geometryAndBrandColorAreBounded() {
        assertEquals(240, BrandTilePolicy.renderedHeight(400, Prefs.TILE_TVOS))
        assertEquals(225, BrandTilePolicy.renderedHeight(400, Prefs.TILE_WIDE))
        val color = BrandTilePolicy.brandColor(0xFFFFFFFF.toInt())
        assertEquals(0xFF, color ushr 24)
        assertTrue((color ushr 16 and 255) <= 128)
    }
}
