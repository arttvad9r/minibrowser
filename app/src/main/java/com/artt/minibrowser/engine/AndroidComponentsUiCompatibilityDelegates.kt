package com.artt.minibrowser.engine

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/**
 * Retains MiniBrowser-only security/error UI state while leaving the stock A-C ProgressDelegate in
 * control of every Gecko callback. The sidecar update happens before forwarding so UI observers do
 * not retain an older compatibility value after the matching Gecko callback has begun.
 *
 * GeckoView delegate callbacks are Java default methods. Kotlin interface delegation would inherit
 * those defaults instead of forwarding them, so every stock callback is forwarded explicitly.
 */
internal class AndroidComponentsProgressUiCompatibilityDelegate(
    private val delegate: GeckoSession.ProgressDelegate,
    private val sessionId: String,
    private val compatibilityState: AndroidComponentsUiCompatibilityState,
) : GeckoSession.ProgressDelegate {
    override fun onPageStart(session: GeckoSession, url: String) {
        compatibilityState.onPageStart(sessionId)
        delegate.onPageStart(session, url)
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) =
        delegate.onPageStop(session, success)

    override fun onProgressChange(session: GeckoSession, progress: Int) =
        delegate.onProgressChange(session, progress)

    override fun onSecurityChange(
        session: GeckoSession,
        securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
    ) {
        compatibilityState.onSecurityChange(
            sessionId = sessionId,
            isException = securityInfo.isException,
            isSecure = securityInfo.isSecure,
        )
        delegate.onSecurityChange(session, securityInfo)
    }

    override fun onSessionStateChange(
        session: GeckoSession,
        sessionState: GeckoSession.SessionState,
    ) = delegate.onSessionStateChange(session, sessionState)
}

/**
 * Observes Gecko error categories for MiniBrowser's coarse load-error UI while preserving the exact
 * stock A-C NavigationDelegate return value and error-page behavior.
 *
 * GeckoView delegate callbacks are Java default methods. Kotlin interface delegation would inherit
 * those defaults instead of forwarding them, so every stock callback is forwarded explicitly.
 */
internal class AndroidComponentsNavigationUiCompatibilityDelegate(
    private val delegate: GeckoSession.NavigationDelegate,
    private val sessionId: String,
    private val compatibilityState: AndroidComponentsUiCompatibilityState,
) : GeckoSession.NavigationDelegate {
    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
    ) = delegate.onLocationChange(session, url, perms, hasUserGesture)

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) =
        delegate.onCanGoBack(session, canGoBack)

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) =
        delegate.onCanGoForward(session, canGoForward)

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? = delegate.onLoadRequest(session, request)

    override fun onSubframeLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? = delegate.onSubframeLoadRequest(session, request)

    override fun onNewSession(
        session: GeckoSession,
        uri: String,
    ): GeckoResult<GeckoSession>? = delegate.onNewSession(session, uri)

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError,
    ): GeckoResult<String>? {
        compatibilityState.onLoadError(sessionId, error.category)
        return delegate.onLoadError(session, uri, error)
    }
}

/**
 * Wraps the stock delegates already installed by GeckoEngineSession. The raw UI handoff must be
 * seeded before GeckoEngineSession construction; this function only installs ongoing observers and
 * therefore cannot overwrite a newer callback with stale handoff state.
 */
internal fun installAndroidComponentsUiCompatibilityDelegates(
    session: GeckoSession,
    sessionId: String,
    compatibilityState: AndroidComponentsUiCompatibilityState,
) {
    session.progressDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsProgressUiCompatibilityDelegate) {
            session.progressDelegate = AndroidComponentsProgressUiCompatibilityDelegate(
                delegate = delegate,
                sessionId = sessionId,
                compatibilityState = compatibilityState,
            )
        }
    }
    session.navigationDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsNavigationUiCompatibilityDelegate) {
            session.navigationDelegate = AndroidComponentsNavigationUiCompatibilityDelegate(
                delegate = delegate,
                sessionId = sessionId,
                compatibilityState = compatibilityState,
            )
        }
    }
}
