package com.artt.minibrowser.engine

import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
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
        rawSession = tab.session,
        linkedEngineSession = linkedEngineSession,
    )
}

internal fun loadBrowserUrl(tab: Tab, browserStore: BrowserStore, url: String) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> target.session.loadUri(url)
        is BrowserCommandTarget.Linked -> target.session.loadUrl(url)
        null -> Unit
    }
}

internal fun goBrowserBack(tab: Tab, browserStore: BrowserStore) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> target.session.goBack()
        is BrowserCommandTarget.Linked -> target.session.goBack()
        null -> Unit
    }
}

internal fun goBrowserForward(tab: Tab, browserStore: BrowserStore) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> target.session.goForward()
        is BrowserCommandTarget.Linked -> target.session.goForward()
        null -> Unit
    }
}

internal fun reloadOrStopBrowser(
    tab: Tab,
    browserStore: BrowserStore,
    isLoading: Boolean,
) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> {
            if (isLoading) target.session.stop() else target.session.reload()
        }

        is BrowserCommandTarget.Linked -> {
            if (isLoading) target.session.stopLoading() else target.session.reload()
        }

        null -> Unit
    }
}

internal fun toggleBrowserDesktopMode(
    tab: Tab,
    browserStore: BrowserStore,
    enable: Boolean,
) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> toggleDesktopMode(tab)
        is BrowserCommandTarget.Linked -> target.session.toggleDesktopMode(enable = enable, reload = true)
        null -> Unit
    }
}

internal fun exitBrowserFullscreen(tab: Tab, browserStore: BrowserStore) {
    when (val target = browserCommandTargetForTab(tab, browserStore)) {
        is BrowserCommandTarget.Raw -> target.session.exitFullScreen()
        is BrowserCommandTarget.Linked -> target.session.exitFullScreenMode()
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
