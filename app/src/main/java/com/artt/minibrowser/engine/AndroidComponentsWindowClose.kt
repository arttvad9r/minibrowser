package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore

/**
 * Consumes Gecko window.close for a tab whose lifetime already belongs to Android Components.
 *
 * BrowserStore is removed first so TabsRemovedMiddleware remains the sole EngineSession close owner.
 * TabManager then removes only its relinquished structural record; its ownership guard cannot close
 * the underlying GeckoSession a second time.
 */
@MainThread
internal fun closeAndroidComponentsOwnedTabFromWindowRequest(
    tabManager: TabManager,
    store: BrowserStore,
    sessionId: String,
): Boolean {
    val tabId = sessionId.toLongOrNull() ?: return false
    val tab = tabManager.tabs.value.firstOrNull { it.id == tabId } ?: return false
    if (tab.rawSessionOwnership != RawSessionOwnership.Relinquished) return false

    val storeTab = store.state.tabs.firstOrNull { it.id == sessionId } ?: return false
    if (storeTab.engineState.engineSession == null) return false

    store.dispatch(TabListAction.RemoveTabAction(sessionId))
    checkNotNull(tabManager.closeTab(tabId)) {
        "Relinquished TabManager record disappeared during Android Components window close"
    }
    return true
}
