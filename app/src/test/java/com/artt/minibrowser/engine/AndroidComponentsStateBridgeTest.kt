package com.artt.minibrowser.engine

import android.util.JsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineSessionState

class AndroidComponentsStateBridgeTest {
    @Test
    fun initialSyncCreatesMozillaTabsAndSelectsCurrentTab() {
        val tabs = listOf(
            snapshot(id = "1", url = "https://example.com", title = "Example", desktop = true),
            snapshot(id = "2", url = "about:blank", isPrivate = true),
        )

        val actions = browserStoreSyncActions(BrowserState(), tabs, selectedTabId = "2")

        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions.first())
        assertEquals(listOf("1", "2"), add.tabs.map { it.id })
        assertEquals(listOf(false, true), add.tabs.map { it.content.private })
        assertEquals(listOf(true, false), add.tabs.map { it.content.desktopMode })
        assertEquals("Example", add.tabs.first().content.title)
        assertEquals(TabListAction.SelectTabAction("2"), actions.last())
    }

    @Test
    fun initialSyncCarriesEngineRestoreStateWithoutCreatingLiveSession() {
        val tab = snapshot(id = "1", url = "https://example.com")
        val engineState = TestEngineSessionState("restored")

        val actions = browserStoreSyncActions(
            state = BrowserState(),
            tabs = listOf(tab),
            selectedTabId = "1",
            engineSessionStates = mapOf("1" to engineState),
        )

        val added = assertIs<TabListAction.AddMultipleTabsAction>(actions.first()).tabs.single()
        assertSame(engineState, added.engineState.engineSessionState)
        assertNull(added.engineState.engineSession)
        assertEquals(TabListAction.SelectTabAction("1"), actions.last())
    }

    @Test
    fun unchangedStateProducesNoActions() {
        val tab = snapshot(
            id = "7",
            url = "https://example.com/page",
            title = "Page",
            desktop = true,
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
    fun unchangedEngineRestoreStateKeepsShadowTabIdentity() {
        val tab = snapshot(id = "7", url = "https://example.com/page")
        val engineState = TestEngineSessionState("same")
        val state = BrowserState(
            tabs = listOf(tab.toState(engineSessionState = engineState)),
            selectedTabId = "7",
        )

        assertTrue(
            browserStoreSyncActions(
                state = state,
                tabs = listOf(tab),
                selectedTabId = "7",
                engineSessionStates = mapOf("7" to engineState),
            ).isEmpty(),
        )
    }

    @Test
    fun clearingEngineRestoreStateRecreatesOnlyShadowTab() {
        val tab = snapshot(id = "7", url = "https://example.com/page", desktop = true)
        val oldEngineState = TestEngineSessionState("old")
        val state = BrowserState(
            tabs = listOf(tab.toState(engineSessionState = oldEngineState)),
            selectedTabId = "7",
        )

        val actions = browserStoreSyncActions(
            state = state,
            tabs = listOf(tab),
            selectedTabId = "7",
            engineSessionStates = mapOf<String, EngineSessionState?>("7" to null),
        )

        assertEquals(TabListAction.RemoveTabsAction(listOf("7")), actions[0])
        val added = assertIs<TabListAction.AddMultipleTabsAction>(actions[1]).tabs.single()
        assertNull(added.engineState.engineSessionState)
        assertNull(added.engineState.engineSession)
        assertTrue(added.content.desktopMode)
        assertEquals(TabListAction.SelectTabAction("7"), actions.last())
    }

    @Test
    fun replacingEngineRestoreStateRecreatesOnlyShadowTab() {
        val tab = snapshot(id = "7", url = "https://example.com/page")
        val oldEngineState = TestEngineSessionState("old")
        val newEngineState = TestEngineSessionState("new")
        val state = BrowserState(
            tabs = listOf(tab.toState(engineSessionState = oldEngineState)),
            selectedTabId = "7",
        )

        val actions = browserStoreSyncActions(
            state = state,
            tabs = listOf(tab),
            selectedTabId = "7",
            engineSessionStates = mapOf("7" to newEngineState),
        )

        assertEquals(TabListAction.RemoveTabsAction(listOf("7")), actions[0])
        val added = assertIs<TabListAction.AddMultipleTabsAction>(actions[1]).tabs.single()
        assertSame(newEngineState, added.engineState.engineSessionState)
        assertEquals(TabListAction.SelectTabAction("7"), actions.last())
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
    fun desktopModeChangeUsesGranularBrowserStateAction() {
        val current = snapshot(id = "3", url = "https://example.com", desktop = false)
        val next = current.copy(desktop = true)
        val state = BrowserState(tabs = listOf(current.toState()), selectedTabId = "3")

        val actions = browserStoreSyncActions(state, listOf(next), "3")

        assertEquals(listOf(ContentAction.UpdateTabDesktopMode("3", true)), actions)
    }

    @Test
    fun tabReorderUsesMoveWithoutRebuildingSessions() {
        val first = snapshot(id = "1", url = "https://one.example")
        val second = snapshot(id = "2", url = "https://two.example")
        val state = BrowserState(
            tabs = listOf(first.toState(), second.toState()),
            selectedTabId = "1",
        )

        val actions = browserStoreSyncActions(state, listOf(second, first), selectedTabId = "1")

        assertEquals(
            listOf(
                TabListAction.MoveTabsAction(
                    tabIds = listOf("2"),
                    targetTabId = "1",
                    placeAfter = false,
                ),
            ),
            actions,
        )
    }

    @Test
    fun tabAddedInMiddleIsAddedThenMovedWithoutRebuildingExistingTabs() {
        val first = snapshot(id = "1", url = "https://one.example")
        val second = snapshot(id = "2", url = "https://two.example")
        val third = snapshot(id = "3", url = "https://three.example")
        val state = BrowserState(
            tabs = listOf(first.toState(), third.toState()),
            selectedTabId = "1",
        )

        val actions = browserStoreSyncActions(
            state,
            listOf(first, second, third),
            selectedTabId = "1",
        )

        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions.first())
        assertEquals(listOf("2"), add.tabs.map { it.id })
        assertEquals(
            TabListAction.MoveTabsAction(
                tabIds = listOf("2"),
                targetTabId = "3",
                placeAfter = false,
            ),
            actions.last(),
        )
        assertTrue(actions.none { it is TabListAction.RemoveAllTabsAction })
    }

    @Test
    fun tabRemovalRemovesOnlyMissingTab() {
        val first = snapshot(id = "1", url = "https://one.example")
        val second = snapshot(id = "2", url = "https://two.example")
        val third = snapshot(id = "3", url = "https://three.example")
        val state = BrowserState(
            tabs = listOf(first.toState(), second.toState(), third.toState()),
            selectedTabId = "1",
        )

        val actions = browserStoreSyncActions(state, listOf(first, third), selectedTabId = "1")

        assertEquals(listOf(TabListAction.RemoveTabsAction(listOf("2"))), actions)
    }

    @Test
    fun contentChangeIsPreservedWhileTabsReorder() {
        val first = snapshot(id = "1", url = "https://one.example", title = "Old")
        val second = snapshot(id = "2", url = "https://two.example")
        val state = BrowserState(
            tabs = listOf(first.toState(), second.toState()),
            selectedTabId = "1",
        )

        val actions = browserStoreSyncActions(
            state,
            listOf(second, first.copy(title = "New")),
            selectedTabId = "1",
        )

        assertEquals(ContentAction.UpdateTitleAction("1", "New"), actions[0])
        assertEquals(
            TabListAction.MoveTabsAction(
                tabIds = listOf("2"),
                targetTabId = "1",
                placeAfter = false,
            ),
            actions[1],
        )
    }

    @Test
    fun privacyChangeRecreatesOnlyAffectedTabAndRestoresSelection() {
        val current = snapshot(id = "1", url = "https://example.com", isPrivate = false, desktop = true)
        val next = current.copy(isPrivate = true)
        val state = BrowserState(tabs = listOf(current.toState()), selectedTabId = "1")

        val actions = browserStoreSyncActions(state, listOf(next), selectedTabId = "1")

        assertEquals(TabListAction.RemoveTabsAction(listOf("1")), actions[0])
        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions[1])
        assertEquals(listOf("1"), add.tabs.map { it.id })
        assertTrue(add.tabs.single().content.private)
        assertTrue(add.tabs.single().content.desktopMode)
        assertEquals(TabListAction.SelectTabAction("1"), actions.last())
        assertTrue(actions.none { it is TabListAction.RemoveAllTabsAction })
    }

    private fun snapshot(
        id: String,
        url: String,
        title: String = "",
        isPrivate: Boolean = false,
        desktop: Boolean = false,
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
        desktop = desktop,
        progress = progress,
        loading = loading,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        fullscreen = fullscreen,
    )

    private fun BrowserStoreTabSnapshot.toState(
        engineSessionState: EngineSessionState? = null,
    ) = TabSessionState(
        id = id,
        content = ContentState(
            url = url,
            private = isPrivate,
            title = title,
            desktopMode = desktop,
            progress = progress,
            loading = loading,
            fullScreen = fullscreen,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
        ),
        engineState = EngineState(engineSessionState = engineSessionState),
    )

    private data class TestEngineSessionState(val marker: String) : EngineSessionState {
        override fun writeTo(writer: JsonWriter) {
            writer.beginObject()
            writer.name("marker").value(marker)
            writer.endObject()
        }
    }
}
