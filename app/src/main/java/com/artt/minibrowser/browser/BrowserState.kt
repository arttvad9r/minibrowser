package com.artt.minibrowser.browser

enum class BrowserScreen { Browser, Settings, History, Bookmarks }

data class BrowserUiState(
    val screen: BrowserScreen = BrowserScreen.Browser,
    val showSwitcher: Boolean = false,
    val showFind: Boolean = false,
    val showSiteInfo: Boolean = false,
)
