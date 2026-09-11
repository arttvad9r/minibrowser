package com.artt.minibrowser.engine

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.robolectric.annotation.Config

@Config(sdk = [35])
class AndroidComponentsUiCompatibilityDelegatesTest {
    @Test
    fun navigationDelegateForwardsNewSessionToWrappedDelegate() {
        val parent = GeckoSession()
        val child = GeckoSession()
        val expectedResult = GeckoResult.fromValue(child)
        val calls = AtomicInteger()
        val delegate =
            object : GeckoSession.NavigationDelegate {
                override fun onNewSession(
                    session: GeckoSession,
                    uri: String,
                ): GeckoResult<GeckoSession>? {
                    assertSame(parent, session)
                    assertEquals("https://example.com/child", uri)
                    calls.incrementAndGet()
                    return expectedResult
                }
            }

        val wrapper =
            AndroidComponentsNavigationUiCompatibilityDelegate(
                delegate = delegate,
                sessionId = "tab",
                compatibilityState = AndroidComponentsUiCompatibilityState(),
            )

        val result = wrapper.onNewSession(parent, "https://example.com/child")

        assertEquals(1, calls.get())
        assertSame(expectedResult, result)
    }
}
