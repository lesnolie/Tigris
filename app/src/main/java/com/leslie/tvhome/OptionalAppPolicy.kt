package com.leslie.tvhome

/**
 * Strict boundaries for separately installed third-party TV apps.
 * These are independent APKs, not bundled Tigris code or system components.
 *
 * First install accepts only the documented original upstream signing identity.
 * Any upstream certificate rotation requires an explicit reviewed Tigris change.
 */
internal object OptionalAppPolicy {
    const val MIRACAST_PKG = "com.weekd.miracastreceiver"
    const val HGTV_PKG = "com.hgtv.star"

    // Both fingerprints originate in their respective upstream release records.
    const val MIRACAST_CERT = "ea7f4568203d294bd37acac966d6d707f79147549d24c2244f58a6fbc27a6291"
    const val HGTV_CERT = "b237b1d3714a5a010b453e0a51ed32bceac40d8d4207c61bfdf8d6c97a0caeaa"

    fun isOptional(pkg: String) = pkg == MIRACAST_PKG || pkg == HGTV_PKG

    fun trustedCertificate(pkg: String) = when (pkg) {
        MIRACAST_PKG -> MIRACAST_CERT
        HGTV_PKG -> HGTV_CERT
        else -> null
    }

    fun selectApk(pkg: String, tag: String, names: Collection<String>): String? = when (pkg) {
        MIRACAST_PKG -> names.singleOrNull {
            it.matches(Regex("^miracast-receiver-v[0-9]+(?:\\.[0-9]+)*-universal\\.apk$")) &&
                it == "miracast-receiver-${tag}-universal.apk"
        }
        HGTV_PKG -> {
            if (!tag.matches(Regex("^v[0-9]+(?:\\.[0-9]+)+$"))) null
            else names.singleOrNull { it == "HGTV_${tag.removePrefix("v")}.apk" }
        }
        else -> null
    }

    /** External APKs always need a GitHub Release asset digest; missing hashes fail closed. */
    fun validDigest(pkg: String, digest: String): Boolean =
        !isOptional(pkg) || digest.matches(Regex("^sha256:[0-9a-fA-F]{64}$"))

    /** Never silently install either optional component for a new user. */
    fun mayAutoUpdate(pkg: String, alreadyInstalled: Boolean): Boolean =
        !isOptional(pkg) || alreadyInstalled
}
