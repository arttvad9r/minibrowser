package com.artt.minibrowser

import com.artt.minibrowser.browser.BrowserPictureInPictureMediaState
import com.artt.minibrowser.browser.PictureInPictureAspectRatio
import com.artt.minibrowser.browser.calculatePictureInPictureAspectRatio
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
}
