package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class AppTileArtworkTest {
    @Test fun nativeWideBannerFillsCardWithoutDistortion() {
        val p = AppTileArtwork.banner(1280, 720, 400, 225)
        assertEquals(AppTileArtwork.Mode.COVER, p.mode)
        assertEquals(0, p.left)
        assertEquals(0, p.top)
        assertEquals(400, p.width)
        assertEquals(225, p.height)
    }

    @Test fun squareAndExtremelyWideBannersAreKeptUncropped() {
        val square = AppTileArtwork.banner(512, 512, 400, 225)
        assertEquals(AppTileArtwork.Mode.CONTAIN, square.mode)
        assertTrue(square.left > 0 && square.top >= 0)
        assertTrue(square.right <= 400 && square.bottom <= 225)
        assertEquals(square.width, square.height)

        val panoramic = AppTileArtwork.banner(1440, 480, 400, 225)
        assertEquals(AppTileArtwork.Mode.CONTAIN, panoramic.mode)
        assertTrue(panoramic.left >= 0 && panoramic.right <= 400)
        assertTrue(panoramic.top > 0)
    }

    @Test fun nearWidescreenBannerCanCenterCropButNeverStretch() {
        val p = AppTileArtwork.banner(1280, 800, 400, 225)
        assertEquals(AppTileArtwork.Mode.COVER, p.mode)
        assertTrue(p.height > 225)
        assertEquals(400, p.width)
        assertEquals(0, p.left)
    }

    @Test fun tinyBannersUseTheCleanIconFallbackInstead() {
        assertFalse(AppTileArtwork.usableBanner(96, 54))
        assertFalse(AppTileArtwork.usableBanner(400, 50))
        assertTrue(AppTileArtwork.usableBanner(320, 180))
    }

    @Test fun iconHasConsistentOpticalWeightAcrossGridSizes() {
        assertEquals(144, AppTileArtwork.iconSize(225))
        assertEquals(72, AppTileArtwork.iconSize(112))
        assertEquals(1, AppTileArtwork.iconSize(1))
    }
}
