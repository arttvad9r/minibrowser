package com.artt.minibrowser.engine

import com.artt.minibrowser.data.MiniBrowserHistoryBackend
import com.artt.minibrowser.data.historyVisitedStatuses
import com.artt.minibrowser.data.persistentHistoryUrls
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidComponentsHistoryTrackingDelegateTest {
    @Test
    fun forwardsHistoryCallbacksWithoutChangingStorageOwnership() = runBlocking {
        val backend = FakeHistoryBackend()
        val delegate = AndroidComponentsHistoryTrackingDelegate(backend)

        delegate.onVisited("https://example.test/page", PageVisit(VisitType.LINK))
        delegate.onTitleChanged("https://example.test/page", "Example")
        delegate.onPreviewImageChange("https://example.test/page", "https://example.test/preview.png")

        assertEquals(listOf("https://example.test/page" to null), backend.visits)
        assertEquals(listOf("https://example.test/page" to "Example"), backend.titles)
    }

    @Test
    fun delegatesVisitedQueriesAndPreservesRequestedOrder() = runBlocking {
        val backend = FakeHistoryBackend(
            visited = linkedSetOf("https://example.test/a", "https://example.test/c"),
        )
        val delegate = AndroidComponentsHistoryTrackingDelegate(backend)
        val requested = listOf(
            "https://example.test/c",
            "https://example.test/b",
            "https://example.test/a",
        )

        assertEquals(listOf(true, false, true), delegate.getVisited(requested))
        assertEquals(backend.visited.toList(), delegate.getVisited())
    }

    @Test
    fun keepsMiniBrowserHistoryUriPolicy() {
        val delegate = AndroidComponentsHistoryTrackingDelegate(FakeHistoryBackend())

        assertTrue(delegate.shouldStoreUri("https://example.test/page"))
        assertFalse(delegate.shouldStoreUri("about:blank"))
    }

    @Test
    fun visitedStatusHelpersRejectInternalUrisAndDeduplicatePersistentRows() {
        val stored = setOf("https://example.test/a")
        assertEquals(
            listOf(true, false, false),
            historyVisitedStatuses(
                listOf(
                    "https://example.test/a",
                    "about:blank",
                    "https://example.test/b",
                ),
                stored,
            ),
        )
        assertEquals(
            listOf("https://example.test/a", "https://example.test/b"),
            persistentHistoryUrls(
                listOf(
                    "https://example.test/a",
                    "about:blank",
                    "https://example.test/a",
                    "https://example.test/b",
                ),
            ),
        )
    }

    private class FakeHistoryBackend(
        val visited: LinkedHashSet<String> = linkedSetOf(),
    ) : MiniBrowserHistoryBackend {
        val visits = mutableListOf<Pair<String, String?>>()
        val titles = mutableListOf<Pair<String, String?>>()

        override fun record(url: String, title: String?) {
            visits += url to title
        }

        override fun updateTitle(url: String, title: String?) {
            titles += url to title
        }

        override suspend fun getVisited(uris: List<String>): List<Boolean> =
            uris.map(visited::contains)

        override suspend fun getVisited(): List<String> = visited.toList()
    }
}
