package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class WallhavenApiTest {
    @Test fun queryCannotOverridePublicFilters() {
        val url = WallhavenApi.search("space &purity=111", "111&apikey=secret", "invalid", "invalid")
        assertTrue(url.contains("q=space+%26purity%3D111"))
        assertTrue(url.contains("&purity=100"))
        assertTrue(url.contains("&categories=100"))
        assertTrue(url.contains("&atleast=3840x2160"))
        assertFalse(url.contains("apikey="))
    }

    @Test fun supportedFiltersAndUnicodeArePreserved() {
        val url = WallhavenApi.search("星空", "010", "random", "1920x1080")
        assertTrue(url.contains("q=%E6%98%9F%E7%A9%BA"))
        assertTrue(url.contains("&categories=010"))
        assertTrue(url.contains("&sorting=random"))
        assertTrue(url.contains("&atleast=1920x1080"))
        assertTrue(url.contains("&ratios=16x9"))
    }
}
