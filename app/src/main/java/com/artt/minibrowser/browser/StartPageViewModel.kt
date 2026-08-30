package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class StartPageOperation { Load, RefreshRecent, Add, Rename, Delete }

internal data class StartPageUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val recent: List<HistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: StartPageOperation? = null,
)

internal class StartPageViewModel : ViewModel {
    private val loadBookmarks: suspend () -> List<Bookmark>
    private val loadRecent: suspend () -> List<HistoryEntry>
    private val addBookmark: suspend (String, String) -> Unit
    private val renameBookmark: suspend (String, String) -> Unit
    private val deleteBookmark: suspend (String) -> Unit
    private val operationMutex = Mutex()

    constructor(
        bookmarksRepository: BookmarksRepository,
        historyRepository: HistoryRepository,
    ) : super() {
        loadBookmarks = bookmarksRepository::all
        loadRecent = { historyRepository.recent(3) }
        addBookmark = bookmarksRepository::add
        renameBookmark = bookmarksRepository::rename
        deleteBookmark = bookmarksRepository::remove
    }

    internal constructor(
        loadBookmarks: suspend () -> List<Bookmark>,
        loadRecent: suspend () -> List<HistoryEntry>,
        addBookmark: suspend (String, String) -> Unit,
        renameBookmark: suspend (String, String) -> Unit,
        deleteBookmark: suspend (String) -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.loadBookmarks = loadBookmarks
        this.loadRecent = loadRecent
        this.addBookmark = addBookmark
        this.renameBookmark = renameBookmark
        this.deleteBookmark = deleteBookmark
    }

    private val _uiState = MutableStateFlow(StartPageUiState())
    val uiState = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            operationMutex.withLock {
                val previous = _uiState.value
                _uiState.value = previous.copy(
                    isLoading = previous.bookmarks.isEmpty() && previous.recent.isEmpty(),
                    error = null,
                )
                try {
                    val bookmarks = loadBookmarks()
                    val recent = loadRecent()
                    _uiState.value = StartPageUiState(
                        bookmarks = bookmarks,
                        recent = recent,
                        isLoading = false,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    _uiState.value = previous.copy(isLoading = false, error = StartPageOperation.Load)
                }
            }
        }
    }

    fun refreshRecent() {
        viewModelScope.launch {
            operationMutex.withLock {
                val previous = _uiState.value
                _uiState.value = previous.copy(error = null)
                try {
                    val recent = loadRecent()
                    _uiState.value = previous.copy(
                        recent = recent,
                        isLoading = false,
                        error = null,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    _uiState.value = previous.copy(
                        isLoading = false,
                        error = StartPageOperation.RefreshRecent,
                    )
                }
            }
        }
    }

    fun add(url: String, title: String) = mutateBookmarks(StartPageOperation.Add) {
        addBookmark(url, title)
    }

    fun rename(url: String, title: String) = mutateBookmarks(StartPageOperation.Rename) {
        renameBookmark(url, title)
    }

    fun delete(url: String) = mutateBookmarks(StartPageOperation.Delete) {
        deleteBookmark(url)
    }

    fun retry() {
        when (_uiState.value.error) {
            StartPageOperation.Load -> refresh()
            StartPageOperation.RefreshRecent -> refreshRecent()
            StartPageOperation.Add, StartPageOperation.Rename, StartPageOperation.Delete, null -> dismissError()
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun mutateBookmarks(
        operation: StartPageOperation,
        mutation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            operationMutex.withLock {
                val previous = _uiState.value
                _uiState.value = previous.copy(error = null)
                try {
                    mutation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    _uiState.value = previous.copy(
                        isLoading = false,
                        error = operation,
                    )
                    return@withLock
                }

                try {
                    val bookmarks = loadBookmarks()
                    _uiState.value = previous.copy(
                        bookmarks = bookmarks,
                        isLoading = false,
                        error = null,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    _uiState.value = previous.copy(
                        isLoading = false,
                        error = StartPageOperation.Load,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            bookmarksRepository: BookmarksRepository,
            historyRepository: HistoryRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { StartPageViewModel(bookmarksRepository, historyRepository) }
        }
    }
}
