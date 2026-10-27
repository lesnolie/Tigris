package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class OptionalAppPolicyTest {
    @Test fun useOnlyCanonicalHGTVApkFromMatchedRelease() {
        assertEquals("HGTV_1.2.6.apk", OptionalAppPolicy.selectApk(
            OptionalAppPolicy.HGTV_PKG, "v1.2.6",
            listOf("HGTV_1.2.6.apk", "update.json", "SHA256SUMS")))
        assertNull(OptionalAppPolicy.selectApk(OptionalAppPolicy.HGTV_PKG, "v1.2.7",
            listOf("HGTV_1.2.6.apk")))
        assertNull(OptionalAppPolicy.selectApk(OptionalAppPolicy.HGTV_PKG, "v1.2.6",
            listOf("HGTV_1.2.6.apk", "HGTV_1.2.6.apk")))
        assertNull(OptionalAppPolicy.selectApk(OptionalAppPolicy.HGTV_PKG, "../evil",
            listOf("HGTV_1.2.6.apk")))
    }

    @Test fun castingStillSelectsUniversalOnly() {
        assertEquals("miracast-receiver-v1.2.8-universal.apk",
            OptionalAppPolicy.selectApk(OptionalAppPolicy.MIRACAST_PKG, "v1.2.8",
                listOf("miracast-receiver-v1.2.8-universal.apk",
                    "miracast-receiver-v1.2.8-arm64-v8a.apk")))
        assertNull(OptionalAppPolicy.selectApk(OptionalAppPolicy.MIRACAST_PKG, "v1.2.8",
            listOf("miracast-receiver-v1.2.8-arm64-v8a.apk")))
    }

    @Test fun optionalDownloadsNeedReleaseDigestAndTrustedSigner() {
        val digest = "sha256:" + "a".repeat(64)
        for (pkg in listOf(OptionalAppPolicy.MIRACAST_PKG, OptionalAppPolicy.HGTV_PKG)) {
            assertTrue(OptionalAppPolicy.validDigest(pkg, digest))
            assertFalse(OptionalAppPolicy.validDigest(pkg, ""))
            assertFalse(OptionalAppPolicy.validDigest(pkg, "sha256:invalid"))
            assertEquals(64, OptionalAppPolicy.trustedCertificate(pkg)?.length)
            assertFalse(OptionalAppPolicy.mayAutoUpdate(pkg, false))
            assertTrue(OptionalAppPolicy.mayAutoUpdate(pkg, true))
        }
        assertNull(OptionalAppPolicy.trustedCertificate("random.apk"))
    }
}
