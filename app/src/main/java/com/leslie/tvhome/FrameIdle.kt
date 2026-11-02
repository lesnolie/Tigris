package com.leslie.tvhome

/**
 * When the photo frame may start by itself.
 *
 * Two triggers existed. [HomeActivity] posted its own delayed runnable, but only when
 * `Settings.System.screen_saver_timeout` was greater than zero (Xiaomi's value is often -1,
 * and then nothing was scheduled and nothing was logged). Separately, enabling
 * “电视空闲时显示相框” registered [PhotoDream] as the system dream. Android can start that dream
 * over whatever is on screen once the device is idle, including a cast or video app, and the
 * settings row labeled “空闲多久显示相框” wrote `screen_off_timeout` (the TV's sleep timer) rather
 * than either of those delays.
 *
 * The delay is now an in-app preference. A positive vendor screensaver timeout can only make
 * the frame earlier, so a firmware screensaver does not appear first. The preference is never
 * written back to `screen_off_timeout`.
 *
 * What can be seen without privileged permissions, and what cannot:
 * - [android.media.AudioManager.isMusicActive] and `isMusicActiveRemotely` need no special
 *   permission, but they do not name the player. Tigris's own muted wallpaper and intro also
 *   start an audio track; those are ignored while they run and for a few seconds after they
 *   stop, unless a foreign session or playback configuration is visible.
 * - Other apps' media sessions and [android.media.AudioPlaybackConfiguration]s are visible when
 *   the revival notification listener is enabled. Without it, a silent player that publishes
 *   neither a session nor the music stream is invisible. A cast that plays audio while the
 *   home screen's own video wallpaper is running can also hide behind that wallpaper if the
 *   listener is off.
 * - `FLAG_KEEP_SCREEN_ON` is not a public query. When local debugging is already authorized,
 *   the idle timer reads `dumpsys window` for the hold-screen window. A partial wake lock does
 *   not keep the screen on and is not treated as one. Without that grant, a silent window flag
 *   on an activity that never took focus can be missed. An app that did take the foreground
 *   still cannot receive the in-app timer, and a system dream started over it is dismissed.
 * - The system may paint one frame of [PhotoDream] before `wakeUp()` returns to the app
 *   underneath. That is the dream the user opted into; a normal app cannot veto the start.
 */
internal object FrameIdlePolicy {
    const val TAG = "TigrisFrame"
    const val VENDOR_LEAD_MS = 15_000L
    const val MIN_DELAY_MS = 30_000L
    const val OWN_AUDIO_GRACE_MS = 3_000L
    const val RETRY_MS = 15_000L
    const val DEFAULT_MINUTES = 10
    val CHOICES_MINUTES = listOf(1, 3, 5, 10, 15, 30)

    enum class Source { IN_APP, VENDOR_LEAD, NOT_SCHEDULED }
    enum class Block { HOME, FOCUS, ASLEEP, OVERLAY, MEDIA, WAKE }

    data class Schedule(val delayMs: Long?, val source: Source, val logLine: String)

    data class Media(
        val musicActive: Boolean = false,
        val remoteMusicActive: Boolean = false,
        val ownPlayback: Boolean = false,
        val ownPlaybackStoppedAgoMs: Long? = null,
        val foreignSessionPlaying: Boolean? = null,
        val foreignPlayback: Boolean? = null,
    )

    data class Suppression(val block: Block, val detail: String) {
        /** Overlay, playback and a held screen can clear without a new key. */
        val retry: Boolean get() = block == Block.OVERLAY || block == Block.MEDIA || block == Block.WAKE
        override fun toString() = detail
    }

    data class DreamDecision(val show: Boolean, val reason: String)

    fun nextMinutes(current: Int): Int {
        val i = CHOICES_MINUTES.indexOf(current).let { if (it < 0) CHOICES_MINUTES.indexOf(DEFAULT_MINUTES) else it }
        return CHOICES_MINUTES[(i + 1) % CHOICES_MINUTES.size]
    }

    fun minutes(value: Int) = if (value in CHOICES_MINUTES) value else DEFAULT_MINUTES

    /**
     * Armed when the user turned the idle frame on, or when the firmware publishes a positive
     * screensaver timeout (the existing early start that stays ahead of that firmware).
     * The in-app minutes are the delay unless the firmware timeout would fire sooner.
     */
    fun schedule(frameEnabled: Boolean, idleMinutes: Int, screenSaverTimeoutMs: Long?): Schedule {
        val chosen = minutes(idleMinutes)
        val prefMs = chosen * 60_000L
        val vendorText = if (screenSaverTimeoutMs == null) "missing" else screenSaverTimeoutMs.toString()
        val vendorLead = if (screenSaverTimeoutMs != null && screenSaverTimeoutMs > 0)
            maxOf(MIN_DELAY_MS, screenSaverTimeoutMs - VENDOR_LEAD_MS) else null
        if (!frameEnabled && vendorLead == null) {
            return Schedule(null, Source.NOT_SCHEDULED,
                "frame idle: not scheduled; photo frame idle is off and screen_saver_timeout=$vendorText")
        }
        if (vendorLead == null || prefMs <= vendorLead) {
            val comparison = if (vendorLead == null) "screen_saver_timeout=$vendorText"
            else "screen_saver_timeout=$vendorText would lead at ${vendorLead}ms"
            return Schedule(prefMs, Source.IN_APP,
                "frame idle: ${prefMs}ms from in-app preference ($chosen min); $comparison")
        }
        return Schedule(vendorLead, Source.VENDOR_LEAD,
            "frame idle: ${vendorLead}ms from screen_saver_timeout=$vendorText minus ${VENDOR_LEAD_MS}ms; in-app preference ($chosen min) is later")
    }

    /** Active [android.media.session.PlaybackState] values that mean "still playing". */
    fun sessionActive(state: Int?): Boolean = state != null && state in ACTIVE_SESSION_STATES

    /** `u/pid:<uid>/` from [android.media.AudioPlaybackConfiguration.toString]. */
    fun clientUidFromSummary(summary: String): Int? =
        UID_IN_SUMMARY.find(summary)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Active when reflection says so, or when the summary's player state is started.
     * A summary with no state stays active: [android.media.AudioManager.getActivePlaybackConfigurations]
     * already filtered to the current players.
     */
    fun summaryLooksActive(summary: String, reflectedActive: Boolean? = null): Boolean {
        if (reflectedActive != null) return reflectedActive
        val state = PLAYER_STATE.find(summary)?.groupValues?.get(1)?.lowercase() ?: return true
        return state !in INACTIVE_PLAYER_STATES
    }

    /**
     * @param uids client uids of active media playback this process was allowed to see.
     * `null` means the uid could not be read. [listComplete] is false when the platform
     * only returns this app's players.
     */
    fun foreignFromUids(uids: List<Int?>, ourUid: Int, listComplete: Boolean): Boolean? {
        if (uids.any { it != null && it != ourUid }) return true
        if (!listComplete || uids.any { it == null }) return null
        return false
    }

    fun mediaBlockReason(media: Media): String? {
        if (media.foreignSessionPlaying == true) return "a media session from another app is playing"
        if (media.foreignPlayback == true) return "another app's media audio is active"
        if (media.remoteMusicActive) return "AudioManager.isMusicActiveRemotely is true"
        if (!media.musicActive) return null
        if (media.foreignSessionPlaying == false && media.foreignPlayback == false) return null
        if (media.ownPlayback) return null
        val ago = media.ownPlaybackStoppedAgoMs
        if (ago != null && ago in 0..OWN_AUDIO_GRACE_MS) return null
        return "AudioManager.isMusicActive is true and it is not Tigris playback"
    }

    fun suppression(
        resumed: Boolean,
        focused: Boolean,
        interactive: Boolean,
        overlays: Boolean,
        media: Media,
    ): Suppression? = when {
        !resumed -> Suppression(Block.HOME, "home screen is not resumed")
        !focused -> Suppression(Block.FOCUS, "home screen does not have window focus")
        !interactive -> Suppression(Block.ASLEEP, "screen is not interactive")
        overlays -> Suppression(Block.OVERLAY, "welcome, intro, menu or control center is open")
        else -> mediaBlockReason(media)?.let { Suppression(Block.MEDIA, it) }
    }

    fun dreamDecision(coversHome: Boolean, idleForMs: Long, delayMs: Long?, media: Media): DreamDecision {
        if (!coversHome) return DreamDecision(false, "Tigris home screen is not in front")
        mediaBlockReason(media)?.let { return DreamDecision(false, it) }
        if (delayMs == null) return DreamDecision(false, "photo frame idle is not armed")
        if (idleForMs < delayMs) return DreamDecision(false, "idle ${idleForMs}ms is below ${delayMs}ms")
        return DreamDecision(true, "home screen idle ${idleForMs}ms reached ${delayMs}ms")
    }

    /**
     * `dumpsys window` lines `mHoldScreenWindow` and `mLastWakeLockHoldingWindow`.
     * `null` when the dump is missing or those fields are not there. `true` when a window
     * belonging to some other package is holding the screen on.
     */
    fun foreignHoldScreen(dump: String?, ourPackage: String): Boolean? {
        if (dump.isNullOrBlank()) return null
        var seen = false
        var foreign = false
        var unparsed = false
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            val key = HOLD_KEYS.firstOrNull { line.startsWith(it) } ?: continue
            seen = true
            val value = line.substring(key.length).trim()
            if (value.isEmpty() || value == "null") continue
            val pkg = PACKAGE_IN_WINDOW.find(value)?.groupValues?.get(1)
            if (pkg == null) unparsed = true
            else if (pkg != ourPackage) foreign = true
        }
        if (!seen) return null
        if (foreign) return true
        if (unparsed) return null
        return false
    }

    private val HOLD_KEYS = listOf("mHoldScreenWindow=", "mLastWakeLockHoldingWindow=")
    private val PACKAGE_IN_WINDOW = Regex("""([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)*)/""")
    private val UID_IN_SUMMARY = Regex("""u/pid:(\d+)/""")
    private val PLAYER_STATE = Regex("""state:([A-Za-z_]+)""")
    private val INACTIVE_PLAYER_STATES = setOf("released", "idle", "paused", "stopped")
    private val ACTIVE_SESSION_STATES = setOf(
        3, // PlaybackState.STATE_PLAYING
        4, // STATE_FAST_FORWARDING
        5, // STATE_REWINDING
        6, // STATE_BUFFERING
        8, // STATE_CONNECTING
        9, // STATE_SKIPPING_TO_PREVIOUS
        10, // STATE_SKIPPING_TO_NEXT
    )
}

/**
 * Process-wide view of whether the home screen is the thing the user is looking at.
 * [PhotoDream] starts in the same process but cannot see [HomeActivity]'s fields.
 * Times are elapsed-realtime milliseconds supplied by the caller.
 */
internal object HomeForeground {
    const val PAUSE_GRACE_MS = 2_500L
    const val DREAM_BOUNCE_MS = 3_000L

    var inFront: Boolean = false
        private set
    private var pausedAt = Long.MIN_VALUE
    private var hasFocus = false
    private var focusLostAt = Long.MIN_VALUE
    private var rejectedDreamAt = Long.MIN_VALUE
    private var lastInteractionAt = Long.MIN_VALUE
    var ownPlayback: Boolean = false
        private set
    private var ownPlaybackStoppedAt = Long.MIN_VALUE

    fun resume(now: Long) {
        inFront = true
        if (pausedAt != Long.MIN_VALUE && now >= pausedAt) pausedAt = Long.MIN_VALUE
    }

    fun pause(now: Long) {
        inFront = false
        pausedAt = now
    }

    fun windowFocus(focused: Boolean, now: Long) {
        hasFocus = focused
        focusLostAt = if (focused) Long.MIN_VALUE else now
    }

    fun interact(now: Long) {
        lastInteractionAt = now
    }

    fun idleFor(now: Long): Long =
        if (lastInteractionAt == Long.MIN_VALUE) 0L else (now - lastInteractionAt).coerceAtLeast(0L)

    /**
     * True when the home screen is still up, or it left the front only because this dream
     * replaced it. An overlay that took focus earlier, or an app opened seconds ago, is not covered.
     */
    fun dreamCoversHome(now: Long): Boolean {
        if (!inFront) {
            if (pausedAt == Long.MIN_VALUE) return false
            return now - pausedAt in 0..PAUSE_GRACE_MS
        }
        if (hasFocus) return true
        if (focusLostAt == Long.MIN_VALUE) return false
        return now - focusLostAt in 0..PAUSE_GRACE_MS
    }

    fun noteDreamRejected(now: Long) {
        rejectedDreamAt = now
    }

    /** Home was paused by a dream we immediately dismissed, so the idle deadline stays. */
    fun isDreamBounce(now: Long) = now - rejectedDreamAt in 0..DREAM_BOUNCE_MS

    fun setOwnPlayback(active: Boolean, now: Long) {
        if (active) ownPlayback = true
        else if (ownPlayback) {
            ownPlayback = false
            ownPlaybackStoppedAt = now
        }
    }

    fun ownPlaybackStoppedAgo(now: Long): Long? {
        if (ownPlayback || ownPlaybackStoppedAt == Long.MIN_VALUE) return null
        return (now - ownPlaybackStoppedAt).coerceAtLeast(0L)
    }

    fun mediaHint(now: Long) = FrameIdlePolicy.Media(
        ownPlayback = ownPlayback,
        ownPlaybackStoppedAgoMs = ownPlaybackStoppedAgo(now),
    )

    fun resetForTests() {
        inFront = false
        pausedAt = Long.MIN_VALUE
        hasFocus = false
        focusLostAt = Long.MIN_VALUE
        rejectedDreamAt = Long.MIN_VALUE
        lastInteractionAt = Long.MIN_VALUE
        ownPlayback = false
        ownPlaybackStoppedAt = Long.MIN_VALUE
    }
}
