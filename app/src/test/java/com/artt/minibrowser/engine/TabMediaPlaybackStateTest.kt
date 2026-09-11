package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabMediaPlaybackStateTest {
    @Test
    fun fullscreenVideoMetadataPreservesPlaybackState() {
        val updated = tabMediaPlaybackStateWithVideoMetadata(
            state = TabMediaPlaybackState(playing = true),
            fullscreenVideo = true,
            width = 1920L,
            height = 1080L,
        )

        assertTrue(updated.playing)
        assertEquals(1920L, updated.videoWidth)
        assertEquals(1080L, updated.videoHeight)
    }

    @Test
    fun leavingFullscreenVideoClearsDimensionsWithoutInventingPlaybackChange() {
        val updated = tabMediaPlaybackStateWithVideoMetadata(
            state = TabMediaPlaybackState(
                playing = false,
                videoWidth = 1280L,
                videoHeight = 720L,
            ),
            fullscreenVideo = false,
            width = 1280L,
            height = 720L,
        )

        assertFalse(updated.playing)
        assertEquals(0L, updated.videoWidth)
        assertEquals(0L, updated.videoHeight)
    }

    @Test
    fun missingVideoDimensionsFailClosedToUnknownAspectRatio() {
        val updated = tabMediaPlaybackStateWithVideoMetadata(
            state = TabMediaPlaybackState(playing = true),
            fullscreenVideo = true,
            width = null,
            height = null,
        )

        assertTrue(updated.playing)
        assertEquals(0L, updated.videoWidth)
        assertEquals(0L, updated.videoHeight)
    }
}
