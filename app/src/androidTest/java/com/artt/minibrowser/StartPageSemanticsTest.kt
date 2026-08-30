package com.artt.minibrowser

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.StartPageBookmarkUiState
import com.artt.minibrowser.ui.StartPageRecentUiState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartPageSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStartPageExposesPrimaryActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appName = context.getString(R.string.app_name)
        val bookmarks = context.getString(R.string.bookmarks_title)
        val addBookmark = context.getString(R.string.add_bookmark_content_description)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                StartPage(
                    bookmarks = emptyList(),
                    iconsDir = File(context.cacheDir, "test-icons"),
                    recent = emptyList(),
                    isPrivate = false,
                    onOpen = {},
                    onAllBookmarks = {},
                    onAllHistory = {},
                    onRefreshRecent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onAdd = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(appName).assertIsDisplayed()
        composeRule.onNodeWithText(bookmarks).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(addBookmark)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun bookmarkTileHasAccessibleNameAndClickAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = "Example bookmark"

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                StartPage(
                    bookmarks = listOf(
                        StartPageBookmarkUiState(
                            url = "https://example.com",
                            title = title,
                            host = "example.com",
                        ),
                    ),
                    iconsDir = File(context.cacheDir, "test-icons"),
                    recent = emptyList(),
                    isPrivate = false,
                    onOpen = {},
                    onAllBookmarks = {},
                    onAllHistory = {},
                    onRefreshRecent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onAdd = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(title)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun recentActionsKeepMinimumTouchTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showAllHistory = context.getString(R.string.show_all_history)
        val recentTitle = "Example"

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                StartPage(
                    bookmarks = emptyList(),
                    iconsDir = File(context.cacheDir, "test-icons"),
                    recent = listOf(
                        StartPageRecentUiState(
                            url = "https://example.com",
                            title = recentTitle,
                        ),
                    ),
                    isPrivate = false,
                    onOpen = {},
                    onAllBookmarks = {},
                    onAllHistory = {},
                    onRefreshRecent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onAdd = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithText(recentTitle)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithText(showAllHistory)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }
}
