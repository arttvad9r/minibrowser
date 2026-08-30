package com.artt.minibrowser

import com.artt.minibrowser.engine.isMediaStorePublishSuccessful
import com.artt.minibrowser.engine.normalizeDownloadMime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadPublishPolicyTest {
    @Test
    fun requiresAtLeastOneUpdatedMediaStoreRow() {
        assertFalse(isMediaStorePublishSuccessful(0))
        assertFalse(isMediaStorePublishSuccessful(-1))
        assertTrue(isMediaStorePublishSuccessful(1))
        assertTrue(isMediaStorePublishSuccessful(2))
    }

    @Test
    fun normalizesValidDownloadMimeTypes() {
        assertEquals("image/png", normalizeDownloadMime(" IMAGE/PNG ; charset=UTF-8"))
        assertEquals("application/vnd.api+json", normalizeDownloadMime("application/vnd.api+json"))
    }

    @Test
    fun malformedDownloadMimeFallsBackToOctetStream() {
        val fallback = "application/octet-stream"
        assertEquals(fallback, normalizeDownloadMime(null))
        assertEquals(fallback, normalizeDownloadMime("text"))
        assertEquals(fallback, normalizeDownloadMime("text/"))
        assertEquals(fallback, normalizeDownloadMime("text/html/extra"))
        assertEquals(fallback, normalizeDownloadMime("text/html\r\nInjected: value"))
        assertEquals(fallback, normalizeDownloadMime("текст/plain"))
    }
}
