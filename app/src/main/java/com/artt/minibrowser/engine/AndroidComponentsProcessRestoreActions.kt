package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction

/**
 * Materializes the persisted A-C tab structure before TabManager may create any raw GeckoSession.
 *
 * Only the selected A-C tab gets a live EngineSession during startup. Background tabs keep their
 * optional EngineSessionState in BrowserStore and can be materialized lazily when they are selected.
 */
internal fun androidComponentsProcessRestoreActions(
    plan: AndroidComponentsProcessRestorePlan,
): List<BrowserAction> = buildList {
    if (plan.tabs.isEmpty()) return@buildList

    add(TabListAction.AddMultipleTabsAction(plan.tabs))
    plan.selectedTabId?.let { selectedTabId ->
        add(TabListAction.SelectTabAction(selectedTabId))
        add(EngineAction.CreateEngineSessionAction(tabId = selectedTabId))
    }
}
