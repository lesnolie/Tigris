package com.leslie.tvhome

/** Reconcile durable update intent with the installed package and our exact system session. */
internal object UpdateRecovery {
    enum class Action { NONE, COMPLETE, RETRY, RESUME }

    fun action(phase: String, transferring: Boolean, installedCode: Long?, targetCode: Long?,
               expectedSession: Int, matchingSession: Int?): Action {
        if (transferring) return Action.NONE
        if (targetCode != null && installedCode != null && installedCode >= targetCode) return Action.COMPLETE
        if (phase !in setOf("download", "verify", "stage", "pending")) return Action.NONE
        if (phase != "pending") return Action.RETRY
        return if (expectedSession >= 0 && matchingSession == expectedSession) Action.RESUME else Action.RETRY
    }
}
