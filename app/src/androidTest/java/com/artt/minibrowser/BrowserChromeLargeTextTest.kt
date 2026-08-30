package com.artt.minibrowser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.BrowserSuggestionUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.TopBar
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserChromeLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun omniboxSuggestionRemainsUsableWithLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDescription = context.getString(R.string.search_content_description)
        val suggestionTitle = "Очень длинная подсказка из истории браузера"

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MinibrowserTheme(darkTheme = false) {
                    TopBar(
                        state = BrowserChromeUiState(url = "about:blank"),
                        tabCount = 1,
                        bookmarked = false,
                        iconsDir = File(context.cacheDir, "test-icons"),
                        omniboxFocus = remember { FocusRequester() },
                        suggestions = listOf(
                            BrowserSuggestionUiState(
                                label = suggestionTitle,
                                url = "https://example.com/page",
                            ),
                        ),
                        onSuggestionQueryChanged = {},
                        onSubmitQuery = {},
                        adblockStatus = BrowserExtensionUiState.Disabled,
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

        composeRule.onNodeWithContentDescription(searchDescription).performClick()
        composeRule
            .onNodeWithText(suggestionTitle)
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
