package com.artt.minibrowser

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.TopBar
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuUsesMinimumTouchTargetsAndRealSwitchSemantics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menuDescription = context.getString(R.string.menu_content_description)
        val backDescription = context.getString(R.string.action_back)
        val adblockLabel = context.getString(R.string.settings_adblock)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                TopBar(
                    state = BrowserChromeUiState(
                        url = "https://example.com",
                        isWebPage = true,
                        canGoBack = true,
                    ),
                    tabCount = 1,
                    bookmarked = false,
                    iconsDir = File(context.cacheDir, "menu-semantics-icons"),
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
                    onDownloads = {},
                    onSettings = {},
                    onTranslate = {},
                    onToggleDesktop = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(menuDescription).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(backDescription)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithText(adblockLabel)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                ),
            )
    }
}