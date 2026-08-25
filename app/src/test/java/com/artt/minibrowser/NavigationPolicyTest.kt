package com.artt.minibrowser

import com.artt.minibrowser.engine.NavigationTarget
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.resolveNavigation
import com.artt.minibrowser.browser.NavigationController
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationPolicyTest {
    @Test fun queuesColdStartUntilHandlerIsReady() {
        val controller = NavigationController()
        var received = ""
        controller.accept("https://example.com")
        controller.setHandler { received = it }
        assertEquals("https://example.com", received)
    }
    @Test fun acceptsOnlyWebAndWhitelistedInternalUris() {
        assertEquals(NavigationTarget.Web("https://example.com"), resolveNavigation("https://example.com", SearchEngine.GOOGLE))
        assertEquals(NavigationTarget.Internal("about:blank"), resolveNavigation("about:blank", SearchEngine.GOOGLE))
        assertEquals(NavigationTarget.External("mailto:test@example.com"), resolveNavigation("mailto:test@example.com", SearchEngine.GOOGLE))
    }

    @Test fun schemesAreNotPassedToGeckoAsArbitraryUris() {
        assertEquals(NavigationTarget.External("intent://example.com"), resolveNavigation("intent://example.com", SearchEngine.GOOGLE))
        assertEquals(NavigationTarget.Search("chrome://crash"), resolveNavigation("chrome://crash", SearchEngine.GOOGLE))
    }
}
