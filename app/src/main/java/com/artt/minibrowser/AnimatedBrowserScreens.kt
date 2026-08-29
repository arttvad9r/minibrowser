package com.artt.minibrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.ui.AppIcons
import com.artt.minibrowser.ui.BookmarkActionsSheet
import com.artt.minibrowser.ui.BrowserMotionScreen
import com.artt.minibrowser.ui.EmptyState
import com.artt.minibrowser.ui.Favicon
import com.artt.minibrowser.ui.MotionTokens
import com.artt.minibrowser.ui.hostOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** History destination with shared-axis motion and progress-aware Predictive Back. */
@Composable
internal fun MotionHistoryScreen(
    repo: HistoryRepository,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LaunchedEffect(reload) { entries = repo.recent(200) }

    BrowserMotionScreen(onBack = onBack) { requestExit ->
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestExit(onBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
                Text("История", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { scope.launch { repo.clear(); reload++ } }) {
                    Text("Очистить", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (entries.isEmpty()) {
                EmptyState(AppIcons.History, "История пуста", "Посещённые страницы появятся здесь.")
            } else {
                val groups = remember(entries) { motionGroupByDay(entries) }
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEach { (label, groupEntries) ->
                        item(key = "header_$label") {
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
                                    .clickable { requestExit { onOpen(entry.url) } }
                                    .animateItem()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Favicon(hostOf(entry.url), iconsDir, 28.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.title.ifBlank { entry.url },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${hostOf(entry.url)} · ${timeFormat.format(Date(entry.visitedAt))}",
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

/** Bookmarks destination with the same motion model as History and Settings. */
@Composable
internal fun MotionBookmarksScreen(
    bookmarks: List<Bookmark>,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<Bookmark?>(null) }

    BrowserMotionScreen(onBack = onBack) { requestExit ->
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestExit(onBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
                Text("Закладки", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            }

            if (bookmarks.isEmpty()) {
                EmptyState(AppIcons.Star, "Закладок пока нет", "Сохранённые страницы появятся здесь.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(bookmarks, key = { it.url }) { bookmark ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { requestExit { onOpen(bookmark.url) } }
                                .animateItem()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Favicon(bookmark.host, iconsDir, 40.dp)
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
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    "Действия",
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

/**
 * Smooth browser loading line: progress changes interpolate instead of jumping, and completion
 * reaches 100% before the indicator fades away. A fast page therefore no longer flashes a bar for
 * a single frame.
 */
@Composable
internal fun SmoothPageProgress(tab: Tab?) {
    val rawProgress = tab?.progress ?: -1f
    val tabId = tab?.id
    val progress = remember(tabId) { Animatable(0f) }
    var visible by remember(tabId) { mutableStateOf(false) }

    LaunchedEffect(tabId, rawProgress) {
        if (rawProgress >= 0f) {
            if (!visible) {
                progress.snapTo(0f)
                visible = true
            }
            progress.animateTo(
                targetValue = rawProgress.coerceIn(0f, 0.96f),
                animationSpec = tween(MotionTokens.Standard),
            )
        } else if (visible) {
            progress.animateTo(1f, animationSpec = tween(MotionTokens.Quick))
            delay(90)
            visible = false
            delay(MotionTokens.Quick.toLong())
            progress.snapTo(0f)
        }
    }

    Box(Modifier.fillMaxWidth().height(2.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(MotionTokens.Quick)),
            exit = fadeOut(tween(MotionTokens.Quick)),
        ) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

private fun motionGroupByDay(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val groups = linkedMapOf(
        "Сегодня" to mutableListOf<HistoryEntry>(),
        "Вчера" to mutableListOf<HistoryEntry>(),
        "Ранее" to mutableListOf<HistoryEntry>(),
    )
    entries.forEach { entry ->
        when {
            entry.visitedAt >= todayStart -> groups.getValue("Сегодня").add(entry)
            entry.visitedAt >= yesterdayStart -> groups.getValue("Вчера").add(entry)
            else -> groups.getValue("Ранее").add(entry)
        }
    }
    return groups.filter { it.value.isNotEmpty() }.map { it.key to it.value.toList() }
}
