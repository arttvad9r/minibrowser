package com.artt.minibrowser.ui

internal enum class BrowserSecurityUiState {
    Unknown,
    Secure,
    Insecure,
    Exception,
}

internal enum class BrowserExtensionUiState {
    Installing,
    Error,
    Enabled,
    Disabled,
}

internal data class BrowserChromeUiState(
    val url: String = "",
    val isPrivate: Boolean = false,
    val securityState: BrowserSecurityUiState = BrowserSecurityUiState.Unknown,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktop: Boolean = false,
)
