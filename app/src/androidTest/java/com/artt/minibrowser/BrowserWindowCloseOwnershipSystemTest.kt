package com.artt.minibrowser

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.androidComponentsExistingSessionTransferPlan
import com.artt.minibrowser.engine.closeAndroidComponentsOwnedTabFromWindowRequest
import java.io.File
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
class BrowserWindowCloseOwnershipSystemTest {
    @Test
    fun relinquishedWindowCloseRemovesBrowserStoreOwnerBeforeTabManagerRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val storeDir = File(app.cacheDir, "window-close-owner-${System.nanoTime()}")
        var tabManager: TabManager? = null
        var engineSession: GeckoEngineSession? = null
        var rawSession: GeckoSession? = null

        try {
            instrumentation.runOnMainSync {
                val manager = TabManager(app.runtime, storeDir, app)
                tabManager = manager
                val tab = checkNotNull(manager.current())
                val raw = tab.session
                rawSession = raw
                val tabId = tab.id.toString()
                val store = BrowserStore(
                    middleware = EngineMiddleware.create(
                        engine = app.engine,
                        trimMemoryAutomatically = false,
                    ),
                )
                store.dispatch(
                    TabListAction.AddTabAction(
                        createTab(
                            url = "about:blank",
                            private = false,
                            id = tabId,
                        ),
                    ),
                )

                tab.relinquishRawSessionOwnership(raw)
                val owned = GeckoEngineSession(
                    runtime = app.runtime,
                    privateMode = false,
                    geckoSessionProvider = { raw },
                    openGeckoSession = false,
                )
                engineSession = owned
                androidComponentsExistingSessionTransferPlan(
                    tabId = tabId,
                    mediaSessionHandoff = null,
                ).linkAndReplayTo(store, owned)

                assertTrue("Transferred GeckoSession starts open", raw.isOpen)
                assertSame(
                    "Test store links the exact A-C owner",
                    owned,
                    store.state.tabs.single { it.id == tabId }.engineState.engineSession,
                )

                assertTrue(
                    "Linked window close is consumed",
                    closeAndroidComponentsOwnedTabFromWindowRequest(manager, store, tabId),
                )
                assertTrue("BrowserStore owner is removed first", store.state.tabs.none { it.id == tabId })
                assertTrue("TabManager relinquished record is removed", manager.tabs.value.none { it.id == tab.id })
            }

            val transferredRawSession = checkNotNull(rawSession)
            val deadline = SystemClock.uptimeMillis() + CLOSE_TIMEOUT_MS
            while (
                SystemClock.uptimeMillis() < deadline &&
                isSessionOpenOnMainThread(instrumentation, transferredRawSession)
            ) {
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
            assertFalse(
                "BrowserStore TabsRemovedMiddleware closes the underlying supplied GeckoSession",
                isSessionOpenOnMainThread(instrumentation, transferredRawSession),
            )
            engineSession = null
        } finally {
            instrumentation.runOnMainSync {
                engineSession?.close()
                rawSession?.let { raw ->
                    if (raw.isOpen) raw.close()
                }
                tabManager?.close()
            }
            storeDir.deleteRecursively()
        }
    }

    private fun isSessionOpenOnMainThread(
        instrumentation: android.app.Instrumentation,
        session: GeckoSession,
    ): Boolean {
        var open = false
        instrumentation.runOnMainSync {
            open = session.isOpen
        }
        return open
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
