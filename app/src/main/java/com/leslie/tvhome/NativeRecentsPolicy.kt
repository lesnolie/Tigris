package com.leslie.tvhome

/** Capability-based routing: never infer native Recents from the TV brand. */
internal object NativeRecentsPolicy {
    private const val SYSTEM_ACTIONS_API_LEVEL = 30

    /** Android 11+ reports available system actions. Earlier versions can only try the action. */
    fun canAttempt(serviceConnected: Boolean, sdkInt: Int, recentsAdvertised: Boolean): Boolean =
        serviceConnected && (sdkInt < SYSTEM_ACTIONS_API_LEVEL || recentsAdvertised)
}
