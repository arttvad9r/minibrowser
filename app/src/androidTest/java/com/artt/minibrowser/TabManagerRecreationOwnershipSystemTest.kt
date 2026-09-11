package com.artt.minibrowser

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.RawSessionOwnership
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.TabManagerRecreationHandoffRegistry
import java.io.File
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
class TabManagerRecreationOwnershipSystemTest {
    @Test
    fun rawSessionsPrivateTabsAndSequenceAreAdoptedByExactIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val storeDir = File(app.cacheDir, "tab-recreation-raw-${System.nanoTime()}")
        val rawTabId = 93_001L
        var oldManager: TabManager? = null
        var replacement: TabManager? = null
        lateinit var publicTab: Tab
        lateinit var privateTab: Tab
        lateinit var publicSession: GeckoSession
        var retainedMediaDelegate: Any? = null
        var closedHighId = 0L
        val androidComponentsIds = mutableSetOf<String>()

        // This test needs one raw GeckoSession identity to survive recreation. Ordinary fresh tabs
        // are A-C-owned now, so make the legacy raw owner an explicit persisted precondition.
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
                oldManager = manager
                publicTab = checkNotNull(manager.current())
                publicSession = publicTab.session
                retainedMediaDelegate = publicTab.rawMediaSessionDelegate
                assertNotNull("Raw owner installs media delegate", retainedMediaDelegate)

                val viewOwnedSelectionDelegate = object : GeckoSession.SelectionActionDelegate {
                    override fun onShowActionRequest(
                        session: GeckoSession,
                        selection: GeckoSession.SelectionActionDelegate.Selection,
                    ) = Unit

                    override fun onHideAction(session: GeckoSession, reason: Int) = Unit
                }
                publicSession.selectionActionDelegate = viewOwnedSelectionDelegate
                assertSame(
                    "Raw render view installs an Activity-bound selection delegate",
                    viewOwnedSelectionDelegate,
                    publicSession.selectionActionDelegate,
                )

                privateTab = manager.newTab(null, private = true)
                androidComponentsIds += privateTab.id.toString()
                val closedHighTab = manager.newTab(null, private = false)
                androidComponentsIds += closedHighTab.id.toString()
                closedHighId = closedHighTab.id
                manager.closeTab(closedHighId)
                manager.select(publicTab.id)

                val handoff = manager.detachForRecreation()
                assertTrue("Selected raw GeckoSession stays open across handoff", publicSession.isOpen)
                assertNull("Old progress delegate releases Activity-owned manager", publicSession.progressDelegate)
                assertNull("Old navigation delegate releases Activity-owned manager", publicSession.navigationDelegate)
                assertNull("Old content delegate releases Activity-owned manager", publicSession.contentDelegate)
                assertNull("Old history delegate releases Activity-owned manager", publicSession.historyDelegate)
                assertSame(
                    "TabManager detach does not own the view-installed selection delegate",
                    viewOwnedSelectionDelegate,
                    publicSession.selectionActionDelegate,
                )
                TabManagerRecreationHandoffRegistry.publish(storeDir, handoff)
                assertNull(
                    "Retention boundary releases the old view/Activity selection delegate",
                    publicSession.selectionActionDelegate,
                )
            }

            // MainActivity preloads the versioned disk snapshot before constructing TabManager.
            TabStore.preloadStateForNextRestore(storeDir)

            instrumentation.runOnMainSync {
                val adopted = TabManager(app.runtime, storeDir, app)
                replacement = adopted

                assertSame("Public Tab object is adopted by identity", publicTab, adopted.current())
                assertSame("Underlying raw GeckoSession is never recreated", publicSession, publicTab.session)
                assertSame(
                    "Private tab survives configuration change without disk persistence",
                    privateTab,
                    adopted.tabs.value.single { it.id == privateTab.id },
                )
                assertSame(
                    "Engine-only media delegate and its playback handoff state survive recreation",
                    retainedMediaDelegate,
                    publicTab.rawMediaSessionDelegate,
                )
                assertNotNull("Replacement manager rebinds progress delegate", publicSession.progressDelegate)
                assertNotNull("Replacement manager rebinds navigation delegate", publicSession.navigationDelegate)
                assertNotNull("Replacement manager rebinds content delegate", publicSession.contentDelegate)
                assertNotNull("Replacement manager rebinds history delegate", publicSession.historyDelegate)
                assertNull(
                    "New view owns installing a fresh selection delegate after recreation",
                    publicSession.selectionActionDelegate,
                )

                val next = adopted.newTab(null, private = false)
                androidComponentsIds += next.id.toString()
                assertTrue(
                    "Recreation keeps the monotonic sequence beyond already-closed tab IDs",
                    next.id > closedHighId,
                )
            }
        } finally {
            instrumentation.runOnMainSync {
                replacement?.close()
                oldManager?.close()
                if (androidComponentsIds.isNotEmpty()) {
                    // Keep cleanup ordered after every AddTabAction even when the store worker has
                    // not exposed those rows yet; snapshot-based conditional removal can race them.
                    app.browserStore.dispatch(
                        TabListAction.RemoveTabsAction(androidComponentsIds.toList()),
                    )
                }
                TabManagerRecreationHandoffRegistry.clearForTest(storeDir)
            }
            TabStore.loadState(storeDir)
            storeDir.deleteRecursively()
        }
    }

    @Test
    fun relinquishedTabRemainsLinkedToSameAndroidComponentsOwnerAcrossHandoff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val storeDir = File(app.cacheDir, "tab-recreation-linked-${System.nanoTime()}")
        val tabId = 900_000_000L + (System.nanoTime() and 0x0FFFFFFFL)
        val sessionId = tabId.toString()
        var oldManager: TabManager? = null
        var replacement: TabManager? = null
        lateinit var transferredTab: Tab
        var rawSession: GeckoSession? = null
        var linkedEngineSession: Any? = null

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

                val manager = TabManager(app.runtime, storeDir, app)
                oldManager = manager
                transferredTab = checkNotNull(manager.current())
                val suppliedRawSession = transferredTab.session
                rawSession = suppliedRawSession
                val engineSession = app.transferExistingTabToAndroidComponents(manager, transferredTab)
                linkedEngineSession = engineSession

                assertEquals(RawSessionOwnership.Relinquished, transferredTab.rawSessionOwnership)
                assertSame(
                    "Transfer links the exact A-C EngineSession",
                    engineSession,
                    store.state.tabs.single { it.id == sessionId }.engineState.engineSession,
                )
                assertTrue("Underlying GeckoSession stays open after transfer", suppliedRawSession.isOpen)

                val handoff = manager.detachForRecreation()
                TabManagerRecreationHandoffRegistry.publish(storeDir, handoff)
            }

            TabStore.preloadStateForNextRestore(storeDir)

            instrumentation.runOnMainSync {
                val adopted = TabManager(app.runtime, storeDir, app)
                replacement = adopted
                val storeTab = app.browserStore.state.tabs.single { it.id == sessionId }
                val suppliedRawSession = checkNotNull(rawSession)

                assertSame("Relinquished structural Tab is adopted by identity", transferredTab, adopted.current())
                assertEquals(RawSessionOwnership.Relinquished, transferredTab.rawSessionOwnership)
                assertSame("Underlying supplied GeckoSession identity is unchanged", suppliedRawSession, transferredTab.session)
                assertSame(
                    "App-scoped BrowserStore keeps the same linked EngineSession owner",
                    linkedEngineSession,
                    storeTab.engineState.engineSession,
                )
                assertTrue("Recreation does not close the A-C-owned GeckoSession", suppliedRawSession.isOpen)
            }
        } finally {
            instrumentation.runOnMainSync {
                val store = app.browserStore
                if (store.state.tabs.any { it.id == sessionId }) {
                    store.dispatch(TabListAction.RemoveTabAction(sessionId))
                }
                replacement?.closeTab(tabId)
                replacement?.close()
                oldManager?.close()
                TabManagerRecreationHandoffRegistry.clearForTest(storeDir)
            }

            rawSession?.let { suppliedRawSession ->
                val deadline = SystemClock.uptimeMillis() + CLOSE_TIMEOUT_MS
                while (
                    SystemClock.uptimeMillis() < deadline &&
                    isSessionOpenOnMainThread(instrumentation, suppliedRawSession)
                ) {
                    SystemClock.sleep(POLL_INTERVAL_MS)
                }
                assertFalse(
                    "BrowserStore remains the close owner after recreation",
                    isSessionOpenOnMainThread(instrumentation, suppliedRawSession),
                )
            }
            TabStore.loadState(storeDir)
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
