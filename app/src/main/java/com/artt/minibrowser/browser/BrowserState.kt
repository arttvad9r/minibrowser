package com.artt.minibrowser.browser

enum class BrowserScreen { Browser, Settings, Downloads, History, Bookmarks }
enum class BrowserOverlay { Switcher, Find, SiteInfo }

data class BrowserUiState(
    val screen: BrowserScreen = BrowserScreen.Browser,
    val overlay: BrowserOverlay? = null,
) {
    val showSwitcher: Boolean get() = overlay == BrowserOverlay.Switcher
    val showFind: Boolean get() = overlay == BrowserOverlay.Find
    val showSiteInfo: Boolean get() = overlay == BrowserOverlay.SiteInfo
}
