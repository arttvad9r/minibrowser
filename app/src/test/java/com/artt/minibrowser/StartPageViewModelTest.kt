package com.artt.minibrowser

import com.artt.minibrowser.browser.StartPageOperation
import com.artt.minibrowser.browser.StartPageUiState
import com.artt.minibrowser.browser.StartPageViewModel
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.HistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals

class StartPageViewModelTest {
    @Test
    fun initialStateIsLoading() {
        val viewModel = startPageViewModel()

        assertEquals(StartPageUiState(), viewModel.uiState.value)
    }

    @Test
    fun refreshPublishesBookmarksAndRecent() {
        val bookmarks = listOf(Bookmark("https://example.com", "Example", "example.com", 1))
        val recent = listOf(HistoryEntry("https://example.org", "Example Org", 10L, 1))
        val viewModel = startPageViewModel(
            loadBookmarks = { bookmarks },
            loadRecent = { recent },
        )

        viewModel.refresh()

        assertEquals(
            StartPageUiState(bookmarks = bookmarks, recent = recent, isLoading = false),
            viewModel.uiState.value,
        )
    }

    @Test
    fun refreshPublishesLoadError() {
        val viewModel = startPageViewModel(loadBookmarks = { error("read failed") })

        viewModel.refresh()

        assertEquals(
            StartPageUiState(isLoading = false, error = StartPageOperation.Load),
            viewModel.uiState.value,
        )
    }

    @Test
    fun refreshRecentPreservesBookmarks() {
        val bookmark = Bookmark("https://example.com", "Example", "example.com", 1)
        var recent = listOf(HistoryEntry("https://old.example", "Old", 5L, 1))
        val viewModel = startPageViewModel(
            loadBookmarks = { listOf(bookmark) },
            loadRecent = { recent },
        )
        viewModel.refresh()
        recent = listOf(HistoryEntry("https://new.example", "New", 10L, 1))

        viewModel.refreshRecent()

        assertEquals(
            StartPageUiState(
                bookmarks = listOf(bookmark),
                recent = recent,
                isLoading = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun addReloadsBookmarksWithoutReloadingRecent() {
        val recent = listOf(HistoryEntry("https://recent.example", "Recent", 10L, 1))
        val added = Bookmark("https://added.example", "Added", "added.example", 1)
        var bookmarks = emptyList<Bookmark>()
        var addRequest: Pair<String, String>? = null
        var recentLoads = 0
        val viewModel = startPageViewModel(
            loadBookmarks = { bookmarks },
            loadRecent = { recentLoads++; recent },
            addBookmark = { url, title ->
                addRequest = url to title
                bookmarks = listOf(added)
            },
        )
        viewModel.refresh()

        viewModel.add(added.url, added.title)

        assertEquals(added.url to added.title, addRequest)
        assertEquals(1, recentLoads)
        assertEquals(
            StartPageUiState(listOf(added), recent, isLoading = false),
            viewModel.uiState.value,
        )
    }

    @Test
    fun renameAndDeleteReloadBookmarks() {
        val original = Bookmark("https://example.com", "Original", "example.com", 1)
        val renamed = original.copy(title = "Renamed")
        var bookmarks = listOf(original)
        val viewModel = startPageViewModel(
            loadBookmarks = { bookmarks },
            renameBookmark = { _, title -> bookmarks = listOf(original.copy(title = title)) },
            deleteBookmark = { bookmarks = emptyList() },
        )
        viewModel.refresh()

        viewModel.rename(original.url, renamed.title)
        assertEquals(listOf(renamed), viewModel.uiState.value.bookmarks)

        viewModel.delete(original.url)
        assertEquals(emptyList(), viewModel.uiState.value.bookmarks)
    }

    @Test
    fun mutationFailurePreservesContentAndPublishesError() {
        val bookmark = Bookmark("https://example.com", "Example", "example.com", 1)
        val recent = listOf(HistoryEntry("https://recent.example", "Recent", 10L, 1))
        val viewModel = startPageViewModel(
            loadBookmarks = { listOf(bookmark) },
            loadRecent = { recent },
            deleteBookmark = { error("delete failed") },
        )
        viewModel.refresh()

        viewModel.delete(bookmark.url)

        assertEquals(
            StartPageUiState(
                bookmarks = listOf(bookmark),
                recent = recent,
                isLoading = false,
                error = StartPageOperation.Delete,
            ),
            viewModel.uiState.value,
        )
    }

    private fun startPageViewModel(
        loadBookmarks: suspend () -> List<Bookmark> = { emptyList() },
        loadRecent: suspend () -> List<HistoryEntry> = { emptyList() },
        addBookmark: suspend (String, String) -> Unit = { _, _ -> },
        renameBookmark: suspend (String, String) -> Unit = { _, _ -> },
        deleteBookmark: suspend (String) -> Unit = {},
    ): StartPageViewModel = StartPageViewModel(
        loadBookmarks = loadBookmarks,
        loadRecent = loadRecent,
        addBookmark = addBookmark,
        renameBookmark = renameBookmark,
        deleteBookmark = deleteBookmark,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
