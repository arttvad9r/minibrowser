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
    val mediaSessionHandoff = rawMediaSessionDelegate?.handoffSnapshot()
    val uiCompatibilityHandoff = androidComponentsUiCompatibilityHandoff()

    relinquishRawSessionOwnership(expectedSession)
    return AndroidComponentsRawSessionRelinquishHandoff(
        rawSession = expectedSession,
        mediaSessionHandoff = mediaSessionHandoff,
        uiCompatibilityHandoff = uiCompatibilityHandoff,
    )
}
