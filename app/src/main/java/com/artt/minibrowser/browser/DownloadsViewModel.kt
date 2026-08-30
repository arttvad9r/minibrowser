package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.BrowserDownload
import com.artt.minibrowser.data.DownloadsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal data class DownloadsUiState(
    val downloads: List<BrowserDownload> = emptyList(),
    val isRestoring: Boolean = false,
)

/** Owns UI-facing download state behind the DownloadsRepository data boundary. */
internal class DownloadsViewModel : ViewModel {
    private val clearHistory: () -> Unit
    private val _uiState: MutableStateFlow<DownloadsUiState>
    val uiState: StateFlow<DownloadsUiState>

    constructor(repository: DownloadsRepository) : super() {
        clearHistory = repository::clear
        _uiState = MutableStateFlow(
            downloadsUiState(repository.downloads.value, repository.restoreCompleted.value),
        )
        uiState = _uiState.asStateFlow()
        repository.initialize()
        observe(repository.downloads, repository.restoreCompleted, viewModelScope)
    }

    internal constructor(
        downloads: StateFlow<List<BrowserDownload>>,
        restoreCompleted: StateFlow<Boolean>,
        initialize: () -> Unit,
        clearHistory: () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.clearHistory = clearHistory
        _uiState = MutableStateFlow(downloadsUiState(downloads.value, restoreCompleted.value))
        uiState = _uiState.asStateFlow()
        initialize()
        observe(downloads, restoreCompleted, viewModelScope)
    }

    fun clear() = clearHistory()

    private fun observe(
        downloads: StateFlow<List<BrowserDownload>>,
        restoreCompleted: StateFlow<Boolean>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            combine(downloads, restoreCompleted, ::downloadsUiState).collect { state ->
                _uiState.value = state
            }
        }
    }

    companion object {
        fun factory(repository: DownloadsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DownloadsViewModel(repository) }
        }
    }
}

private fun downloadsUiState(
    downloads: List<BrowserDownload>,
    restoreCompleted: Boolean,
): DownloadsUiState = DownloadsUiState(
    downloads = downloads,
    isRestoring = !restoreCompleted && downloads.isEmpty(),
)
