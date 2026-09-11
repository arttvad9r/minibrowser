package com.artt.minibrowser.engine

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.concept.engine.DownloadDelegate
import mozilla.components.lib.state.Middleware
import mozilla.components.support.ktx.kotlin.sanitizeFileName

/**
 * Derives the same URL fallback that the raw Gecko download path can hand to MiniBrowser's
 * filename parser without depending on Activity/UI state.
 */
internal fun downloadFallbackName(url: String?): String {
    val rawSegment = runCatching { URI(url.orEmpty()).rawPath }
        .getOrNull()
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: return "download"

    return runCatching {
        URLDecoder.decode(
            rawSegment.replace("+", "%2B"),
            StandardCharsets.UTF_8.name(),
        )
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "download"
}

/** MiniBrowser's filename policy before GeckoEngineSession's mandatory post-sanitization. */
internal fun suggestedDownloadFilename(
    contentDisposition: String?,
    url: String?,
): String = parseFilename(contentDisposition, downloadFallbackName(url))

/**
 * Final filename used by the raw Gecko owner during the migration.
 *
 * GeckoEngineSession 154.0.1 calls DownloadDelegate.guessFileName(...) and then applies the same
 * Mozilla sanitizeFileName() extension before publishing EngineSession.Observer.onExternalResource.
 * Applying it here keeps final filenames stable across the live ownership cutover.
 */
internal fun androidComponentsCompatibleDownloadFilename(
    contentDisposition: String?,
    url: String?,
): String = suggestedDownloadFilename(contentDisposition, url).sanitizeFileName()

/**
 * A-C download delegate. GeckoEngineSession post-sanitizes this suggestion before publishing the
 * resulting [DownloadState] through BrowserStore.
 */
internal class AndroidComponentsDownloadDelegate : DownloadDelegate {
    override fun guessFileName(
        contentDisposition: String?,
        url: String?,
        mimeType: String?,
    ): String = suggestedDownloadFilename(contentDisposition, url)
}

/**
 * Hands A-C-owned authenticated responses to MiniBrowser's existing Activity download UI exactly
 * once. A consumed response is removed from BrowserStore without closing it because the download
 * controller now owns its lifetime. If no Activity host can take ownership, A-C closes the response
 * through its standard cancel action instead of leaking an unconsumed body.
 */
internal fun androidComponentsDownloadMiddleware(
    consume: (DownloadState) -> Boolean,
): Middleware<BrowserState, BrowserAction> = { store, next, action ->
    next(action)
    if (action is ContentAction.UpdateDownloadAction) {
        val download = action.download
        store.dispatch(
            if (consume(download)) {
                ContentAction.ConsumeDownloadAction(action.sessionId, download.id)
            } else {
                ContentAction.CancelDownloadAction(action.sessionId, download.id)
            },
        )
    }
}
