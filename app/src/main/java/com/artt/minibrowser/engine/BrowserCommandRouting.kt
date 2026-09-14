package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.session.SessionUseCases
import org.mozilla.geckoview.GeckoSession

/** Resolves and executes browser commands against the session that currently owns the tab. */
internal fun browserCommandTargetForTab(
    tab: Tab,
    browserStore: BrowserStore,
): BrowserCommandTarget<GeckoSession, EngineSession>? {
    val linkedEngineSession = browserStore.state.tabs
        .firstOrNull { it.id == tab.id.toString() }
        ?.engineState
        ?.engineSession
    return resolveBrowserCommandTarget(
        ownership = tab.rawSessionOwnership,
        rawSession = tab.rawSessionOrNull,
        linkedEngineSession = linkedEngineSession,
    )
}

internal fun loadBrowserUrl(tab: Tab, browserStore: BrowserStore, url: String) {
    when (browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> tab.session.loadUri(url)
        is BrowserCommandTarget.Linked -> SessionUseCases(browserStore).loadUrl(
            url = url,
            sessionId = tab.id.toString(),
        )
        null -> {
            // A fresh/process-restored A-C tab may not have an EngineSession yet. Match
            // SessionUseCases: queue the load for EngineMiddleware instead of dropping it. The
            // raw-to-A-C handoff interval is deliberately excluded because it still retains a raw
            // session whose authority has already been relinquished.
            if (
                tab.rawSessionOwnership == RawSessionOwnership.Relinquished &&
                tab.rawSessionOrNull == null
            ) {
                browserStore.dispatch(
                    EngineAction.LoadUrlAction(
                        tabId = tab.id.toString(),
                        url = url,
                    ),
                )
            }
        }
    }
}

internal fun goBrowserBack(tab: Tab, browserStore: BrowserStore) {
    when (browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> tab.session.goBack()
        is BrowserCommandTarget.Linked -> SessionUseCases(browserStore).goBack(tab.id.toString())
        null -> Unit
    }
}

internal fun goBrowserForward(tab: Tab, browserStore: BrowserStore) {
    when (browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> tab.session.goForward()
        is BrowserCommandTarget.Linked -> SessionUseCases(browserStore).goForward(tab.id.toString())
        null -> Unit
    }
}

internal fun reloadOrStopBrowser(
    tab: Tab,
    browserStore: BrowserStore,
    isLoading: Boolean,
) {
    when (browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> {
            if (isLoading) tab.session.stop() else tab.session.reload()
        }

        is BrowserCommandTarget.Linked -> {
            val sessionUseCases = SessionUseCases(browserStore)
            if (isLoading) {
                sessionUseCases.stopLoading(tab.id.toString())
            } else {
                sessionUseCases.reload(tab.id.toString())
            }
        }

        null -> Unit
    }
}

internal fun toggleBrowserDesktopMode(
    tab: Tab,
    browserStore: BrowserStore,
    enable: Boolean,
) {
    when (browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> toggleDesktopMode(tab)
        is BrowserCommandTarget.Linked -> SessionUseCases(browserStore).requestDesktopSite(
            enable = enable,
            tabId = tab.id.toString(),
        )
        null -> Unit
    }
}

internal fun exitBrowserFullscreen(tab: Tab, browserStore: BrowserStore) {
    when (browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> tab.session.exitFullScreen()
        is BrowserCommandTarget.Linked -> SessionUseCases(browserStore).exitFullscreen(tab.id.toString())
        null -> Unit
    }
}

internal fun clearBrowserFindMatches(tab: Tab, browserStore: BrowserStore) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> target.session.finder.clear()
        is BrowserCommandTarget.Linked -> target.session.clearFindMatches()
        null -> Unit
    }
}

internal fun notifyBrowserPictureInPictureModeChanged(
    tab: Tab,
    browserStore: BrowserStore,
    enabled: Boolean,
) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> target.session.compositorController.onPipModeChanged(enabled)
        is BrowserCommandTarget.Linked -> target.session.onPipModeChanged(enabled)
        null -> Unit
    }

    // Match Android Components' PictureInPictureFeature state contract even in the narrow
    // relinquished-before-link interval, while never falling back to the raw GeckoSession.
    browserStore.dispatch(
        ContentAction.PictureInPictureChangedAction(
            sessionId = tab.id.toString(),
            pipEnabled = enabled,
        ),
    )
}
