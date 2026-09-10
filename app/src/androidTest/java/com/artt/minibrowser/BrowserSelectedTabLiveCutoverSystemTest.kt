package com.artt.minibrowser

import android.os.SystemClock
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import java.io.File
import mozilla.components.browser.state.action.TabListAction
import org.junit.AfterClass
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSelectedTabLiveCutoverSystemTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectingRawBackgroundTabTransfersItToAndroidComponents() {
        val app = targetApp()

        composeRule.waitUntil(LINK_TIMEOUT_MS) {
            val state = app.browserStore.state
            val selectedId = state.selectedTabId
            selectedId != null && state.tabs
                .firstOrNull { it.id == selectedId }
                ?.engineState
                ?.engineSession != null
        }
        val initialSelectedId = requireNotNull(app.browserStore.state.selectedTabId)
        val initialIds = app.browserStore.state.tabs.mapTo(mutableSetOf()) { it.id }

        composeRule.runOnUiThread {
            composeRule.activity.openBackgroundTab(BACKGROUND_URL, private = false)
        }

        composeRule.waitUntil(LINK_TIMEOUT_MS) {
            val state = app.browserStore.state
            state.selectedTabId == initialSelectedId &&
                state.tabs.any { tab -> tab.id !in initialIds && tab.content.url == BACKGROUND_URL }
        }
        val background = app.browserStore.state.tabs.single { tab ->
            tab.id !in initialIds && tab.content.url == BACKGROUND_URL
        }
        assertNull(
            "A background raw tab must stay raw until the user selects it",
            background.engineState.engineSession,
        )

        val tabCount = app.browserStore.state.tabs.size
        val tabsDescription = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.tabs_count, tabCount, tabCount)
        composeRule.onNodeWithContentDescription(tabsDescription).performClick()
        composeRule.mainClock.advanceTimeBy(SWITCHER_SETTLE_MS)
        composeRule.waitForIdle()
        val unselectedTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            .and(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        composeRule.onNode(unselectedTab).performClick()

        composeRule.waitUntil(LINK_TIMEOUT_MS) {
            val state = app.browserStore.state
            state.selectedTabId == background.id &&
                state.tabs.firstOrNull { it.id == background.id }
                    ?.engineState
                    ?.engineSession != null
        }
        assertTrue(
            "Selected raw background tab becomes linked to Android Components",
            app.browserStore.state.tabs.first { it.id == background.id }.engineState.engineSession != null,
        )
    }

    private companion object {
        const val BACKGROUND_URL = "about:blank#selected-cutover-test"
        const val LINK_TIMEOUT_MS = 10_000L
        const val SWITCHER_SETTLE_MS = 1_000L
        const val RESET_TIMEOUT_MS = 5_000L

        @JvmStatic
        @BeforeClass
        fun prepareIsolatedBrowserState() {
            resetBrowserState()
        }

        @JvmStatic
        @AfterClass
        fun clearIsolatedBrowserState() {
            resetBrowserState()
        }

        fun targetApp(): BrowserApp =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BrowserApp

        fun resetBrowserState() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = instrumentation.targetContext
            val app = targetContext.applicationContext as BrowserApp
            instrumentation.runOnMainSync {
                val tabIds = app.browserStore.state.tabs.map { it.id }
                if (tabIds.isNotEmpty()) {
                    app.browserStore.dispatch(TabListAction.RemoveTabsAction(tabIds))
                }
            }

            val deadline = SystemClock.uptimeMillis() + RESET_TIMEOUT_MS
            while (app.browserStore.state.tabs.isNotEmpty() && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(25L)
            }
            check(app.browserStore.state.tabs.isEmpty()) { "BrowserStore did not reset before test" }
            TabStore.saveState(File(targetContext.filesDir, "tabs"), PersistedBrowserState())
        }
    }
}
