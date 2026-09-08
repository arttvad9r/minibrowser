package com.artt.minibrowser.engine

import mozilla.components.concept.engine.request.RequestInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidComponentsExternalAppRequestInterceptorTest {
    @Test
    fun passDecisionLeavesOriginalRequestUntouched() {
        assertNull(externalAppInterceptionResponse(ExternalAppRequestDecision.Pass))
    }

    @Test
    fun denyDecisionMapsToAndroidComponentsDenyResponse() {
        assertSame(
            RequestInterceptor.InterceptionResponse.Deny,
            externalAppInterceptionResponse(ExternalAppRequestDecision.Deny),
        )
    }

    @Test
    fun browserPolicyNeverInterceptsSubframeRequests() {
        assertTrue(shouldInterceptExternalAppRequest(isSubframeRequest = false))
        assertFalse(shouldInterceptExternalAppRequest(isSubframeRequest = true))
    }

    @Test
    fun engineNeutralRequestCarriesOnlyPolicyInputs() {
        assertEquals(
            ExternalAppNavigationRequest(
                uri = "tg://resolve?domain=example",
                hasUserGesture = true,
                isRedirect = false,
            ),
            ExternalAppNavigationRequest(
                uri = "tg://resolve?domain=example",
                hasUserGesture = true,
                isRedirect = false,
            ),
        )
    }
}
