package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidComponentsGeckoCompatibilityDelegatesTest {
    @Test
    fun linkedAudioAndVideoUseRawContextMenuCompatibility() {
        assertTrue(
            shouldUseRawLinkedMediaContextMenu(
                elementType = GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO,
                linkUri = "https://example.test/page",
                srcUri = "https://example.test/audio.mp3",
            ),
        )
        assertTrue(
            shouldUseRawLinkedMediaContextMenu(
                elementType = GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO,
                linkUri = "https://example.test/page",
                srcUri = "https://example.test/video.webm",
            ),
        )
    }

    @Test
    fun plainMediaAndNonMediaStayOnStockAndroidComponentsPath() {
        assertFalse(
            shouldUseRawLinkedMediaContextMenu(
                elementType = GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO,
                linkUri = null,
                srcUri = "https://example.test/video.webm",
            ),
        )
        assertFalse(
            shouldUseRawLinkedMediaContextMenu(
                elementType = GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO,
                linkUri = "https://example.test/page",
                srcUri = null,
            ),
        )
        assertFalse(
            shouldUseRawLinkedMediaContextMenu(
                elementType = GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE,
                linkUri = "https://example.test/page",
                srcUri = "https://example.test/image.png",
            ),
        )
        assertFalse(
            shouldUseRawLinkedMediaContextMenu(
                elementType = GeckoSession.ContentDelegate.ContextElement.TYPE_NONE,
                linkUri = "https://example.test/page",
                srcUri = null,
            ),
        )
    }
}
