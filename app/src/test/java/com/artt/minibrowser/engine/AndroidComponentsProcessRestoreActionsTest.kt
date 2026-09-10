package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab

class AndroidComponentsProcessRestoreActionsTest {
    @Test
    fun emptyPlanProducesNoActions() {
        assertTrue(
            androidComponentsProcessRestoreActions(
                AndroidComponentsProcessRestorePlan(tabs = emptyList(), selectedTabId = null),
            ).isEmpty(),
        )
    }

    @Test
    fun selectedAndroidComponentsTabIsAddedAndSelectedButStaysStateOnly() {
        val first = createTab(url = "https://one.example/", id = "1")
        val selected = createTab(url = "https://two.example/", id = "2")
        val actions = androidComponentsProcessRestoreActions(
            AndroidComponentsProcessRestorePlan(
                tabs = listOf(first, selected),
                selectedTabId = "2",
            ),
        )

        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions[0])
        assertEquals(listOf("1", "2"), add.tabs.map { it.id })
        assertSame(first, add.tabs[0])
        assertSame(selected, add.tabs[1])
        assertEquals(TabListAction.SelectTabAction("2"), actions[1])
        assertEquals(2, actions.size)
    }

    @Test
    fun backgroundAndroidComponentsTabsStayStateOnlyWhenPersistedSelectionIsRaw() {
        val first = createTab(url = "https://one.example/", id = "1")
        val second = createTab(url = "https://two.example/", id = "2")
        val actions = androidComponentsProcessRestoreActions(
            AndroidComponentsProcessRestorePlan(
                tabs = listOf(first, second),
                selectedTabId = null,
            ),
        )

        val add = assertIs<TabListAction.AddMultipleTabsAction>(actions.single())
        assertEquals(listOf("1", "2"), add.tabs.map { it.id })
    }
}
