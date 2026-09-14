package com.artt.minibrowser

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedSessionOwner
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import java.io.File
import mozilla.components.browser.state.action.TabListAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {
    @Before
    fun resetBrowserStateBeforeTest() {
        resetBrowserState()
    }

    @After
    fun resetBrowserStateAfterTest() {
        resetBrowserState()
    }

    @Test
    fun launchesToResumedState() {
        // Smoke the real Application + MainActivity + initial Compose/Gecko wiring. Performance
        // thresholds belong to the benchmark source set; this test only guards startup correctness.
        launchMainActivity().use { scenario ->
            assertResumed(scenario)
        }
    }

    @Test
    fun recreatesToResumedState() {
        // Keep a real Gecko-backed browser host usable across Activity recreation. This exercises
        // exact in-process ownership handoff against the same application-owned BrowserStore and
        // GeckoRuntime rather than relying on a synthetic state-holder test.
        launchMainActivity().use { scenario ->
            scenario.recreate()
            assertResumed(scenario)
        }
    }

    @Test
    fun freshStartupTabTransfersToAndroidComponentsAndPersistsOwnership() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val app = targetContext.applicationContext as BrowserApp
        val tabsDir = File(targetContext.filesDir, "tabs")

        launchMainActivity().use { scenario ->
            assertResumed(scenario)
            assertTrue(
                "Fresh startup tab becomes a linked Android Components session",
                waitUntil(LINK_TIMEOUT_MS) {
                    var linked = false
                    instrumentation.runOnMainSync {
                        val selectedId = app.browserStore.state.selectedTabId
                        linked = selectedId != null && app.browserStore.state.tabs
                            .firstOrNull { it.id == selectedId }
                            ?.engineState
                            ?.engineSession != null
                    }
                    linked
                },
            )
        }

        val persisted = TabStore.loadState(tabsDir)
        assertEquals(1, persisted.tabs.size)
        assertEquals(PersistedSessionOwner.AndroidComponents, persisted.tabs.single().sessionOwner)
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent.makeMainActivity(ComponentName(targetContext, MainActivity::class.java))
        return ActivityScenario.launch(intent)
    }

    private fun assertResumed(scenario: ActivityScenario<MainActivity>) {
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertFalse(activity.isDestroyed)
        }
    }

    private fun resetBrowserState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val app = targetContext.applicationContext as BrowserApp
        clearBrowserStore(instrumentation, app)
        // TabStore serializes this write behind any final Activity snapshot queued by the previous
        // scenario, so the next test starts from one deterministic empty persistent state.
        TabStore.saveState(File(targetContext.filesDir, "tabs"), PersistedBrowserState())
    }

    private fun clearBrowserStore(
        instrumentation: android.app.Instrumentation,
        app: BrowserApp,
    ) {
        instrumentation.runOnMainSync {
            val tabIds = app.browserStore.state.tabs.map { it.id }
            if (tabIds.isNotEmpty()) {
                app.browserStore.dispatch(TabListAction.RemoveTabsAction(tabIds))
            }
        }
        assertTrue(
            "BrowserStore clears before startup test isolation continues",
            waitUntil(LINK_TIMEOUT_MS) { app.browserStore.state.tabs.isEmpty() },
        )
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
