package com.artt.minibrowser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.TopBar
import java.io.File
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuReflowsQuickActionsAndKeepsBottomActionReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menuDescription = context.getString(R.string.menu_content_description)
        val quickActionLabels = listOf(
            context.getString(R.string.new_tab_title),
            context.getString(R.string.private_tab_title),
            context.getString(R.string.bookmarks_title),
            context.getString(R.string.history_title),
        )
        val settingsLabel = context.getString(R.string.settings_title)

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MinibrowserTheme(darkTheme = false) {
                    TopBar(
                        state = BrowserChromeUiState(
                            url = "https://example.com",
                            isWebPage = true,
                        ),
                        tabCount = 1,
                        bookmarked = false,
                        iconsDir = File(context.cacheDir, "menu-large-text-icons"),
                        omniboxFocus = remember { FocusRequester() },
                        suggestions = emptyList(),
                        onSuggestionQueryChanged = {},
                        onSubmitQuery = {},
                        adblockStatus = BrowserExtensionUiState.Enabled,
                        onToggleAdblock = {},
                        onRetryAdblock = {},
                        onNavigate = {},
                        onBack = {},
                        onForward = {},
                        onReload = {},
                        onSiteInfo = {},
                        onSwitcher = {},
                        onNewTab = {},
                        onNewPrivateTab = {},
                        onFind = {},
                        onShare = {},
                        onToggleBookmark = {},
                        onBookmarks = {},
                        onHistory = {},
                        onSettings = {},
                        onTranslate = {},
                        onToggleDesktop = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(menuDescription).performClick()
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        quickActionLabels.forEach { label ->
            val bounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= rootBounds.left, "$label starts outside the window")
            assertTrue(bounds.right <= rootBounds.right, "$label ends outside the window")
        }

        composeRule.onNodeWithText(settingsLabel)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
