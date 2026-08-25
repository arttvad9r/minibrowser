package com.artt.minibrowser.ui

// Домашняя страница: логотип, большой поиск, быстрые закладки, недавние страницы.
// Поиск не дублирует омнибокс: тап по строке фокусирует адресную строку (onSearchFocus).

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.HistoryEntry
import java.io.File

@Composable
fun StartPage(
    bookmarks: List<Bookmark>,
    iconsDir: File,
    recent: List<HistoryEntry>,
    onSearchFocus: () -> Unit,
    onOpen: (String) -> Unit,
    onAllBookmarks: () -> Unit,
    onAllHistory: () -> Unit,
    onRefreshRecent: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (url: String, title: String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Bookmark?>(null) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        Modifier
            .fillMaxSize()
            // Непрозрачный фон: под страницей находится GeckoView.
            .background(MaterialTheme.colorScheme.background)
            // Тап по пустому месту закрывает подсказки омнибокса.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(force = true) } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            "Minibrowser",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(24.dp))

        // Большая строка поиска — активирует существующий омнибокс.
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(Radius.search)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onSearchFocus)
                .semantics { contentDescription = "Поиск или адрес" }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Поиск или адрес", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(28.dp))

        SectionHeader("Закладки", actionLabel = "Все", onAction = onAllBookmarks)
        Spacer(Modifier.height(12.dp))
        BookmarkRow(bookmarks, iconsDir,
            onOpen = onOpen,
            onAdd = { showAdd = true },
            onLongPress = { selected = it })
        Spacer(Modifier.height(24.dp))

        if (recent.isNotEmpty()) {
            RecentCard(recent, iconsDir, onOpen = onOpen, onShowAll = onAllHistory, onRefresh = onRefreshRecent)
        }
        Spacer(Modifier.height(32.dp))
    }

    val sel = selected
    if (sel != null) {
        BookmarkActionsSheet(
            bookmark = sel,
            onDismiss = { selected = null },
            onOpen = { onOpen(sel.url) },
            onRename = { onRename(sel.url, it); selected = null },
            onDelete = { onDelete(sel.url); selected = null },
        )
    }
    if (showAdd) {
        AddBookmarkSheet(onDismiss = { showAdd = false }, onAdd = { url, title -> showAdd = false; onAdd(url, title) })
    }
}

/** Горизонтальная лента быстрых закладок: ~4 в ряд, последний элемент «Добавить». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmarks: List<Bookmark>,
    iconsDir: File,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (Bookmark) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bookmarks.forEach { bm ->
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(Radius.button)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.button)
                        .combinedClickable(onClick = { onOpen(bm.url) }, onLongClick = { onLongPress(bm) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Favicon(bm.host, iconsDir, 30.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    bm.title.ifBlank { bm.host },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(Radius.button)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, "Добавить закладку", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Добавить",
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Недавние»: лёгкая белая карточка с несколькими последними страницами. */
@Composable
private fun RecentCard(
    recent: List<HistoryEntry>,
    iconsDir: File,
    onOpen: (String) -> Unit,
    onShowAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card)
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Недавние", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp).semantics { contentDescription = "Обновить" }) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        recent.take(3).forEach { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(e.url) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(hostOf(e.url), iconsDir, 24.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.title.ifBlank { e.url },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        e.url,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowAll)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Показать всю историю", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Диалог добавления закладки — bottom sheet с названием и адресом. */
@Composable
fun AddBookmarkSheet(onDismiss: () -> Unit, onAdd: (url: String, title: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Text("Добавить закладку", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Field(label = "Название", value = title, onChange = { title = it }, placeholder = "YouTube")
        Spacer(Modifier.height(12.dp))
        Field(label = "Адрес", value = url, onChange = { url = it }, placeholder = "youtube.com")
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
            Button(
                onClick = { onAdd(normalizeUrl(url), title.trim()) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("Добавить") }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = Radius.button,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

private fun normalizeUrl(q: String): String {
    val s = q.trim()
    return if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
}
