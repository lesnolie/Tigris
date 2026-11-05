package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.session.MediaSessionManager
import android.os.Process
import android.provider.Settings

/**
 * Reads playback signals the idle policy can use. Session and foreign-uid playback lists
 * require the revival notification listener; the music-stream booleans do not.
 */
internal object FrameIdleProbe {
    fun media(ctx: Context, hint: FrameIdlePolicy.Media = FrameIdlePolicy.Media()): FrameIdlePolicy.Media {
        val am = ctx.getSystemService(AudioManager::class.java)
        val sessionsVisible = listenerEnabled(ctx)
        val foreground = recentForegroundPackage(ctx)
        val sessions = if (sessionsVisible) foreignSessions(ctx) else null
        return hint.copy(
            musicActive = runCatching { am?.isMusicActive == true }.getOrDefault(false),
            remoteMusicActive = remoteMusic(am),
            foreignSessionPlaying = sessions?.any { FrameIdlePolicy.sessionActive(it.state) },
            foreignPlayback = foreignPlayback(am, Process.myUid(), sessionsVisible),
            pausedForegroundVideo = sessions != null &&
                FrameIdlePolicy.pausedForegroundVideo(foreground, sessions),
            castReceiverForeground = FrameIdlePolicy.isCastingPackage(foreground),
        )
    }

    /** Removed from current SDK stubs; still present on the TVs this app runs on. */
    private fun remoteMusic(am: AudioManager?): Boolean {
        if (am == null) return false
        return runCatching {
            AudioManager::class.java.getMethod("isMusicActiveRemotely").invoke(am) as Boolean
        }.getOrDefault(false)
    }

    private fun listenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        val mine = ComponentName(ctx, RevivalListener::class.java).flattenToString()
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.flattenToString() == mine }
    }

    private fun foreignSessions(ctx: Context): List<FrameIdlePolicy.Session>? {
        val manager = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
        val component = ComponentName(ctx, RevivalListener::class.java)
        val controllers = runCatching { manager.getActiveSessions(component) }.getOrNull() ?: return null
        return controllers.filter { it.packageName != ctx.packageName }.map {
            FrameIdlePolicy.Session(it.packageName, it.playbackState?.state)
        }
    }

    /**
     * Last activity actually brought to the foreground before Android launched the dream.
     * Requires the user's Usage Access grant; missing/denied grants return null and fail
     * closed for third-party paused video. Do not use process importance as a foreground proxy.
     */
    private fun recentForegroundPackage(ctx: Context): String? {
        val usage = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { usage.queryEvents(now - 2 * 3_600_000L, now + 1L) }
            .getOrNull() ?: return null
        val event = UsageEvents.Event()
        var lastForeground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) {
                lastForeground = event.packageName
            }
        }
        return lastForeground
    }

    private fun foreignPlayback(am: AudioManager?, ourUid: Int, listComplete: Boolean): Boolean? {
        if (am == null) return null
        val configs = runCatching { am.activePlaybackConfigurations }.getOrNull() ?: return null
        val uids = configs.filter { playbackActive(it) && mediaUsage(it) }.map { clientUid(it) }
        return FrameIdlePolicy.foreignFromUids(uids, ourUid, listComplete)
    }

    private fun playbackActive(config: AudioPlaybackConfiguration): Boolean {
        val reflected = runCatching {
            AudioPlaybackConfiguration::class.java.getMethod("isActive").invoke(config) as Boolean
        }.getOrNull()
        return FrameIdlePolicy.summaryLooksActive(config.toString(), reflected)
    }

    private fun mediaUsage(config: AudioPlaybackConfiguration): Boolean {
        val usage = config.audioAttributes.usage
        return usage == AudioAttributes.USAGE_MEDIA ||
            usage == AudioAttributes.USAGE_GAME ||
            usage == AudioAttributes.USAGE_UNKNOWN ||
            usage == AudioAttributes.USAGE_ASSISTANT ||
            usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
            usage == AudioAttributes.USAGE_ALARM
    }

    private fun clientUid(config: AudioPlaybackConfiguration): Int? {
        val reflected = runCatching {
            AudioPlaybackConfiguration::class.java.getMethod("getClientUid").invoke(config) as Int
        }.getOrNull()
        if (reflected != null) return reflected
        return FrameIdlePolicy.clientUidFromSummary(config.toString())
    }
}
