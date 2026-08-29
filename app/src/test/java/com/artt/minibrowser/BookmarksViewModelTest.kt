package com.artt.minibrowser

import com.artt.minibrowser.browser.BookmarksOperation
import com.artt.minibrowser.browser.BookmarksUiState
import com.artt.minibrowser.browser.BookmarksViewModel
import com.artt.minibrowser.data.Bookmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarksViewModelTest {
    @Test
    fun initialStateIsLoading() {
        val viewModel = bookmarksViewModel()

        assertEquals(BookmarksUiState(), viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesBookmarks() {
        val bookmarks = listOf(
            Bookmark("https://example.com", "Example", "example.com", 1),
            Bookmark("https://example.org", "Example Org", "example.org", 2),
        )
        val viewModel = bookmarksViewModel(loadBookmarks = { bookmarks })

        viewModel.refresh()

        assertEquals(BookmarksUiState(bookmarks = bookmarks, isLoading = false), viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesEmptyState() {
        val viewModel = bookmarksViewModel(loadBookmarks = { emptyList() })

        viewModel.refresh()

        assertEquals(BookmarksUiState(isLoading = false), viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesLoadError() {
        val viewModel = bookmarksViewModel(loadBookmarks = { error("read failed") })

        viewModel.refresh()

        assertEquals(
            BookmarksUiState(isLoading = false, error = BookmarksOperation.Load),
            viewModel.uiState.value,
        )
    }

    @Test
    fun renameReloadsBookmarks() {
        val updated = Bookmark("https://example.com", "Renamed", "example.com", 1)
        var renamed: Pair<String, String>? = null
        val viewModel = bookmarksViewModel(
            loadBookmarks = { listOf(updated) },
            renameBookmark = { url, title -> renamed = url to title },
        )

        viewModel.rename(updated.url, updated.title)

        assertEquals(updated.url to updated.title, renamed)
        assertEquals(BookmarksUiState(listOf(updated), isLoading = false), viewModel.uiState.value)
    }

    @Test
    fun deleteReloadsBookmarks() {
        var deleted: String? = null
        val viewModel = bookmarksViewModel(
            loadBookmarks = { emptyList() },
            deleteBookmark = { deleted = it },
        )

        viewModel.delete("https://example.com")

        assertEquals("https://example.com", deleted)
        assertEquals(BookmarksUiState(isLoading = false), viewModel.uiState.value)
    }

    @Test
    fun mutationFailurePreservesContentAndPublishesError() {
        val existing = Bookmark("https://example.com", "Example", "example.com", 1)
        val viewModel = bookmarksViewModel(
            loadBookmarks = { listOf(existing) },
            deleteBookmark = { error("delete failed") },
        )
        viewModel.refresh()

        viewModel.delete(existing.url)

        assertEquals(
            BookmarksUiState(
                bookmarks = listOf(existing),
                isLoading = false,
                error = BookmarksOperation.Delete,
            ),
            viewModel.uiState.value,
        )
    }

    private fun bookmarksViewModel(
        loadBookmarks: suspend () -> List<Bookmark> = { emptyList() },
        renameBookmark: suspend (String, String) -> Unit = { _, _ -> },
        deleteBookmark: suspend (String) -> Unit = {},
    ): BookmarksViewModel = BookmarksViewModel(
        loadBookmarks = loadBookmarks,
        renameBookmark = renameBookmark,
        deleteBookmark = deleteBookmark,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
