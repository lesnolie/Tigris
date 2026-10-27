package com.leslie.tvhome

/** Device-specific installation guidance, not an installation executor. */
internal object HomeInstallGuide {
    fun nextStep(manufacturer: String, brand: String, nativeHome: Boolean,
                 guardBound: Boolean, secureSettings: Boolean): Int {
        if (nativeHome) return R.string.install_home_next_native
        if (guardBound) return R.string.install_home_next_guard
        val identity = "$manufacturer $brand".lowercase()
        return when {
            identity.contains("xiaomi") || identity.contains("redmi") ->
                R.string.install_home_next_xiaomi
            identity.contains("tcl") || identity.contains("ffalcon") ->
                R.string.install_home_next_tcl
            !secureSettings -> R.string.install_home_next_permission
            else -> R.string.install_home_next_generic
        }
    }
}
