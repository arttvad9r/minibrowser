package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.BrowserDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DownloadsUiState(
    val downloads: List<BrowserDownload> = emptyList(),
)

/** Owns the UI-facing download-history state without coupling the renderer to its global store. */
internal class DownloadsViewModel : ViewModel {
    private val clearHistory: () -> Unit
    private val _uiState: MutableStateFlow<DownloadsUiState>
    val uiState: StateFlow<DownloadsUiState>

    constructor(
        downloads: StateFlow<List<BrowserDownload>>,
        initialize: () -> Unit,
        clearHistory: () -> Unit,
    ) : super() {
        this.clearHistory = clearHistory
        _uiState = MutableStateFlow(DownloadsUiState(downloads.value))
        uiState = _uiState.asStateFlow()
        initialize()
        observe(downloads, viewModelScope)
    }

    internal constructor(
        downloads: StateFlow<List<BrowserDownload>>,
        initialize: () -> Unit,
        clearHistory: () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.clearHistory = clearHistory
        _uiState = MutableStateFlow(DownloadsUiState(downloads.value))
        uiState = _uiState.asStateFlow()
        initialize()
        observe(downloads, viewModelScope)
    }

    fun clear() = clearHistory()

    private fun observe(
        downloads: StateFlow<List<BrowserDownload>>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            downloads.collect { items ->
                _uiState.value = DownloadsUiState(downloads = items)
            }
        }
    }

    companion object {
        fun factory(
            downloads: StateFlow<List<BrowserDownload>>,
            initialize: () -> Unit,
            clearHistory: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DownloadsViewModel(
                    downloads = downloads,
                    initialize = initialize,
                    clearHistory = clearHistory,
                )
            }
        }
    }
}
