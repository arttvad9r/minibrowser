package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore

/** One already-linked A-C session that must be synchronously closed before Gecko storage is cleared. */
internal data class AndroidComponentsWebDataClearTarget(
    val sessionId: String,
    val close: () -> Unit,
)

/**
 * Validates the ownership boundary before a destructive web-data clear starts.
 *
 * Every relinquished TabManager session must have exactly one linked BrowserStore EngineSession, and
 * BrowserStore must not contain a linked EngineSession for a tab that is still raw-owned. This check
 * happens before any session is unlinked or closed so an inconsistent ownership graph fails closed.
 */
internal fun prepareAndroidComponentsWebDataClearTargets(
    browserState: BrowserState,
    relinquishedSessionIds: Set<String>,
): List<AndroidComponentsWebDataClearTarget> {
    val linked = browserState.tabs.mapNotNull { tab ->
        tab.engineState.engineSession?.let { engineSession ->
            AndroidComponentsWebDataClearTarget(
                sessionId = tab.id,
                close = engineSession::close,
            )
        }
    }
    val linkedIds = linked.mapTo(mutableSetOf()) { it.sessionId }
    check(linkedIds == relinquishedSessionIds) {
        "Relinquished TabManager sessions must exactly match linked BrowserStore EngineSessions before web-data clear"
    }
    return linked
}

/**
 * Executes the only safe ordering before GeckoRuntime storage clear.
 *
 * A-C's TabsRemovedMiddleware closes removed sessions asynchronously. Unlinking first makes its
 * subsequent RemoveAllTabsAction structural-only; the EngineSession close itself therefore completes
 * synchronously before the caller proceeds to Gecko's StorageController.clearData().
 */
internal fun runAndroidComponentsWebDataClear(
    targets: List<AndroidComponentsWebDataClearTarget>,
    unlink: (sessionId: String) -> Unit,
    removeAllTabs: () -> Unit,
) {
    targets.forEach { target ->
        unlink(target.sessionId)
        target.close()
    }
    removeAllTabs()
}

@MainThread
internal fun clearAndroidComponentsTabsBeforeWebDataClear(
    store: BrowserStore,
    targets: List<AndroidComponentsWebDataClearTarget>,
) {
    runAndroidComponentsWebDataClear(
        targets = targets,
        unlink = { sessionId ->
            store.dispatch(EngineAction.UnlinkEngineSessionAction(sessionId))
        },
        removeAllTabs = {
            store.dispatch(TabListAction.RemoveAllTabsAction(recoverable = false))
        },
    )
}
