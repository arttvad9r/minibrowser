package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.store.BrowserStore
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
 * tab URL when skipLoading=false, so this plan hard-codes skipLoading=true. The plan also owns the
 * ordering of the BrowserStore cutover: Store.dispatch() is synchronous, therefore retained media
 * state can be replayed immediately after the link action has been reduced and EngineObserver is
 * attached to the transferred EngineSession.
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

    /**
     * Performs the ordered BrowserStore side of the existing-session ownership cutover.
     *
     * BrowserStore observers may see the linked state before media replay because these are separate
     * synchronous dispatches; this method guarantees their required order, not transactional atomicity.
     * Keeping the sequence in one operation prevents callers from replaying retained media too early
     * or from forgetting the replay altogether.
     */
    fun linkAndReplayTo(store: BrowserStore, engineSession: EngineSession) {
        val target = store.state.tabs.firstOrNull { it.id == tabId }
        checkNotNull(target) { "BrowserStore tab $tabId does not exist" }
        check(target.engineState.engineSession == null) {
            "BrowserStore tab $tabId already has an EngineSession"
        }

        store.dispatch(linkAction(engineSession))
        check(
            store.state.tabs.firstOrNull { it.id == tabId }
                ?.engineState
                ?.engineSession === engineSession,
        ) { "BrowserStore did not link the prepared EngineSession for tab $tabId" }

        mediaReplayActions.forEach(store::dispatch)
    }
}

internal fun androidComponentsExistingSessionTransferPlan(
    tabId: String,
    mediaSessionHandoff: AndroidComponentsMediaSessionHandoff?,
): AndroidComponentsExistingSessionTransferPlan = AndroidComponentsExistingSessionTransferPlan(
    tabId = tabId,
    mediaReplayActions = androidComponentsMediaSessionHandoffActions(tabId, mediaSessionHandoff),
)
