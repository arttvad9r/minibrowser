package com.artt.minibrowser

import com.artt.minibrowser.browser.StartPageOperation
import com.artt.minibrowser.browser.StartPageUiState
import com.artt.minibrowser.browser.StartPageViewModel
import com.artt.minibrowser.browser.normalizeBookmarkInputUrl
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.HistoryEntry
import kotlinx.coroutines.CompletableDeferred
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
    fun refreshFailurePreservesExistingContent() {
        val bookmark = Bookmark("https://example.com", "Example", "example.com", 1)
        val recent = listOf(HistoryEntry("https://recent.example", "Recent", 10L, 1))
        var fail = false
        val viewModel = startPageViewModel(
            loadBookmarks = {
                if (fail) error("read failed")
                listOf(bookmark)
            },
            loadRecent = { recent },
        )
        viewModel.refresh()
        fail = true

        viewModel.refresh()

        assertEquals(
            StartPageUiState(
                bookmarks = listOf(bookmark),
                recent = recent,
                isLoading = false,
                error = StartPageOperation.Load,
            ),
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
    fun bookmarkInputNormalizationPreservesValidSchemesAndAddsMissingHttps() {
        assertEquals("HTTPS://example.com/path", normalizeBookmarkInputUrl(" HTTPS://example.com/path "))
        assertEquals("http://example.com/path", normalizeBookmarkInputUrl("http://example.com/path"))
        assertEquals("https://example.com/path", normalizeBookmarkInputUrl(" example.com/path "))
        assertEquals("https://пример.рф/путь", normalizeBookmarkInputUrl("пример.рф/путь"))
        assertEquals(null, normalizeBookmarkInputUrl("https://"))
        assertEquals(null, normalizeBookmarkInputUrl("javascript:alert(1)"))
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
    fun addNormalizesHostOnlyInputBeforeRepository() {
        var addRequest: Pair<String, String>? = null
        val viewModel = startPageViewModel(
            addBookmark = { url, title -> addRequest = url to title },
        )
        viewModel.refresh()

        viewModel.add(" example.com/path ", "Example")

        assertEquals("https://example.com/path" to "Example", addRequest)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun invalidAddDoesNotReachRepository() {
        var addRequests = 0
        val viewModel = startPageViewModel(
            addBookmark = { _, _ -> addRequests++ },
        )
        viewModel.refresh()

        viewModel.add("https://", "Broken")

        assertEquals(0, addRequests)
        assertEquals(
            StartPageUiState(isLoading = false, error = StartPageOperation.Add),
            viewModel.uiState.value,
        )
    }

    @Test
    fun addDuringInitialRefreshWaitsAndPreservesRecentContent() {
        val gate = CompletableDeferred<Unit>()
        val recent = listOf(HistoryEntry("https://recent.example", "Recent", 10L, 1))
        val added = Bookmark("https://added.example", "Added", "added.example", 1)
        var bookmarks = emptyList<Bookmark>()
        var addRequest: Pair<String, String>? = null
        val viewModel = startPageViewModel(
            loadBookmarks = { bookmarks },
            loadRecent = {
                gate.await()
                recent
            },
            addBookmark = { url, title ->
                addRequest = url to title
                bookmarks = listOf(added)
            },
        )

        viewModel.refresh()
        viewModel.add(added.url, added.title)

        assertEquals(null, addRequest)
        assertEquals(StartPageUiState(), viewModel.uiState.value)

        gate.complete(Unit)

        assertEquals(added.url to added.title, addRequest)
        assertEquals(
            StartPageUiState(
                bookmarks = listOf(added),
                recent = recent,
                isLoading = false,
            ),
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

    @Test
    fun postMutationReloadFailureIsReportedAsLoadError() {
        val bookmark = Bookmark("https://example.com", "Example", "example.com", 1)
        val recent = listOf(HistoryEntry("https://recent.example", "Recent", 10L, 1))
        var bookmarkLoads = 0
        var deleted = false
        val viewModel = startPageViewModel(
            loadBookmarks = {
                bookmarkLoads++
                if (bookmarkLoads > 1) error("reload failed")
                listOf(bookmark)
            },
            loadRecent = { recent },
            deleteBookmark = { deleted = true },
        )
        viewModel.refresh()

        viewModel.delete(bookmark.url)

        assertEquals(true, deleted)
        assertEquals(
            StartPageUiState(
                bookmarks = listOf(bookmark),
                recent = recent,
                isLoading = false,
                error = StartPageOperation.Load,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun bookmarkMutationRunsAfterInFlightFullRefreshSoStaleSnapshotCannotWin() {
        val gate = CompletableDeferred<Unit>()
        val bookmark = Bookmark("https://example.com", "Example", "example.com", 1)
        val recent = listOf(HistoryEntry("https://recent.example", "Recent", 10L, 1))
        var bookmarkLoads = 0
        var recentLoads = 0
        var deleted = false
        val viewModel = startPageViewModel(
            loadBookmarks = {
                bookmarkLoads++
                if (bookmarkLoads < 3) listOf(bookmark) else emptyList()
            },
            loadRecent = {
                recentLoads++
                if (recentLoads == 2) gate.await()
                recent
            },
            deleteBookmark = { deleted = true },
        )
        viewModel.refresh()

        viewModel.refresh()
        assertEquals(2, bookmarkLoads)
        assertEquals(2, recentLoads)

        viewModel.delete(bookmark.url)
        assertEquals(false, deleted)

        gate.complete(Unit)

        assertEquals(true, deleted)
        assertEquals(3, bookmarkLoads)
        assertEquals(
            StartPageUiState(
                bookmarks = emptyList(),
                recent = recent,
                isLoading = false,
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
