package com.artt.minibrowser

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.engine.BrowserApp
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserFreshAndroidComponentsSessionSystemTest {
    @Test
    fun browserStoreCreatedSessionInstallsFreshSessionCompatibility() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val tabId = "fresh-ac-session-test"
        val url = "data:text/html,<html><title>fresh</title><body>fresh</body></html>"

        try {
            instrumentation.runOnMainSync {
                removeStoreTab(app, tabId)
                app.browserStore.dispatch(
                    TabListAction.AddTabAction(
                        createTab(url = url, id = tabId),
                    ),
                )
                app.browserStore.dispatch(EngineAction.CreateEngineSessionAction(tabId))
            }

            assertTrue(
                "EngineMiddleware links the fresh EngineSession",
                waitUntil(LINK_TIMEOUT_MS) {
                    var linked = false
                    instrumentation.runOnMainSync {
                        linked = app.browserStore.state.tabs
                            .firstOrNull { it.id == tabId }
                            ?.engineState
                            ?.engineSession != null
                    }
                    linked
                },
            )
            assertTrue(
                "Fresh Gecko callbacks reach MiniBrowser's UI compatibility wrapper",
                waitUntil(LINK_TIMEOUT_MS) {
                    app.uiCompatibilityState.snapshot(tabId) != null
                },
            )

            instrumentation.runOnMainSync {
                val linkedSession = app.browserStore.state.tabs
                    .first { it.id == tabId }
                    .engineState
                    .engineSession
                assertNotNull("Linked BrowserStore tab keeps its EngineSession", linkedSession)
                assertTrue(
                    "Fresh A-C session keeps MiniBrowser's suspend-media policy",
                    linkedSession!!.settings.suspendMediaWhenInactive,
                )
            }
        } finally {
            instrumentation.runOnMainSync {
                removeStoreTab(app, tabId)
            }
        }
    }

    private fun removeStoreTab(app: BrowserApp, tabId: String) {
        if (app.browserStore.state.tabs.any { it.id == tabId }) {
            app.browserStore.dispatch(TabListAction.RemoveTabAction(tabId))
        }
        app.uiCompatibilityState.remove(tabId)
        app.sessionStatePersistence.remove(tabId)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return condition()
    }

    private companion object {
        const val LINK_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
