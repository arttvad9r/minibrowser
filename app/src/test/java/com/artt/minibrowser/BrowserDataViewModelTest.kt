package com.artt.minibrowser

import com.artt.minibrowser.browser.BrowserDataUiState
import com.artt.minibrowser.browser.BrowserDataViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals

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

        assertEquals(listOf("previews", "history", "favicons", "web"), events)
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

        assertEquals(listOf("previews", "history", "bookmarks", "favicons", "web"), events)
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
        assertEquals(BrowserDataUiState(isClearing = true), viewModel.uiState.value)

        gate.complete(Unit)
        assertEquals(BrowserDataUiState(), viewModel.uiState.value)
    }

    @Test
    fun failureStopsRemainingOperationsAndPublishesError() {
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

        assertEquals(listOf("previews", "history"), events)
        assertEquals(BrowserDataUiState(clearFailed = true), viewModel.uiState.value)

        viewModel.dismissError()
        assertEquals(BrowserDataUiState(), viewModel.uiState.value)
    }

    private fun browserDataViewModel(
        clearTabPreviews: () -> Unit = {},
        clearHistory: suspend () -> Unit = {},
        clearBookmarks: suspend () -> Unit = {},
        clearFaviconCaches: () -> Unit = {},
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
