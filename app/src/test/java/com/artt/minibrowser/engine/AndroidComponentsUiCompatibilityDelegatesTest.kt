package com.artt.minibrowser.engine

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
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

    @Test
    fun navigationDelegateForwardsUnobservedJavaDefaultCallbacks() {
        val session = GeckoSession()
        var forwarded: Boolean? = null
        val delegate =
            object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                    forwarded = canGoBack
                }
            }
        val wrapper =
            AndroidComponentsNavigationUiCompatibilityDelegate(
                delegate = delegate,
                sessionId = "tab",
                compatibilityState = AndroidComponentsUiCompatibilityState(),
            )

        wrapper.onCanGoBack(session, true)

        assertEquals(true, forwarded)
    }

    @Test
    fun progressDelegateForwardsUnobservedJavaDefaultCallbacks() {
        val session = GeckoSession()
        var forwarded: Int? = null
        val delegate =
            object : GeckoSession.ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, progress: Int) {
                    forwarded = progress
                }
            }
        val wrapper =
            AndroidComponentsProgressUiCompatibilityDelegate(
                delegate = delegate,
                sessionId = "tab",
                compatibilityState = AndroidComponentsUiCompatibilityState(),
            )

        wrapper.onProgressChange(session, 42)

        assertEquals(42, forwarded)
    }
}
