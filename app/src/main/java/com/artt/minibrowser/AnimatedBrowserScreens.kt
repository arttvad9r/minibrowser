package com.artt.minibrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artt.minibrowser.browser.BookmarksOperation
import com.artt.minibrowser.browser.BookmarksUiState
import com.artt.minibrowser.browser.BookmarksViewModel
import com.artt.minibrowser.browser.HistoryOperation
import com.artt.minibrowser.browser.HistoryUiState
import com.artt.minibrowser.browser.HistoryViewModel
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.ui.BookmarkItemUiState
import com.artt.minibrowser.ui.BookmarksScreenContent
import com.artt.minibrowser.ui.BookmarksScreenOperation
import com.artt.minibrowser.ui.BookmarksScreenUiState
import com.artt.minibrowser.ui.HistoryItemUiState
import com.artt.minibrowser.ui.HistoryScreenContent
import com.artt.minibrowser.ui.HistoryScreenOperation
import com.artt.minibrowser.ui.HistoryScreenUiState
import java.io.File

/** History destination. Repository access is owned by the destination ViewModel, not the renderer. */
@Composable
internal fun MotionHistoryScreen(
    repo: HistoryRepository,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    // This destination only enters composition when History is open, so constructing the factory and
    // ViewModel here preserves lazy Room creation on the ordinary browser cold-start path.
    val factory = remember(repo) { HistoryViewModel.factory(repo) }
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val state by historyViewModel.uiState.collectAsStateWithLifecycle()

    // Re-read history whenever this destination is re-entered. Existing content stays visible while
    // the refresh runs, so returning to History does not flash an artificial loading state.
    LaunchedEffect(historyViewModel) { historyViewModel.refresh() }

    HistoryScreenContent(
        state = state.toScreenUiState(),
        iconsDir = iconsDir,
        onBack = onBack,
        onOpen = onOpen,
        onClear = historyViewModel::clear,
        onRetry = historyViewModel::retry,
    )
}

/** Bookmarks destination. Repository access and mutations are owned by the destination ViewModel. */
@Composable
internal fun MotionBookmarksScreen(
    repo: BookmarksRepository,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val factory = remember(repo) { BookmarksViewModel.factory(repo) }
    val bookmarksViewModel: BookmarksViewModel = viewModel(factory = factory)
    val state by bookmarksViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookmarksViewModel) { bookmarksViewModel.refresh() }

    BookmarksScreenContent(
        state = BookmarksScreenUiState(
            bookmarks = state.bookmarks.map { bookmark ->
                BookmarkItemUiState(
                    url = bookmark.url,
                    title = bookmark.title,
                    host = bookmark.host,
                )
            },
            isLoading = state.isLoading,
            error = state.error?.toScreenOperation(),
        ),
        iconsDir = iconsDir,
        onBack = onBack,
        onOpen = onOpen,
        onRename = bookmarksViewModel::rename,
        onDelete = bookmarksViewModel::delete,
        onRetryLoad = bookmarksViewModel::retryLoad,
        onDismissError = bookmarksViewModel::dismissError,
    )
}

private fun HistoryUiState.toScreenUiState(): HistoryScreenUiState = when (this) {
    HistoryUiState.Loading -> HistoryScreenUiState.Loading
    HistoryUiState.Empty -> HistoryScreenUiState.Empty
    is HistoryUiState.Error -> HistoryScreenUiState.Error(operation.toScreenOperation())
    is HistoryUiState.Content -> HistoryScreenUiState.Content(
        entries = entries.map { entry ->
            HistoryItemUiState(
                url = entry.url,
                title = entry.title,
                visitedAt = entry.visitedAt,
            )
        },
        error = error?.toScreenOperation(),
    )
}

private fun HistoryOperation.toScreenOperation(): HistoryScreenOperation = when (this) {
    HistoryOperation.Load -> HistoryScreenOperation.Load
    HistoryOperation.Clear -> HistoryScreenOperation.Clear
}

private fun BookmarksOperation.toScreenOperation(): BookmarksScreenOperation = when (this) {
    BookmarksOperation.Load -> BookmarksScreenOperation.Load
    BookmarksOperation.Rename -> BookmarksScreenOperation.Rename
    BookmarksOperation.Delete -> BookmarksScreenOperation.Delete
}
