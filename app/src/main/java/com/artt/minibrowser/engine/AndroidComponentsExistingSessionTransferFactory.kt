package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Result of the cutover-only wrapping step for an already-open GeckoSession.
 *
 * Creating this object has already transferred Gecko delegate authority to GeckoEngineSession. The
 * caller must dispatch [transferPlan]'s link action immediately, then its media replay actions, and
 * must never return close/replace authority to TabManager.
 */
internal data class AndroidComponentsPreparedExistingSessionTransfer(
    val engineSession: GeckoEngineSession,
    val transferPlan: AndroidComponentsExistingSessionTransferPlan,
)

/**
 * Cutover hook for preserving the identity of an already-open raw GeckoSession.
 *
 * This function must only be called after the raw owner has atomically relinquished delegate and
 * close/replace authority and captured any active media-session handoff. GeckoEngineSession's
 * constructor immediately installs A-C Gecko delegates on [rawSession]; the compatibility wrappers
 * are installed only after those stock delegates exist.
 *
 * The current raw/shadow runtime never calls this function.
 */
@MainThread
internal fun prepareAndroidComponentsExistingSessionTransferAfterRawRelinquish(
    runtime: GeckoRuntime,
    rawSession: GeckoSession,
    sessionContext: AndroidComponentsGeckoSessionContext,
    configurator: AndroidComponentsOwnedSessionConfigurator,
    compatibilityRegistry: AndroidComponentsGeckoCompatibilityRegistry,
    mediaSessionHandoff: AndroidComponentsMediaSessionHandoff?,
): AndroidComponentsPreparedExistingSessionTransfer {
    check(rawSession.isOpen) { "Existing-session ownership transfer requires an open GeckoSession" }
    check(rawSession.settings.usePrivateMode == sessionContext.privateMode) {
        "Existing GeckoSession private mode must match BrowserStore session metadata"
    }

    val engineSession = GeckoEngineSession(
        runtime = runtime,
        privateMode = sessionContext.privateMode,
        geckoSessionProvider = { rawSession },
        openGeckoSession = false,
    )
    configurator.configure(engineSession.settings)
    installAndroidComponentsGeckoCompatibilityDelegates(
        session = rawSession,
        compatibility = compatibilityRegistry.forSession(sessionContext),
    )

    return AndroidComponentsPreparedExistingSessionTransfer(
        engineSession = engineSession,
        transferPlan = androidComponentsExistingSessionTransferPlan(
            tabId = sessionContext.sessionId,
            mediaSessionHandoff = mediaSessionHandoff,
        ),
    )
}
