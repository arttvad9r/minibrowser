package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.concept.engine.mediasession.MediaSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidComponentsMediaSessionHandoffTest {
    @Test
    fun noActiveMediaSessionNeedsNoReplay() {
        assertTrue(androidComponentsMediaSessionHandoffActions("42", null).isEmpty())
    }

    @Test
    fun activationPrecedesPlaybackAndFullscreenReplay() {
        val controller = FakeController()
        val metadata = MediaSession.ElementMetadata(
            width = 1920,
            height = 1080,
            videoTrackCount = 1,
        )
        val actions = androidComponentsMediaSessionHandoffActions(
            tabId = "42",
            handoff = AndroidComponentsMediaSessionHandoff(
                controller = controller,
                playbackState = MediaSession.PlaybackState.PLAYING,
                fullscreen = true,
                elementMetadata = metadata,
            ),
        )

        assertEquals(3, actions.size)
        val activated = actions[0] as MediaSessionAction.ActivatedMediaSessionAction
        assertEquals("42", activated.tabId)
        assertSame(controller, activated.mediaSessionController)

        val playback = actions[1] as MediaSessionAction.UpdateMediaPlaybackStateAction
        assertEquals(MediaSession.PlaybackState.PLAYING, playback.playbackState)

        val fullscreen = actions[2] as MediaSessionAction.UpdateMediaFullscreenAction
        assertTrue(fullscreen.fullScreen)
        assertEquals(metadata, fullscreen.elementMetadata)
    }

    @Test
    fun unknownPlaybackAndDefaultFullscreenDoNotInventState() {
        val actions = androidComponentsMediaSessionHandoffActions(
            tabId = "7",
            handoff = AndroidComponentsMediaSessionHandoff(controller = FakeController()),
        )

        assertEquals(1, actions.size)
        assertTrue(actions.single() is MediaSessionAction.ActivatedMediaSessionAction)
    }

    private class FakeController : MediaSession.Controller {
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
