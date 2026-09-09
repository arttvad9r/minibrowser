package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.concept.engine.EngineSession

/**
 * Non-live plan for linking an already-open raw GeckoSession after ownership has moved to A-C.
 *
 * Preconditions are deliberately outside this type: the raw owner must already have relinquished
 * delegate and close/replace authority, the existing GeckoSession must already be wrapped in an
 * owned GeckoEngineSession, session settings/compatibility proxies must already be installed, and the
 * BrowserStore tab must still have no linked EngineSession.
 *
 * Existing-session transfer must never trigger a second navigation. A-C LinkingMiddleware loads the
 * tab URL when skipLoading=false, so this plan hard-codes skipLoading=true. Media state is replayed
 * only after the LinkEngineSessionAction has been dispatched and reduced.
 */
internal data class AndroidComponentsExistingSessionTransferPlan(
    val tabId: String,
    val mediaReplayActions: List<BrowserAction>,
) {
    val skipLoading: Boolean = true
    val includeParent: Boolean = false

    fun linkAction(engineSession: EngineSession): EngineAction.LinkEngineSessionAction =
        EngineAction.LinkEngineSessionAction(
            tabId = tabId,
            engineSession = engineSession,
            skipLoading = skipLoading,
            includeParent = includeParent,
        )
}

internal fun androidComponentsExistingSessionTransferPlan(
    tabId: String,
    mediaSessionHandoff: AndroidComponentsMediaSessionHandoff?,
): AndroidComponentsExistingSessionTransferPlan = AndroidComponentsExistingSessionTransferPlan(
    tabId = tabId,
    mediaReplayActions = androidComponentsMediaSessionHandoffActions(tabId, mediaSessionHandoff),
)
