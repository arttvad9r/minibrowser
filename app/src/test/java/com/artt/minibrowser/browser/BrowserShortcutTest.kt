package com.artt.minibrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserShortcutTest {
    @Test
    fun routesNewTabAction() {
        assertEquals(BrowserShortcut.NewTab, browserShortcutForAction(ACTION_NEW_TAB))
    }

    @Test
    fun routesPrivateTabAction() {
        assertEquals(BrowserShortcut.NewPrivateTab, browserShortcutForAction(ACTION_NEW_PRIVATE_TAB))
    }

    @Test
    fun ignoresUnrelatedActions() {
        assertNull(browserShortcutForAction(null))
        assertNull(browserShortcutForAction("android.intent.action.VIEW"))
    }
}
