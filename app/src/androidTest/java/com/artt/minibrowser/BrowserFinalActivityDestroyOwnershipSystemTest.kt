package com.artt.minibrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.closeBrowserSessionsForFinalActivityDestroy
import java.io.File
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
class BrowserFinalActivityDestroyOwnershipSystemTest {
    @Test
    fun finalDestroyPersistsBeforeClosingAndRemovingAndroidComponentsOwner() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val storeDir = File(app.cacheDir, "final-destroy-owner-${System.nanoTime()}")
        val tabId = 800_000_000L + (System.nanoTime() and 0x0FFFFFFFL)
        val sessionId = tabId.toString()
        var manager: TabManager? = null
        var rawSession: GeckoSession? = null

        TabStore.saveState(
            storeDir,
            PersistedBrowserState(
                selectedId = tabId,
                tabs = listOf(PersistedTab(id = tabId, url = "about:blank")),
            ),
        )

        try {
            instrumentation.runOnMainSync {
                val store = app.browserStore
                if (store.state.tabs.any { it.id == sessionId }) {
                    store.dispatch(TabListAction.RemoveTabAction(sessionId))
                }
                store.dispatch(
                    TabListAction.AddTabAction(
                        createTab(
                            url = "about:blank",
                            private = false,
                            id = sessionId,
                        ),
                    ),
                )

                val tabManager = TabManager(app.runtime, storeDir, app)
                manager = tabManager
                val tab = checkNotNull(tabManager.current())
                rawSession = tab.session
                app.transferExistingTabToAndroidComponents(tabManager, tab)

                assertTrue("Transferred GeckoSession is open before final destroy", tab.session.isOpen)
                closeBrowserSessionsForFinalActivityDestroy(tabManager, store)

                assertFalse(
                    "Final destroy synchronously closes the underlying A-C-owned GeckoSession",
                    tab.session.isOpen,
                )
                assertTrue(
                    "Final destroy removes the relinquished BrowserStore owner",
                    store.state.tabs.none { it.id == sessionId },
                )
            }

            val persisted = TabStore.loadState(storeDir)
            assertTrue(
                "Final persistence snapshot is published before BrowserStore removal",
                persisted.tabs.any { it.id == tabId && it.url == "about:blank" },
            )
        } finally {
            instrumentation.runOnMainSync {
                val store = app.browserStore
                if (store.state.tabs.any { it.id == sessionId }) {
                    store.dispatch(TabListAction.RemoveTabAction(sessionId))
                }
                manager?.close()
                rawSession?.let { session ->
                    if (session.isOpen) session.close()
                }
            }
            storeDir.deleteRecursively()
        }
    }

    @Test
    fun finalDestroyAcceptsAndroidComponentsSuspendAfterSynchronousUnlink() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val storeDir = File(app.cacheDir, "final-destroy-suspended-${System.nanoTime()}")
        val tabId = 900_000_000L + (System.nanoTime() and 0x0FFFFFFFL)
        val sessionId = tabId.toString()
        var manager: TabManager? = null
        var rawSession: GeckoSession? = null

        TabStore.saveState(
            storeDir,
            PersistedBrowserState(
                selectedId = tabId,
                tabs = listOf(PersistedTab(id = tabId, url = "about:blank")),
            ),
        )

        try {
            instrumentation.runOnMainSync {
                val store = app.browserStore
                if (store.state.tabs.any { it.id == sessionId }) {
                    store.dispatch(TabListAction.RemoveTabAction(sessionId))
                }
                store.dispatch(
                    TabListAction.AddTabAction(
                        createTab(
                            url = "about:blank",
                            private = false,
                            id = sessionId,
                        ),
                    ),
                )

                val tabManager = TabManager(app.runtime, storeDir, app)
                manager = tabManager
                val tab = checkNotNull(tabManager.current())
                rawSession = tab.session
                app.transferExistingTabToAndroidComponents(tabManager, tab)

                assertTrue("Transferred GeckoSession is open before suspend", tab.session.isOpen)
                store.dispatch(EngineAction.SuspendEngineSessionAction(sessionId))
                assertTrue(
                    "SuspendEngineSessionAction unlinks the A-C owner synchronously",
                    store.state.tabs.first { it.id == sessionId }.engineState.engineSession == null,
                )

                closeBrowserSessionsForFinalActivityDestroy(tabManager, store)
                assertTrue(
                    "Final destroy removes a suspended relinquished BrowserStore tab",
                    store.state.tabs.none { it.id == sessionId },
                )
            }

            // SuspendMiddleware owns the already-unlinked EngineSession close. Let its main-thread
            // coroutine complete rather than taking close authority back through the stale raw ref.
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertFalse(
                    "SuspendMiddleware eventually closes the underlying relinquished GeckoSession",
                    checkNotNull(rawSession).isOpen,
                )
            }
        } finally {
            instrumentation.runOnMainSync {
                val store = app.browserStore
                if (store.state.tabs.any { it.id == sessionId }) {
                    store.dispatch(TabListAction.RemoveTabAction(sessionId))
                }
                manager?.close()
                rawSession?.let { session ->
                    if (session.isOpen) session.close()
                }
            }
            storeDir.deleteRecursively()
        }
    }
}
