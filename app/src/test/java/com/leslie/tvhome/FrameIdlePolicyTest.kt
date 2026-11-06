package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FrameIdlePolicyTest {
    @Test fun shellSessionsKeepPausedAndPlayingStatesAndRejectUnavailableDump() {
        assertNull(FrameIdlePolicy.sessionsFromDump("Permission Denial"))
        val sessions = FrameIdlePolicy.sessionsFromDump("""
            MEDIA SESSION SERVICE (dumpsys media_session)
              package=com.hgtv.star
              active=false
              state=PlaybackState {state=2, position=1}
              package=other.player
              active=true
              state=PlaybackState {state=3, position=1}
        """.trimIndent())!!
        assertTrue(FrameIdlePolicy.pausedForegroundVideo("com.hgtv.star", sessions))
        assertTrue(sessions.any { FrameIdlePolicy.sessionActive(it.state) })
        assertFalse(FrameIdlePolicy.dreamDecision(false, FrameIdlePolicy.Media(
            pausedForegroundVideo = true, foreignSessionPlaying = true)).show)
    }

    @Test fun dreamOverlayKeepsThePausedPlayersForegroundIdentity() {
        var foreground: String? = null
        for ((pkg, activity) in listOf(
            "com.hgtv.star" to "com.hgtv.star.tv.MainActivity",
            "com.leslie.tvhome" to "android.service.dreams.DreamActivity",
        )) {
            if (FrameIdlePolicy.isContentForeground(activity)) foreground = pkg
        }
        assertEquals("com.hgtv.star", foreground)
        assertTrue(FrameIdlePolicy.dreamDecision(false, FrameIdlePolicy.Media(
            pausedForegroundVideo = FrameIdlePolicy.pausedForegroundVideo(
                foreground, listOf(FrameIdlePolicy.Session("com.hgtv.star", 2))),
        )).show)
    }

    @Before fun resetForeground() = HomeForeground.resetForTests()

    @Test fun missingOrNonPositiveTimeoutUsesTheInAppMinutes() {
        for (timeout in listOf(null, 0L, -1L)) {
            val schedule = FrameIdlePolicy.schedule(frameEnabled = true, idleMinutes = 5, screenSaverTimeoutMs = timeout)
            assertEquals(FrameIdlePolicy.Source.IN_APP, schedule.source)
            assertEquals(5 * 60_000L, schedule.delayMs)
            assertTrue(schedule.logLine.contains("in-app preference (5 min)"))
            assertTrue(schedule.logLine.contains("screen_saver_timeout=${timeout ?: "missing"}"))
        }
    }

    @Test fun positiveVendorTimeoutCanOnlyMakeTheFrameEarlier() {
        val early = FrameIdlePolicy.schedule(true, 10, 120_000L)
        assertEquals(FrameIdlePolicy.Source.VENDOR_LEAD, early.source)
        assertEquals(105_000L, early.delayMs)
        assertTrue(early.logLine.contains("screen_saver_timeout=120000"))

        val preferenceWins = FrameIdlePolicy.schedule(true, 3, 600_000L)
        assertEquals(FrameIdlePolicy.Source.IN_APP, preferenceWins.source)
        assertEquals(3 * 60_000L, preferenceWins.delayMs)
        assertTrue(preferenceWins.logLine.contains("screen_saver_timeout=600000"))
    }

    @Test fun shortVendorTimeoutNeverSchedulesTheFrameAfterTheSystemDeadline() {
        val schedule = FrameIdlePolicy.schedule(false, 10, 10_000L)
        assertEquals(7_500L, schedule.delayMs)
        assertEquals(FrameIdlePolicy.Source.VENDOR_LEAD, schedule.source)
        assertTrue(schedule.delayMs!! < 10_000L)
    }

    @Test fun screenOffTimeoutWinsWhenHomeAndSystemBothUseTenMinutes() {
        val frame = FrameIdlePolicy.schedule(true, 10, null, 600_000L)
        assertEquals(FrameIdlePolicy.Source.SCREEN_OFF_LEAD, frame.source)
        assertEquals(570_000L, frame.delayMs)
        assertTrue(frame.logLine.contains("screen_off_timeout=600000"))
    }

    @Test fun earliestSystemDeadlineWinsEvenWithFirmwareDreamConfigured() {
        val frame = FrameIdlePolicy.schedule(true, 10, 600_000L, 600_000L)
        assertEquals(FrameIdlePolicy.Source.SCREEN_OFF_LEAD, frame.source)
        assertEquals(570_000L, frame.delayMs)
        val earlyDream = FrameIdlePolicy.schedule(true, 10, 120_000L, 300_000L)
        assertEquals(FrameIdlePolicy.Source.VENDOR_LEAD, earlyDream.source)
        assertEquals(105_000L, earlyDream.delayMs)
    }

    @Test fun shortPowerTimeoutAndEarlierUserPreferenceAreRespected() {
        val fastSleep = FrameIdlePolicy.schedule(true, 10, -1L, 60_000L)
        assertEquals(FrameIdlePolicy.Source.SCREEN_OFF_LEAD, fastSleep.source)
        assertEquals(45_000L, fastSleep.delayMs)
        val appWins = FrameIdlePolicy.schedule(true, 1, -1L, 600_000L)
        assertEquals(FrameIdlePolicy.Source.IN_APP, appWins.source)
        assertEquals(60_000L, appWins.delayMs)
    }

    @Test fun disabledPhotoFrameDoesNotStartJustBecauseTheTvHasASleepTimeout() {
        val disabled = FrameIdlePolicy.schedule(false, 10, -1L, 600_000L)
        assertNull(disabled.delayMs)
        assertEquals(FrameIdlePolicy.Source.NOT_SCHEDULED, disabled.source)
        assertNull(FrameIdlePolicy.schedule(false, 10, -1L, null).delayMs)
    }

    @Test fun idleFrameStaysOffWithoutAPreferenceOrAVendorTimeout() {
        val schedule = FrameIdlePolicy.schedule(false, 10, -1L)
        assertNull(schedule.delayMs)
        assertEquals(FrameIdlePolicy.Source.NOT_SCHEDULED, schedule.source)
        assertTrue(schedule.logLine.contains("not scheduled"))
        assertTrue(schedule.logLine.contains("screen_saver_timeout=-1"))
        assertNull(FrameIdlePolicy.schedule(false, 10, null).delayMs)
    }

    @Test fun unknownMinutesFallBackToTenAndTheChoicesCycle() {
        assertEquals(10, FrameIdlePolicy.minutes(0))
        assertEquals(10, FrameIdlePolicy.minutes(60))
        assertEquals(1, FrameIdlePolicy.nextMinutes(30))
        assertEquals(3, FrameIdlePolicy.nextMinutes(1))
        assertEquals(15, FrameIdlePolicy.nextMinutes(10))
    }

    @Test fun frameRequiresTheResumedFocusedHomeScreen() {
        val clear = FrameIdlePolicy.Media()
        assertEquals(FrameIdlePolicy.Block.HOME, FrameIdlePolicy.suppression(false, true, true, false, clear)!!.block)
        assertEquals(FrameIdlePolicy.Block.FOCUS, FrameIdlePolicy.suppression(true, false, true, false, clear)!!.block)
        assertEquals(FrameIdlePolicy.Block.ASLEEP, FrameIdlePolicy.suppression(true, true, false, false, clear)!!.block)
        val overlay = FrameIdlePolicy.suppression(true, true, true, true, clear)!!
        assertEquals(FrameIdlePolicy.Block.OVERLAY, overlay.block)
        assertTrue(overlay.retry)
        assertNull(FrameIdlePolicy.suppression(true, true, true, false, clear))
    }

    @Test fun mediaAndWakeLocksBlockEvenOnTheHomeScreen() {
        val session = FrameIdlePolicy.suppression(true, true, true, false,
            FrameIdlePolicy.Media(foreignSessionPlaying = true))!!
        assertEquals(FrameIdlePolicy.Block.MEDIA, session.block)
        assertTrue(session.retry)
        assertTrue(session.detail.contains("media session"))

        val remote = FrameIdlePolicy.mediaBlockReason(FrameIdlePolicy.Media(remoteMusicActive = true, ownPlayback = true))
        assertTrue(remote!!.contains("isMusicActiveRemotely"))

        assertNull(FrameIdlePolicy.mediaBlockReason(FrameIdlePolicy.Media(musicActive = true, ownPlayback = true)))
        assertNull(FrameIdlePolicy.mediaBlockReason(
            FrameIdlePolicy.Media(musicActive = true, ownPlaybackStoppedAgoMs = 500)))
        val stranger = FrameIdlePolicy.mediaBlockReason(FrameIdlePolicy.Media(musicActive = true))
        assertTrue(stranger!!.contains("isMusicActive"))
        assertNull(FrameIdlePolicy.mediaBlockReason(FrameIdlePolicy.Media(
            musicActive = true,
            foreignSessionPlaying = false,
            foreignPlayback = false,
        )))
        assertTrue(FrameIdlePolicy.sessionActive(3))
        assertFalse(FrameIdlePolicy.sessionActive(2))
        assertFalse(FrameIdlePolicy.sessionActive(null))
    }

    @Test fun pausedVideoCanDreamOnlyWhenTheActualForegroundPlayerReportsPause() {
        val player = "com.example.videoplayer"
        val paused = listOf(FrameIdlePolicy.Session(player, 2))
        assertTrue(FrameIdlePolicy.pausedForegroundVideo(player, paused))
        assertTrue(FrameIdlePolicy.dreamDecision(false,
            FrameIdlePolicy.Media(pausedForegroundVideo = true)).show)
        assertFalse(FrameIdlePolicy.pausedForegroundVideo(null, paused))
        assertFalse(FrameIdlePolicy.pausedForegroundVideo("com.example.other", paused))
        assertFalse(FrameIdlePolicy.pausedForegroundVideo(player, emptyList()))
        assertFalse(FrameIdlePolicy.pausedForegroundVideo(player,
            listOf(FrameIdlePolicy.Session(player, null))))
        assertFalse(FrameIdlePolicy.pausedForegroundVideo(player,
            paused + FrameIdlePolicy.Session(player, 3)))
        assertFalse(FrameIdlePolicy.pausedForegroundVideo(player,
            paused + FrameIdlePolicy.Session(player, null)))
    }

    @Test fun playbackAndCastingOverridePausedVideoAndOurSettingsUi() {
        val cast = "com.weekd.miracastreceiver"
        assertTrue(FrameIdlePolicy.isCastingPackage(cast))
        assertTrue(FrameIdlePolicy.isCastingPackage("com.dangbei.screencast"))
        assertFalse(FrameIdlePolicy.isCastingPackage("com.example.videoplayer"))
        assertFalse(FrameIdlePolicy.pausedForegroundVideo(cast,
            listOf(FrameIdlePolicy.Session(cast, 2))))
        assertFalse(FrameIdlePolicy.dreamDecision(true,
            FrameIdlePolicy.Media(castReceiverForeground = true)).show)
        assertFalse(FrameIdlePolicy.dreamDecision(false,
            FrameIdlePolicy.Media(pausedForegroundVideo = true, foreignSessionPlaying = true)).show)
        assertFalse(FrameIdlePolicy.dreamDecision(false,
            FrameIdlePolicy.Media(pausedForegroundVideo = true, foreignPlayback = true)).show)
        assertFalse(FrameIdlePolicy.dreamDecision(false,
            FrameIdlePolicy.Media(pausedForegroundVideo = true, remoteMusicActive = true)).show)
        assertFalse(FrameIdlePolicy.dreamDecision(false,
            FrameIdlePolicy.Media(pausedForegroundVideo = true, castReceiverForeground = true)).show)
    }

    @Test fun noVerifiedPauseNeverClaimsThirdPartyVideoIsSafe() {
        assertFalse(FrameIdlePolicy.dreamDecision(false, FrameIdlePolicy.Media()).show)
        assertFalse(FrameIdlePolicy.dreamDecision(false,
            FrameIdlePolicy.Media(foreignSessionPlaying = false, foreignPlayback = false)).show)
        assertTrue(FrameIdlePolicy.dreamDecision(true, FrameIdlePolicy.Media()).show)
        assertTrue(FrameIdlePolicy.sessionActive(11))
    }

    @Test fun playbackSummariesExposeUidAndStartedState() {
        val summary = "AudioPlaybackConfiguration piid:12 type:android.media.AudioTrack u/pid:10123/4321 state:started attr:usage=USAGE_MEDIA"
        assertEquals(10123, FrameIdlePolicy.clientUidFromSummary(summary))
        assertTrue(FrameIdlePolicy.summaryLooksActive(summary))
        assertFalse(FrameIdlePolicy.summaryLooksActive(summary.replace("started", "paused")))
        assertTrue(FrameIdlePolicy.summaryLooksActive("no state here"))
        assertFalse(FrameIdlePolicy.summaryLooksActive("state:started", reflectedActive = false))
        assertNull(FrameIdlePolicy.clientUidFromSummary("no uid"))
    }

    @Test fun playbackUidsAreForeignOnlyWhenTheListShowsAnotherApp() {
        assertEquals(true, FrameIdlePolicy.foreignFromUids(listOf(10, 20), ourUid = 10, listComplete = false))
        assertNull(FrameIdlePolicy.foreignFromUids(listOf(10), ourUid = 10, listComplete = false))
        assertEquals(false, FrameIdlePolicy.foreignFromUids(listOf(10), ourUid = 10, listComplete = true))
        assertNull(FrameIdlePolicy.foreignFromUids(listOf(null), ourUid = 10, listComplete = true))
        assertEquals(false, FrameIdlePolicy.foreignFromUids(emptyList(), ourUid = 10, listComplete = true))
    }

    @Test fun holdScreenWindowComesFromTheWindowDump() {
        val ours = "com.leslie.tvhome"
        assertNull(FrameIdlePolicy.foreignHoldScreen(null, ours))
        assertNull(FrameIdlePolicy.foreignHoldScreen("mCurrentFocus=Window{a u0 $ours/$ours.HomeActivity}", ours))
        assertEquals(false, FrameIdlePolicy.foreignHoldScreen("""
            mHoldScreenWindow=Window{abc u0 $ours/$ours.HomeActivity}
            mLastWakeLockHoldingWindow=null
        """.trimIndent(), ours))
        assertEquals(false, FrameIdlePolicy.foreignHoldScreen("""
            mHoldScreenWindow=null
            mLastWakeLockHoldingWindow=null
        """.trimIndent(), ours))
        assertEquals(true, FrameIdlePolicy.foreignHoldScreen("""
            mHoldScreenWindow=null
            mLastWakeLockHoldingWindow=Window{def u0 com.example.cast/com.example.cast.Player}
        """.trimIndent(), ours))
        assertNull(FrameIdlePolicy.foreignHoldScreen("mHoldScreenWindow=not-a-window", ours))
    }

    @Test fun systemDreamUsesTheSystemsIdleDecisionForHomeAndOurSettingsUi() {
        val quiet = FrameIdlePolicy.Media()
        val playing = FrameIdlePolicy.Media(musicActive = true)
        assertFalse(FrameIdlePolicy.dreamDecision(false, quiet).show)
        assertFalse(FrameIdlePolicy.dreamDecision(true, playing).show)
        // Sony's dream focus transition made Home report only 32 ms idle after 10 actual minutes.
        // That independent clock must not reject the system's already-triggered dream.
        HomeForeground.interact(600_000)
        assertEquals(32L, HomeForeground.idleFor(600_032))
        assertTrue(FrameIdlePolicy.dreamDecision(true, quiet).show)
        HomeForeground.resetForTests() // Settings never initialized Home's clock at all.
        assertEquals(0L, HomeForeground.idleFor(600_032))
        assertTrue(FrameIdlePolicy.dreamDecision(true, quiet).show)
    }

    @Test fun dreamCoversHomeOnlyForTheHomeScreenOrTheDreamThatJustReplacedIt() {
        val clock = HomeForeground
        assertFalse(clock.dreamCoversHome(10_000))
        clock.resume(1_000)
        clock.windowFocus(true, 1_000)
        clock.interact(1_000)
        assertTrue(clock.dreamCoversHome(5_000))
        assertEquals(4_000L, clock.idleFor(5_000))

        clock.windowFocus(false, 2_000)
        assertTrue(clock.dreamCoversHome(2_200))
        assertFalse(clock.dreamCoversHome(6_000))

        clock.windowFocus(true, 7_000)
        clock.pause(8_000)
        assertTrue(clock.dreamCoversHome(8_400))
        assertFalse(clock.dreamCoversHome(12_000))
    }

    @Test fun rejectingOurOwnDreamDoesNotLookLikeTheUserCameBack() {
        HomeForeground.resume(0)
        HomeForeground.interact(0)
        HomeForeground.noteDreamRejected(5_000)
        assertTrue(HomeForeground.isDreamBounce(5_100))
        assertFalse(HomeForeground.isDreamBounce(9_000))
        assertEquals(5_100L, HomeForeground.idleFor(5_100))
    }

    @Test fun ownPlaybackGraceDoesNotOutliveTheWallpaper() {
        HomeForeground.setOwnPlayback(true, 1_000)
        assertTrue(HomeForeground.mediaHint(1_500).ownPlayback)
        HomeForeground.setOwnPlayback(false, 2_000)
        assertEquals(400L, HomeForeground.mediaHint(2_400).ownPlaybackStoppedAgoMs)
        assertNull(FrameIdlePolicy.mediaBlockReason(HomeForeground.mediaHint(2_400).copy(musicActive = true)))
        assertTrue(FrameIdlePolicy.mediaBlockReason(HomeForeground.mediaHint(6_000).copy(musicActive = true))!!.contains("isMusicActive"))
    }

    @Test fun settingsNoLongerWriteTheSystemScreenOffTimeout() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val hits = root.walk().filter { it.extension == "kt" }
            .filter { "settings put system screen_off_timeout" in it.readText() }
            .map { it.relativeTo(root).path }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }
}
