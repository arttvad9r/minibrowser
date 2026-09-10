package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalAppNavigationPolicyRegistryTest {
    private val request = ExternalAppNavigationRequest(
        uri = "https://example.com/",
        hasUserGesture = true,
        isRedirect = false,
    )

    @Test
    fun passesWhenNoActivityPolicyIsBound() {
        val registry = ExternalAppNavigationPolicyRegistry()

        assertEquals(ExternalAppRequestDecision.Pass, registry.onLoadRequest(request))
    }

    @Test
    fun forwardsToTheCurrentlyBoundPolicy() {
        val registry = ExternalAppNavigationPolicyRegistry()
        val policy = ExternalAppNavigationPolicy { ExternalAppRequestDecision.Deny }

        registry.bind(policy)

        assertEquals(ExternalAppRequestDecision.Deny, registry.onLoadRequest(request))
    }

    @Test
    fun staleUnbindDoesNotClearAReplacementActivityPolicy() {
        val registry = ExternalAppNavigationPolicyRegistry()
        val oldPolicy = ExternalAppNavigationPolicy { ExternalAppRequestDecision.Pass }
        val replacementPolicy = ExternalAppNavigationPolicy { ExternalAppRequestDecision.Deny }

        registry.bind(oldPolicy)
        registry.bind(replacementPolicy)
        registry.unbind(oldPolicy)

        assertEquals(ExternalAppRequestDecision.Deny, registry.onLoadRequest(request))
    }

    @Test
    fun currentPolicyUnbindRestoresPassThroughBehavior() {
        val registry = ExternalAppNavigationPolicyRegistry()
        val policy = ExternalAppNavigationPolicy { ExternalAppRequestDecision.Deny }

        registry.bind(policy)
        registry.unbind(policy)

        assertEquals(ExternalAppRequestDecision.Pass, registry.onLoadRequest(request))
    }
}
