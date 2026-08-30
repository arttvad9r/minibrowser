package com.artt.minibrowser

import com.artt.minibrowser.browser.DownloadsUiState
import com.artt.minibrowser.browser.DownloadsViewModel
import com.artt.minibrowser.data.BrowserDownload
import com.artt.minibrowser.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadsViewModelTest {
    @Test
    fun creationInitializesStoreOnce() {
        var initializes = 0

        downloadsViewModel(initialize = { initializes++ })

        assertEquals(1, initializes)
    }

    @Test
    fun emptyHistoryIsRestoringUntilStoreRestoreCompletes() {
        val restored = MutableStateFlow(false)
        val viewModel = downloadsViewModel(restoreCompleted = restored)

        assertEquals(DownloadsUiState(isRestoring = true), viewModel.uiState.value)

        restored.value = true

        assertEquals(DownloadsUiState(), viewModel.uiState.value)
    }

    @Test
    fun liveItemsAreExposedImmediatelyWhileRestoreIsRunning() {
        val item = download("one")
        val source = MutableStateFlow(listOf(item))
        val restored = MutableStateFlow(false)

        val viewModel = downloadsViewModel(downloads = source, restoreCompleted = restored)

        assertEquals(DownloadsUiState(listOf(item)), viewModel.uiState.value)
    }

    @Test
    fun liveUpdatesAreForwarded() {
        val source = MutableStateFlow(emptyList<BrowserDownload>())
        val viewModel = downloadsViewModel(downloads = source)
        val item = download("two")

        source.value = listOf(item)

        assertEquals(DownloadsUiState(listOf(item)), viewModel.uiState.value)
    }

    @Test
    fun restoredItemsReplaceLoadingState() {
        val source = MutableStateFlow(emptyList<BrowserDownload>())
        val restored = MutableStateFlow(false)
        val viewModel = downloadsViewModel(downloads = source, restoreCompleted = restored)
        val item = download("restored")

        source.value = listOf(item)
        restored.value = true

        assertEquals(DownloadsUiState(listOf(item)), viewModel.uiState.value)
    }

    @Test
    fun clearDelegatesToStore() {
        var clears = 0
        val viewModel = downloadsViewModel(clearHistory = { clears++ })

        viewModel.clear()

        assertEquals(1, clears)
    }

    private fun downloadsViewModel(
        downloads: MutableStateFlow<List<BrowserDownload>> = MutableStateFlow(emptyList()),
        restoreCompleted: MutableStateFlow<Boolean> = MutableStateFlow(true),
        initialize: () -> Unit = {},
        clearHistory: () -> Unit = {},
    ): DownloadsViewModel = DownloadsViewModel(
        downloads = downloads,
        restoreCompleted = restoreCompleted,
        initialize = initialize,
        clearHistory = clearHistory,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun download(id: String) = BrowserDownload(
        id = id,
        name = "$id.txt",
        sourceUrl = "https://example.com",
        mime = "text/plain",
        status = DownloadStatus.Completed,
        startedAt = 1L,
        finishedAt = 2L,
        bytes = 3L,
        location = "content://downloads/$id",
    )
}
