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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val HISTORY_SCREEN_LIMIT = 200

internal enum class HistoryOperation { Load, Clear }

internal sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(val entries: List<HistoryEntry>) : HistoryUiState
    data object Empty : HistoryUiState
    data class Error(val operation: HistoryOperation) : HistoryUiState
}

internal class HistoryViewModel : ViewModel {
    private val loadEntries: suspend () -> List<HistoryEntry>
    private val clearEntries: suspend () -> Unit

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
        viewModelScope.launch {
            if (_uiState.value !is HistoryUiState.Content) {
                _uiState.value = HistoryUiState.Loading
            }
            _uiState.value = try {
                val entries = loadEntries()
                if (entries.isEmpty()) HistoryUiState.Empty else HistoryUiState.Content(entries)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                HistoryUiState.Error(HistoryOperation.Load)
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            _uiState.value = try {
                clearEntries()
                HistoryUiState.Empty
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                HistoryUiState.Error(HistoryOperation.Clear)
            }
        }
    }

    fun retry() {
        when ((_uiState.value as? HistoryUiState.Error)?.operation) {
            HistoryOperation.Clear -> clear()
            HistoryOperation.Load, null -> refresh()
        }
    }

    companion object {
        fun factory(repository: HistoryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository) }
        }
    }
}
