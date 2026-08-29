package com.artt.minibrowser

import com.artt.minibrowser.browser.HistoryOperation
import com.artt.minibrowser.browser.HistoryUiState
import com.artt.minibrowser.browser.HistoryViewModel
import com.artt.minibrowser.data.HistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryViewModelTest {
    @Test
    fun initialStateIsLoading() {
        val viewModel = historyViewModel()

        assertEquals(HistoryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesContent() {
        val entries = listOf(
            HistoryEntry("https://example.com", "Example", 10L, 1),
            HistoryEntry("https://example.org", "Example Org", 5L, 2),
        )
        val viewModel = historyViewModel(loadEntries = { entries })

        viewModel.refresh()

        assertEquals(HistoryUiState.Content(entries), viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesEmptyState() {
        val viewModel = historyViewModel(loadEntries = { emptyList() })

        viewModel.refresh()

        assertEquals(HistoryUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesLoadError() {
        val viewModel = historyViewModel(loadEntries = { error("read failed") })

        viewModel.refresh()

        assertEquals(HistoryUiState.Error(HistoryOperation.Load), viewModel.uiState.value)
    }

    @Test
    fun clearPublishesEmptyState() {
        var cleared = false
        val viewModel = historyViewModel(clearEntries = { cleared = true })

        viewModel.clear()

        assertEquals(true, cleared)
        assertEquals(HistoryUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun retryRepeatsFailedClear() {
        var attempts = 0
        val viewModel = historyViewModel(
            clearEntries = {
                attempts++
                if (attempts == 1) error("clear failed")
            },
        )

        viewModel.clear()
        assertEquals(HistoryUiState.Error(HistoryOperation.Clear), viewModel.uiState.value)

        viewModel.retry()

        assertEquals(2, attempts)
        assertEquals(HistoryUiState.Empty, viewModel.uiState.value)
    }

    private fun historyViewModel(
        loadEntries: suspend () -> List<HistoryEntry> = { emptyList() },
        clearEntries: suspend () -> Unit = {},
    ): HistoryViewModel = HistoryViewModel(
        loadEntries = loadEntries,
        clearEntries = clearEntries,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
