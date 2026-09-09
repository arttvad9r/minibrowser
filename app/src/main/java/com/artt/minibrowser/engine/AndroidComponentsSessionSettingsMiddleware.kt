package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.DownloadDelegate
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.concept.engine.request.RequestInterceptor
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
    requestInterceptor: RequestInterceptor,
) {
    settings.historyTrackingDelegate = historyTrackingDelegate
    settings.downloadDelegate = downloadDelegate
    settings.requestInterceptor = requestInterceptor
    settings.suspendMediaWhenInactive = true
}

/**
 * Owns the small session-only policy that must survive the raw -> A-C handoff.
 *
 * Delegate/interceptor factories are lazy on purpose: constructing the shadow BrowserStore must not
 * initialize HistorySink or other live feature infrastructure before an EngineSession is actually
 * created/linked.
 */
internal class AndroidComponentsOwnedSessionConfigurator(
    private val externalNavigationPolicy: ExternalAppNavigationPolicy,
    historyTrackingDelegateFactory: () -> HistoryTrackingDelegate = { AndroidComponentsHistoryTrackingDelegate() },
    downloadDelegateFactory: () -> DownloadDelegate = { AndroidComponentsDownloadDelegate() },
    requestInterceptorFactory: (ExternalAppNavigationPolicy) -> RequestInterceptor =
        { AndroidComponentsExternalAppRequestInterceptor(it) },
) {
    private val historyTrackingDelegate by lazy(LazyThreadSafetyMode.NONE, historyTrackingDelegateFactory)
    private val downloadDelegate by lazy(LazyThreadSafetyMode.NONE, downloadDelegateFactory)
    private val requestInterceptor by lazy(LazyThreadSafetyMode.NONE) {
        requestInterceptorFactory(externalNavigationPolicy)
    }

    fun configure(settings: Settings) {
        configureAndroidComponentsOwnedSession(
            settings = settings,
            historyTrackingDelegate = historyTrackingDelegate,
            downloadDelegate = downloadDelegate,
            requestInterceptor = requestInterceptor,
        )
    }
}

/**
 * Engine facade used only by EngineMiddleware. Ordinary A-C-created sessions are configured as soon
 * as Engine.createSession() returns, before CreateEngineSessionMiddleware applies desktop mode,
 * restores state, or dispatches LinkEngineSessionAction.
 *
 * The underlying GeckoEngine intentionally keeps defaultSettings=null so this facade cannot rewrite
 * the shared GeckoRuntime settings that the raw MiniBrowser owner still controls.
 */
internal class AndroidComponentsSessionConfiguringEngine(
    private val delegate: Engine,
    private val configurator: AndroidComponentsOwnedSessionConfigurator,
) : Engine by delegate {
    override fun createSession(private: Boolean, contextId: String?): EngineSession =
        delegate.createSession(private, contextId).also { configurator.configure(it.settings) }
}

/**
 * Fallback for EngineSessions created outside Engine.createSession(), notably WebExtension paths.
 * It is inert while MiniBrowser does not dispatch/link live A-C EngineSessions.
 */
internal fun androidComponentsSessionSettingsMiddleware(
    configurator: AndroidComponentsOwnedSessionConfigurator,
): Middleware<BrowserState, BrowserAction> = { _, next, action ->
    if (action is EngineAction.LinkEngineSessionAction) {
        configurator.configure(action.engineSession.settings)
    }
    next(action)
}
