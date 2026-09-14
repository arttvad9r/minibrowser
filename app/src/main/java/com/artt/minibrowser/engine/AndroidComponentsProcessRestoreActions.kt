package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction

/**
 * Restores the persisted A-C tab structure before TabManager may create any raw GeckoSession.
 *
 * EngineSession materialization stays lazy. The selected A-C tab is marked selected here, then A-C's
 * lifecycle-aware SessionFeature requests CreateEngineSessionAction when the tab is actually rendered.
 * This keeps startup free of live A-C sessions until the Activity compatibility host is bound.
 */
internal fun androidComponentsProcessRestoreActions(
    plan: AndroidComponentsProcessRestorePlan,
): List<BrowserAction> = buildList {
    if (plan.tabs.isEmpty()) return@buildList

    add(TabListAction.AddMultipleTabsAction(plan.tabs))
    plan.selectedTabId?.let { selectedTabId ->
        add(TabListAction.SelectTabAction(selectedTabId))
    }
}
