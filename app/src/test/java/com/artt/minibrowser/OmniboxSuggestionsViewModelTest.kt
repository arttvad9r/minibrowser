package com.artt.minibrowser

import com.artt.minibrowser.browser.OmniboxSuggestionsUiState
import com.artt.minibrowser.browser.OmniboxSuggestionsViewModel
import com.artt.minibrowser.data.Suggestion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OmniboxSuggestionsViewModelTest {
    @Test
    fun productionProviderStaysLazyForBlankQuery() {
        var resolutions = 0
        val viewModel = OmniboxSuggestionsViewModel(
            repositoryProvider = {
                resolutions++
                error("repository should stay lazy")
            },
        )

        viewModel.updateQuery("   ")

        assertEquals(0, resolutions)
        assertEquals(OmniboxSuggestionsUiState(), viewModel.uiState.value)
    }

    @Test
    fun queryPublishesSuggestions() {
        val expected = listOf(Suggestion("Example", "https://example.com"))
        val viewModel = omniboxViewModel(searchSuggestions = { expected })

        viewModel.updateQuery("exa")

        assertEquals(
            OmniboxSuggestionsUiState(query = "exa", suggestions = expected),
            viewModel.uiState.value,
        )
    }

    @Test
    fun debounceRunsBeforeStorageSearch() {
        val gate = CompletableDeferred<Unit>()
        var searches = 0
        val viewModel = omniboxViewModel(
            debounce = { gate.await() },
            searchSuggestions = {
                searches++
                emptyList()
            },
        )

        viewModel.updateQuery("exa")
        assertEquals(0, searches)
        assertEquals(OmniboxSuggestionsUiState(query = "exa"), viewModel.uiState.value)

        gate.complete(Unit)

        assertEquals(1, searches)
        assertEquals(OmniboxSuggestionsUiState(query = "exa"), viewModel.uiState.value)
    }

    @Test
    fun newQueryCancelsPreviousSearch() {
        var firstCancelled = false
        val secondResult = listOf(Suggestion("Second", "https://second.example"))
        val viewModel = omniboxViewModel(
            searchSuggestions = { query ->
                if (query == "first") {
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled = true
                    }
                } else {
                    secondResult
                }
            },
        )

        viewModel.updateQuery("first")
        viewModel.updateQuery("second")

        assertEquals(
            OmniboxSuggestionsUiState(query = "second", suggestions = secondResult),
            viewModel.uiState.value,
        )
        assertTrue(firstCancelled)
    }

    @Test
    fun clearingQueryCancelsPendingSearchAndClearsResults() {
        var pendingCancelled = false
        val viewModel = omniboxViewModel(
            searchSuggestions = {
                try {
                    awaitCancellation()
                } finally {
                    pendingCancelled = true
                }
            },
        )

        viewModel.updateQuery("exa")
        viewModel.updateQuery(null)

        assertEquals(OmniboxSuggestionsUiState(), viewModel.uiState.value)
        assertTrue(pendingCancelled)
    }

    @Test
    fun searchFailureCollapsesTransientSuggestions() {
        val viewModel = omniboxViewModel(searchSuggestions = { error("search failed") })

        viewModel.updateQuery("exa")

        assertEquals(OmniboxSuggestionsUiState(query = "exa"), viewModel.uiState.value)
    }

    private fun omniboxViewModel(
        searchSuggestions: suspend (String) -> List<Suggestion> = { emptyList() },
        debounce: suspend () -> Unit = {},
    ): OmniboxSuggestionsViewModel = OmniboxSuggestionsViewModel(
        searchSuggestions = searchSuggestions,
        debounce = debounce,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
