package com.artt.minibrowser

import com.artt.minibrowser.engine.shouldTryExternalWebAppLink
import com.artt.minibrowser.engine.specializedHandlerPackages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalAppNavigationPolicyTest {
    @Test
    fun directWebClicksMayCheckForSpecializedNativeHandler() {
        assertTrue(
            shouldTryExternalWebAppLink(
                targetUri = "https://youtu.be/example",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
        assertTrue(
            shouldTryExternalWebAppLink(
                targetUri = "https://qr.nspk.ru/example",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
        assertTrue(
            shouldTryExternalWebAppLink(
                targetUri = "https://example.com/account",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun redirectMayCheckNativeHandlerOnlyWhenChainStartedWithUserGesture() {
        assertTrue(
            shouldTryExternalWebAppLink(
                targetUri = "https://bank.example/pay",
                hasUserGesture = false,
                isRedirect = true,
                redirectFromRecentUserGesture = true,
            ),
        )
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "https://bank.example/pay",
                hasUserGesture = false,
                isRedirect = true,
                redirectFromRecentUserGesture = false,
            ),
        )
    }

    @Test
    fun navigationWithoutGestureStaysInGecko() {
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "https://youtu.be/example",
                hasUserGesture = false,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun nonWebSchemesAreHandledBySeparateCustomSchemePath() {
        assertFalse(
            shouldTryExternalWebAppLink(
                targetUri = "tg://resolve?domain=example",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun browserAndSelfPackagesNeverCountAsSpecializedHandlers() {
        assertEquals(
            listOf("com.google.android.youtube"),
            specializedHandlerPackages(
                targetPackages = listOf(
                    "com.artt.minibrowser",
                    "com.android.chrome",
                    "com.google.android.youtube",
                ),
                genericBrowserPackages = setOf("com.artt.minibrowser", "com.android.chrome"),
                selfPackage = "com.artt.minibrowser",
            ),
        )
        assertEquals(
            emptyList(),
            specializedHandlerPackages(
                targetPackages = listOf("com.artt.minibrowser", "com.android.chrome"),
                genericBrowserPackages = setOf("com.artt.minibrowser", "com.android.chrome"),
                selfPackage = "com.artt.minibrowser",
            ),
        )
    }

    @Test
    fun multipleBankHandlersRemainAvailableForChooser() {
        assertEquals(
            listOf("bank.one", "bank.two"),
            specializedHandlerPackages(
                targetPackages = listOf("com.android.chrome", "bank.one", "bank.two", "bank.one"),
                genericBrowserPackages = setOf("com.android.chrome"),
                selfPackage = "com.artt.minibrowser",
            ),
        )
    }
}
