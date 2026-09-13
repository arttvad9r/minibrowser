package com.artt.minibrowser.engine

import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab

/**
 * Structural and BrowserStore records for a tab owned by Android Components from birth.
 *
 * TabManager remains the only Long ID allocator. BrowserStore receives the same ID before the
 * structural record is published, while EngineSession creation stays lazy in SessionFeature.
 */
internal data class AndroidComponentsFreshTabPlan(
    val structuralTab: Tab,
    val storeTab: TabSessionState,
)

internal fun androidComponentsFreshTabPlan(
    id: Long,
    url: String?,
    isPrivate: Boolean,
): AndroidComponentsFreshTabPlan {
    val initialUrl = url.orEmpty()
    return AndroidComponentsFreshTabPlan(
        structuralTab = Tab.androidComponentsOwned(
            id = id,
            isPrivate = isPrivate,
        ).apply {
            this.url = initialUrl
        },
        storeTab = createTab(
            url = initialUrl,
            private = isPrivate,
            id = id.toString(),
        ),
    )
}
