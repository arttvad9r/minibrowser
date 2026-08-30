package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val HISTORY_SCREEN_LIMIT = 200

internal enum class HistoryOperation { Load, Clear }

internal sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(
        val entries: List<HistoryEntry>,
        val error: HistoryOperation? = null,
    ) : HistoryUiState
    data object Empty : HistoryUiState
    data class Error(val operation: HistoryOperation) : HistoryUiState
}

internal class HistoryViewModel : ViewModel {
    private val loadEntries: suspend () -> List<HistoryEntry>
    private val clearEntries: suspend () -> Unit
    private var operationJob: Job? = null

    constructor(repository: HistoryRepository) : super() {
        loadEntries = { repository.recent(HISTORY_SCREEN_LIMIT) }
        clearEntries = repository::clear
    }

    internal constructor(
        loadEntries: suspend () -> List<HistoryEntry>,
        clearEntries: suspend () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.loadEntries = loadEntries
        this.clearEntries = clearEntries
    }

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun refresh() {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = if (previous is HistoryUiState.Content) {
                previous.copy(error = null)
            } else {
                HistoryUiState.Loading
            }
            try {
                val entries = loadEntries()
                _uiState.value = if (entries.isEmpty()) {
                    HistoryUiState.Empty
                } else {
                    HistoryUiState.Content(entries)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.value = if (previous is HistoryUiState.Content) {
                    previous.copy(error = HistoryOperation.Load)
                } else {
                    HistoryUiState.Error(HistoryOperation.Load)
                }
            }
        }
    }

    fun clear() {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            val previous = _uiState.value
            if (previous is HistoryUiState.Content) {
                _uiState.value = previous.copy(error = null)
            }
            try {
                clearEntries()
                _uiState.value = HistoryUiState.Empty
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.value = if (previous is HistoryUiState.Content) {
                    previous.copy(error = HistoryOperation.Clear)
                } else {
                    HistoryUiState.Error(HistoryOperation.Clear)
                }
            }
        }
    }

    fun retry() {
        when (currentError()) {
            HistoryOperation.Clear -> clear()
            HistoryOperation.Load, null -> refresh()
        }
    }

    private fun currentError(): HistoryOperation? = when (val state = _uiState.value) {
        is HistoryUiState.Content -> state.error
        is HistoryUiState.Error -> state.operation
        HistoryUiState.Empty, HistoryUiState.Loading -> null
    }

    companion object {
        fun factory(repository: HistoryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository) }
        }
    }
}
