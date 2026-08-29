package com.artt.minibrowser

import com.artt.minibrowser.browser.PageBookmarkOperation
import com.artt.minibrowser.browser.PageBookmarkUiState
import com.artt.minibrowser.browser.PageBookmarkViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals

class PageBookmarkViewModelTest {
    @Test
    fun productionProviderStaysLazyForNonWebUrl() {
        var resolutions = 0
        val viewModel = PageBookmarkViewModel(
            repositoryProvider = {
                resolutions++
                error("repository should stay lazy")
            },
        )

        viewModel.sync("about:blank")

        assertEquals(0, resolutions)
        assertEquals(PageBookmarkUiState(), viewModel.uiState.value)
    }

    @Test
    fun nonWebUrlDoesNotCheckStorage() {
        var checks = 0
        val viewModel = pageBookmarkViewModel(
            checkBookmarked = {
                checks++
                true
            },
        )

        viewModel.sync("about:blank")

        assertEquals(0, checks)
        assertEquals(PageBookmarkUiState(), viewModel.uiState.value)
    }

    @Test
    fun syncPublishesBookmarkedState() {
        val viewModel = pageBookmarkViewModel(checkBookmarked = { true })

        viewModel.sync("https://example.com")

        assertEquals(
            PageBookmarkUiState(url = "https://example.com", isBookmarked = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun syncPublishesUnbookmarkedState() {
        val viewModel = pageBookmarkViewModel(checkBookmarked = { false })

        viewModel.sync("http://example.com")

        assertEquals(PageBookmarkUiState(url = "http://example.com"), viewModel.uiState.value)
    }

    @Test
    fun syncFailurePublishesLoadError() {
        val viewModel = pageBookmarkViewModel(checkBookmarked = { error("read failed") })

        viewModel.sync("https://example.com")

        assertEquals(
            PageBookmarkUiState(url = "https://example.com", error = PageBookmarkOperation.Load),
            viewModel.uiState.value,
        )
    }

    @Test
    fun toggleAddsUnbookmarkedPage() {
        var added: Pair<String, String>? = null
        val viewModel = pageBookmarkViewModel(
            checkBookmarked = { false },
            addBookmark = { url, title -> added = url to title },
        )
        viewModel.sync("https://example.com")

        viewModel.toggle("https://example.com", "Example")

        assertEquals("https://example.com" to "Example", added)
        assertEquals(
            PageBookmarkUiState(url = "https://example.com", isBookmarked = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun toggleRemovesBookmarkedPage() {
        var removed: String? = null
        val viewModel = pageBookmarkViewModel(
            checkBookmarked = { true },
            removeBookmark = { removed = it },
        )
        viewModel.sync("https://example.com")

        viewModel.toggle("https://example.com", "Example")

        assertEquals("https://example.com", removed)
        assertEquals(PageBookmarkUiState(url = "https://example.com"), viewModel.uiState.value)
    }

    @Test
    fun toggleFailurePreservesKnownState() {
        val viewModel = pageBookmarkViewModel(
            checkBookmarked = { true },
            removeBookmark = { error("delete failed") },
        )
        viewModel.sync("https://example.com")

        viewModel.toggle("https://example.com", "Example")

        assertEquals(
            PageBookmarkUiState(
                url = "https://example.com",
                isBookmarked = true,
                error = PageBookmarkOperation.Toggle,
            ),
            viewModel.uiState.value,
        )
    }

    private fun pageBookmarkViewModel(
        checkBookmarked: suspend (String) -> Boolean = { false },
        addBookmark: suspend (String, String) -> Unit = { _, _ -> },
        removeBookmark: suspend (String) -> Unit = {},
    ): PageBookmarkViewModel = PageBookmarkViewModel(
        checkBookmarked = checkBookmarked,
        addBookmark = addBookmark,
        removeBookmark = removeBookmark,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
