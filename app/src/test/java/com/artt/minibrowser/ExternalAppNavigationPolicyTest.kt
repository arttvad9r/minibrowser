package com.artt.minibrowser

import com.artt.minibrowser.engine.shouldTryExternalWebAppLink
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalAppNavigationPolicyTest {
    @Test
    fun telegramUserClickMayTryNativeApp() {
        assertTrue(
            shouldTryExternalWebAppLink(
                targetUri = "https://t.me/example",
                triggerUri = "https://example.com/page",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun ordinaryCrossSiteClickStaysInGecko() {
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "https://other.example/account",
                triggerUri = "https://example.com/home",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun telegramInternalWebNavigationStaysInGecko() {
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "https://t.me/another",
                triggerUri = "https://t.me/example",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun redirectChainsStayInGecko() {
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "https://t.me/example",
                triggerUri = "https://example.com/start",
                hasUserGesture = true,
                isRedirect = true,
            ),
        )
    }

    @Test
    fun navigationWithoutGestureStaysInGecko() {
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "https://t.me/example",
                triggerUri = "https://example.com/page",
                hasUserGesture = false,
                isRedirect = false,
            ),
        )
    }
}
