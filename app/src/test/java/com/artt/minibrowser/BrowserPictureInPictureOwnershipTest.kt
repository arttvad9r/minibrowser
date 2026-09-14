package com.artt.minibrowser

import com.artt.minibrowser.browser.BrowserPictureInPicturePlaybackState
import com.artt.minibrowser.engine.RawSessionOwnership
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserPictureInPictureOwnershipTest {
    @Test
    fun ownedTabUsesRawMediaSnapshot() {
        val raw = BrowserPictureInPicturePlaybackState(
            playing = true,
            videoWidth = 1920,
            videoHeight = 1080,
        )
        val linked = BrowserPictureInPicturePlaybackState()

        assertEquals(
            raw,
            pictureInPicturePlaybackStateForOwnership(
                ownership = RawSessionOwnership.Owned,
                rawPlayback = raw,
                linkedPlayback = linked,
            ),
        )
    }

    @Test
    fun relinquishedTabUsesBrowserStoreMediaSnapshot() {
        val raw = BrowserPictureInPicturePlaybackState(
            playing = false,
            videoWidth = 320,
            videoHeight = 180,
        )
        val linked = BrowserPictureInPicturePlaybackState(
            playing = true,
            videoWidth = 2560,
            videoHeight = 1440,
        )

        assertEquals(
            linked,
            pictureInPicturePlaybackStateForOwnership(
                ownership = RawSessionOwnership.Relinquished,
                rawPlayback = raw,
                linkedPlayback = linked,
            ),
        )
    }

    @Test
    fun missingTabFailsClosedInsteadOfUsingEitherSnapshot() {
        assertEquals(
            BrowserPictureInPicturePlaybackState(),
            pictureInPicturePlaybackStateForOwnership(
                ownership = null,
                rawPlayback = BrowserPictureInPicturePlaybackState(playing = true),
                linkedPlayback = BrowserPictureInPicturePlaybackState(playing = true),
            ),
        )
    }
}
