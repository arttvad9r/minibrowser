package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoSession

/**
 * Raw-only state captured at the exact ownership boundary before Android Components replaces Gecko
 * delegates. Capturing and relinquishing live in one operation so callers cannot accidentally make
 * raw callbacks inert before media/UI state has been preserved.
 */
internal data class AndroidComponentsRawSessionRelinquishHandoff(
    val rawSession: GeckoSession,
    val mediaSessionHandoff: AndroidComponentsMediaSessionHandoff?,
    val uiCompatibilityHandoff: AndroidComponentsUiCompatibilityHandoff,
)

/**
 * Captures all state that GeckoView delegate replacement will not replay, then irreversibly removes
 * TabManager's raw-session mutation authority. The exact expected GeckoSession identity is checked by
 * [Tab.relinquishRawSessionOwnership]. If capture fails, ownership remains raw because the transition
 * is deliberately the final operation.
 */
internal fun Tab.captureAndroidComponentsHandoffAndRelinquish(
    expectedSession: GeckoSession,
): AndroidComponentsRawSessionRelinquishHandoff {
    check(ownsRawSession(expectedSession)) {
        "Raw GeckoSession must still be owned at Android Components handoff"
    }
    val handoff = AndroidComponentsRawSessionRelinquishHandoff(
        rawSession = expectedSession,
        mediaSessionHandoff = rawMediaSessionDelegate?.handoffSnapshot(),
        uiCompatibilityHandoff = androidComponentsUiCompatibilityHandoff(),
    )

    // Keep this transition as the final potentially failing operation. Once it succeeds, callers may
    // immediately treat any later failure as terminal without a gap after Relinquished.
    relinquishRawSessionOwnership(expectedSession)
    return handoff
}
