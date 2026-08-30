package com.artt.minibrowser

import com.artt.minibrowser.browser.HistoryOperation
import com.artt.minibrowser.browser.HistoryUiState
import com.artt.minibrowser.browser.HistoryViewModel
import com.artt.minibrowser.data.HistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
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
        val entries = historyEntries()
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
    fun initialRefreshPublishesLoadError() {
        val viewModel = historyViewModel(loadEntries = { error("read failed") })

        viewModel.refresh()

        assertEquals(HistoryUiState.Error(HistoryOperation.Load), viewModel.uiState.value)
    }

    @Test
    fun refreshFailurePreservesExistingContent() {
        val entries = historyEntries()
        var reads = 0
        val viewModel = historyViewModel(
            loadEntries = {
                reads++
                if (reads == 1) entries else error("refresh failed")
            },
        )

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(
            HistoryUiState.Content(entries, error = HistoryOperation.Load),
            viewModel.uiState.value,
        )
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
    fun clearFailurePreservesExistingContent() {
        val entries = historyEntries()
        val viewModel = historyViewModel(
            loadEntries = { entries },
            clearEntries = { error("clear failed") },
        )

        viewModel.refresh()
        viewModel.clear()

        assertEquals(
            HistoryUiState.Content(entries, error = HistoryOperation.Clear),
            viewModel.uiState.value,
        )
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

    @Test
    fun clearCancelsInFlightRefreshSoOldRowsCannotReturn() {
        val entries = historyEntries()
        var reads = 0
        val viewModel = historyViewModel(
            loadEntries = {
                reads++
                if (reads == 1) entries else awaitCancellation()
            },
        )

        viewModel.refresh()
        viewModel.refresh()
        assertEquals(2, reads)

        viewModel.clear()

        assertEquals(HistoryUiState.Empty, viewModel.uiState.value)
    }

    private fun historyEntries(): List<HistoryEntry> = listOf(
        HistoryEntry("https://example.com", "Example", 10L, 1),
        HistoryEntry("https://example.org", "Example Org", 5L, 2),
    )

    private fun historyViewModel(
        loadEntries: suspend () -> List<HistoryEntry> = { emptyList() },
        clearEntries: suspend () -> Unit = {},
    ): HistoryViewModel = HistoryViewModel(
        loadEntries = loadEntries,
        clearEntries = clearEntries,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
