package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BrowserDataStatus { Idle, Clearing, Failed }

internal data class BrowserDataUiState(
    val status: BrowserDataStatus = BrowserDataStatus.Idle,
) {
    val isClearing: Boolean get() = status == BrowserDataStatus.Clearing
    val clearFailed: Boolean get() = status == BrowserDataStatus.Failed
}

/** Owns UI state for the cross-feature browser-data clearing operation. */
internal class BrowserDataViewModel : ViewModel {
    private val clearBrowserData: suspend (Boolean) -> Unit

    constructor(clearer: BrowserDataClearer) : super() {
        clearBrowserData = clearer::clear
    }

    internal constructor(
        clearTabPreviews: () -> Unit,
        clearHistory: suspend () -> Unit,
        clearBookmarks: suspend () -> Unit,
        clearFaviconCaches: () -> Unit,
        clearWebData: suspend () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        clearBrowserData = BrowserDataClearer(
            clearTabPreviews = clearTabPreviews,
            clearHistory = clearHistory,
            clearBookmarks = clearBookmarks,
            clearFaviconCaches = clearFaviconCaches,
            clearWebData = clearWebData,
        )::clear
    }

    private val _uiState = MutableStateFlow(BrowserDataUiState())
    val uiState = _uiState.asStateFlow()

    fun clear(withBookmarks: Boolean) {
        if (_uiState.value.status == BrowserDataStatus.Clearing) return
        _uiState.value = BrowserDataUiState(BrowserDataStatus.Clearing)
        viewModelScope.launch {
            try {
                clearBrowserData(withBookmarks)
                _uiState.value = BrowserDataUiState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.value = BrowserDataUiState(BrowserDataStatus.Failed)
            }
        }
    }

    fun dismissError() {
        if (_uiState.value.status == BrowserDataStatus.Failed) {
            _uiState.value = BrowserDataUiState()
        }
    }

    companion object {
        fun factory(clearer: BrowserDataClearer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BrowserDataViewModel(clearer) }
        }
    }
}
