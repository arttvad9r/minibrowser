package com.artt.minibrowser.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

@RunWith(AndroidJUnit4::class)
class AndroidComponentsRawSessionRelinquishSystemTest {
    @Test
    fun capturesUiStateBeforeIrreversiblyRelinquishingExactRawSession() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val rawSession = newRawSession()
            val tab = Tab(rawSession, id = 42L, isPrivate = false).apply {
                securityState = SecurityState.Exception
                loadError = PageLoadError.Network
            }

            val handoff = tab.captureAndroidComponentsHandoffAndRelinquish(rawSession)

            assertSame(rawSession, handoff.rawSession)
            assertNull(handoff.mediaSessionHandoff)
            assertEquals(SecurityState.Exception, handoff.uiCompatibilityHandoff.securityState)
            assertEquals(PageLoadError.Network, handoff.uiCompatibilityHandoff.pageLoadError)
            assertEquals(RawSessionOwnership.Relinquished, tab.rawSessionOwnership)
        }
    }

    @Test
    fun differentSessionIdentityCannotRelinquishTab() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val rawSession = newRawSession()
            val otherSession = newRawSession()
            val tab = Tab(rawSession, id = 43L, isPrivate = false)

            try {
                tab.captureAndroidComponentsHandoffAndRelinquish(otherSession)
                fail("Different GeckoSession identity must not relinquish raw ownership")
            } catch (_: IllegalStateException) {
                // Expected.
            }

            assertEquals(RawSessionOwnership.Owned, tab.rawSessionOwnership)
        }
    }

    private fun newRawSession(): GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .suspendMediaWhenInactive(true)
            .build(),
    )
}
