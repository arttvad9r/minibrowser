package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.DownloadDelegate
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.lib.state.Middleware

/**
 * Configures the session-only settings MiniBrowser needs once BrowserStore starts owning live
 * EngineSessions. Keeping this separate from GeckoEngine DefaultSettings avoids mutating the shared
 * GeckoRuntime before the ownership cutover.
 */
internal fun configureAndroidComponentsOwnedSession(
    settings: Settings,
    historyTrackingDelegate: HistoryTrackingDelegate,
    downloadDelegate: DownloadDelegate,
) {
    settings.historyTrackingDelegate = historyTrackingDelegate
    settings.downloadDelegate = downloadDelegate
    settings.suspendMediaWhenInactive = true
}

/**
 * Pre-cutover middleware. It is inert while MiniBrowser does not dispatch Create/LinkEngineSession
 * actions. Once A-C creates a session, configure it immediately before the Link action continues
 * through EngineMiddleware and reaches the BrowserStore reducer.
 *
 * Delegate factories are lazy on purpose: merely constructing the shadow BrowserStore must not
 * initialize HistorySink or other live feature infrastructure before a session is actually linked.
 */
internal fun androidComponentsSessionSettingsMiddleware(
    historyTrackingDelegateFactory: () -> HistoryTrackingDelegate = { AndroidComponentsHistoryTrackingDelegate() },
    downloadDelegateFactory: () -> DownloadDelegate = { AndroidComponentsDownloadDelegate() },
): Middleware<BrowserState, BrowserAction> {
    val historyTrackingDelegate by lazy(LazyThreadSafetyMode.NONE, historyTrackingDelegateFactory)
    val downloadDelegate by lazy(LazyThreadSafetyMode.NONE, downloadDelegateFactory)

    return { _, next, action ->
        if (action is EngineAction.LinkEngineSessionAction) {
            configureAndroidComponentsOwnedSession(
                settings = action.engineSession.settings,
                historyTrackingDelegate = historyTrackingDelegate,
                downloadDelegate = downloadDelegate,
            )
        }
        next(action)
    }
}
