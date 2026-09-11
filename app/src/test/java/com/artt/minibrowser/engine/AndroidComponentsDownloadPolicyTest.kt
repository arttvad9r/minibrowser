package com.artt.minibrowser.engine

import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Response
import mozilla.components.support.ktx.kotlin.sanitizeFileName

class AndroidComponentsDownloadPolicyTest {
    @Test
    fun urlFallbackUsesDecodedLastPathSegment() {
        assertEquals(
            "отчет+final.pdf",
            downloadFallbackName(
                "https://example.test/files/%D0%BE%D1%82%D1%87%D0%B5%D1%82+final.pdf?token=secret",
            ),
        )
        assertEquals("download", downloadFallbackName("https://example.test/"))
        assertEquals("download", downloadFallbackName(null))
    }

    @Test
    fun delegateReturnsMiniBrowserSuggestionBeforeGeckoPostSanitization() {
        val delegate = AndroidComponentsDownloadDelegate()
        val disposition = "attachment; filename=\"report  final?.pdf\""

        assertEquals(
            "report  final?.pdf",
            delegate.guessFileName(
                contentDisposition = disposition,
                url = "https://example.test/fallback.bin",
                mimeType = "application/pdf",
            ),
        )
    }

    @Test
    fun rawFinalFilenameMatchesFutureGeckoEngineSessionPostSanitization() {
        val delegate = AndroidComponentsDownloadDelegate()
        val disposition = "attachment; filename=\"report  final?.pdf\""
        val url = "https://example.test/fallback.bin"
        val suggested = delegate.guessFileName(disposition, url, "application/pdf")

        assertEquals("report final_.pdf", suggested.sanitizeFileName())
        assertEquals(
            suggested.sanitizeFileName(),
            androidComponentsCompatibleDownloadFilename(disposition, url),
        )
    }

    @Test
    fun miniBrowserHardeningStillRunsBeforeMozillaPostSanitization() {
        val disposition = "attachment; filename=\"../report\u202Efdp.exe\""

        assertEquals(
            "reportfdp.exe",
            androidComponentsCompatibleDownloadFilename(
                disposition,
                "https://example.test/fallback.bin",
            ),
        )
    }

    @Test
    fun consumedDownloadLeavesResponseOpenForMiniBrowserAndClearsBrowserStoreState() {
        val stream = TrackingInputStream("body".encodeToByteArray())
        val download = download(stream)
        var handedOff: DownloadState? = null
        val store = store {
            handedOff = it
            true
        }

        store.dispatch(ContentAction.UpdateDownloadAction("42", download))
        store.awaitDownloadHandled { handedOff != null }

        assertSame(download, handedOff)
        assertNull(store.state.tabs.single().content.download)
        assertFalse(stream.closed)
        download.response?.close()
        assertTrue(stream.closed)
    }

    @Test
    fun rejectedDownloadIsClosedAndCleared() {
        val stream = TrackingInputStream("body".encodeToByteArray())
        val download = download(stream)
        var calls = 0
        val store = store {
            calls++
            false
        }

        store.dispatch(ContentAction.UpdateDownloadAction("42", download))
        store.awaitDownloadHandled { calls > 0 }

        assertEquals(1, calls)
        assertNull(store.state.tabs.single().content.download)
        assertTrue(stream.closed)
    }

    @Test
    fun throwingDownloadConsumerFailsClosedWithoutPoisoningBrowserStore() {
        val stream = TrackingInputStream("body".encodeToByteArray())
        val download = download(stream)
        var calls = 0
        val store = store {
            calls++
            error("Activity handoff failed")
        }

        store.dispatch(ContentAction.UpdateDownloadAction("42", download))
        store.awaitDownloadHandled { calls > 0 }

        assertEquals(1, calls)
        assertNull(store.state.tabs.single().content.download)
        assertTrue(stream.closed)
    }

    private fun BrowserStore.awaitDownloadHandled(handled: () -> Boolean) = runBlocking {
        // BrowserStore.dispatch() is asynchronous in the pinned A-C 154.0.1 API. Wait for both the
        // handoff callback and the nested ConsumeDownloadAction instead of depending on its return
        // type, which changed in later Android Components releases.
        withTimeout(5_000) {
            while (!handled() || state.tabs.single().content.download != null) {
                delay(1)
            }
        }
    }

    private fun store(consume: (DownloadState) -> Boolean) = BrowserStore(
        initialState = BrowserState(
            tabs = listOf(createTab(url = "https://example.test", id = "42")),
        ),
        middleware = listOf(androidComponentsDownloadMiddleware(consume)),
    )

    private fun download(stream: TrackingInputStream) = DownloadState(
        id = "download-1",
        url = "https://example.test/file.bin",
        fileName = "file.bin",
        contentType = "application/octet-stream",
        response = Response(
            url = "https://example.test/file.bin",
            status = Response.SUCCESS,
            headers = MutableHeaders(),
            body = Response.Body(stream),
        ),
    )

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
