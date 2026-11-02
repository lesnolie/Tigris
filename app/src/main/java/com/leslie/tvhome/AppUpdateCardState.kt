package com.leslie.tvhome

/** Pure state policy for the single-action tvOS-style app update cards. */
internal object AppUpdateCardState {
    enum class Action {
        GET, OPEN, UPDATE, CHECK, CURRENT,
        DOWNLOADING, VERIFYING, WAITING, CONFIRM, RETRY
    }

    data class Snapshot(
        val installed: Boolean,
        val optional: Boolean,
        val checked: Boolean,
        val updateAvailable: Boolean,
        val phase: String = "idle",
        val hasConfirmation: Boolean = false,
    )

    fun action(s: Snapshot): Action = when {
        s.phase == "download" -> Action.DOWNLOADING
        s.phase == "verify" || s.phase == "stage" -> Action.VERIFYING
        s.phase == "pending" -> if (s.hasConfirmation) Action.CONFIRM else Action.WAITING
        s.phase == "failed" -> Action.RETRY
        !s.installed -> Action.GET
        s.updateAvailable -> Action.UPDATE
        s.optional -> Action.OPEN
        !s.checked -> Action.CHECK
        else -> Action.CURRENT
    }

    fun actionable(action: Action) = action in setOf(
        Action.GET, Action.OPEN, Action.UPDATE, Action.CHECK, Action.CONFIRM, Action.RETRY
    )
}
