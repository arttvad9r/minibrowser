package com.artt.minibrowser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.TopBar
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuKeepsFourQuickActionsSymmetricAndBottomActionReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menuDescription = context.getString(R.string.menu_content_description)
        val newTabLabel = context.getString(R.string.new_tab_title)
        val quickActionLabels = listOf(
            context.getString(R.string.private_tab_title),
            context.getString(R.string.downloads_title),
            context.getString(R.string.history_title),
            context.getString(R.string.bookmarks_title),
        )
        val settingsLabel = context.getString(R.string.settings_title)
        val windowWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
        val symmetryTolerancePx = 4f * context.resources.displayMetrics.density

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
                        onDownloads = {},
                        onSettings = {},
                        onTranslate = {},
                        onToggleDesktop = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(menuDescription).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(newTabLabel).assertDoesNotExist()
        val quickActionBounds = quickActionLabels.map { label ->
            composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.also { bounds ->
                assertTrue("$label starts outside the window", bounds.left >= 0f)
                assertTrue("$label ends outside the window", bounds.right <= windowWidthPx)
            }
        }
        val firstTop = quickActionBounds.first().top
        val firstBottom = quickActionBounds.first().bottom
        quickActionBounds.drop(1).forEach { bounds ->
            assertTrue(
                "Quick actions should stay on one horizontal row",
                abs(bounds.top - firstTop) <= 2f,
            )
            assertTrue(
                "Quick action labels should reserve the same vertical space",
                abs(bounds.bottom - firstBottom) <= 2f,
            )
        }

        val centers = quickActionBounds.map { bounds -> (bounds.left + bounds.right) / 2f }
        val gaps = centers.zipWithNext { left, right -> right - left }
        assertTrue("Quick actions must preserve their requested order", gaps.all { it > 0f })
        val referenceGap = gaps.first()
        gaps.drop(1).forEach { gap ->
            assertTrue(
                "Quick action centers should be evenly spaced: $gaps",
                abs(gap - referenceGap) <= symmetryTolerancePx,
            )
        }
        assertTrue(
            "Quick action row should be horizontally symmetric: $centers",
            abs(centers.first() - (windowWidthPx - centers.last())) <= symmetryTolerancePx,
        )

        composeRule.onNode(
            hasScrollAction() and hasAnyDescendant(hasText(settingsLabel)),
        ).performScrollToNode(hasText(settingsLabel))
        composeRule.onNodeWithText(settingsLabel).assertIsDisplayed()
    }
}
