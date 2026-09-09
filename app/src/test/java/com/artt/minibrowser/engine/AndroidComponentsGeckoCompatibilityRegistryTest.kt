package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class AndroidComponentsGeckoCompatibilityRegistryTest {
    @Test
    fun closingOlderLeaseDoesNotClearReplacementActivityBinding() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val first = unusedHandler()
        val second = unusedHandler()

        val firstLease = registry.bind(first)
        val secondLease = registry.bind(second)

        assertSame(second, registry.currentHandler())
        firstLease.close()
        assertSame(second, registry.currentHandler())

        secondLease.close()
        assertNull(registry.currentHandler())
    }

    @Test
    fun closingCurrentLeaseClearsBinding() {
        val registry = AndroidComponentsGeckoCompatibilityRegistry()
        val handler = unusedHandler()

        val lease = registry.bind(handler)
        assertSame(handler, registry.currentHandler())

        lease.close()
        assertNull(registry.currentHandler())
    }

    private fun unusedHandler() = object : AndroidComponentsGeckoCompatibilityHandler {
        override fun onWeekPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = error("Not used by registry lease tests")

        override fun onXrPermission(
            session: GeckoSession,
            permission: GeckoSession.PermissionDelegate.ContentPermission,
        ): GeckoResult<Int>? = error("Not used by registry lease tests")

        override fun onLinkedMediaContextMenu(
            session: GeckoSession,
            element: GeckoSession.ContentDelegate.ContextElement,
        ): Boolean = error("Not used by registry lease tests")
    }
}
