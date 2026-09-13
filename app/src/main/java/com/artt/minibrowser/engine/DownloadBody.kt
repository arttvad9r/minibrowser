package com.artt.minibrowser.engine

import java.io.InputStream
import mozilla.components.concept.fetch.Response

/**
 * Owns an authenticated response body until the download IO coroutine is ready to consume it.
 *
 * Android Components' [Response.Body] exposes its stream only through useStream(), which closes
 * the body when the block returns. Keeping ownership at this level prevents callers from opening
 * the stream on the engine callback thread and accidentally closing it before asynchronous IO
 * takes over.
 */
internal interface DownloadBody : AutoCloseable {
    fun <R> useStream(block: (InputStream) -> R): R
}

/** Adapter for MiniBrowser's current raw GeckoView WebResponse body. */
internal class InputStreamDownloadBody(
    private val stream: InputStream,
) : DownloadBody {
    override fun <R> useStream(block: (InputStream) -> R): R = stream.use(block)

    override fun close() {
        runCatching { stream.close() }
    }
}

/** Adapter for an Android Components authenticated concept-fetch response. */
internal class AndroidComponentsDownloadBody(
    private val response: Response,
) : DownloadBody {
    override fun <R> useStream(block: (InputStream) -> R): R = response.body.useStream(block)

    override fun close() {
        runCatching { response.close() }
    }
}
