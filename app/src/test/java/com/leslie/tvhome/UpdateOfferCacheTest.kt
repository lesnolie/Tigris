package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class UpdateOfferCacheTest {
    private val repo = "lesnolie/Tigris"
    private val url = "https://github.com/lesnolie/Tigris/releases/download/v2.0.20/Tigris-v2.0.20.apk"
    private val digest = "sha256:" + "a".repeat(64)

    @Test fun retainsOnlyACompleteCheckedRelease() {
        assertTrue(UpdateOfferCache.usable(true, "v2.0.20", url, digest, 2314136L, repo, "2.0.19"))
        assertFalse(UpdateOfferCache.usable(false, "v2.0.20", url, digest, 2314136L, repo, "2.0.19"))
        assertFalse(UpdateOfferCache.usable(true, "v2.0.20", url, "", 2314136L, repo, "2.0.19"))
        assertFalse(UpdateOfferCache.usable(true, "v2.0.20", "https://evil.example/Tigris.apk", digest, 2314136L, repo, "2.0.19"))
        assertFalse(UpdateOfferCache.usable(true, "v2.0.20", url, digest, 0L, repo, "2.0.19"))
    }

    @Test fun anAlreadyInstalledVersionMakesTheOldOfferUnusable() {
        assertFalse(UpdateOfferCache.usable(true, "v2.0.20", url, digest, 2314136L, repo, "2.0.20"))
        assertFalse(UpdateOfferCache.usable(true, "v2.0.20", url, digest, 2314136L, repo, "2.0.21"))
        assertTrue(UpdateOfferCache.usable(true, "v2.0.20", url, digest, 2314136L, repo, null))
    }
}
