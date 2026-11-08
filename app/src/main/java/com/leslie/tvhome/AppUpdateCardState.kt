package com.leslie.tvhome

/** Pure state policy for the single-action tvOS-style app update cards. */
internal object AppUpdateCardState {
    enum class Action {
        GET, OPEN, UPDATE, CHECK, CURRENT, UNINSTALL,
        DOWNLOADING, VERIFYING, WAITING, CONFIRM, RETRY
    }

    data class Snapshot(
        val installed: Boolean,
        val optional: Boolean,
        val checked: Boolean,
        val updateAvailable: Boolean,
        val phase: String = "idle",
        val hasConfirmation: Boolean = false,
        /** A pre-rename MiracastReceiver is present and must be uninstalled before the new package. */
        val legacyInstalled: Boolean = false,
    )

    fun action(s: Snapshot): Action = when {
        s.phase == "download" -> Action.DOWNLOADING
        s.phase == "verify" || s.phase == "stage" -> Action.VERIFYING
        s.phase == "pending" && (!s.installed || s.updateAvailable) ->
            if (s.hasConfirmation) Action.CONFIRM else Action.WAITING
        !s.installed && s.legacyInstalled -> Action.UNINSTALL
        s.phase == "failed" && (!s.installed || s.updateAvailable) -> Action.RETRY
        !s.installed -> Action.GET
        s.updateAvailable -> Action.UPDATE
        s.optional -> Action.OPEN
        !s.checked -> Action.CHECK
        else -> Action.CURRENT
    }

    fun actionable(action: Action) = action in setOf(
        Action.GET, Action.OPEN, Action.UPDATE, Action.CHECK, Action.CONFIRM, Action.RETRY, Action.UNINSTALL
    )
}
