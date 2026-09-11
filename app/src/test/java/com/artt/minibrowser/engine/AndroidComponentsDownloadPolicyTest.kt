package com.artt.minibrowser.engine

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.InitAction
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

        store.dispatchAndDrain(ContentAction.UpdateDownloadAction("42", download))

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

        store.dispatchAndDrain(ContentAction.UpdateDownloadAction("42", download))

        assertEquals(1, calls)
        assertNull(store.state.tabs.single().content.download)
        assertTrue(stream.closed)
    }

    @Test
    fun throwingDownloadConsumerFailsClosedWithoutPoisoningBrowserStore() {
        val stream = TrackingInputStream("body".encodeToByteArray())
        val download = download(stream)
        val store = store { error("Activity handoff failed") }

        store.dispatchAndDrain(ContentAction.UpdateDownloadAction("42", download))

        assertNull(store.state.tabs.single().content.download)
        assertTrue(stream.closed)
    }

    private fun BrowserStore.dispatchAndDrain(action: BrowserAction) = runBlocking {
        dispatch(action).join()
        // Store.dispatch is asynchronous. The middleware queues a consume action after the update;
        // this no-op action is a FIFO barrier so assertions see the terminal BrowserStore state.
        dispatch(InitAction).join()
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
