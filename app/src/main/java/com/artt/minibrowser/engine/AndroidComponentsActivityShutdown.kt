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
 * then deterministically unlink/close/remove every relinquished or otherwise linked A-C owner. This
 * keeps BrowserStore / EngineMiddleware as the only A-C close authority and prevents app-scoped
 * linked sessions from surviving after their Activity UI host is gone.
 */
@MainThread
internal fun closeBrowserSessionsForFinalActivityDestroy(
    tabManager: TabManager,
    store: BrowserStore,
) {
    val tabsBeforeClose = tabManager.tabs.value
    val rawOwnedTabIds = tabsBeforeClose
        .filter { it.hasRawSessionAuthority }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val relinquishedTabs = tabsBeforeClose.filterNot { it.hasRawSessionAuthority }
    val relinquishedTabIds = relinquishedTabs.mapTo(mutableSetOf()) { it.id.toString() }
    val openRelinquishedTabIds = relinquishedTabs
        .filter { it.session.isOpen }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val linkedStoreTabs = store.state.tabs.filter { it.engineState.engineSession != null }
    val linkedStoreTabIds = linkedStoreTabs.mapTo(mutableSetOf()) { it.id }

    // Reuse the same ownership proof as web-data clearing: mixed raw+linked ownership is illegal,
    // every still-open relinquished raw GeckoSession must have an A-C owner, while orphaned linked
    // BrowserStore sessions are safe to close rather than leaving them alive after the Activity.
    val storeTabIdsToRemove = androidComponentsWebDataClearStoreTabIds(
        rawOwnedTabIds = rawOwnedTabIds,
        relinquishedTabIds = relinquishedTabIds,
        openRelinquishedTabIds = openRelinquishedTabIds,
        linkedStoreTabIds = linkedStoreTabIds,
    )
    val storeTabIdsToRemoveSet = storeTabIdsToRemove.toSet()
    val linkedSessionsToClose = linkedStoreTabs
        .filter { it.id in storeTabIdsToRemoveSet }
        .map { tab -> tab.id to checkNotNull(tab.engineState.engineSession) }

    // capturePersistenceSnapshot() still sees BrowserStore-linked state here. This must happen before
    // unlink/removal, otherwise durable A-C EngineSessionState for relinquished tabs could be lost.
    // close() is idempotent, so lifecycle-observer ordering does not affect this boundary.
    tabManager.close()

    // Unlink synchronously before direct close. TabsRemovedMiddleware then observes no EngineSession
    // and cannot schedule a second asynchronous close when the structural store tabs are removed.
    linkedSessionsToClose.forEach { (sessionId, _) ->
        store.dispatch(EngineAction.UnlinkEngineSessionAction(sessionId))
    }
    linkedSessionsToClose.forEach { (_, engineSession) ->
        engineSession.close()
    }

    check(relinquishedTabs.none { it.session.isOpen }) {
        "Android Components EngineSessions must close before final Activity shutdown completes"
    }

    if (storeTabIdsToRemove.isNotEmpty()) {
        store.dispatch(TabListAction.RemoveTabsAction(storeTabIdsToRemove))
    }
}
