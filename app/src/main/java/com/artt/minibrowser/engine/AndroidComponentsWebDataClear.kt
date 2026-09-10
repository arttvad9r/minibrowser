package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore

/**
 * Builds the BrowserStore removal set for a web-data clear while enforcing single session ownership.
 *
 * Every still-open relinquished GeckoSession must already have a linked A-C EngineSession so that
 * BrowserStore/EngineMiddleware remains its only close owner. A linked EngineSession on a raw-owned
 * Tab would be mixed ownership and must fail before either side is mutated.
 */
internal fun androidComponentsWebDataClearStoreTabIds(
    rawOwnedTabIds: Set<String>,
    relinquishedTabIds: Set<String>,
    openRelinquishedTabIds: Set<String>,
    linkedStoreTabIds: Set<String>,
): List<String> {
    check(rawOwnedTabIds.intersect(linkedStoreTabIds).isEmpty()) {
        "Raw-owned tabs cannot have linked Android Components EngineSessions during web-data clear"
    }
    check(openRelinquishedTabIds.all(linkedStoreTabIds::contains)) {
        "Open relinquished tabs require linked Android Components EngineSessions before web-data clear"
    }
    return (linkedStoreTabIds + relinquishedTabIds).toList()
}

/**
 * Deterministically closes every A-C-owned session before Gecko storage clearing can begin.
 *
 * A-C's TabsRemovedMiddleware unlinks synchronously but schedules EngineSession.close() on its scope.
 * Waiting one main-loop turn is not an ownership guarantee. Capture the linked sessions, unlink them
 * first so normal tab removal cannot schedule a second close, close those captured owners directly,
 * verify the supplied GeckoSessions crossed the close boundary, and only then remove BrowserStore
 * and TabManager structural records.
 */
@MainThread
internal fun closeAndroidComponentsOwnedTabsBeforeWebDataClear(
    tabManager: TabManager,
    store: BrowserStore,
) {
    val tabsBeforeClear = tabManager.tabs.value
    val rawOwnedTabIds = tabsBeforeClear
        .filter { it.hasRawSessionAuthority }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val relinquishedTabs = tabsBeforeClear.filterNot { it.hasRawSessionAuthority }
    val relinquishedTabIds = relinquishedTabs.mapTo(mutableSetOf()) { it.id.toString() }
    val openRelinquishedTabIds = relinquishedTabs
        .filter { it.session.isOpen }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val linkedStoreTabs = store.state.tabs.filter { it.engineState.engineSession != null }
    val linkedStoreTabIds = linkedStoreTabs.mapTo(mutableSetOf()) { it.id }

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

    // LinkingMiddleware unregisters the EngineSession observer and EngineStateReducer clears the
    // link synchronously. TabsRemovedMiddleware therefore sees no EngineSession later and cannot
    // race a second asynchronous close against GeckoRuntime.storageController.clearData().
    linkedSessionsToClose.forEach { (sessionId, _) ->
        store.dispatch(EngineAction.UnlinkEngineSessionAction(sessionId))
    }
    linkedSessionsToClose.forEach { (_, engineSession) ->
        engineSession.close()
    }

    check(relinquishedTabs.none { it.session.isOpen }) {
        "Android Components EngineSessions must close before Gecko storage data is cleared"
    }

    if (storeTabIdsToRemove.isNotEmpty()) {
        store.dispatch(TabListAction.RemoveTabsAction(storeTabIdsToRemove))
    }

    // closeTab() now removes only TabManager's structural record. Its ownership guard deliberately
    // cannot close the already-relinquished raw GeckoSession a second time.
    relinquishedTabs.forEach { tabManager.closeTab(it.id) }
}

/**
 * Clears Gecko web data without crossing raw/A-C close ownership.
 *
 * A-C-owned sessions are deterministically unlinked and closed before their structural records are
 * removed. TabManager then sees only raw-owned tabs, so its existing fail-fast invariant remains a
 * useful final guard around the GeckoRuntime storage clear path.
 */
internal suspend fun clearWebDataAcrossAndroidComponentsOwnership(
    tabManager: TabManager,
    store: BrowserStore,
) = withContext(Dispatchers.Main.immediate) {
    closeAndroidComponentsOwnedTabsBeforeWebDataClear(tabManager, store)
    tabManager.clearWebData()
}
