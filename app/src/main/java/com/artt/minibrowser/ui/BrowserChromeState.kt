package com.artt.minibrowser.ui

internal enum class BrowserSecurityUiState {
    Unknown,
    Secure,
    Insecure,
    Exception,
}

enum class BrowserExtensionUiState {
    Installing,
    Error,
    Enabled,
    Disabled,
}

internal data class BrowserChromeUiState(
    val url: String = "",
    val isWebPage: Boolean = false,
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val securityState: BrowserSecurityUiState = BrowserSecurityUiState.Unknown,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktop: Boolean = false,
)
