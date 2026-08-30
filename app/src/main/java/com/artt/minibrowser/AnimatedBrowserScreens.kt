package com.artt.minibrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artt.minibrowser.browser.BookmarksOperation
import com.artt.minibrowser.browser.BookmarksUiState
import com.artt.minibrowser.browser.BookmarksViewModel
import com.artt.minibrowser.browser.HistoryOperation
import com.artt.minibrowser.browser.HistoryUiState
import com.artt.minibrowser.browser.HistoryViewModel
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.ui.AppIcons
import com.artt.minibrowser.ui.BookmarkActionsSheet
import com.artt.minibrowser.ui.BrowserMotionScreen
import com.artt.minibrowser.ui.CenteredSinglePane
import com.artt.minibrowser.ui.EmptyState
import com.artt.minibrowser.ui.Favicon
import com.artt.minibrowser.ui.hostOf
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

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

    HistoryScreen(
        state = state,
        iconsDir = iconsDir,
        onBack = onBack,
        onOpen = onOpen,
        onClear = historyViewModel::clear,
        onRetry = historyViewModel::retry,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    BrowserMotionScreen(onBack = onBack) { requestExit ->
        CenteredSinglePane {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { requestExit(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.history_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (state is HistoryUiState.Content && state.entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(
                                stringResource(R.string.action_clear),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                when (state) {
                    HistoryUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    HistoryUiState.Empty -> {
                        EmptyState(
                            AppIcons.History,
                            stringResource(R.string.history_empty_title),
                            stringResource(R.string.history_empty_subtitle),
                        )
                    }
                    is HistoryUiState.Error -> {
                        val title = when (state.operation) {
                            HistoryOperation.Load -> stringResource(R.string.history_load_error)
                            HistoryOperation.Clear -> stringResource(R.string.history_clear_error)
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyState(AppIcons.History, title, stringResource(R.string.retry_hint))
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                    }
                    is HistoryUiState.Content -> {
                        state.error?.let { operation ->
                            val message = when (operation) {
                                HistoryOperation.Load -> stringResource(R.string.history_load_error)
                                HistoryOperation.Clear -> stringResource(R.string.history_clear_error)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    message,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(onClick = onRetry) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                        val groups = remember(state.entries) { motionGroupByDay(state.entries) }
                        LazyColumn(Modifier.fillMaxSize()) {
                            groups.forEach { (group, groupEntries) ->
                                item(key = "header_${group.name}") {
                                    val label = when (group) {
                                        HistoryDayGroup.Today -> stringResource(R.string.history_today)
                                        HistoryDayGroup.Yesterday -> stringResource(R.string.history_yesterday)
                                        HistoryDayGroup.Earlier -> stringResource(R.string.history_earlier)
                                    }
                                    Text(
                                        label,
                                        Modifier.padding(start = 24.dp, top = 12.dp, bottom = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                items(groupEntries, key = { it.url }) { entry ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable { requestExit { onOpen(entry.url) } }
                                            .padding(horizontal = 20.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Favicon(entry.url, iconsDir, 28.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                entry.title.ifBlank { entry.url },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.history_entry_subtitle,
                                                    hostOf(entry.url),
                                                    timeFormat.format(Date(entry.visitedAt)),
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_dialog_title)) },
            text = { Text(stringResource(R.string.history_clear_dialog_message)) },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.action_clear)) }
            },
        )
    }
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

    BookmarksScreen(
        state = state,
        iconsDir = iconsDir,
        onBack = onBack,
        onOpen = onOpen,
        onRename = bookmarksViewModel::rename,
        onDelete = bookmarksViewModel::delete,
        onRetryLoad = bookmarksViewModel::retryLoad,
        onDismissError = bookmarksViewModel::dismissError,
    )
}

@Composable
private fun BookmarksScreen(
    state: BookmarksUiState,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onDismissError: () -> Unit,
) {
    var selected by remember { mutableStateOf<Bookmark?>(null) }

    BrowserMotionScreen(onBack = onBack) { requestExit ->
        CenteredSinglePane {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { requestExit(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.bookmarks_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                when {
                    state.isLoading && state.bookmarks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error == BookmarksOperation.Load && state.bookmarks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyState(
                                    AppIcons.Star,
                                    stringResource(R.string.bookmarks_load_error),
                                    stringResource(R.string.retry_hint),
                                )
                                TextButton(onClick = onRetryLoad) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                    }
                    state.bookmarks.isEmpty() -> {
                        EmptyState(
                            AppIcons.Star,
                            stringResource(R.string.bookmarks_empty_title),
                            stringResource(R.string.bookmarks_empty_subtitle),
                        )
                    }
                    else -> {
                        val visibleError = state.error
                        if (visibleError != null) {
                            val message = when (visibleError) {
                                BookmarksOperation.Load -> stringResource(R.string.bookmarks_refresh_error)
                                BookmarksOperation.Rename -> stringResource(R.string.bookmark_rename_error)
                                BookmarksOperation.Delete -> stringResource(R.string.bookmark_delete_error)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    message,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                val retryable = visibleError == BookmarksOperation.Load
                                TextButton(onClick = if (retryable) onRetryLoad else onDismissError) {
                                    Text(
                                        stringResource(
                                            if (retryable) R.string.action_retry else R.string.action_hide,
                                        ),
                                    )
                                }
                            }
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.bookmarks, key = { it.url }) { bookmark ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { requestExit { onOpen(bookmark.url) } }
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Favicon(bookmark.url, iconsDir, 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            bookmark.title.ifBlank { bookmark.host },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            hostOf(bookmark.url).ifBlank { bookmark.url },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = { selected = bookmark },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            stringResource(R.string.actions_content_description),
                                            Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { bookmark ->
        BookmarkActionsSheet(
            bookmark = bookmark,
            onDismiss = { selected = null },
            onOpen = { onOpen(bookmark.url); selected = null },
            onRename = { onRename(bookmark.url, it); selected = null },
            onDelete = { onDelete(bookmark.url); selected = null },
        )
    }
}

private enum class HistoryDayGroup { Today, Yesterday, Earlier }

private fun motionGroupByDay(entries: List<HistoryEntry>): List<Pair<HistoryDayGroup, List<HistoryEntry>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val groups = linkedMapOf(
        HistoryDayGroup.Today to mutableListOf<HistoryEntry>(),
        HistoryDayGroup.Yesterday to mutableListOf<HistoryEntry>(),
        HistoryDayGroup.Earlier to mutableListOf<HistoryEntry>(),
    )
    entries.forEach { entry ->
        when {
            entry.visitedAt >= todayStart -> groups.getValue(HistoryDayGroup.Today).add(entry)
            entry.visitedAt >= yesterdayStart -> groups.getValue(HistoryDayGroup.Yesterday).add(entry)
            else -> groups.getValue(HistoryDayGroup.Earlier).add(entry)
        }
    }
    return groups.filter { it.value.isNotEmpty() }.map { it.key to it.value.toList() }
}
