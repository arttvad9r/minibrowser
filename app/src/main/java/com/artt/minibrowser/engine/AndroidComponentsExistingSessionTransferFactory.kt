package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Result of the cutover-only wrapping step for an already-open GeckoSession.
 *
 * Creating this object has already transferred Gecko delegate authority to GeckoEngineSession. The
 * caller must immediately apply [transferPlan] to BrowserStore and must never return close/replace
 * authority to TabManager.
 */
internal data class AndroidComponentsPreparedExistingSessionTransfer(
    val engineSession: GeckoEngineSession,
    val transferPlan: AndroidComponentsExistingSessionTransferPlan,
)

/**
 * Validated identity/metadata for an existing-session ownership transfer.
 *
 * The raw owner must obtain this token while it still owns the GeckoSession. Only after preflight
 * succeeds may it atomically capture media state and relinquish delegate/close/replace authority.
 */
internal class AndroidComponentsExistingSessionTransferPreflight private constructor(
    internal val rawSession: GeckoSession,
    internal val sessionContext: AndroidComponentsGeckoSessionContext,
) {
    internal fun revalidateAfterRawRelinquish() {
        validateExistingSessionTransfer(rawSession, sessionContext)
    }

    internal companion object {
        fun validate(
            rawSession: GeckoSession,
            sessionContext: AndroidComponentsGeckoSessionContext,
        ): AndroidComponentsExistingSessionTransferPreflight {
            validateExistingSessionTransfer(rawSession, sessionContext)
            return AndroidComponentsExistingSessionTransferPreflight(rawSession, sessionContext)
        }
    }
}

private fun validateExistingSessionTransfer(
    rawSession: GeckoSession,
    sessionContext: AndroidComponentsGeckoSessionContext,
) {
    check(rawSession.isOpen) { "Existing-session ownership transfer requires an open GeckoSession" }
    check(rawSession.settings.usePrivateMode == sessionContext.privateMode) {
        "Existing GeckoSession private mode must match BrowserStore session metadata"
    }
}

/**
 * Performs the transfer preflight while raw GeckoSession ownership is still intact.
 *
 * This must run before the raw owner drops any delegates or close/replace authority. In particular,
 * private-mode mismatch is rejected here, so a failed preflight leaves the raw owner fully usable.
 */
@MainThread
internal fun preflightAndroidComponentsExistingSessionTransfer(
    rawSession: GeckoSession,
    sessionContext: AndroidComponentsGeckoSessionContext,
): AndroidComponentsExistingSessionTransferPreflight =
    AndroidComponentsExistingSessionTransferPreflight.validate(rawSession, sessionContext)

/**
 * Cutover hook for preserving the identity of an already-open raw GeckoSession.
 *
 * Call this immediately after the raw owner has atomically relinquished delegate and close/replace
 * authority and captured any active media-session handoff. [preflight] must have been obtained before
 * that relinquish. GeckoEngineSession's constructor immediately installs A-C Gecko delegates on the
 * existing GeckoSession; compatibility wrappers are installed only after those stock delegates exist.
 *
 * The current raw/shadow runtime never calls this function.
 */
@MainThread
internal fun prepareAndroidComponentsExistingSessionTransferAfterRawRelinquish(
    runtime: GeckoRuntime,
    preflight: AndroidComponentsExistingSessionTransferPreflight,
    configurator: AndroidComponentsOwnedSessionConfigurator,
    compatibilityRegistry: AndroidComponentsGeckoCompatibilityRegistry,
    mediaSessionHandoff: AndroidComponentsMediaSessionHandoff?,
): AndroidComponentsPreparedExistingSessionTransfer {
    // Recheck after the ownership boundary to catch a broken caller that closed or mutated the session
    // between preflight and takeover. Normal callers execute both phases synchronously on the main thread.
    preflight.revalidateAfterRawRelinquish()
    val rawSession = preflight.rawSession
    val sessionContext = preflight.sessionContext

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
