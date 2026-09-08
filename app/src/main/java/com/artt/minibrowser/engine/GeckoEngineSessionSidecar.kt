package com.artt.minibrowser.engine

import mozilla.components.browser.engine.gecko.GeckoEngineSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Creates an Android Components engine-session facade around an existing raw GeckoSession without
 * changing MiniBrowser's current delegate ownership.
 *
 * GeckoEngineSession installs Gecko delegates during construction. Stage 2b only needs the facade
 * so GeckoEngineView can render the existing session; TabManager and GeckoView must retain the exact
 * delegate set they had before wrapping. Restoring every delegate installed by GeckoEngineSession
 * also prevents the raw GeckoSession from retaining this temporary sidecar after EngineView.release().
 */
internal fun createGeckoEngineSessionSidecar(
    runtime: GeckoRuntime,
    session: GeckoSession,
    privateMode: Boolean,
): GeckoEngineSession {
    val navigationDelegate = session.navigationDelegate
    val progressDelegate = session.progressDelegate
    val contentDelegate = session.contentDelegate
    val contentBlockingDelegate = session.contentBlockingDelegate
    val permissionDelegate = session.permissionDelegate
    val promptDelegate = session.promptDelegate
    val mediaDelegate = session.mediaDelegate
    val historyDelegate = session.historyDelegate
    val mediaSessionDelegate = session.mediaSessionDelegate
    val scrollDelegate = session.scrollDelegate
    val translationsSessionDelegate = session.translationsSessionDelegate

    val engineSession = GeckoEngineSession(
        runtime = runtime,
        privateMode = privateMode,
        geckoSessionProvider = { session },
        openGeckoSession = false,
    )

    session.navigationDelegate = navigationDelegate
    session.progressDelegate = progressDelegate
    session.contentDelegate = contentDelegate
    session.contentBlockingDelegate = contentBlockingDelegate
    session.permissionDelegate = permissionDelegate
    session.promptDelegate = promptDelegate
    session.mediaDelegate = mediaDelegate
    session.historyDelegate = historyDelegate
    session.mediaSessionDelegate = mediaSessionDelegate
    session.scrollDelegate = scrollDelegate
    session.translationsSessionDelegate = translationsSessionDelegate

    return engineSession
}
