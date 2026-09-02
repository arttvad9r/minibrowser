package com.artt.minibrowser.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import java.text.DateFormat
import java.util.Date

internal enum class DownloadStatusUiState { Downloading, Completed, Failed }
internal enum class DownloadFailureUiState { Interrupted, SaveFailed, Unknown }

internal data class DownloadItemUiState(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val status: DownloadStatusUiState,
    val startedAt: Long,
    val finishedAt: Long?,
    val bytes: Long,
    val canOpen: Boolean,
    val failureReason: DownloadFailureUiState,
)

internal data class DownloadsScreenUiState(
    val downloads: List<DownloadItemUiState>,
    val isRestoring: Boolean,
)

/** Pure downloads renderer; storage and file-opening side effects stay in the destination route. */
@Composable
internal fun DownloadsScreenContent(
    state: DownloadsScreenUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onOpen: (String) -> Unit,
    backEnabled: Boolean = true,
) {
    val downloads = state.downloads
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    var showClearConfirm by remember { mutableStateOf(false) }

    BrowserMotionScreen(onBack = onBack, backEnabled = backEnabled) { requestExit ->
        CenteredSinglePane {
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

                when {
                    state.isRestoring -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    downloads.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                AppIcons.Download,
                                stringResource(R.string.downloads_empty_title),
                                stringResource(R.string.downloads_empty_subtitle),
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(downloads, key = { it.id }) { item ->
                                DownloadCard(
                                    item,
                                    dateFormat,
                                    Modifier.animateItem(
                                        fadeInSpec = tween(MotionTokens.Content),
                                        placementSpec = tween(MotionTokens.Content),
                                        fadeOutSpec = tween(MotionTokens.Content),
                                    ),
                                ) { onOpen(item.id) }
                            }
                        }
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
    item: DownloadItemUiState,
    dateFormat: DateFormat,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val targetIconTint = when (item.status) {
        DownloadStatusUiState.Downloading -> MaterialTheme.colorScheme.primary
        DownloadStatusUiState.Completed -> MaterialTheme.colorScheme.onSurface
        DownloadStatusUiState.Failed -> MaterialTheme.colorScheme.error
    }
    val iconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = tween(MotionTokens.IconState),
        label = "download status color",
    )
    var cardModifier = modifier
        .fillMaxWidth()
        .clip(Radius.card)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card)
    if (item.canOpen) cardModifier = cardModifier.softClickable(onClick = onOpen)

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
            AnimatedContent(
                targetState = item.status,
                transitionSpec = {
                    fadeIn(tween(MotionTokens.Content))
                        .togetherWith(fadeOut(tween(MotionTokens.IconState)))
                },
                label = "download status",
            ) { status ->
                val statusText: String
                val statusDescription: String
                when (status) {
                    DownloadStatusUiState.Downloading -> {
                        statusText = stringResource(R.string.download_status_downloading)
                        statusDescription = stringResource(
                            R.string.download_status_downloading_accessibility,
                            item.name,
                        )
                    }
                    DownloadStatusUiState.Completed -> {
                        val whenDone = item.finishedAt ?: item.startedAt
                        statusText = stringResource(
                            R.string.download_completed_subtitle,
                            Formatter.formatShortFileSize(context, item.bytes.coerceAtLeast(0L)),
                            dateFormat.format(Date(whenDone)),
                        )
                        statusDescription = stringResource(
                            R.string.download_status_completed_accessibility,
                            item.name,
                            statusText,
                        )
                    }
                    DownloadStatusUiState.Failed -> {
                        val failure = when (item.failureReason) {
                            DownloadFailureUiState.Interrupted -> stringResource(R.string.download_failure_interrupted)
                            DownloadFailureUiState.SaveFailed -> stringResource(R.string.download_save_error)
                            DownloadFailureUiState.Unknown -> stringResource(R.string.download_failed_default)
                        }
                        statusText = stringResource(R.string.download_failed_subtitle, failure)
                        statusDescription = stringResource(
                            R.string.download_status_failed_accessibility,
                            item.name,
                            failure,
                        )
                    }
                }
                Text(
                    statusText,
                    Modifier.clearAndSetSemantics {
                        contentDescription = statusDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == DownloadStatusUiState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            val host = remember(item.sourceUrl) { hostOf(item.sourceUrl) }
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
