package com.artt.minibrowser.engine

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AndroidComponentsNavigationCompatibilityForwardingTest {
    @Test
    fun nonPopupNavigationCallbacksStayOnStockDelegate() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val compatibility = registry.forSession(
            AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = false),
        )
        val session = GeckoSession()
        var forwarded: Boolean? = null
        val stock = object : GeckoSession.NavigationDelegate {
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                forwarded = canGoForward
            }
        }
        val wrapper = AndroidComponentsNewSessionCompatibilityDelegate(stock, compatibility)

        wrapper.onCanGoForward(session, true)

        assertEquals(true, forwarded)
    }
}
