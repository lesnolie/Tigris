package com.leslie.tvhome

/**
 * Automatic takeovers need a fresh stock HOME window, a short settle, and a budget.
 * A real Home press ([Kind.USER_HOME]) is never budgeted: the 3-in-30s pause was stopping
 * the remote from returning to Tigris.
 *
 * During the first minutes after boot, stock-home windows are budgeted too. That is the
 * 雷鸟 75S545C flip-flop (issue #14): the guard and the factory launcher were pulling each
 * other forward. After that window, a stock HOME window is answered without spending the budget.
 */
internal class HomeTakeoverPolicy {
    enum class Window { UNKNOWN, STOCK_HOME, OTHER }
    enum class Kind { AUTOMATIC, WINDOW, USER_HOME }

    private var window = Window.UNKNOWN
    private var observedAt = 0L
    private var lastTakeover = Long.MIN_VALUE / 2
    private val attempts = ArrayDeque<Long>()
    private var pausedUntil = 0L

    fun observe(window: Window, now: Long) {
        this.window = window
        observedAt = now
    }

    fun request(
        now: Long,
        enabled: Boolean,
        awake: Boolean,
        visitingStock: Boolean,
        kind: Kind = Kind.AUTOMATIC,
        inBootWindow: Boolean = true,
    ): Boolean {
        if (!enabled || !awake) return false
        if (kind == Kind.USER_HOME) return true
        val budgeted = kind == Kind.AUTOMATIC || inBootWindow
        if (visitingStock || window != Window.STOCK_HOME) return false
        if (!budgeted) {
            if (now < observedAt || now - observedAt > MAX_AGE_MS) return false
            window = Window.UNKNOWN
            return true
        }
        if (now - observedAt !in SETTLE_MS..MAX_AGE_MS || now - lastTakeover < COOLDOWN_MS || now < pausedUntil) return false
        while (attempts.isNotEmpty() && now - attempts.first() >= WINDOW_MS) attempts.removeFirst()
        if (attempts.size >= MAX_ATTEMPTS) {
            pausedUntil = now + PAUSE_MS
            return false
        }
        attempts.addLast(now)
        lastTakeover = now
        window = Window.UNKNOWN
        return true
    }

    companion object {
        const val SETTLE_MS = 350L
        const val BOOT_WINDOW_MS = 3 * 60 * 1000L
        val BOOT_RECHECKS_MS = listOf(30_000L, 45_000L)
        private const val MAX_AGE_MS = 2_000L
        private const val COOLDOWN_MS = 2_000L
        private const val WINDOW_MS = 30_000L
        private const val PAUSE_MS = 60_000L
        private const val MAX_ATTEMPTS = 3
    }
}
