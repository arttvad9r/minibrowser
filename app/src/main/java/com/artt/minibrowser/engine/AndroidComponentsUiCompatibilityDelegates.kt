package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/**
 * Retains MiniBrowser-only security/error UI state while leaving the stock A-C ProgressDelegate in
 * control of every Gecko callback. The sidecar update happens before forwarding so UI observers do
 * not retain an older compatibility value after the matching Gecko callback has begun.
 */
internal class AndroidComponentsProgressUiCompatibilityDelegate(
    private val delegate: GeckoSession.ProgressDelegate,
    private val sessionId: String,
    private val compatibilityState: AndroidComponentsUiCompatibilityState,
) : GeckoSession.ProgressDelegate by delegate {
    override fun onPageStart(session: GeckoSession, url: String) {
        compatibilityState.onPageStart(sessionId)
        delegate.onPageStart(session, url)
    }

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
}

/**
 * Observes Gecko error categories for MiniBrowser's coarse load-error UI while preserving the exact
 * stock A-C NavigationDelegate return value and error-page behavior.
 */
internal class AndroidComponentsNavigationUiCompatibilityDelegate(
    private val delegate: GeckoSession.NavigationDelegate,
    private val sessionId: String,
    private val compatibilityState: AndroidComponentsUiCompatibilityState,
) : GeckoSession.NavigationDelegate by delegate {
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
