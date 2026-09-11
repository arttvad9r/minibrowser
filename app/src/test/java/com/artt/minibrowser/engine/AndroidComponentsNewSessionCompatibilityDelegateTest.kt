package com.artt.minibrowser.engine

import android.app.Activity
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AndroidComponentsNewSessionCompatibilityDelegateTest {
    @Test
    fun missingActivityHostFailsNewWindowClosedWithoutStockAndroidComponentsFallback() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val compatibility = registry.forSession(
            AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = true),
        )
        val session = GeckoSession()
        var stockCalls = 0
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(
                session: GeckoSession,
                uri: String,
            ): GeckoResult<GeckoSession>? {
                stockCalls++
                return GeckoResult.fromValue(GeckoSession())
            }
        }

        installAndroidComponentsGeckoCompatibilityDelegates(session, compatibility)

        val installed = assertIs<AndroidComponentsNewSessionCompatibilityDelegate>(session.navigationDelegate)
        val result = installed.onNewSession(session, "https://example.test/popup")

        assertNull(result)
        assertEquals(0, stockCalls)
    }

    @Test
    fun activityHostUsesRawPopupPolicyAndImmutablePrivateMode() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val parent = GeckoSession()
        var openedUri: String? = null
        var openedPrivate: Boolean? = null
        var openCalls = 0
        val host = AndroidComponentsActivityGeckoCompatibilityHost(
            activity = activity,
            selectedSessionId = { "42" },
            requestPermissions = null,
            pickFiles = null,
            openTab = { _, _ -> Unit },
            openBackgroundTab = { _, _ -> Unit },
            openWindowSession = { uri, private ->
                openCalls++
                openedUri = uri
                openedPrivate = private
                GeckoSession()
            },
        )
        val context = AndroidComponentsGeckoSessionContext(
            sessionId = "42",
            privateMode = true,
        )

        assertNotNull(host.onNewSession(context, parent, "https://example.test/popup"))
        assertEquals(1, openCalls)
        assertEquals("https://example.test/popup", openedUri)
        assertTrue(openedPrivate == true)

        assertNull(host.onNewSession(context, parent, "javascript:alert(1)"))
        assertEquals(1, openCalls)
    }
}
