package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore

/**
 * Final Activity shutdown accepts both linked and already-suspended A-C-owned tabs.
 *
 * Unlike web-data clearing, shutdown does not need every still-open relinquished GeckoSession to be
 * linked at this exact instant: A-C SuspendMiddleware unlinks synchronously and closes the captured
 * EngineSession asynchronously. A relinquished structural tab must still have its BrowserStore record,
 * except for a sessionless fresh A-C tab whose AddTabAction is already queued but has not reduced yet.
 * In that narrow case shutdown must queue removal for the same id after the pending add.
 *
 * A final Activity destroy also clears every raw-owned shadow BrowserStore record. BrowserStore is
 * process-scoped, so leaving those records behind can make a later Activity instance mistake stale
 * shadow state for an already-restored store and skip rebuilding persisted A-C-owned tabs.
 */
internal fun androidComponentsFinalActivityShutdownStoreTabIds(
    rawOwnedTabIds: Set<String>,
    relinquishedTabIds: Set<String>,
    storeTabIds: Set<String>,
    linkedStoreTabIds: Set<String>,
    sessionlessRelinquishedTabIds: Set<String> = emptySet(),
): List<String> {
    check(rawOwnedTabIds.intersect(linkedStoreTabIds).isEmpty()) {
        "Raw-owned tabs cannot have linked Android Components EngineSessions during final Activity shutdown"
    }
    val missingRelinquishedTabIds = relinquishedTabIds - storeTabIds
    check(missingRelinquishedTabIds.all(sessionlessRelinquishedTabIds::contains)) {
        "Relinquished tabs require BrowserStore records before final Activity shutdown"
    }
    return (storeTabIds + missingRelinquishedTabIds).toList()
}

/**
 * Final Activity shutdown across the raw/A-C ownership boundary.
 *
 * Configuration changes use [TabManagerRecreationHandoff] instead. On a real final destroy, persist
 * the complete mixed-ownership snapshot first through TabManager, close raw-owned sessions there,
 * then deterministically unlink/close every currently-linked A-C owner and remove every process-scoped
 * BrowserStore record. A-C sessions already unlinked by SuspendMiddleware retain its pending close
 * ownership and must not be closed a second time through the stale raw GeckoSession reference.
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
    val sessionlessRelinquishedTabIds = relinquishedTabs
        .filter { it.rawSessionOrNull == null }
        .mapTo(mutableSetOf()) { it.id.toString() }
    val storeTabs = store.state.tabs
    val storeTabIds = storeTabs.mapTo(mutableSetOf()) { it.id }
    val linkedStoreTabs = storeTabs.filter { it.engineState.engineSession != null }
    val linkedStoreTabIds = linkedStoreTabs.mapTo(mutableSetOf()) { it.id }

    val storeTabIdsToRemove = androidComponentsFinalActivityShutdownStoreTabIds(
        rawOwnedTabIds = rawOwnedTabIds,
        relinquishedTabIds = relinquishedTabIds,
        storeTabIds = storeTabIds,
        linkedStoreTabIds = linkedStoreTabIds,
        sessionlessRelinquishedTabIds = sessionlessRelinquishedTabIds,
    )
    val storeTabIdsToRemoveSet = storeTabIdsToRemove.toSet()
    val linkedSessionsToClose = linkedStoreTabs
        .filter { it.id in storeTabIdsToRemoveSet }
        .map { tab -> tab.id to checkNotNull(tab.engineState.engineSession) }
    val linkedRelinquishedTabIds = relinquishedTabIds.intersect(linkedStoreTabIds)

    // capturePersistenceSnapshot() still sees BrowserStore state here. This must happen before
    // unlink/removal, otherwise durable A-C EngineSessionState for relinquished tabs could be lost.
    // A sessionless fresh tab whose AddTabAction has not reduced yet falls back to structural
    // metadata in TabManager.capturePersistenceSnapshot(). close() is idempotent, so lifecycle-
    // observer ordering does not affect this boundary.
    tabManager.close()

    // Unlink synchronously before direct close. TabsRemovedMiddleware then observes no EngineSession
    // and cannot schedule a second asynchronous close when the structural store tabs are removed.
    linkedSessionsToClose.forEach { (sessionId, _) ->
        store.dispatch(EngineAction.UnlinkEngineSessionAction(sessionId))
    }
    linkedSessionsToClose.forEach { (_, engineSession) ->
        engineSession.close()
    }

    check(
        relinquishedTabs.none { tab ->
            tab.id.toString() in linkedRelinquishedTabIds && tab.rawSessionOrNull?.isOpen == true
        },
    ) {
        "Linked relinquished GeckoSessions must close before final Activity shutdown completes"
    }

    if (storeTabIdsToRemove.isNotEmpty()) {
        // This list can intentionally include a sessionless fresh id whose AddTabAction is still
        // ahead of this action in BrowserStore's queue. Reducer ordering then guarantees no late
        // process-scoped orphan survives final Activity destruction.
        store.dispatch(TabListAction.RemoveTabsAction(storeTabIdsToRemove))
    }
}
