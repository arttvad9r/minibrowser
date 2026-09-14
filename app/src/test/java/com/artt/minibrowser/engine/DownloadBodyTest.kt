package com.artt.minibrowser.engine

import java.io.ByteArrayInputStream
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadBodyTest {
    @Test
    fun rawInputStreamIsConsumedOnlyWhenIoTakesOwnership() {
        val stream = TrackingInputStream("raw-body".encodeToByteArray())
        val body = InputStreamDownloadBody(stream)

        assertFalse(stream.closed)
        assertEquals("raw-body", body.useStream { it.readBytes().decodeToString() })
        assertTrue(stream.closed)
    }

    @Test
    fun androidComponentsResponseStaysOpenUntilIoConsumesIt() {
        val stream = TrackingInputStream("engine-body".encodeToByteArray())
        val body = AndroidComponentsDownloadBody(response(stream))

        assertFalse(stream.closed)
        assertEquals("engine-body", body.useStream { it.readBytes().decodeToString() })
        assertTrue(stream.closed)
    }

    @Test
    fun androidComponentsResponseClosesWhenDownloadIsRejectedBeforeConsumption() {
        val stream = TrackingInputStream("unused".encodeToByteArray())
        val body = AndroidComponentsDownloadBody(response(stream))

        body.close()

        assertTrue(stream.closed)
    }

    @Test
    fun androidComponentsResponseClosesWhenIoFailsWhileConsumingIt() {
        val stream = TrackingInputStream("failure".encodeToByteArray())
        val body = AndroidComponentsDownloadBody(response(stream))

        assertFailsWith<IllegalStateException> {
            body.useStream { error("copy failed") }
        }
        assertTrue(stream.closed)
    }

    private fun response(stream: TrackingInputStream) = Response(
        url = "https://example.test/download",
        status = Response.SUCCESS,
        headers = MutableHeaders(),
        body = Response.Body(stream),
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
