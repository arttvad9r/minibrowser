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

/** Coordinates the cross-feature "clear browser data" operation outside the UI renderer. */
internal class BrowserDataViewModel : ViewModel {
    private val clearTabPreviews: () -> Unit
    private val clearHistory: suspend () -> Unit
    private val clearBookmarks: suspend () -> Unit
    private val clearFaviconCaches: () -> Unit
    private val clearWebData: suspend () -> Unit

    constructor(
        clearTabPreviews: () -> Unit,
        clearHistory: suspend () -> Unit,
        clearBookmarks: suspend () -> Unit,
        clearFaviconCaches: () -> Unit,
        clearWebData: suspend () -> Unit,
    ) : super() {
        this.clearTabPreviews = clearTabPreviews
        this.clearHistory = clearHistory
        this.clearBookmarks = clearBookmarks
        this.clearFaviconCaches = clearFaviconCaches
        this.clearWebData = clearWebData
    }

    internal constructor(
        clearTabPreviews: () -> Unit,
        clearHistory: suspend () -> Unit,
        clearBookmarks: suspend () -> Unit,
        clearFaviconCaches: () -> Unit,
        clearWebData: suspend () -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.clearTabPreviews = clearTabPreviews
        this.clearHistory = clearHistory
        this.clearBookmarks = clearBookmarks
        this.clearFaviconCaches = clearFaviconCaches
        this.clearWebData = clearWebData
    }

    private val _uiState = MutableStateFlow(BrowserDataUiState())
    val uiState = _uiState.asStateFlow()

    fun clear(withBookmarks: Boolean) {
        if (_uiState.value.status == BrowserDataStatus.Clearing) return
        _uiState.value = BrowserDataUiState(BrowserDataStatus.Clearing)
        viewModelScope.launch {
            try {
                // Preserve the previous user-visible ordering while keeping every storage detail
                // behind one testable boundary instead of scattering it through Settings UI/root.
                clearTabPreviews()
                clearHistory()
                if (withBookmarks) clearBookmarks()
                clearFaviconCaches()
                clearWebData()
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
        fun factory(
            clearTabPreviews: () -> Unit,
            clearHistory: suspend () -> Unit,
            clearBookmarks: suspend () -> Unit,
            clearFaviconCaches: () -> Unit,
            clearWebData: suspend () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BrowserDataViewModel(
                    clearTabPreviews = clearTabPreviews,
                    clearHistory = clearHistory,
                    clearBookmarks = clearBookmarks,
                    clearFaviconCaches = clearFaviconCaches,
                    clearWebData = clearWebData,
                )
            }
        }
    }
}
