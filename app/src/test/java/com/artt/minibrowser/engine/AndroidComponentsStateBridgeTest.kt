package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState

class AndroidComponentsStateBridgeTest {
    @Test
    fun initialSyncCreatesMozillaTabsAndSelectsCurrentTab() {
        val tabs = listOf(
            snapshot(id = "1", url = "https://example.com", title = "Example"),
            snapshot(id = "2", url = "about:blank", isPrivate = true),
        )

        val actions = browserStoreSyncActions(BrowserState(), tabs, selectedTabId = "2")

        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions.first())
        assertEquals(listOf("1", "2"), add.tabs.map { it.id })
        assertEquals(listOf(false, true), add.tabs.map { it.content.private })
        assertEquals("Example", add.tabs.first().content.title)
        assertEquals(TabListAction.SelectTabAction("2"), actions.last())
    }

    @Test
    fun unchangedStateProducesNoActions() {
        val tab = snapshot(
            id = "7",
            url = "https://example.com/page",
            title = "Page",
            progress = 100,
            loading = false,
            canGoBack = true,
            canGoForward = false,
            fullscreen = false,
        )
        val state = BrowserState(
            tabs = listOf(tab.toState()),
            selectedTabId = "7",
        )

        assertTrue(browserStoreSyncActions(state, listOf(tab), "7").isEmpty())
    }

    @Test
    fun contentChangeUsesGranularBrowserStateAction() {
        val current = snapshot(id = "3", url = "https://example.com", title = "Old")
        val next = current.copy(title = "New")
        val state = BrowserState(tabs = listOf(current.toState()), selectedTabId = "3")

        val actions = browserStoreSyncActions(state, listOf(next), "3")

        assertEquals(listOf(ContentAction.UpdateTitleAction("3", "New")), actions)
    }

    @Test
    fun tabReorderRebuildsStoreInExactMiniBrowserOrder() {
        val first = snapshot(id = "1", url = "https://one.example")
        val second = snapshot(id = "2", url = "https://two.example")
        val state = BrowserState(
            tabs = listOf(first.toState(), second.toState()),
            selectedTabId = "1",
        )

        val actions = browserStoreSyncActions(state, listOf(second, first), selectedTabId = "1")

        assertEquals(TabListAction.RemoveAllTabsAction(recoverable = false), actions[0])
        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions[1])
        assertEquals(listOf("2", "1"), add.tabs.map { it.id })
        assertEquals(TabListAction.SelectTabAction("1"), actions.last())
    }

    private fun snapshot(
        id: String,
        url: String,
        title: String = "",
        isPrivate: Boolean = false,
        progress: Int = 0,
        loading: Boolean = false,
        canGoBack: Boolean = false,
        canGoForward: Boolean = false,
        fullscreen: Boolean = false,
    ) = BrowserStoreTabSnapshot(
        id = id,
        url = url,
        title = title,
        isPrivate = isPrivate,
        progress = progress,
        loading = loading,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        fullscreen = fullscreen,
    )

    private fun BrowserStoreTabSnapshot.toState() = TabSessionState(
        id = id,
        content = ContentState(
            url = url,
            private = isPrivate,
            title = title,
            progress = progress,
            loading = loading,
            fullScreen = fullscreen,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
        ),
    )
}
