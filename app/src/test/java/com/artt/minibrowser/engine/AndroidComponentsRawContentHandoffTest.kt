package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.createTab

class AndroidComponentsRawContentHandoffTest {
    @Test
    fun pendingRawContentDeltaIsFullyFlushedAtHandoff() {
        val state = BrowserState(
            tabs = listOf(
                createTab(
                    url = "about:blank",
                    private = false,
                    id = "7",
                ),
            ),
            selectedTabId = "7",
        )
        val next = BrowserStoreTabSnapshot(
            id = "7",
            url = "https://example.com/page",
            title = "Example",
            isPrivate = false,
            desktop = true,
            progress = 42,
            loading = true,
            canGoBack = true,
            canGoForward = true,
            fullscreen = true,
            securityState = SecurityState.Secure,
        )

        val actions = rawContentHandoffActions(state, next)

        assertEquals(
            listOf(
                ContentAction.UpdateUrlAction("7", "https://example.com/page"),
                ContentAction.UpdateTitleAction("7", "Example"),
                ContentAction.UpdateTabDesktopMode("7", true),
                ContentAction.UpdateProgressAction("7", 42),
                ContentAction.UpdateLoadingStateAction("7", true),
                ContentAction.UpdateBackNavigationStateAction("7", true),
                ContentAction.UpdateForwardNavigationStateAction("7", true),
                ContentAction.FullScreenChangedAction("7", true),
                ContentAction.UpdateSecurityInfoAction("7", SecurityInfo.Secure()),
            ),
            actions,
        )
    }

    @Test
    fun relinquishedSnapshotCannotOverwriteBrowserStoreAtHandoff() {
        val state = BrowserState(
            tabs = listOf(createTab(url = "about:blank", private = false, id = "7")),
            selectedTabId = "7",
        )
        val relinquished = BrowserStoreTabSnapshot(
            id = "7",
            url = "https://stale.example",
            title = "Stale",
            isPrivate = false,
            desktop = false,
            progress = 100,
            loading = false,
            canGoBack = false,
            canGoForward = false,
            fullscreen = false,
            securityState = SecurityState.Unknown,
            rawSessionOwnership = RawSessionOwnership.Relinquished,
        )

        assertFailsWith<IllegalStateException> {
            rawContentHandoffActions(state, relinquished)
        }
    }
}
