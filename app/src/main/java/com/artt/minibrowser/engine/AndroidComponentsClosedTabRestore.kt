package com.artt.minibrowser.engine

import com.artt.minibrowser.data.PersistedSessionOwner
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.EngineSessionState

/**
 * Safe in-memory A-C state captured before BrowserStore removal closes the EngineSession.
 * The opaque state is accepted only when the persistence sidecar binds it to the exact live URL.
 */
internal data class AndroidComponentsClosedTabCapture(
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val desktop: Boolean,
    val engineSessionState: EngineSessionState?,
    val engineSessionStateUrl: String?,
)

internal fun androidComponentsClosedTabCapture(
    tab: TabSessionState,
    boundState: AndroidComponentsBoundEngineSessionState?,
): AndroidComponentsClosedTabCapture {
    val url = tab.content.url
    val safeState = boundState?.takeIf { it.stateUrl == url }
    return AndroidComponentsClosedTabCapture(
        url = url,
        title = tab.content.title,
        isPrivate = tab.content.private,
        desktop = tab.content.desktopMode,
        engineSessionState = safeState?.state,
        engineSessionStateUrl = safeState?.stateUrl,
    )
}

internal data class AndroidComponentsClosedTabRestorePlan(
    val structuralTab: Tab,
    val storeTab: TabSessionState,
    val engineSessionState: EngineSessionState?,
)

/**
 * Recreates an A-C-owned tab without ever allocating a raw GeckoSession.
 * Missing or stale opaque state fails closed to a fresh A-C session at the captured URL.
 */
internal fun androidComponentsClosedTabRestorePlan(
    snapshot: ClosedTabSnapshot,
): AndroidComponentsClosedTabRestorePlan? {
    if (snapshot.sessionOwner != PersistedSessionOwner.AndroidComponents) return null

    val safeState = snapshot.engineSessionState
        ?.takeIf { snapshot.engineSessionStateUrl == snapshot.url }
    val structuralTab = Tab.androidComponentsOwned(
        id = snapshot.id,
        isPrivate = snapshot.isPrivate,
    ).apply {
        url = snapshot.url
        title = snapshot.title
        desktop = snapshot.desktop
        lastAccess = snapshot.lastAccess
    }
    val storeTab = createTab(
        url = snapshot.url,
        private = snapshot.isPrivate,
        id = snapshot.id.toString(),
        title = snapshot.title,
        desktopMode = snapshot.desktop,
        engineSessionState = safeState,
    )
    return AndroidComponentsClosedTabRestorePlan(
        structuralTab = structuralTab,
        storeTab = storeTab,
        engineSessionState = safeState,
    )
}
