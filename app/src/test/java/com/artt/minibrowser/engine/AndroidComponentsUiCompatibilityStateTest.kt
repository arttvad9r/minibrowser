package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mozilla.geckoview.WebRequestError

class AndroidComponentsUiCompatibilityStateTest {
    @Test
    fun seedPreservesRawSecurityAndLoadErrorAcrossOwnershipBoundary() {
        val state = AndroidComponentsUiCompatibilityState()

        state.seed(
            sessionId = "7",
            handoff = AndroidComponentsUiCompatibilityHandoff(
                securityState = SecurityState.Exception,
                pageLoadError = PageLoadError.Security,
            ),
        )

        assertEquals(
            AndroidComponentsUiCompatibilitySnapshot(
                securityState = SecurityState.Exception,
                pageLoadError = PageLoadError.Security,
            ),
            state.snapshot("7"),
        )
    }

    @Test
    fun pageStartClearsPreviousErrorAndResetsSecurityToUnknown() {
        val state = AndroidComponentsUiCompatibilityState()
        state.seed(
            "7",
            AndroidComponentsUiCompatibilityHandoff(
                securityState = SecurityState.Secure,
                pageLoadError = PageLoadError.Network,
            ),
        )

        state.onPageStart("7")

        assertEquals(AndroidComponentsUiCompatibilitySnapshot(), state.snapshot("7"))
    }

    @Test
    fun certificateExceptionTakesPrecedenceOverSecureFlag() {
        val state = AndroidComponentsUiCompatibilityState()

        state.onSecurityChange(sessionId = "7", isException = true, isSecure = true)

        assertEquals(SecurityState.Exception, state.snapshot("7")?.securityState)
    }

    @Test
    fun securityUpdatesPreserveCurrentLoadError() {
        val state = AndroidComponentsUiCompatibilityState()
        state.onLoadError("7", WebRequestError.ERROR_CATEGORY_NETWORK)

        state.onSecurityChange(sessionId = "7", isException = false, isSecure = true)

        assertEquals(SecurityState.Secure, state.snapshot("7")?.securityState)
        assertEquals(PageLoadError.Network, state.snapshot("7")?.pageLoadError)
    }

    @Test
    fun loadErrorUpdatesPreserveCurrentSecurityAndUseRawCategoryMapping() {
        val state = AndroidComponentsUiCompatibilityState()
        state.onSecurityChange(sessionId = "7", isException = false, isSecure = true)

        state.onLoadError("7", WebRequestError.ERROR_CATEGORY_SECURITY)
        assertEquals(SecurityState.Secure, state.snapshot("7")?.securityState)
        assertEquals(PageLoadError.Security, state.snapshot("7")?.pageLoadError)

        state.onLoadError("7", WebRequestError.ERROR_CATEGORY_NETWORK)
        assertEquals(PageLoadError.Network, state.snapshot("7")?.pageLoadError)

        state.onLoadError("7", WebRequestError.ERROR_CATEGORY_UNKNOWN)
        assertEquals(PageLoadError.Generic, state.snapshot("7")?.pageLoadError)
    }

    @Test
    fun removeDropsCompatibilityStateForClosedSession() {
        val state = AndroidComponentsUiCompatibilityState()
        state.onSecurityChange(sessionId = "7", isException = false, isSecure = false)

        state.remove("7")

        assertNull(state.snapshot("7"))
    }

    @Test
    fun retainDropsOnlySessionsThatNoLongerExistInBrowserStore() {
        val state = AndroidComponentsUiCompatibilityState()
        state.onSecurityChange(sessionId = "7", isException = false, isSecure = true)
        state.onLoadError(sessionId = "8", category = WebRequestError.ERROR_CATEGORY_NETWORK)

        state.retain(setOf("8", "9"))

        assertNull(state.snapshot("7"))
        assertEquals(PageLoadError.Network, state.snapshot("8")?.pageLoadError)
        assertNull(state.snapshot("9"))
    }

    @Test
    fun effectiveUiStateFallsBackToRawStateBeforeTakeover() {
        assertEquals(
            SecurityState.Secure,
            effectiveUiSecurityState(compatibility = null, raw = SecurityState.Secure),
        )
        assertEquals(
            PageLoadError.Network,
            effectiveUiPageLoadError(compatibility = null, raw = PageLoadError.Network),
        )
    }

    @Test
    fun compatibilitySnapshotOverridesRawStateAfterTakeoverIncludingExplicitErrorClear() {
        val compatibility = AndroidComponentsUiCompatibilitySnapshot(
            securityState = SecurityState.Exception,
            pageLoadError = null,
        )

        assertEquals(
            SecurityState.Exception,
            effectiveUiSecurityState(compatibility, raw = SecurityState.Secure),
        )
        assertNull(
            effectiveUiPageLoadError(
                compatibility = compatibility,
                raw = PageLoadError.Security,
            ),
        )
    }
}
