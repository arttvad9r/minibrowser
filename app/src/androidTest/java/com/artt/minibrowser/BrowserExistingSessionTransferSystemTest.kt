package com.artt.minibrowser

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.engine.AndroidComponentsContentCompatibilityDelegate
import com.artt.minibrowser.engine.AndroidComponentsGeckoCompatibilityRegistry
import com.artt.minibrowser.engine.AndroidComponentsGeckoSessionContext
import com.artt.minibrowser.engine.AndroidComponentsOwnedSessionConfigurator
import com.artt.minibrowser.engine.AndroidComponentsPermissionCompatibilityDelegate
import com.artt.minibrowser.engine.AndroidComponentsPreparedExistingSessionTransfer
import com.artt.minibrowser.engine.AndroidComponentsPromptCompatibilityDelegate
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.ExternalAppNavigationPolicyRegistry
import com.artt.minibrowser.engine.prepareAndroidComponentsExistingSessionTransferAfterRawRelinquish
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

@RunWith(AndroidJUnit4::class)
class BrowserExistingSessionTransferSystemTest {
    @Test
    fun existingOpenGeckoSessionCanBeTakenOverWithoutChangingIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp

        instrumentation.runOnMainSync {
            val rawSession = newRawSession(app)
            var prepared: AndroidComponentsPreparedExistingSessionTransfer? = null
            try {
                assertTrue("Raw session is open before ownership transfer", rawSession.isOpen)

                prepared = prepareTransfer(app, rawSession, "existing-transfer-test")

                assertTrue("Ownership wrapper keeps the existing GeckoSession open", rawSession.isOpen)
                assertTrue(
                    "A-C prompt delegate is wrapped by the selective compatibility proxy",
                    rawSession.promptDelegate is AndroidComponentsPromptCompatibilityDelegate,
                )
                assertTrue(
                    "A-C permission delegate is wrapped by the selective compatibility proxy",
                    rawSession.permissionDelegate is AndroidComponentsPermissionCompatibilityDelegate,
                )
                assertTrue(
                    "A-C content delegate is wrapped by the selective compatibility proxy",
                    rawSession.contentDelegate is AndroidComponentsContentCompatibilityDelegate,
                )
                assertTrue(
                    "Future owned session keeps MiniBrowser's suspend-media policy",
                    prepared!!.engineSession.settings.suspendMediaWhenInactive,
                )
                assertTrue("Existing-session link is forced to skip loading", prepared!!.transferPlan.skipLoading)
                assertFalse("Existing-session link must not infer a parent", prepared!!.transferPlan.includeParent)

                prepared!!.engineSession.close()
                prepared = null
                assertFalse(
                    "Once wrapped, GeckoEngineSession is the close owner of the supplied raw session",
                    rawSession.isOpen,
                )
            } finally {
                prepared?.engineSession?.close()
                if (rawSession.isOpen) rawSession.close()
            }
        }
    }

    @Test
    fun browserStoreRemovalClosesTheTransferredRawSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val tabId = "existing-linked-transfer-test"
        lateinit var rawSession: GeckoSession
        var prepared: AndroidComponentsPreparedExistingSessionTransfer? = null

        try {
            instrumentation.runOnMainSync {
                val store = app.browserStore
                store.state.tabs.firstOrNull { it.id == tabId }?.let {
                    store.dispatch(TabListAction.RemoveTabAction(tabId))
                }
                store.dispatch(
                    TabListAction.AddTabAction(
                        createTab(
                            url = "about:blank",
                            private = false,
                            id = tabId,
                        ),
                    ),
                )

                rawSession = newRawSession(app)
                prepared = prepareTransfer(app, rawSession, tabId)
                store.dispatch(prepared!!.transferPlan.linkAction(prepared!!.engineSession))
                prepared!!.transferPlan.mediaReplayActions.forEach(store::dispatch)

                val linked = store.state.tabs.first { it.id == tabId }.engineState.engineSession
                assertSame("BrowserStore links the exact prepared EngineSession", prepared!!.engineSession, linked)

                store.dispatch(TabListAction.RemoveTabAction(tabId))
                assertTrue("Transferred test tab is removed from BrowserStore", store.state.tabs.none { it.id == tabId })
            }

            val deadline = SystemClock.uptimeMillis() + CLOSE_TIMEOUT_MS
            while (rawSession.isOpen && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
            assertFalse(
                "TabsRemovedMiddleware closes the underlying supplied GeckoSession",
                rawSession.isOpen,
            )
            prepared = null
        } finally {
            instrumentation.runOnMainSync {
                app.browserStore.state.tabs.firstOrNull { it.id == tabId }?.let {
                    app.browserStore.dispatch(TabListAction.RemoveTabAction(tabId))
                }
                prepared?.engineSession?.close()
                if (::rawSession.isInitialized && rawSession.isOpen) rawSession.close()
            }
        }
    }

    @Test
    fun privateModeMismatchIsRejectedBeforeDelegateTakeover() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp

        instrumentation.runOnMainSync {
            val rawSession = newRawSession(app)
            val promptBefore = rawSession.promptDelegate
            val permissionBefore = rawSession.permissionDelegate
            val contentBefore = rawSession.contentDelegate
            try {
                var rejected = false
                try {
                    prepareAndroidComponentsExistingSessionTransferAfterRawRelinquish(
                        runtime = app.runtime,
                        rawSession = rawSession,
                        sessionContext = AndroidComponentsGeckoSessionContext(
                            sessionId = "private-mismatch-test",
                            privateMode = true,
                        ),
                        configurator = newConfigurator(),
                        compatibilityRegistry = AndroidComponentsGeckoCompatibilityRegistry(),
                        mediaSessionHandoff = null,
                    )
                } catch (_: IllegalStateException) {
                    rejected = true
                }

                assertTrue("Private-mode mismatch is rejected", rejected)
                assertTrue("Rejected transfer leaves the raw session open", rawSession.isOpen)
                assertSame("Prompt ownership is untouched before rejection", promptBefore, rawSession.promptDelegate)
                assertSame(
                    "Permission ownership is untouched before rejection",
                    permissionBefore,
                    rawSession.permissionDelegate,
                )
                assertSame("Content ownership is untouched before rejection", contentBefore, rawSession.contentDelegate)
            } finally {
                if (rawSession.isOpen) rawSession.close()
            }
        }
    }

    private fun prepareTransfer(
        app: BrowserApp,
        rawSession: GeckoSession,
        tabId: String,
    ): AndroidComponentsPreparedExistingSessionTransfer =
        prepareAndroidComponentsExistingSessionTransferAfterRawRelinquish(
            runtime = app.runtime,
            rawSession = rawSession,
            sessionContext = AndroidComponentsGeckoSessionContext(
                sessionId = tabId,
                privateMode = false,
            ),
            configurator = newConfigurator(),
            compatibilityRegistry = AndroidComponentsGeckoCompatibilityRegistry(),
            mediaSessionHandoff = null,
        )

    private fun newRawSession(app: BrowserApp): GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .suspendMediaWhenInactive(true)
            .build(),
    ).also { it.open(app.runtime) }

    private fun newConfigurator() = AndroidComponentsOwnedSessionConfigurator(
        externalNavigationPolicy = ExternalAppNavigationPolicyRegistry(),
    )

    private companion object {
        const val CLOSE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
