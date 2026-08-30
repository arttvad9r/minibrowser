package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrowserViewModel : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    fun screen(value: BrowserScreen) {
        val current = _state.value
        _state.value = current.copy(
            screen = value,
            overlay = current.overlay.takeIf { value == BrowserScreen.Browser },
        )
    }

    fun showSwitcher(value: Boolean) = setOverlay(BrowserOverlay.Switcher, value)
    fun showFind(value: Boolean) = setOverlay(BrowserOverlay.Find, value)
    fun showSiteInfo(value: Boolean) = setOverlay(BrowserOverlay.SiteInfo, value)

    private fun setOverlay(target: BrowserOverlay, visible: Boolean) {
        val current = _state.value
        val next = when {
            visible && current.screen == BrowserScreen.Browser -> target
            !visible && current.overlay == target -> null
            else -> current.overlay
        }
        if (next != current.overlay) _state.value = current.copy(overlay = next)
    }
}
