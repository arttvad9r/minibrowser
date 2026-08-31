package com.artt.minibrowser

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BookmarkItemUiState
import com.artt.minibrowser.ui.BookmarksScreenContent
import com.artt.minibrowser.ui.BookmarksScreenOperation
import com.artt.minibrowser.ui.BookmarksScreenUiState
import com.artt.minibrowser.ui.HistoryItemUiState
import com.artt.minibrowser.ui.HistoryScreenContent
import com.artt.minibrowser.ui.HistoryScreenOperation
import com.artt.minibrowser.ui.HistoryScreenUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentErrorLiveRegionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyInitialLoadFailureIsPoliteErrorWithSeparateRetryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.history_load_error)
        val hint = context.getString(R.string.retry_hint)
        val retry = context.getString(R.string.action_retry)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                HistoryScreenContent(
                    state = HistoryScreenUiState.Error(HistoryScreenOperation.Load),
                    iconsDir = File(context.cacheDir, "history-initial-error-icons"),
                    onBack = {},
                    onOpen = {},
                    onClear = {},
                    onRetry = {},
                )
            }
        }

        assertPoliteError(message, hint)
        composeRule.onNodeWithText(retry).assertHasClickAction()
    }

    @Test
    fun bookmarkInitialLoadFailureIsPoliteErrorWithSeparateRetryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.bookmarks_load_error)
        val hint = context.getString(R.string.retry_hint)
        val retry = context.getString(R.string.action_retry)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BookmarksScreenContent(
                    state = BookmarksScreenUiState(
                        bookmarks = emptyList(),
                        isLoading = false,
                        error = BookmarksScreenOperation.Load,
                    ),
                    iconsDir = File(context.cacheDir, "bookmarks-initial-error-icons"),
                    onBack = {},
                    onOpen = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onRetryLoad = {},
                    onDismissError = {},
                )
            }
        }

        assertPoliteError(message, hint)
        composeRule.onNodeWithText(retry).assertHasClickAction()
    }

    @Test
    fun historyContentFailureIsPoliteLiveRegion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.history_clear_error)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                HistoryScreenContent(
                    state = HistoryScreenUiState.Content(
                        entries = listOf(
                            HistoryItemUiState(
                                url = "https://example.com",
                                title = "Example",
                                visitedAt = System.currentTimeMillis(),
                            ),
                        ),
                        error = HistoryScreenOperation.Clear,
                    ),
                    iconsDir = File(context.cacheDir, "history-live-region-icons"),
                    onBack = {},
                    onOpen = {},
                    onClear = {},
                    onRetry = {},
                )
            }
        }

        assertPoliteLiveRegion(message)
    }

    @Test
    fun bookmarkContentFailureIsPoliteLiveRegion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.bookmark_rename_error)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BookmarksScreenContent(
                    state = BookmarksScreenUiState(
                        bookmarks = listOf(
                            BookmarkItemUiState(
                                url = "https://example.com",
                                title = "Example",
                                host = "example.com",
                            ),
                        ),
                        isLoading = false,
                        error = BookmarksScreenOperation.Rename,
                    ),
                    iconsDir = File(context.cacheDir, "bookmark-live-region-icons"),
                    onBack = {},
                    onOpen = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onRetryLoad = {},
                    onDismissError = {},
                )
            }
        }

        assertPoliteLiveRegion(message)
    }

    private fun assertPoliteError(text: String, hint: String) {
        composeRule.onNodeWithText(text)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    hint,
                ),
            )
    }

    private fun assertPoliteLiveRegion(text: String) {
        composeRule.onNodeWithText(text)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }
}
