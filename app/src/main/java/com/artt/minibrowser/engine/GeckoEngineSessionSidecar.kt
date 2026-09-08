package com.artt.minibrowser.engine

import mozilla.components.browser.engine.gecko.GeckoEngineSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Creates an Android Components engine-session facade around an existing raw GeckoSession without
 * changing MiniBrowser's current delegate ownership.
 *
 * GeckoEngineSession installs its own delegates during construction. Stage 2b only needs the
 * facade so GeckoEngineView can render the existing session; navigation, progress, content,
 * history, prompt and permission behavior must remain owned by TabManager until those features are
 * migrated deliberately. Snapshot and restore those delegates immediately after construction.
 */
internal fun createGeckoEngineSessionSidecar(
    runtime: GeckoRuntime,
    session: GeckoSession,
    privateMode: Boolean,
): GeckoEngineSession {
    val navigationDelegate = session.navigationDelegate
    val progressDelegate = session.progressDelegate
    val contentDelegate = session.contentDelegate
    val historyDelegate = session.historyDelegate
    val promptDelegate = session.promptDelegate
    val permissionDelegate = session.permissionDelegate

    val engineSession = GeckoEngineSession(
        runtime = runtime,
        privateMode = privateMode,
        geckoSessionProvider = { session },
        openGeckoSession = false,
    )

    session.navigationDelegate = navigationDelegate
    session.progressDelegate = progressDelegate
    session.contentDelegate = contentDelegate
    session.historyDelegate = historyDelegate
    session.promptDelegate = promptDelegate
    session.permissionDelegate = permissionDelegate

    return engineSession
}
