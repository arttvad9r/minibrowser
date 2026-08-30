package com.artt.minibrowser.ui

internal enum class SettingsSearchEngineUiState {
    Google,
    DuckDuckGo,
    Yandex,
    Bing,
}

internal data class SettingsScreenUiState(
    val searchEngine: SettingsSearchEngineUiState,
    val theme: Int,
    val adblockEnabled: Boolean,
    val votEnabled: Boolean,
    val translateTarget: String,
    val adblockStatus: BrowserExtensionUiState,
    val votStatus: BrowserExtensionUiState,
    val clearDataInProgress: Boolean,
    val clearDataFailed: Boolean,
)
