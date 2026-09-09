package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    private fun unusedHost() = object : AndroidComponentsGeckoCompatibilityHost {
        override fun onWeekPrompt(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = error("Not used by registry lease tests")

        override fun onXrPermission(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            permission: GeckoSession.PermissionDelegate.ContentPermission,
        ): GeckoResult<Int>? = error("Not used by registry lease tests")

        override fun onLinkedMediaContextMenu(
            context: AndroidComponentsGeckoSessionContext,
            session: GeckoSession,
            element: GeckoSession.ContentDelegate.ContextElement,
        ): Boolean = error("Not used by registry lease tests")
    }
}
