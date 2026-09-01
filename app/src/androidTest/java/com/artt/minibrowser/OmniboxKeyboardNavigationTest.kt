package com.artt.minibrowser

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
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

    @Test
    fun outsideTouchWhileSuggestionsVisibleKeepsOmniboxFocused() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suggestion = BrowserSuggestionUiState("First", "https://first.example")

        composeRule.setContent {
            TestTopBar(context, listOf(suggestion)) { }
        }

        val omnibox = focusOmnibox(context)
        composeRule.onNodeWithText(suggestion.label).assertIsDisplayed()

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            click()
        }

        omnibox.assertIsFocused()
        composeRule.onNodeWithText(suggestion.label).assertIsDisplayed()
    }

    @Test
    fun focusedOmniboxTakesChromeRowAndHidesAdjacentActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val newTabDescription = context.getString(R.string.new_tab_title)
        val menuDescription = context.getString(R.string.menu_content_description)
        val tabsDescription = context.resources.getQuantityString(R.plurals.tabs_count, 1, 1)

        composeRule.setContent {
            TestTopBar(context, emptyList()) { }
        }

        composeRule.onNodeWithContentDescription(newTabDescription).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(tabsDescription).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(menuDescription).assertIsDisplayed()

        focusOmnibox(context)

        composeRule.onNodeWithContentDescription(newTabDescription).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(tabsDescription).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(menuDescription).assertDoesNotExist()
    }

    @Test
    fun systemBackExitsOmniboxBeforeBrowserBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suggestion = BrowserSuggestionUiState("First", "https://first.example")
        var browserBackCount = 0
        var dispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            BackHandler(enabled = true) { browserBackCount++ }
            TestTopBar(context, listOf(suggestion)) { }
        }

        focusOmnibox(context)
        composeRule.onNodeWithText(suggestion.label).assertIsDisplayed()

        composeRule.runOnIdle {
            checkNotNull(dispatcher).onBackPressed()
        }

        composeRule.onNodeWithText(suggestion.label).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, browserBackCount)
            checkNotNull(dispatcher).onBackPressed()
            assertEquals(1, browserBackCount)
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

    private companion object {
        const val TEST_SURFACE_TAG = "omnibox-test-surface"
    }
}

@Composable
private fun TestTopBar(
    context: Context,
    suggestions: List<BrowserSuggestionUiState>,
    onNavigate: (String) -> Unit,
) {
    MinibrowserTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().testTag("omnibox-test-surface")) {
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
}
