package com.leslie.tvhome

/** SCREEN_OFF alone says nothing about whether a person pressed the remote's power key. */
internal object PowerButtonShutdownPolicy {
    const val MAX_EVENT_AGE_MS = 5_000L
    data class Decision(val shutdown: Boolean, val reason: String)

    fun decide(dump: String?, nowUptimeMs: Long, interactive: Boolean): Decision {
        if (interactive) return Decision(false, "display is already interactive")
        if (dump.isNullOrBlank()) return Decision(false, "sleep reason is unavailable")
        fun field(name: String) = Regex("(?m)^\\s*" + name + "=([^\\s]+)").find(dump)?.groupValues?.get(1)
        val state = field("mWakefulness")
        if (state != "Asleep" && state != "Dozing") return Decision(false, "display state is $state")
        val reason = field("mLastSleepReason")
        if (reason != "power_button") return Decision(false, "sleep reason is ${reason ?: "unavailable"}")
        val time = field("mLastSleepTime")?.toLongOrNull()
            ?: return Decision(false, "sleep time is unavailable")
        if (time <= 0 || time > nowUptimeMs || nowUptimeMs - time > MAX_EVENT_AGE_MS)
            return Decision(false, "power key event is not recent")
        return Decision(true, "recent system-confirmed power button sleep")
    }
}
