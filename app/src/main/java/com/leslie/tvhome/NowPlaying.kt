package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/** Only authorized notification listeners can inspect third-party MediaSessions.
 * No polling other apps, no secret APIs, and no fabricated Now Playing metadata. */
internal object NowPlaying {
    data class Track(val title: String, val artist: String, val app: String, val playing: Boolean,
        val previous: Boolean, val next: Boolean)

    private fun controller(ctx: Context): MediaController? = try {
        val mgr = ctx.getSystemService(MediaSessionManager::class.java)
        val enabled = ComponentName(ctx, RevivalListener::class.java)
        val all = mgr.getActiveSessions(enabled)
        all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
    } catch (_: SecurityException) { null }
      catch (_: RuntimeException) { null }

    fun current(ctx: Context): Track? = controller(ctx)?.let { c ->
        val data = c.metadata
        Track(
            title = data?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: data?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: c.packageName,
            artist = data?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            app = c.packageName,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            previous = c.playbackState?.actions?.and(PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
            next = c.playbackState?.actions?.and(PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
        )
    }

    fun transport(ctx: Context, action: String): Boolean {
        val c = controller(ctx) ?: return false
        val flags = c.playbackState?.actions ?: return false
        return when (action) {
            "previous" -> if ((flags and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L)
                runCatching { c.transportControls.skipToPrevious(); true }.getOrDefault(false) else false
            "next" -> if ((flags and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L)
                runCatching { c.transportControls.skipToNext(); true }.getOrDefault(false) else false
            else -> false
        }
    }

    fun toggle(ctx: Context): Boolean {
        val c = controller(ctx) ?: return false
        return runCatching {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
            true
        }.getOrDefault(false)
    }
}
