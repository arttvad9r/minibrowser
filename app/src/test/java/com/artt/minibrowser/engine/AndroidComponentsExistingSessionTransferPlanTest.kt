package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.concept.engine.mediasession.MediaSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidComponentsExistingSessionTransferPlanTest {
    @Test
    fun existingSessionLinkNeverReloadsOrUsesParent() {
        val plan = androidComponentsExistingSessionTransferPlan(
            tabId = "42",
            mediaSessionHandoff = null,
        )

        assertEquals("42", plan.tabId)
        assertTrue(plan.skipLoading)
        assertFalse(plan.includeParent)
        assertTrue(plan.mediaReplayActions.isEmpty())
    }

    @Test
    fun mediaReplayIsPreparedForDispatchAfterLink() {
        val controller = NoOpController
        val metadata = MediaSession.ElementMetadata(
            width = 1280,
            height = 720,
            videoTrackCount = 1,
        )
        val plan = androidComponentsExistingSessionTransferPlan(
            tabId = "7",
            mediaSessionHandoff = AndroidComponentsMediaSessionHandoff(
                controller = controller,
                playbackState = MediaSession.PlaybackState.PLAYING,
                fullscreen = true,
                elementMetadata = metadata,
            ),
        )

        assertEquals(3, plan.mediaReplayActions.size)
        val activated = plan.mediaReplayActions[0] as MediaSessionAction.ActivatedMediaSessionAction
        assertEquals("7", activated.tabId)
        assertSame(controller, activated.mediaSessionController)

        val playback = plan.mediaReplayActions[1] as MediaSessionAction.UpdateMediaPlaybackStateAction
        assertEquals(MediaSession.PlaybackState.PLAYING, playback.playbackState)

        val fullscreen = plan.mediaReplayActions[2] as MediaSessionAction.UpdateMediaFullscreenAction
        assertTrue(fullscreen.fullScreen)
        assertSame(metadata, fullscreen.elementMetadata)
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
