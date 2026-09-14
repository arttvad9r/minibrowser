package com.artt.minibrowser.engine

import com.artt.minibrowser.data.HistorySink
import com.artt.minibrowser.data.MiniBrowserHistoryBackend
import com.artt.minibrowser.data.isHistoryUrl
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.concept.storage.PageVisit

/**
 * History adapter for Android Components-owned EngineSessions.
 *
 * [AndroidComponentsOwnedSessionConfigurator] installs it in EngineSession settings for both fresh
 * and transferred A-C sessions. Raw-owned GeckoSessions keep their existing history path until the
 * ownership cutover.
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
