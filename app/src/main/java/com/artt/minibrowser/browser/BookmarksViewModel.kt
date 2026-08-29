package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.BookmarksRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BookmarksOperation { Load, Rename, Delete }

internal data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = true,
    val error: BookmarksOperation? = null,
)

internal class BookmarksViewModel : ViewModel {
    private val loadBookmarks: suspend () -> List<Bookmark>
    private val renameBookmark: suspend (String, String) -> Unit
    private val deleteBookmark: suspend (String) -> Unit

    constructor(repository: BookmarksRepository) : super() {
        loadBookmarks = repository::all
        renameBookmark = repository::rename
        deleteBookmark = repository::remove
    }

    internal constructor(
        loadBookmarks: suspend () -> List<Bookmark>,
        renameBookmark: suspend (String, String) -> Unit,
        deleteBookmark: suspend (String) -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.loadBookmarks = loadBookmarks
        this.renameBookmark = renameBookmark
        this.deleteBookmark = deleteBookmark
    }

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = previous.copy(
                isLoading = previous.bookmarks.isEmpty(),
                error = null,
            )
            try {
                publishBookmarks(loadBookmarks())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                publishLoadFailure(previous)
            }
        }
    }

    fun rename(url: String, title: String) {
        viewModelScope.launch {
            try {
                renameBookmark(url, title)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                publishMutationFailure(BookmarksOperation.Rename)
                return@launch
            }
            reloadAfterMutation()
        }
    }

    fun delete(url: String) {
        viewModelScope.launch {
            try {
                deleteBookmark(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                publishMutationFailure(BookmarksOperation.Delete)
                return@launch
            }
            reloadAfterMutation()
        }
    }

    fun retryLoad() = refresh()

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun reloadAfterMutation() {
        val previous = _uiState.value
        try {
            publishBookmarks(loadBookmarks())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            publishLoadFailure(previous)
        }
    }

    private fun publishBookmarks(bookmarks: List<Bookmark>) {
        _uiState.value = BookmarksUiState(bookmarks = bookmarks, isLoading = false)
    }

    private fun publishLoadFailure(previous: BookmarksUiState) {
        _uiState.value = previous.copy(isLoading = false, error = BookmarksOperation.Load)
    }

    private fun publishMutationFailure(operation: BookmarksOperation) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = operation)
    }

    companion object {
        fun factory(repository: BookmarksRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { BookmarksViewModel(repository) }
        }
    }
}
