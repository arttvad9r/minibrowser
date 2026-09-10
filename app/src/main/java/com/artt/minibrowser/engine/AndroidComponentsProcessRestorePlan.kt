package com.artt.minibrowser.engine

import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedSessionOwner
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.decodeBoundEngineSessionStateEnvelope
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSessionState

/**
 * BrowserStore materialization prepared before process-restored A-C sessions are created.
 *
 * The durable [PersistedSessionOwner] is the only ownership decision. EngineSessionState is optional:
 * a missing, stale, malformed, or engine-incompatible payload still leaves the tab in this A-C plan
 * so EngineMiddleware can create a fresh A-C-owned session and load the persisted URL.
 */
internal data class AndroidComponentsProcessRestorePlan(
    val tabs: List<TabSessionState>,
    val selectedTabId: String?,
)

internal fun androidComponentsProcessRestorePlan(
    state: PersistedBrowserState,
    decodeEngineSessionState: (PersistedTab) -> EngineSessionState?,
): AndroidComponentsProcessRestorePlan {
    val tabs = state.tabs
        .asSequence()
        .filter { it.sessionOwner == PersistedSessionOwner.AndroidComponents }
        .map { persisted ->
            createTab(
                url = persisted.url,
                private = false,
                id = persisted.id.toString(),
                title = persisted.title,
                desktopMode = persisted.desktop,
                engineSessionState = decodeEngineSessionState(persisted),
            )
        }
        .toList()
    val ids = tabs.mapTo(mutableSetOf()) { it.id }
    return AndroidComponentsProcessRestorePlan(
        tabs = tabs,
        selectedTabId = state.selectedId?.toString()?.takeIf(ids::contains),
    )
}

internal fun androidComponentsProcessRestorePlan(
    state: PersistedBrowserState,
    engine: Engine,
): AndroidComponentsProcessRestorePlan = androidComponentsProcessRestorePlan(state) { persisted ->
    decodeBoundEngineSessionStateEnvelope(
        envelope = persisted.engineSessionState,
        stateUrl = persisted.engineSessionStateUrl,
        tabUrl = persisted.url,
        engine = engine,
    )
}
