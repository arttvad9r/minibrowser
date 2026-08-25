package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrowserViewModel : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    fun screen(value: BrowserScreen) { _state.value = _state.value.copy(screen = value) }
    fun showSwitcher(value: Boolean) { _state.value = _state.value.copy(showSwitcher = value) }
    fun showFind(value: Boolean) { _state.value = _state.value.copy(showFind = value) }
    fun showSiteInfo(value: Boolean) { _state.value = _state.value.copy(showSiteInfo = value) }
}
