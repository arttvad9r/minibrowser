package com.artt.minibrowser.engine

import java.lang.ref.WeakReference
import java.util.WeakHashMap
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
import org.mozilla.geckoview.GeckoSession

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
 * Engine facade used only by EngineMiddleware.
 *
 * [freshGeckoSessionFactory] is supplied by BrowserApp because GeckoEngine does not expose the
 * underlying GeckoSession returned by createSession(). Capturing that identity is required only so
 * the LinkEngineSessionAction middleware can install MiniBrowser's selective GeckoView compatibility
 * delegates once Android Components supplies the immutable BrowserStore tab id. All other Engine
 * operations still delegate to the application-scoped GeckoEngine.
 */
internal class AndroidComponentsSessionConfiguringEngine(
    private val delegate: Engine,
    private val configurator: AndroidComponentsOwnedSessionConfigurator,
    private val freshGeckoSessionFactory: ((Boolean, String?) -> Pair<EngineSession, GeckoSession>)? = null,
) : Engine by delegate {
    private data class PendingFreshGeckoSession(
        val session: WeakReference<GeckoSession>,
        val privateMode: Boolean,
    )

    // EngineSession and its GeckoSession reference each other through stock delegates. Keep both map
    // edges weak so a failure before LinkEngineSessionAction cannot turn this short-lived handoff into
    // an application-lifetime leak.
    private val pendingFreshGeckoSessions = WeakHashMap<EngineSession, PendingFreshGeckoSession>()

    override fun createSession(private: Boolean, contextId: String?): EngineSession {
        val fresh = freshGeckoSessionFactory?.invoke(private, contextId)
        val engineSession = if (fresh != null) {
            val (createdEngineSession, rawSession) = fresh
            pendingFreshGeckoSessions[createdEngineSession] = PendingFreshGeckoSession(
                session = WeakReference(rawSession),
                privateMode = private,
            )
            createdEngineSession
        } else {
            delegate.createSession(private, contextId)
        }
        configurator.configure(engineSession.settings)
        return engineSession
    }

    /** Consumes the create->link Gecko identity handoff exactly once. */
    internal fun takeFreshGeckoSession(engineSession: EngineSession): Pair<GeckoSession?, Boolean>? =
        pendingFreshGeckoSessions.remove(engineSession)?.let { pending ->
            pending.session.get() to pending.privateMode
        }
}

/**
 * Fallback for EngineSessions created outside Engine.createSession(), notably WebExtension paths.
 *
 * Fresh sessions created through [AndroidComponentsSessionConfiguringEngine] are additionally wired
 * to MiniBrowser's GeckoView compatibility/persistence sidecars before the link action reaches stock
 * EngineMiddleware. At this point A-C has already applied desktop mode and any persisted restore
 * state, but the synchronous main-thread link still runs before Gecko's asynchronous page callbacks.
 */
internal fun androidComponentsSessionSettingsMiddleware(
    configurator: AndroidComponentsOwnedSessionConfigurator,
    freshSessionEngine: AndroidComponentsSessionConfiguringEngine? = null,
    compatibilityRegistry: AndroidComponentsGeckoCompatibilityRegistry? = null,
    uiCompatibilityState: AndroidComponentsUiCompatibilityState? = null,
    sessionStatePersistence: AndroidComponentsSessionStatePersistenceState? = null,
): Middleware<BrowserState, BrowserAction> = { store, next, action ->
    if (action is EngineAction.LinkEngineSessionAction) {
        configurator.configure(action.engineSession.settings)
        val fresh = freshSessionEngine?.takeFreshGeckoSession(action.engineSession)
        if (fresh != null) {
            val sessionId = action.tabId
            try {
                val rawSession = checkNotNull(fresh.first) {
                    "Fresh GeckoSession was released before BrowserStore link"
                }
                val privateMode = fresh.second
                val target = checkNotNull(store.state.tabs.firstOrNull { it.id == sessionId }) {
                    "BrowserStore tab $sessionId disappeared before fresh session link"
                }
                check(target.engineState.engineSession == null) {
                    "BrowserStore tab $sessionId already has an EngineSession before fresh session link"
                }
                check(target.content.private == privateMode) {
                    "Fresh GeckoSession private mode must match BrowserStore session metadata"
                }
                val context = AndroidComponentsGeckoSessionContext(
                    sessionId = sessionId,
                    privateMode = privateMode,
                )
                installAndroidComponentsGeckoCompatibilityDelegates(
                    session = rawSession,
                    compatibility = checkNotNull(compatibilityRegistry) {
                        "Fresh session compatibility registry is not configured"
                    }.forSession(context),
                )
                installAndroidComponentsSessionStatePersistence(
                    session = rawSession,
                    engineSession = action.engineSession,
                    sessionId = sessionId,
                    persistenceState = checkNotNull(sessionStatePersistence) {
                        "Fresh session persistence state is not configured"
                    },
                )
                installAndroidComponentsUiCompatibilityDelegates(
                    session = rawSession,
                    sessionId = sessionId,
                    compatibilityState = checkNotNull(uiCompatibilityState) {
                        "Fresh session UI compatibility state is not configured"
                    },
                )
            } catch (throwable: Throwable) {
                sessionStatePersistence?.remove(sessionId)
                uiCompatibilityState?.remove(sessionId)
                runCatching { action.engineSession.close() }
                throw throwable
            }
        }
    }
    next(action)
}
