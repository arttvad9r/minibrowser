package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.BookmarksRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PageBookmarkOperation { Load, Toggle }

internal data class PageBookmarkUiState(
    val url: String? = null,
    val isBookmarked: Boolean = false,
    val isLoading: Boolean = false,
    val error: PageBookmarkOperation? = null,
)

/** Owns bookmark persistence state for the currently visible web page in browser chrome. */
internal class PageBookmarkViewModel : ViewModel {
    private val checkBookmarked: suspend (String) -> Boolean
    private val addBookmark: suspend (String, String) -> Unit
    private val removeBookmark: suspend (String) -> Unit
    private var syncJob: Job? = null
    private var toggleJob: Job? = null

    constructor(repositoryProvider: () -> BookmarksRepository) : super() {
        // Keep Room lazy: creating this ViewModel must not resolve DbHolder.db. The repository is
        // materialized only after the first real HTTP(S) bookmark check or mutation.
        val lazyRepository = lazy(LazyThreadSafetyMode.SYNCHRONIZED, repositoryProvider)
        checkBookmarked = { url -> lazyRepository.value.isBookmarked(url) }
        addBookmark = { url, title -> lazyRepository.value.add(url, title) }
        removeBookmark = { url -> lazyRepository.value.remove(url) }
    }

    internal constructor(
        checkBookmarked: suspend (String) -> Boolean,
        addBookmark: suspend (String, String) -> Unit,
        removeBookmark: suspend (String) -> Unit,
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.checkBookmarked = checkBookmarked
        this.addBookmark = addBookmark
        this.removeBookmark = removeBookmark
    }

    private val _uiState = MutableStateFlow(PageBookmarkUiState())
    val uiState = _uiState.asStateFlow()

    fun sync(url: String?) {
        syncJob?.cancel()
        val target = url?.takeIf(::isBookmarkableUrl)
        if (target == null) {
            _uiState.value = PageBookmarkUiState()
            return
        }

        _uiState.value = PageBookmarkUiState(url = target, isLoading = true)
        syncJob = viewModelScope.launch {
            try {
                val bookmarked = checkBookmarked(target)
                if (_uiState.value.url == target) {
                    _uiState.value = PageBookmarkUiState(
                        url = target,
                        isBookmarked = bookmarked,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (_uiState.value.url == target) {
                    _uiState.value = PageBookmarkUiState(
                        url = target,
                        error = PageBookmarkOperation.Load,
                    )
                }
            }
        }
    }

    fun toggle(url: String?, title: String?) {
        val target = url?.takeIf(::isBookmarkableUrl) ?: return
        if (toggleJob?.isActive == true) return

        syncJob?.cancel()
        val previous = _uiState.value
        val knownState = previous.url == target && !previous.isLoading && previous.error == null
        _uiState.value = PageBookmarkUiState(
            url = target,
            isBookmarked = if (previous.url == target) previous.isBookmarked else false,
            isLoading = true,
        )

        toggleJob = viewModelScope.launch {
            try {
                val wasBookmarked = if (knownState) previous.isBookmarked else checkBookmarked(target)
                if (wasBookmarked) {
                    removeBookmark(target)
                } else {
                    addBookmark(target, title.orEmpty())
                }
                if (_uiState.value.url == target) {
                    _uiState.value = PageBookmarkUiState(
                        url = target,
                        isBookmarked = !wasBookmarked,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (_uiState.value.url == target) {
                    _uiState.value = PageBookmarkUiState(
                        url = target,
                        isBookmarked = if (previous.url == target) previous.isBookmarked else false,
                        error = PageBookmarkOperation.Toggle,
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        fun factory(repositoryProvider: () -> BookmarksRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { PageBookmarkViewModel(repositoryProvider) }
        }
    }
}

private fun isBookmarkableUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
