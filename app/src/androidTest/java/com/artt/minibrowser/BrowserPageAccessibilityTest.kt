package com.artt.minibrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isHiddenFromAccessibility
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.BrowserPageActions
import com.artt.minibrowser.ui.BrowserPageContent
import com.artt.minibrowser.ui.BrowserPageLoadErrorUiState
import com.artt.minibrowser.ui.BrowserPageUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPageAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun occludedBrowserPageIsHiddenFromAccessibility() {
        render(hidden = true)

        composeRule.onNodeWithTag(CHILD_TAG, useUnmergedTree = true)
            .assert(hasAnyAncestor(isHiddenFromAccessibility()))
    }

    @Test
    fun visibleBrowserPageRemainsAccessible() {
        render(hidden = false)

        composeRule.onNodeWithTag(CHILD_TAG, useUnmergedTree = true)
            .assert(!hasAnyAncestor(isHiddenFromAccessibility()))
    }

    @Test
    fun fullScreenDestinationExposesPaneTitle() {
        val title = "History"

        composeRule.setContent {
            Box(Modifier.accessibilityPane(title)) {
                Text("Destination", Modifier.testTag(PANE_CHILD_TAG))
            }
        }

        composeRule.onNodeWithTag(PANE_CHILD_TAG, useUnmergedTree = true)
            .assert(
                hasAnyAncestor(
                    SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title),
                ),
            )
    }

    @Test
    fun startPageHidesUnderlyingBrowserContentFromAccessibility() {
        renderBrowserPage(showStart = true)

        composeRule.onNodeWithTag(WEB_CONTENT_TAG, useUnmergedTree = true)
            .assert(hasAnyAncestor(isHiddenFromAccessibility()))
    }

    @Test
    fun loadErrorHidesUnderlyingBrowserContentFromAccessibility() {
        renderBrowserPage(loadError = BrowserPageLoadErrorUiState.Generic)

        composeRule.onNodeWithTag(WEB_CONTENT_TAG, useUnmergedTree = true)
            .assert(hasAnyAncestor(isHiddenFromAccessibility()))
    }

    @Test
    fun unoccludedBrowserContentRemainsAccessible() {
        renderBrowserPage()

        composeRule.onNodeWithTag(WEB_CONTENT_TAG, useUnmergedTree = true)
            .assert(!hasAnyAncestor(isHiddenFromAccessibility()))
    }

    private fun render(hidden: Boolean) {
        composeRule.setContent {
            Box(Modifier.hideFromAccessibilityWhen(hidden)) {
                Text("Browser page", Modifier.testTag(CHILD_TAG))
            }
        }
    }

    private fun renderBrowserPage(
        showStart: Boolean = false,
        loadError: BrowserPageLoadErrorUiState? = null,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BrowserPageContent(
                    state = BrowserPageUiState(
                        chrome = BrowserChromeUiState(),
                        tabCount = 1,
                        bookmarked = false,
                        suggestions = emptyList(),
                        adblockStatus = BrowserExtensionUiState.Disabled,
                        showFind = false,
                        showStart = showStart,
                        inFullscreen = true,
                        loadError = loadError,
                    ),
                    actions = NO_OP_ACTIONS,
                    iconsDir = File(context.cacheDir, "test-icons"),
                    browserContent = {
                        Text("Web content", Modifier.testTag(WEB_CONTENT_TAG))
                    },
                    findContent = null,
                    startPageContent = {
                        Box(Modifier.fillMaxSize()) {
                            Text("Start page")
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val CHILD_TAG = "browser-page-child"
        const val PANE_CHILD_TAG = "browser-pane-child"
        const val WEB_CONTENT_TAG = "web-content-child"

        val NO_OP_ACTIONS = BrowserPageActions(
            onSuggestionQueryChanged = {},
            onSubmitQuery = {},
            onNavigate = {},
            onBack = {},
            onForward = {},
            onReload = {},
            onSiteInfo = {},
            onSwitcher = {},
            onNewTab = {},
            onNewPrivateTab = {},
            onFind = {},
            onCloseFind = {},
            onToggleBookmark = {},
            onBookmarks = {},
            onHistory = {},
            onShare = {},
            onSettings = {},
            onToggleAdblock = { _ -> },
            onRetryAdblock = {},
            onTranslate = {},
            onToggleDesktop = {},
        )
    }
}
