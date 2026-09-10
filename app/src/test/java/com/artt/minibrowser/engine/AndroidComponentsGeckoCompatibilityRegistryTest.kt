package com.artt.minibrowser.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidComponentsGeckoCompatibilityRegistryTest {
    @Test
    fun closingOlderLeaseDoesNotClearReplacementActivityBinding() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val first = unusedHost()
        val second = unusedHost()

        val firstLease = registry.bind(first)
        val secondLease = registry.bind(second)

        assertSame(second, registry.currentHost())
        firstLease.close()
        assertSame(second, registry.currentHost())

        secondLease.close()
        assertNull(registry.currentHost())
    }

    @Test
    fun closingCurrentLeaseClearsBinding() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val host = unusedHost()

        val lease = registry.bind(host)
        assertSame(host, registry.currentHost())

        lease.close()
        assertNull(registry.currentHost())
    }

    @Test
    fun sessionContextKeepsIdentityAndPrivacyIndependentOfActivityLifetime() {
        val context = AndroidComponentsGeckoSessionContext(
            sessionId = "42",
            privateMode = true,
        )

        assertTrue(context.privateMode)
        assertTrue(context.isSelected("42"))
        assertFalse(context.isSelected("41"))
        assertFalse(context.isSelected(null))
    }

    @Test
    fun sessionHandlerCanOutliveCurrentActivityBinding() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val context = AndroidComponentsGeckoSessionContext(
            sessionId = "42",
            privateMode = false,
        )

        val handler = registry.forSession(context)
        val firstLease = registry.bind(unusedHost())
        firstLease.close()
        assertNull(registry.currentHost())

        val second = unusedHost()
        registry.bind(second)
        assertSame(second, registry.currentHost())
        // The same session-bound handler does not retain either Activity-scoped host.
        assertNotNull(handler)
    }

    @Test
    fun missingActivityHostFailsPermissionCallbacksClosed() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val handler = registry.forSession(
            AndroidComponentsGeckoSessionContext(sessionId = "42", privateMode = false),
        )
        val session = GeckoSession()
        var androidRejected = 0
        var mediaRejected = 0

        handler.onAndroidPermissionsRequest(
            session,
            arrayOf(android.Manifest.permission.CAMERA),
            object : GeckoSession.PermissionDelegate.Callback {
                override fun reject() {
                    androidRejected++
                }
            },
        )
        handler.onMediaPermissionRequest(
            session,
            "https://example.test",
            null,
            null,
            object : GeckoSession.PermissionDelegate.MediaCallback {
                override fun reject() {
                    mediaRejected++
                }
            },
        )
        val contentPermission = object : GeckoSession.PermissionDelegate.ContentPermission() {}
        var contentValue: Int? = null
        handler.onContentPermissionRequest(session, contentPermission).accept { value ->
            contentValue = value
        }

        assertEquals(1, androidRejected)
        assertEquals(1, mediaRejected)
        assertEquals(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY, contentValue)
    }

    private fun unusedHost() = object : AndroidComponentsGeckoCompatibilityHost {
        override fun onWeekPrompt(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = error("Not used by registry lease tests")

        override fun onAndroidPermissionsRequest(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            permissions: Array<String>?,
            callback: GeckoSession.PermissionDelegate.Callback,
        ) = error("Not used by registry lease tests")

        override fun onContentPermissionRequest(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            permission: GeckoSession.PermissionDelegate.ContentPermission,
        ): GeckoResult<Int> = error("Not used by registry lease tests")

        override fun onMediaPermissionRequest(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            uri: String,
            video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback,
        ) = error("Not used by registry lease tests")

        override fun onLinkedMediaContextMenu(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            element: GeckoSession.ContentDelegate.ContextElement,
        ): Boolean = error("Not used by registry lease tests")
    }
}
