package com.artt.minibrowser.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
 * Clears Gecko web data without crossing raw/A-C close ownership.
 *
 * A-C-owned sessions are removed from BrowserStore first, allowing TabsRemovedMiddleware to unlink
 * and close their EngineSessions. Their TabManager records are then dropped without a raw close. One
 * main-loop turn lets the middleware's close coroutine run before the underlying GeckoSession state
 * is verified. Only after that boundary does TabManager clear the remaining raw-owned sessions and
 * invoke GeckoRuntime.storageController.clearData().
 */
internal suspend fun clearWebDataAcrossAndroidComponentsOwnership(
    tabManager: TabManager,
    store: BrowserStore,
) = withContext(Dispatchers.Main.immediate) {
    val tabsBeforeClear = tabManager.tabs.value
    val rawOwnedTabIds = tabsBeforeClear
        .filter { it.hasRawSessionAuthority }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val relinquishedTabs = tabsBeforeClear.filterNot { it.hasRawSessionAuthority }
    val relinquishedTabIds = relinquishedTabs.mapTo(mutableSetOf()) { it.id.toString() }
    val openRelinquishedTabIds = relinquishedTabs
        .filter { it.session.isOpen }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val linkedStoreTabIds = store.state.tabs
        .filter { it.engineState.engineSession != null }
        .mapTo(mutableSetOf()) { it.id }

    val storeTabIdsToRemove = androidComponentsWebDataClearStoreTabIds(
        rawOwnedTabIds = rawOwnedTabIds,
        relinquishedTabIds = relinquishedTabIds,
        openRelinquishedTabIds = openRelinquishedTabIds,
        linkedStoreTabIds = linkedStoreTabIds,
    )
    if (storeTabIdsToRemove.isNotEmpty()) {
        store.dispatch(TabListAction.RemoveTabsAction(storeTabIdsToRemove))
    }

    // closeTab() removes only TabManager's structural record for relinquished tabs. Its ownership
    // guard deliberately leaves the supplied raw GeckoSession to BrowserStore/EngineMiddleware.
    relinquishedTabs.forEach { tabManager.closeTab(it.id) }

    if (openRelinquishedTabIds.isNotEmpty()) {
        // TabsRemovedMiddleware closes EngineSessions from its MainScope. Give that queued close one
        // turn before asserting the ownership boundary and entering Gecko storage clearing.
        yield()
        check(relinquishedTabs.none { it.session.isOpen }) {
            "Android Components EngineSessions must close before Gecko storage data is cleared"
        }
    }

    tabManager.clearWebData()
}
