package com.artt.minibrowser.engine

import android.app.Activity
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AndroidComponentsWindowCloseCompatibilityDelegateTest {
    @Test
    fun missingActivityHostConsumesCloseWithoutStockAndroidComponentsFallback() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val compatibility = registry.forSession(
            AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = false),
        )
        val session = GeckoSession()
        var stockCalls = 0
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                stockCalls++
            }
        }

        installAndroidComponentsGeckoCompatibilityDelegates(session, compatibility)

        val installed = assertIs<AndroidComponentsContentCompatibilityDelegate>(session.contentDelegate)
        installed.onCloseRequest(session)

        assertEquals(0, stockCalls)
    }

    @Test
    fun activityHostRoutesCloseByImmutableSessionId() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var closedSessionId: String? = null
        val host = AndroidComponentsActivityGeckoCompatibilityHost(
            activity = activity,
            selectedSessionId = { "42" },
            requestPermissions = null,
            pickFiles = null,
            openTab = { _, _ -> Unit },
            openBackgroundTab = { _, _ -> Unit },
            openWindowSession = { GeckoSession() },
            closeWindowTab = { sessionId ->
                closedSessionId = sessionId
                true
            },
        )

        host.onCloseRequest(
            context = AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = true),
            session = GeckoSession(),
        )

        assertEquals("42", closedSessionId)
    }
}
