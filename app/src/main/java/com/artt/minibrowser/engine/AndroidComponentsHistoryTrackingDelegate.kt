package com.artt.minibrowser.engine

import com.artt.minibrowser.data.HistorySink
import com.artt.minibrowser.data.MiniBrowserHistoryBackend
import com.artt.minibrowser.data.isHistoryUrl
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.concept.storage.PageVisit

/**
 * Pre-cutover adapter for Android Components history callbacks.
 *
 * This is intentionally not installed on a live GeckoEngineSession yet. The raw Gecko owner uses
 * the same top-level/non-unrecoverable visit gate as GeckoEngineSession so history storage semantics
 * are already aligned before live session ownership moves.
 */
internal class AndroidComponentsHistoryTrackingDelegate(
    private val history: MiniBrowserHistoryBackend = HistorySink,
) : HistoryTrackingDelegate {
    override suspend fun onVisited(uri: String, visit: PageVisit) {
        // MiniBrowser's current schema stores aggregate visit count/time, not transition metadata.
        history.record(uri, null)
    }

    override suspend fun onTitleChanged(uri: String, title: String) {
        history.updateTitle(uri, title)
    }

    override suspend fun onPreviewImageChange(uri: String, previewImageUrl: String) {
        // Preview images are not part of MiniBrowser's current Room history schema.
    }

    override suspend fun getVisited(uris: List<String>): List<Boolean> = history.getVisited(uris)

    override suspend fun getVisited(): List<String> = history.getVisited()

    override fun shouldStoreUri(uri: String): Boolean = isHistoryUrl(uri)
}
