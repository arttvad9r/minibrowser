package com.artt.minibrowser.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

internal enum class HistoryScreenOperation { Load, Clear }

internal data class HistoryItemUiState(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

internal sealed interface HistoryScreenUiState {
    data object Loading : HistoryScreenUiState
    data object Empty : HistoryScreenUiState
    data class Error(val operation: HistoryScreenOperation) : HistoryScreenUiState
    data class Content(
        val entries: List<HistoryItemUiState>,
        val error: HistoryScreenOperation? = null,
    ) : HistoryScreenUiState
}

internal enum class BookmarksScreenOperation { Load, Rename, Delete }

internal data class BookmarkItemUiState(
    val url: String,
    val title: String,
    val host: String,
)

internal data class BookmarksScreenUiState(
    val bookmarks: List<BookmarkItemUiState>,
    val isLoading: Boolean,
    val error: BookmarksScreenOperation?,
)

/** Pure history renderer; repository/ViewModel ownership stays in the destination route. */
@Composable
internal fun HistoryScreenContent(
    state: HistoryScreenUiState,
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
                    if (state is HistoryScreenUiState.Content && state.entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(
                                stringResource(R.string.action_clear),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                when (state) {
                    HistoryScreenUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    HistoryScreenUiState.Empty -> {
                        EmptyState(
                            AppIcons.History,
                            stringResource(R.string.history_empty_title),
                            stringResource(R.string.history_empty_subtitle),
                        )
                    }
                    is HistoryScreenUiState.Error -> {
                        val title = when (state.operation) {
                            HistoryScreenOperation.Load -> stringResource(R.string.history_load_error)
                            HistoryScreenOperation.Clear -> stringResource(R.string.history_clear_error)
                        }
                        InitialLoadErrorState(AppIcons.History, title, onRetry)
                    }
                    is HistoryScreenUiState.Content -> {
                        state.error?.let { operation ->
                            val message = when (operation) {
                                HistoryScreenOperation.Load -> stringResource(R.string.history_load_error)
                                HistoryScreenOperation.Clear -> stringResource(R.string.history_clear_error)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    message,
                                    Modifier
                                        .weight(1f)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(onClick = onRetry) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                        val groups = remember(state.entries) { groupHistoryByDay(state.entries) }
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
                                            .softClickable { requestExit { onOpen(entry.url) } }
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

/** Pure bookmarks renderer; repository/ViewModel ownership stays in the destination route. */
@Composable
internal fun BookmarksScreenContent(
    state: BookmarksScreenUiState,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onDismissError: () -> Unit,
) {
    var selected by remember { mutableStateOf<BookmarkItemUiState?>(null) }

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
                    state.error == BookmarksScreenOperation.Load && state.bookmarks.isEmpty() -> {
                        InitialLoadErrorState(
                            AppIcons.Star,
                            stringResource(R.string.bookmarks_load_error),
                            onRetryLoad,
                        )
                    }
                    state.bookmarks.isEmpty() -> {
                        EmptyState(
                            AppIcons.Star,
                            stringResource(R.string.bookmarks_empty_title),
                            stringResource(R.string.bookmarks_empty_subtitle),
                        )
                    }
                    else -> {
                        state.error?.let { operation ->
                            val message = when (operation) {
                                BookmarksScreenOperation.Load -> stringResource(R.string.bookmarks_refresh_error)
                                BookmarksScreenOperation.Rename -> stringResource(R.string.bookmark_rename_error)
                                BookmarksScreenOperation.Delete -> stringResource(R.string.bookmark_delete_error)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    message,
                                    Modifier
                                        .weight(1f)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                val retryable = operation == BookmarksScreenOperation.Load
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
                                val bookmarkActionLabel = bookmark.title.ifBlank {
                                    bookmark.host.ifBlank { hostOf(bookmark.url).ifBlank { bookmark.url } }
                                }
                                val actionsDescription = stringResource(
                                    R.string.bookmark_actions_named_content_description,
                                    bookmarkActionLabel,
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .softClickable { requestExit { onOpen(bookmark.url) } }
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
                                            actionsDescription,
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
            bookmarkKey = bookmark.url,
            bookmarkTitle = bookmark.title,
            onDismiss = { selected = null },
            onOpen = { onOpen(bookmark.url); selected = null },
            onRename = { onRename(bookmark.url, it); selected = null },
            onDelete = { onDelete(bookmark.url); selected = null },
        )
    }
}

@Composable
private fun InitialLoadErrorState(
    icon: ImageVector,
    title: String,
    onRetry: () -> Unit,
) {
    val retryHint = stringResource(R.string.retry_hint)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.semantics(mergeDescendants = true) {
                    error(retryHint)
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                EmptyState(icon, title, retryHint)
            }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private enum class HistoryDayGroup { Today, Yesterday, Earlier }

private fun groupHistoryByDay(
    entries: List<HistoryItemUiState>,
): List<Pair<HistoryDayGroup, List<HistoryItemUiState>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val groups = linkedMapOf(
        HistoryDayGroup.Today to mutableListOf<HistoryItemUiState>(),
        HistoryDayGroup.Yesterday to mutableListOf<HistoryItemUiState>(),
        HistoryDayGroup.Earlier to mutableListOf<HistoryItemUiState>(),
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
