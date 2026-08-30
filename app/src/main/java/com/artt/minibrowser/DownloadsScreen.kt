package com.artt.minibrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artt.minibrowser.browser.DownloadsUiState
import com.artt.minibrowser.browser.DownloadsViewModel
import com.artt.minibrowser.data.BrowserDownload
import com.artt.minibrowser.data.DownloadHistory
import com.artt.minibrowser.data.DownloadStatus
import com.artt.minibrowser.data.formatDownloadSize
import com.artt.minibrowser.ui.AppIcons
import com.artt.minibrowser.ui.BrowserMotionScreen
import com.artt.minibrowser.ui.EmptyState
import com.artt.minibrowser.ui.Radius
import com.artt.minibrowser.ui.softClickable
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Downloads stays in the same Compose navigation layer as Settings/History/Bookmarks. */
@Composable
internal fun MotionDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val factory = remember(applicationContext) {
        DownloadsViewModel.factory(
            downloads = DownloadHistory.items,
            initialize = { DownloadHistory.init(applicationContext) },
            clearHistory = DownloadHistory::clear,
        )
    }
    val downloadsViewModel: DownloadsViewModel = viewModel(factory = factory)
    val state by downloadsViewModel.uiState.collectAsStateWithLifecycle()

    DownloadsScreen(
        state = state,
        onBack = onBack,
        onClear = downloadsViewModel::clear,
        onOpen = { item -> openDownload(context, item) },
    )
}

@Composable
private fun DownloadsScreen(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onOpen: (BrowserDownload) -> Unit,
) {
    val downloads = state.downloads
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    var showClearConfirm by remember { mutableStateOf(false) }

    BrowserMotionScreen(onBack = onBack) { requestExit ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestExit(onBack) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.downloads_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }

            if (downloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        AppIcons.Download,
                        stringResource(R.string.downloads_empty_title),
                        stringResource(R.string.downloads_empty_subtitle),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadCard(item, dateFormat) { onOpen(item) }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.downloads_clear_dialog_title)) },
            text = { Text(stringResource(R.string.downloads_clear_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DownloadCard(
    item: BrowserDownload,
    dateFormat: DateFormat,
    onOpen: () -> Unit,
) {
    val canOpen = item.status == DownloadStatus.Completed && !item.location.isNullOrBlank()
    val iconTint = when (item.status) {
        DownloadStatus.Downloading -> MaterialTheme.colorScheme.primary
        DownloadStatus.Completed -> MaterialTheme.colorScheme.onSurface
        DownloadStatus.Failed -> MaterialTheme.colorScheme.error
    }
    var cardModifier = Modifier
        .fillMaxWidth()
        .clip(Radius.card)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card)
    if (canOpen) cardModifier = cardModifier.softClickable(onClick = onOpen)

    Row(
        cardModifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Download, null, Modifier.size(28.dp), tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(3.dp))
            val statusText = when (item.status) {
                DownloadStatus.Downloading -> stringResource(R.string.download_status_downloading)
                DownloadStatus.Completed -> {
                    val whenDone = item.finishedAt ?: item.startedAt
                    stringResource(
                        R.string.download_completed_subtitle,
                        formatDownloadSize(item.bytes),
                        dateFormat.format(Date(whenDone)),
                    )
                }
                DownloadStatus.Failed -> stringResource(
                    R.string.download_failed_subtitle,
                    item.error ?: stringResource(R.string.download_failed_default),
                )
            }
            Text(
                statusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == DownloadStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            val host = remember(item.sourceUrl) {
                runCatching { Uri.parse(item.sourceUrl).host.orEmpty() }.getOrDefault("")
            }
            if (host.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    host,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun openDownload(context: Context, item: BrowserDownload) {
    val location = item.location ?: return
    val stored = runCatching { Uri.parse(location) }.getOrNull() ?: return
    val uri = runCatching {
        if (stored.scheme == "file") {
            val path = stored.path ?: error("Missing file path")
            val file = File(path)
            if (!file.isFile) error("Downloaded file is missing")
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else {
            stored
        }
    }.getOrElse {
        Toast.makeText(context, context.getString(R.string.download_unavailable), Toast.LENGTH_SHORT).show()
        return
    }

    fun launch(mime: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_file_chooser_title)))
        return true
    }

    val opened = runCatching {
        launch(item.mime.ifBlank { "application/octet-stream" }) || launch("*/*")
    }.getOrDefault(false)
    if (!opened) {
        Toast.makeText(context, context.getString(R.string.no_app_to_open_file), Toast.LENGTH_SHORT).show()
    }
}
