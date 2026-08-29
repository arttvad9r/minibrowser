package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.Suggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class OmniboxSuggestionsUiState(
    val query: String? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val isLoading: Boolean = false,
)

/** Owns the debounced, latest-query-only suggestion pipeline for the omnibox. */
internal class OmniboxSuggestionsViewModel : ViewModel {
    private val searchSuggestions: suspend (String) -> List<Suggestion>
    private val debounce: suspend () -> Unit
    private var searchJob: Job? = null

    constructor(repositoryProvider: () -> HistoryRepository) : super() {
        // Creating the chrome state holder must not open Room. Resolve the repository only after
        // the first non-blank user query survives the debounce window.
        val lazyRepository = lazy(LazyThreadSafetyMode.SYNCHRONIZED, repositoryProvider)
        searchSuggestions = { query -> lazyRepository.value.suggest(query) }
        debounce = { delay(DEBOUNCE_MS) }
    }

    internal constructor(
        searchSuggestions: suspend (String) -> List<Suggestion>,
        debounce: suspend () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.searchSuggestions = searchSuggestions
        this.debounce = debounce
    }

    private val _uiState = MutableStateFlow(OmniboxSuggestionsUiState())
    val uiState = _uiState.asStateFlow()

    fun updateQuery(query: String?) {
        searchJob?.cancel()
        val target = query?.takeIf { it.isNotBlank() }
        if (target == null) {
            _uiState.value = OmniboxSuggestionsUiState()
            return
        }

        _uiState.value = OmniboxSuggestionsUiState(query = target, isLoading = true)
        searchJob = viewModelScope.launch {
            try {
                debounce()
                val suggestions = searchSuggestions(target)
                if (_uiState.value.query == target) {
                    _uiState.value = OmniboxSuggestionsUiState(
                        query = target,
                        suggestions = suggestions,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (_uiState.value.query == target) {
                    // Suggestions are transient chrome. A storage/search failure should collapse
                    // the popup rather than surface a persistent screen-level error.
                    _uiState.value = OmniboxSuggestionsUiState(query = target)
                }
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 90L

        fun factory(repositoryProvider: () -> HistoryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { OmniboxSuggestionsViewModel(repositoryProvider) }
        }
    }
}
