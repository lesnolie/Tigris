package com.leslie.tvhome.tv

/** Reads never initiate registration. Failed user-requested writes back off across process restarts. */
internal class BraviaSession(
    private val now: () -> Long,
    private val readFailure: () -> Long,
    private val writeFailure: (Long) -> Unit,
    private val register: () -> String?,
) {
    private var auth: String? = null
    private var acquiredAt = 0L

    @Synchronized
    fun cookie(allowRegistration: Boolean): String? {
        val time = now()
        if (auth != null && time >= acquiredAt && time - acquiredAt < COOKIE_MS) return auth
        auth = null
        if (!allowRegistration) return null
        val failed = readFailure()
        if (failed > 0 && time >= failed && time - failed < RETRY_MS) return null
        // Record before the request as well: process death during registration must not create a loop.
        writeFailure(time)
        auth = runCatching(register).getOrNull()?.takeIf { it.isNotBlank() }
        if (auth != null) { acquiredAt = time; writeFailure(0) }
        return auth
    }

    @Synchronized
    fun invalidate(backoff: Boolean = false) {
        auth = null
        if (backoff) writeFailure(now())
    }

    companion object {
        const val RETRY_MS = 30 * 60 * 1000L
        private const val COOKIE_MS = 7 * 24 * 3_600_000L
    }
}
