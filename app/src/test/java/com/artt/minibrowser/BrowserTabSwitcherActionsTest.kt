package com.artt.minibrowser

import com.artt.minibrowser.ui.shouldCreateTabBeforeOverviewDismiss
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserTabSwitcherActionsTest {
    @Test
    fun leavingEmptyOverviewCreatesARealBlankTab() {
        assertTrue(shouldCreateTabBeforeOverviewDismiss(tabCount = 0, newTabRequested = false))
    }

    @Test
    fun overviewPlusDoesNotCreateASecondBlankTab() {
        assertFalse(shouldCreateTabBeforeOverviewDismiss(tabCount = 0, newTabRequested = true))
    }

    @Test
    fun existingTabsNeverNeedSyntheticReplacement() {
        assertFalse(shouldCreateTabBeforeOverviewDismiss(tabCount = 1, newTabRequested = false))
    }
}
