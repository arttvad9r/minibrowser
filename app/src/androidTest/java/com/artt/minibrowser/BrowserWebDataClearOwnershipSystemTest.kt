package com.artt.minibrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.androidComponentsExistingSessionTransferPlan
import com.artt.minibrowser.engine.closeAndroidComponentsOwnedTabsBeforeWebDataClear
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
class BrowserWebDataClearOwnershipSystemTest {
    @Test
    fun relinquishedEngineSessionIsClosedBeforeClearPreparationReturns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val storeDir = File(app.cacheDir, "web-data-clear-owner-${System.nanoTime()}")
        val rawTabId = 92_001L
        var tabManager: TabManager? = null
        var engineSession: GeckoEngineSession? = null
        var rawSession: GeckoSession? = null

        // Web-data clear still has to cover a live raw GeckoSession after it crosses into A-C
        // ownership. Seed that legacy input explicitly now that ordinary fresh tabs start A-C-owned.
        TabStore.saveState(
            storeDir,
            PersistedBrowserState(
                selectedId = rawTabId,
                tabs = listOf(PersistedTab(id = rawTabId, url = "about:blank")),
            ),
        )

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

                closeAndroidComponentsOwnedTabsBeforeWebDataClear(manager, store)

                assertFalse(
                    "A-C close owner completes before storage-clear preparation returns",
                    raw.isOpen,
                )
                assertTrue("BrowserStore structural tab is removed", store.state.tabs.none { it.id == tabId })
                assertTrue("TabManager relinquished record is removed", manager.tabs.value.none { it.id == tab.id })
                engineSession = null
            }
        } finally {
            instrumentation.runOnMainSync {
                engineSession?.close()
                rawSession?.let { raw ->
                    if (raw.isOpen) raw.close()
                }
                tabManager?.close()
            }
            TabStore.loadState(storeDir)
            storeDir.deleteRecursively()
        }
    }
}
