package com.artt.minibrowser

import com.artt.minibrowser.browser.BrowserPictureInPictureMediaState
import com.artt.minibrowser.browser.BrowserPictureInPicturePlaybackState
import com.artt.minibrowser.browser.PictureInPictureAspectRatio
import com.artt.minibrowser.browser.calculatePictureInPictureAspectRatio
import com.artt.minibrowser.browser.pictureInPictureMediaStateForTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserPictureInPictureControllerTest {
    @Test
    fun standardVideoRatioIsPreserved() {
        assertEquals(
            PictureInPictureAspectRatio(16, 9),
            calculatePictureInPictureAspectRatio(1920, 1080),
        )
    }

    @Test
    fun unknownRatioFallsBackToSixteenByNine() {
        assertEquals(
            PictureInPictureAspectRatio(16, 9),
            calculatePictureInPictureAspectRatio(0, 0),
        )
    }

    @Test
    fun platformAspectRatioLimitsAreApplied() {
        assertEquals(
            PictureInPictureAspectRatio(239, 100),
            calculatePictureInPictureAspectRatio(3840, 1080),
        )
        assertEquals(
            PictureInPictureAspectRatio(100, 239),
            calculatePictureInPictureAspectRatio(1080, 3840),
        )
    }

    @Test
    fun autoEnterRequiresPlayingFullscreenNonPrivateVideo() {
        val playingFullscreen = BrowserPictureInPictureMediaState(
            fullscreenVideo = true,
            playing = true,
        )
        assertTrue(playingFullscreen.canEnter)
        assertTrue(playingFullscreen.canAutoEnter)

        assertFalse(playingFullscreen.copy(playing = false).canAutoEnter)
        assertFalse(playingFullscreen.copy(fullscreenVideo = false).canEnter)
        assertFalse(playingFullscreen.copy(privateTab = true).canEnter)
        assertFalse(playingFullscreen.copy(privateTab = true).canAutoEnter)
    }

    @Test
    fun contentFullscreenIsThePipFullscreenAuthority() {
        val playback = BrowserPictureInPicturePlaybackState(
            playing = true,
            videoWidth = 1920,
            videoHeight = 1080,
        )

        val fullscreen = pictureInPictureMediaStateForTab(
            contentFullscreen = true,
            privateTab = false,
            playback = playback,
        )
        assertTrue(fullscreen.canAutoEnter)
        assertEquals(1920, fullscreen.videoWidth)
        assertEquals(1080, fullscreen.videoHeight)

        val notFullscreen = pictureInPictureMediaStateForTab(
            contentFullscreen = false,
            privateTab = false,
            playback = playback,
        )
        assertFalse(notFullscreen.canEnter)
        assertFalse(notFullscreen.canAutoEnter)
    }
}
