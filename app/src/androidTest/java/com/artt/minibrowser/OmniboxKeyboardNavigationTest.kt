package com.artt.minibrowser

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.BrowserSuggestionUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.TopBar
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OmniboxKeyboardNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directionDownSelectsSuggestionsAndEnterNavigates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suggestions = listOf(
            BrowserSuggestionUiState("First", "https://first.example"),
            BrowserSuggestionUiState("Second", "https://second.example"),
        )
        var navigatedTo: String? = null

        composeRule.setContent {
            TestTopBar(context, suggestions) { navigatedTo = it }
        }

        val omnibox = focusOmnibox(context)
        omnibox.performKeyInput { pressKey(Key.DirectionDown) }
        omnibox.performKeyInput { pressKey(Key.DirectionDown) }
        omnibox.performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle {
            assertEquals(suggestions[1].url, navigatedTo)
        }
    }

    @Test
    fun directionUpWrapsToLastSuggestionAndEnterNavigates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suggestions = (1..8).map { index ->
            BrowserSuggestionUiState(
                label = "Suggestion $index",
                url = "https://example.com/$index",
            )
        }
        var navigatedTo: String? = null

        composeRule.setContent {
            TestTopBar(context, suggestions) { navigatedTo = it }
        }

        val omnibox = focusOmnibox(context)
        omnibox.performKeyInput { pressKey(Key.DirectionUp) }
        omnibox.performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle {
            assertEquals(suggestions.last().url, navigatedTo)
        }
    }

    @Test
    fun escapeDismissesSuggestionsWithoutNavigating() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suggestion = BrowserSuggestionUiState("First", "https://first.example")
        var navigatedTo: String? = null

        composeRule.setContent {
            TestTopBar(context, listOf(suggestion)) { navigatedTo = it }
        }

        val omnibox = focusOmnibox(context)
        composeRule.onNodeWithText(suggestion.label).assertIsDisplayed()

        omnibox.performKeyInput { pressKey(Key.Escape) }

        composeRule.onNodeWithText(suggestion.label).assertDoesNotExist()
        composeRule.runOnIdle {
            assertNull(navigatedTo)
        }
    }

    private fun focusOmnibox(context: Context) =
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.search_content_description),
            )
            .performClick()
            .let {
                composeRule
                    .onNodeWithContentDescription(
                        context.getString(R.string.omnibox_hint),
                    )
                    .assertIsFocused()
            }
}

@Composable
private fun TestTopBar(
    context: Context,
    suggestions: List<BrowserSuggestionUiState>,
    onNavigate: (String) -> Unit,
) {
    MinibrowserTheme(darkTheme = false) {
        TopBar(
            state = BrowserChromeUiState(url = "about:blank"),
            tabCount = 1,
            bookmarked = false,
            iconsDir = File(context.cacheDir, "test-icons"),
            omniboxFocus = remember { FocusRequester() },
            suggestions = suggestions,
            onSuggestionQueryChanged = {},
            onSubmitQuery = {},
            adblockStatus = BrowserExtensionUiState.Disabled,
            onToggleAdblock = {},
            onRetryAdblock = {},
            onNavigate = onNavigate,
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
