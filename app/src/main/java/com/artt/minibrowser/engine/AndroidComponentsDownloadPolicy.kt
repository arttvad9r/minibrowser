package com.artt.minibrowser.engine

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import mozilla.components.concept.engine.DownloadDelegate
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
 * Applying it here now keeps final filenames stable across the future live ownership cutover.
 */
internal fun androidComponentsCompatibleDownloadFilename(
    contentDisposition: String?,
    url: String?,
): String = suggestedDownloadFilename(contentDisposition, url).sanitizeFileName()

/**
 * Pre-cutover A-C download delegate. It intentionally returns the MiniBrowser suggestion rather
 * than the final filename because GeckoEngineSession performs Mozilla's sanitizeFileName() step.
 */
internal class AndroidComponentsDownloadDelegate : DownloadDelegate {
    override fun guessFileName(
        contentDisposition: String?,
        url: String?,
        mimeType: String?,
    ): String = suggestedDownloadFilename(contentDisposition, url)
}
