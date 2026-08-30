package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val testClearBrowserData: (suspend (Boolean) -> Unit)?

    constructor() : super() {
        testClearBrowserData = null
    }

    internal constructor(
        clearTabPreviews: () -> Unit,
        clearHistory: suspend () -> Unit,
        clearBookmarks: suspend () -> Unit,
        clearFaviconCaches: suspend () -> Unit,
        clearWebData: suspend () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        testClearBrowserData = BrowserDataClearer(
            clearTabPreviews = clearTabPreviews,
            clearHistory = clearHistory,
            clearBookmarks = clearBookmarks,
            clearFaviconCaches = clearFaviconCaches,
            clearWebData = clearWebData,
        )::clear
    }

    private val _uiState = MutableStateFlow(BrowserDataUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * The production clearer is supplied per operation instead of being retained by this ViewModel.
     * BrowserDataViewModel survives Activity recreation, while BrowserDataClearer intentionally owns
     * the current Activity's TabManager. Retaining that clearer here would keep a destroyed browser
     * host alive and could send a later clear request to an already-closed TabManager.
     */
    fun clear(withBookmarks: Boolean, clearer: BrowserDataClearer? = null) {
        if (_uiState.value.status == BrowserDataStatus.Clearing) return
        val clearBrowserData = clearer?.let { current -> current::clear }
            ?: testClearBrowserData
            ?: error("BrowserDataClearer is required for production clear operations")
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
}
