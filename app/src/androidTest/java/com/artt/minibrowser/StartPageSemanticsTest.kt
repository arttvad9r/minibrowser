package com.artt.minibrowser

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
        val showAllBookmarks = context.getString(R.string.show_all_bookmarks_content_description)
        val addBookmark = context.getString(R.string.add_bookmark_content_description)
        val heading = SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit)

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
        composeRule.onNodeWithText(bookmarks)
            .assertIsDisplayed()
            .assert(heading)
        composeRule
            .onNodeWithContentDescription(showAllBookmarks)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule
            .onNodeWithContentDescription(addBookmark)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun bookmarkTileHasAccessibleNameAndLabeledActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = "Example bookmark"
        val actionsDescription = context.getString(
            R.string.bookmark_actions_named_content_description,
            title,
        )
        val hasNamedLongClick = SemanticsMatcher("long click opens named bookmark actions") { node ->
            node.config.getOrNull(SemanticsActions.OnLongClick)?.label == actionsDescription
        }

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

        val bookmarkTile = composeRule
            .onNodeWithContentDescription(title)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(hasNamedLongClick)

        bookmarkTile.performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule
            .onNodeWithText(context.getString(R.string.action_rename))
            .assertIsDisplayed()
    }

    @Test
    fun recentActionsKeepMinimumTouchTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showAllHistory = context.getString(R.string.show_all_history)
        val recentHeading = context.getString(R.string.recent_title)
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

        val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val heading = SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit)
        composeRule.onNodeWithText(recentHeading)
            .assertIsDisplayed()
            .assert(heading)
        composeRule
            .onNodeWithText(recentTitle)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(buttonRole)
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithText(showAllHistory)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(buttonRole)
            .assertHeightIsAtLeast(48.dp)
    }
}
