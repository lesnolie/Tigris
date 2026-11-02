package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun numericOrdering() {
        assertTrue(UpdatePolicy.newer("v1.2.10", "1.2.9"))
        assertFalse(UpdatePolicy.newer("v1.2.7", "1.2.7"))
        assertFalse(UpdatePolicy.newer("v2.0.2", "2.0.3"))
        assertTrue(UpdatePolicy.newer("v2.0.4", "2.0.3-local"))
        // A 2.0.17-test suffix still compares as 2.0.17, so it would not offer the v2.0.17 release.
        assertTrue(UpdatePolicy.newer("v2.0.17", "2.0.16-test73"))
        // The hyphen suffix is not part of the numeric version, so the names tie.
        assertFalse(UpdatePolicy.newer("v2.0.17", "2.0.17-test73"))
        assertFalse(UpdatePolicy.newer("v2.0.17", "2.0.17-test7679"))
        assertTrue(UpdatePolicy.sameNumeric("v2.0.17", "2.0.17-test7679"))
    }

    @Test fun versionCodeNeverOffersADowngradeAndBreaksANameTie() {
        assertFalse(UpdatePolicy.offer("v2.0.17", 23L, "2.0.17-test7679", 24L))
        assertFalse(UpdatePolicy.offer("v2.0.17", null, "2.0.17-test7679", 24L))
        assertFalse(UpdatePolicy.offer("v2.0.19", 23L, "2.0.17-test7679", 24L))
        assertFalse(UpdatePolicy.offer("v2.0.17", 23L, "2.0.17", 23L))
        assertTrue(UpdatePolicy.offer("v2.0.17", 24L, "2.0.17", 23L))
        assertTrue(UpdatePolicy.offer("v2.0.18", null, "2.0.17-test7679", 24L))
        assertTrue(UpdatePolicy.offer("v2.0.18", 25L, "2.0.17-test7679", 24L))
        assertTrue(UpdatePolicy.offer("v2.0.18", 25L, null, null))
    }
    @Test fun rejectsPreviewAndMalformedTags() {
        assertFalse(UpdatePolicy.newer("v2.1.0-rc1", "2.0.3"))
        assertFalse(UpdatePolicy.newer("nightly", "2.0.3"))
        assertFalse(UpdatePolicy.newer("v99999999999999999.0", "2.0.3"))
        assertFalse(UpdatePolicy.newer("v2.0.0", "unknown"))
    }
}
