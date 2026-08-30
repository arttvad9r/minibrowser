package com.artt.minibrowser

import com.artt.minibrowser.browser.BrowserOverlay
import com.artt.minibrowser.browser.BrowserScreen
import com.artt.minibrowser.browser.BrowserViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserViewModelTest {
    @Test
    fun openingOverlayReplacesPreviousOverlay() {
        val viewModel = BrowserViewModel()

        viewModel.showFind(true)
        assertTrue(viewModel.state.value.showFind)

        viewModel.showSwitcher(true)

        assertEquals(BrowserOverlay.Switcher, viewModel.state.value.overlay)
        assertTrue(viewModel.state.value.showSwitcher)
        assertFalse(viewModel.state.value.showFind)
        assertFalse(viewModel.state.value.showSiteInfo)
    }

    @Test
    fun closingInactiveOverlayKeepsActiveOverlay() {
        val viewModel = BrowserViewModel()
        viewModel.showSwitcher(true)

        viewModel.showFind(false)

        assertEquals(BrowserOverlay.Switcher, viewModel.state.value.overlay)
    }

    @Test
    fun leavingBrowserClosesOverlayAndBlocksBrowserOverlayUntilReturn() {
        val viewModel = BrowserViewModel()
        viewModel.showSiteInfo(true)

        viewModel.screen(BrowserScreen.Settings)

        assertNull(viewModel.state.value.overlay)
        assertFalse(viewModel.state.value.showSiteInfo)

        viewModel.showFind(true)
        assertNull(viewModel.state.value.overlay)

        viewModel.screen(BrowserScreen.Browser)
        assertNull(viewModel.state.value.overlay)
    }
}
