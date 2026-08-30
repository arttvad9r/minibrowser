package com.artt.minibrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artt.minibrowser.browser.StartPageOperation
import com.artt.minibrowser.browser.StartPageViewModel
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.ui.StartPage
import java.io.File

/** Non-private Start page destination. Data access and mutations stay outside the renderer. */
@Composable
internal fun StartPageRoute(
    bookmarksRepository: BookmarksRepository,
    historyRepository: HistoryRepository,
    iconsDir: File,
    refreshKey: Any?,
    onOpen: (String) -> Unit,
    onAllBookmarks: () -> Unit,
    onAllHistory: () -> Unit,
) {
    val factory = remember(bookmarksRepository, historyRepository) {
        StartPageViewModel.factory(bookmarksRepository, historyRepository)
    }
    val startPageViewModel: StartPageViewModel = viewModel(factory = factory)
    val state by startPageViewModel.uiState.collectAsStateWithLifecycle()

    // The Activity-scoped ViewModel survives destination changes. Re-read when the Start page is
    // shown again so Settings/Bookmarks/History mutations are reflected without global reload flags.
    LaunchedEffect(startPageViewModel, refreshKey) { startPageViewModel.refresh() }

    Box(Modifier.fillMaxSize()) {
        StartPage(
            bookmarks = state.bookmarks,
            iconsDir = iconsDir,
            recent = state.recent,
            isPrivate = false,
            onOpen = onOpen,
            onAllBookmarks = onAllBookmarks,
            onAllHistory = onAllHistory,
            onRefreshRecent = startPageViewModel::refreshRecent,
            onRename = startPageViewModel::rename,
            onDelete = startPageViewModel::delete,
            onAdd = startPageViewModel::add,
        )

        if (state.isLoading && state.bookmarks.isEmpty() && state.recent.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
        }

        state.error?.let { operation ->
            val hasContent = state.bookmarks.isNotEmpty() || state.recent.isNotEmpty()
            val message = when (operation) {
                StartPageOperation.Load -> if (hasContent) {
                    "Не удалось обновить стартовую страницу"
                } else {
                    "Не удалось загрузить стартовую страницу"
                }
                StartPageOperation.RefreshRecent -> "Не удалось обновить недавние"
                StartPageOperation.Add -> "Не удалось добавить закладку"
                StartPageOperation.Rename -> "Не удалось переименовать закладку"
                StartPageOperation.Delete -> "Не удалось удалить закладку"
            }
            val retryable = operation == StartPageOperation.Load ||
                operation == StartPageOperation.RefreshRecent
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(
                    onClick = if (retryable) startPageViewModel::retry else startPageViewModel::dismissError,
                ) {
                    Text(if (retryable) "Повторить" else "Скрыть")
                }
            }
        }
    }
}
