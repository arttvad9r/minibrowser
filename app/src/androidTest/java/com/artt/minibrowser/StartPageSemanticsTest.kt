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
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.StartPage
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
                        Bookmark(
                            url = "https://example.com",
                            title = title,
                            host = "example.com",
                            position = 0,
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
    fun showAllHistoryKeepsMinimumTouchTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showAllHistory = context.getString(R.string.show_all_history)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                StartPage(
                    bookmarks = emptyList(),
                    iconsDir = File(context.cacheDir, "test-icons"),
                    recent = listOf(
                        HistoryEntry(
                            url = "https://example.com",
                            title = "Example",
                            visitedAt = 1L,
                            visits = 1,
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
            .onNodeWithText(showAllHistory)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }
}
