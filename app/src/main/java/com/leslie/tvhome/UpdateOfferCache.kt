package com.leslie.tvhome

/** A remembered offer stays usable after a transient API failure, not after an upgrade. */
internal object UpdateOfferCache {
    fun usable(available: Boolean, version: String?, url: String?, digest: String?,
               size: Long, repository: String, installed: String?): Boolean {
        if (!available || version.isNullOrBlank() || url.isNullOrBlank()) return false
        if (!url.startsWith("https://github.com/$repository/releases/download/")) return false
        if (size !in 1..250_000_000L) return false
        if (digest == null || !digest.matches(Regex("^sha256:[0-9a-fA-F]{64}$"))) return false
        return installed == null || UpdatePolicy.newer(version, installed)
    }
}
