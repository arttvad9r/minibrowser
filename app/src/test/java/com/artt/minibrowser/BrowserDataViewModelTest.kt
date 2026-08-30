package com.artt.minibrowser

import com.artt.minibrowser.browser.BrowserDataStatus
import com.artt.minibrowser.browser.BrowserDataUiState
import com.artt.minibrowser.browser.BrowserDataViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserDataViewModelTest {
    @Test
    fun clearWithoutBookmarksPreservesOperationOrder() {
        val events = mutableListOf<String>()
        val viewModel = browserDataViewModel(
            clearTabPreviews = { events += "previews" },
            clearHistory = { events += "history" },
            clearBookmarks = { events += "bookmarks" },
            clearFaviconCaches = { events += "favicons" },
            clearWebData = { events += "web" },
        )

        viewModel.clear(withBookmarks = false)

        assertEquals(listOf("previews", "web", "history", "favicons"), events)
        assertEquals(BrowserDataUiState(), viewModel.uiState.value)
    }

    @Test
    fun clearWithBookmarksIncludesBookmarksBeforeCaches() {
        val events = mutableListOf<String>()
        val viewModel = browserDataViewModel(
            clearTabPreviews = { events += "previews" },
            clearHistory = { events += "history" },
            clearBookmarks = { events += "bookmarks" },
            clearFaviconCaches = { events += "favicons" },
            clearWebData = { events += "web" },
        )

        viewModel.clear(withBookmarks = true)

        assertEquals(listOf("previews", "web", "history", "bookmarks", "favicons"), events)
        assertEquals(BrowserDataUiState(), viewModel.uiState.value)
    }

    @Test
    fun duplicateClearIsIgnoredWhileOperationIsRunning() {
        val gate = CompletableDeferred<Unit>()
        var historyCalls = 0
        val viewModel = browserDataViewModel(
            clearHistory = {
                historyCalls++
                gate.await()
            },
        )

        viewModel.clear(withBookmarks = false)
        viewModel.clear(withBookmarks = true)

        assertEquals(1, historyCalls)
        assertEquals(BrowserDataStatus.Clearing, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.isClearing)
        assertFalse(viewModel.uiState.value.clearFailed)

        gate.complete(Unit)
        assertEquals(BrowserDataUiState(), viewModel.uiState.value)
    }

    @Test
    fun clearingStateLastsUntilWebDataOperationCompletes() {
        val gate = CompletableDeferred<Unit>()
        val viewModel = browserDataViewModel(clearWebData = { gate.await() })

        viewModel.clear(withBookmarks = false)

        assertEquals(BrowserDataStatus.Clearing, viewModel.uiState.value.status)
        gate.complete(Unit)
        assertEquals(BrowserDataStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun webDataFailureStopsPersistentStoreCleanup() {
        val events = mutableListOf<String>()
        val viewModel = browserDataViewModel(
            clearTabPreviews = { events += "previews" },
            clearHistory = { events += "history" },
            clearBookmarks = { events += "bookmarks" },
            clearFaviconCaches = { events += "favicons" },
            clearWebData = {
                events += "web"
                error("boom")
            },
        )

        viewModel.clear(withBookmarks = true)

        assertEquals(listOf("previews", "web"), events)
        assertEquals(BrowserDataStatus.Failed, viewModel.uiState.value.status)
    }

    @Test
    fun failureStopsRemainingOperationsAndPublishesExclusiveErrorState() {
        val events = mutableListOf<String>()
        val viewModel = browserDataViewModel(
            clearTabPreviews = { events += "previews" },
            clearHistory = {
                events += "history"
                error("boom")
            },
            clearBookmarks = { events += "bookmarks" },
            clearFaviconCaches = { events += "favicons" },
            clearWebData = { events += "web" },
        )

        viewModel.clear(withBookmarks = true)

        assertEquals(listOf("previews", "web", "history"), events)
        assertEquals(BrowserDataStatus.Failed, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.clearFailed)
        assertFalse(viewModel.uiState.value.isClearing)

        viewModel.dismissError()
        assertEquals(BrowserDataUiState(), viewModel.uiState.value)
    }

    @Test
    fun dismissErrorDoesNotChangeNonErrorState() {
        val gate = CompletableDeferred<Unit>()
        val viewModel = browserDataViewModel(clearHistory = { gate.await() })

        viewModel.clear(withBookmarks = false)
        viewModel.dismissError()

        assertEquals(BrowserDataStatus.Clearing, viewModel.uiState.value.status)
        gate.complete(Unit)
    }

    private fun browserDataViewModel(
        clearTabPreviews: () -> Unit = {},
        clearHistory: suspend () -> Unit = {},
        clearBookmarks: suspend () -> Unit = {},
        clearFaviconCaches: suspend () -> Unit = {},
        clearWebData: suspend () -> Unit = {},
    ): BrowserDataViewModel = BrowserDataViewModel(
        clearTabPreviews = clearTabPreviews,
        clearHistory = clearHistory,
        clearBookmarks = clearBookmarks,
        clearFaviconCaches = clearFaviconCaches,
        clearWebData = clearWebData,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
