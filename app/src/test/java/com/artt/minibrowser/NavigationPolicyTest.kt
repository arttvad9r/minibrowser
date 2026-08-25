package com.artt.minibrowser

import com.artt.minibrowser.engine.NavigationTarget
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.resolveNavigation
import com.artt.minibrowser.engine.selectSafeExternalUri
import com.artt.minibrowser.engine.isAllowedPopupTarget
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

    @Test fun unsafeDirectUriUsesSafeFallback() {
        assertEquals("https://example.com", selectSafeExternalUri("evil://payload", "https://example.com"))
        assertEquals(null, selectSafeExternalUri("evil://payload", "custom://payload"))
        assertEquals("https://example.com", selectSafeExternalUri(null, "https://example.com"))
        assertEquals("https://example.com", selectSafeExternalUri("https://example.com", "https://other.example"))
    }

    @Test fun malformedWebUrisAreRejected() {
        assertEquals(null, selectSafeExternalUri("https:", null))
        assertEquals(null, selectSafeExternalUri("https://", null))
        assertEquals(null, selectSafeExternalUri("https:garbage", null))
        assertEquals("https://example.com/path", selectSafeExternalUri("https://example.com/path", null))
        assertEquals("mailto:test@example.com", selectSafeExternalUri("mailto:test@example.com", null))
        assertEquals(null, selectSafeExternalUri("evil://payload", "https:"))
        assertEquals(null, selectSafeExternalUri("evil://payload", "https://"))
        assertEquals(null, selectSafeExternalUri("evil://payload", "mailto:test@example.com"))
    }

    @Test fun popupPolicyAllowsOnlyValidWebTargets() {
        assertEquals(true, isAllowedPopupTarget("https://drive.google.com/"))
        assertEquals(true, isAllowedPopupTarget("https://www.youtube.com/"))
        assertEquals(false, isAllowedPopupTarget("javascript:alert(1)"))
        assertEquals(false, isAllowedPopupTarget("data:text/html,hello"))
        assertEquals(false, isAllowedPopupTarget("file:///tmp/page"))
        assertEquals(false, isAllowedPopupTarget("chrome://settings"))
        assertEquals(false, isAllowedPopupTarget("https://"))
        assertEquals(false, isAllowedPopupTarget("not a uri"))
        assertEquals(false, isAllowedPopupTarget("  "))
        assertEquals(false, isAllowedPopupTarget(null))
    }
}
