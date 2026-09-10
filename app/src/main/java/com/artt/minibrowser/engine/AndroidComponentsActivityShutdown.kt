package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore

/**
 * Final Activity shutdown across the raw/A-C ownership boundary.
 *
 * Configuration changes use [TabManagerRecreationHandoff] instead. On a real final destroy, persist
 * the complete mixed-ownership snapshot first through TabManager, close raw-owned sessions there,
 * then deterministically unlink/close/remove every relinquished A-C owner. This keeps BrowserStore /
 * EngineMiddleware as the only A-C close authority and prevents app-scoped linked sessions from
 * surviving after their Activity UI host is gone.
 */
@MainThread
internal fun closeBrowserSessionsForFinalActivityDestroy(
    tabManager: TabManager,
    store: BrowserStore,
) {
    val tabs = tabManager.tabs.value
    val rawOwnedTabIds = tabs
        .filter { it.hasRawSessionAuthority }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val relinquishedTabs = tabs.filterNot { it.hasRawSessionAuthority }
    val relinquishedTabIds = relinquishedTabs.mapTo(mutableSetOf()) { it.id.toString() }
    val openRelinquishedTabIds = relinquishedTabs
        .filter { it.session.isOpen }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val linkedStoreTabs = store.state.tabs.filter { it.engineState.engineSession != null }
    val linkedStoreTabIds = linkedStoreTabs.mapTo(mutableSetOf()) { it.id }

    check(rawOwnedTabIds.intersect(linkedStoreTabIds).isEmpty()) {
        "Raw-owned tabs cannot have linked Android Components EngineSessions during final Activity shutdown"
    }
    check(linkedStoreTabIds.all(relinquishedTabIds::contains)) {
        "Linked Android Components EngineSession has no relinquished TabManager structural owner"
    }
    check(openRelinquishedTabIds.all(linkedStoreTabIds::contains)) {
        "Open relinquished tabs require linked Android Components EngineSessions during final Activity shutdown"
    }

    // capturePersistenceSnapshot() still sees BrowserStore-linked state here. This must happen before
    // unlink/removal, otherwise the durable A-C EngineSessionState for relinquished tabs would be lost.
    tabManager.close()

    val linkedSessionsToClose = linkedStoreTabs.map { tab ->
        tab.id to checkNotNull(tab.engineState.engineSession)
    }
    linkedSessionsToClose.forEach { (sessionId, _) ->
        store.dispatch(EngineAction.UnlinkEngineSessionAction(sessionId))
    }
    linkedSessionsToClose.forEach { (_, engineSession) ->
        engineSession.close()
    }

    check(relinquishedTabs.none { it.session.isOpen }) {
        "Android Components EngineSessions must close before final Activity shutdown completes"
    }

    if (relinquishedTabIds.isNotEmpty()) {
        store.dispatch(TabListAction.RemoveTabsAction(relinquishedTabIds.toList()))
    }
}
