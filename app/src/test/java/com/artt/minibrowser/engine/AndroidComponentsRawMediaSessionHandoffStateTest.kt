package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import mozilla.components.concept.engine.mediasession.MediaSession

class AndroidComponentsRawMediaSessionHandoffStateTest {
    @Test
    fun activationResetsStalePlaybackAndFullscreenState() {
        val first = Any()
        val second = Any()
        val metadata = MediaSession.ElementMetadata(width = 1920L, height = 1080L, videoTrackCount = 1)
        val stale = AndroidComponentsRawMediaSessionHandoffState(
            activeSession = first,
            playbackState = MediaSession.PlaybackState.PLAYING,
            fullScreen = true,
            elementMetadata = metadata,
        )

        val activated = rawMediaSessionActivated(second)

        assertSame(second, activated.activeSession)
        assertEquals(MediaSession.PlaybackState.UNKNOWN, activated.playbackState)
        assertEquals(false, activated.fullScreen)
        assertNull(activated.elementMetadata)
        assertSame(first, stale.activeSession)
    }

    @Test
    fun playCanAdoptSessionWhileStalePauseAndStopAreIgnored() {
        val current = Any()
        val stale = Any()
        val playing = rawMediaSessionPlayed(
            state = AndroidComponentsRawMediaSessionHandoffState(),
            session = current,
        )

        val afterStalePause = rawMediaSessionPaused(playing, stale)
        val afterStaleStop = rawMediaSessionStopped(afterStalePause, stale)

        assertSame(current, afterStaleStop.activeSession)
        assertEquals(MediaSession.PlaybackState.PLAYING, afterStaleStop.playbackState)
        assertEquals(
            MediaSession.PlaybackState.PAUSED,
            rawMediaSessionPaused(afterStaleStop, current).playbackState,
        )
        assertEquals(
            MediaSession.PlaybackState.STOPPED,
            rawMediaSessionStopped(afterStaleStop, current).playbackState,
        )
    }

    @Test
    fun fullscreenStateProducesCompleteCutoverHandoff() {
        val session = Any()
        val metadata = MediaSession.ElementMetadata(
            source = "https://example.test/video.webm",
            duration = 12.0,
            width = 1280L,
            height = 720L,
            audioTrackCount = 1,
            videoTrackCount = 1,
        )
        val state = rawMediaSessionFullscreenChanged(
            state = rawMediaSessionPlayed(
                state = AndroidComponentsRawMediaSessionHandoffState(),
                session = session,
            ),
            session = session,
            fullScreen = true,
            elementMetadata = metadata,
        )

        val handoff = state.toHandoff { NoOpController }

        requireNotNull(handoff)
        assertSame(NoOpController, handoff.controller)
        assertEquals(MediaSession.PlaybackState.PLAYING, handoff.playbackState)
        assertEquals(true, handoff.fullscreen)
        assertSame(metadata, handoff.elementMetadata)
    }

    @Test
    fun onlyCurrentSessionDeactivationClearsHandoff() {
        val current = Any()
        val stale = Any()
        val state = rawMediaSessionPlayed(
            state = AndroidComponentsRawMediaSessionHandoffState(),
            session = current,
        )

        val afterStaleDeactivation = rawMediaSessionDeactivated(state, stale)
        val afterCurrentDeactivation = rawMediaSessionDeactivated(afterStaleDeactivation, current)

        assertSame(current, afterStaleDeactivation.activeSession)
        assertNull(afterCurrentDeactivation.activeSession)
        assertNull(afterCurrentDeactivation.toHandoff { NoOpController })
    }

    private object NoOpController : MediaSession.Controller {
        override fun pause() = Unit
        override fun stop() = Unit
        override fun play() = Unit
        override fun seekTo(time: Double, fast: Boolean) = Unit
        override fun seekForward() = Unit
        override fun seekBackward() = Unit
        override fun nextTrack() = Unit
        override fun previousTrack() = Unit
        override fun skipAd() = Unit
        override fun muteAudio(mute: Boolean) = Unit
    }
}
