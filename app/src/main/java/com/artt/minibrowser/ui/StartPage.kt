package com.artt.minibrowser.ui

// Домашняя страница: один настоящий omnibox остаётся в chrome браузера;
// здесь только лёгкий branding, быстрые закладки и недавние страницы.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.R
import java.io.File

internal data class StartPageBookmarkUiState(
    val url: String,
    val title: String,
    val host: String,
)

internal data class StartPageRecentUiState(
    val url: String,
    val title: String,
)

@Composable
internal fun StartPage(
    bookmarks: List<StartPageBookmarkUiState>,
    iconsDir: File,
    recent: List<StartPageRecentUiState>,
    isPrivate: Boolean,
    onOpen: (String) -> Unit,
    onAllBookmarks: () -> Unit,
    onAllHistory: () -> Unit,
    onRefreshRecent: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (url: String, title: String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<StartPageBookmarkUiState?>(null) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    CenteredSinglePane {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(force = true) } }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            if (isPrivate) {
                Icon(
                    AppIcons.Incognito,
                    null,
                    Modifier.align(Alignment.CenterHorizontally).size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.private_tab_title),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.private_tab_subtitle),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Text(
                    stringResource(R.string.app_name),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(24.dp))
            }

            if (!isPrivate) {
                SectionHeader(
                    stringResource(R.string.bookmarks_title),
                    actionLabel = stringResource(R.string.action_all),
                    onAction = onAllBookmarks,
                )
                Spacer(Modifier.height(8.dp))
                BookmarkRow(
                    bookmarks,
                    iconsDir,
                    onOpen = onOpen,
                    onAdd = { showAdd = true },
                    onLongPress = { selected = it },
                )
                Spacer(Modifier.height(18.dp))

                if (recent.isNotEmpty()) {
                    RecentCard(
                        recent,
                        iconsDir,
                        onOpen = onOpen,
                        onShowAll = onAllHistory,
                        onRefresh = onRefreshRecent,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val sel = selected
    if (sel != null) {
        BookmarkActionsSheet(
            bookmarkKey = sel.url,
            bookmarkTitle = sel.title,
            onDismiss = { selected = null },
            onOpen = { onOpen(sel.url) },
            onRename = { onRename(sel.url, it); selected = null },
            onDelete = { onDelete(sel.url); selected = null },
        )
    }
    if (showAdd) {
        AddBookmarkSheet(
            onDismiss = { showAdd = false },
            onAdd = { url, title -> showAdd = false; onAdd(url, title) },
        )
    }
}

/** Горизонтальная лента быстрых закладок: ~4 в ряд, последний элемент «Добавить». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmarks: List<StartPageBookmarkUiState>,
    iconsDir: File,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (StartPageBookmarkUiState) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bookmarks.forEach { bm ->
            val bookmarkDescription = bm.title.ifBlank {
                bm.host.ifBlank { hostOf(bm.url).ifBlank { bm.url } }
            }
            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(Radius.button)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.button)
                        .softCombinedClickable(
                            onClick = { onOpen(bm.url) },
                            onLongClick = { onLongPress(bm) },
                        )
                        .semantics { contentDescription = bookmarkDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Favicon(bm.url, iconsDir, 30.dp)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    bm.title.ifBlank { bm.host },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(Radius.button)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.button)
                    .softClickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    stringResource(R.string.add_bookmark_content_description),
                    Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.action_add),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Недавние»: лёгкая карточка с несколькими содержательными последними страницами. */
@Composable
private fun RecentCard(
    recent: List<StartPageRecentUiState>,
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
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.recent_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    stringResource(R.string.refresh_recent_content_description),
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        recent.take(3).forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onOpen(entry.url) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(entry.url, iconsDir, 24.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title.ifBlank { entry.url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        hostOf(entry.url).ifBlank { entry.url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(
            Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onShowAll)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.show_all_history), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Диалог добавления закладки — bottom sheet с названием и адресом. */
@Composable
fun AddBookmarkSheet(onDismiss: () -> Unit, onAdd: (url: String, title: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.add_bookmark_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Field(
            label = stringResource(R.string.field_title),
            value = title,
            onChange = { title = it },
            placeholder = stringResource(R.string.bookmark_title_placeholder),
        )
        Spacer(Modifier.height(12.dp))
        Field(
            label = stringResource(R.string.field_address),
            value = url,
            onChange = { url = it },
            placeholder = stringResource(R.string.bookmark_url_placeholder),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = { onAdd(normalizeUrl(url), title.trim()) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

private fun normalizeUrl(q: String): String {
    val s = q.trim()
    return if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
}
