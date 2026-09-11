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
class AndroidComponentsContentCompatibilityForwardingTest {
    @Test
    fun unownedContentCallbacksStayOnStockDelegate() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val compatibility = registry.forSession(
            AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = false),
        )
        val session = GeckoSession()
        var forwarded: String? = null
        val stock = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                forwarded = title
            }
        }
        val wrapper = AndroidComponentsContentCompatibilityDelegate(stock, compatibility)

        wrapper.onTitleChange(session, "child title")

        assertEquals("child title", forwarded)
    }
}
