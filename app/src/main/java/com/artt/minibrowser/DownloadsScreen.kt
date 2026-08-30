package com.artt.minibrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artt.minibrowser.browser.DownloadsViewModel
import com.artt.minibrowser.data.BrowserDownload
import com.artt.minibrowser.data.DownloadFailureReason
import com.artt.minibrowser.data.DownloadStatus
import com.artt.minibrowser.data.DownloadsRepository
import com.artt.minibrowser.data.normalizeDownloadMime
import com.artt.minibrowser.ui.DownloadFailureUiState
import com.artt.minibrowser.ui.DownloadItemUiState
import com.artt.minibrowser.ui.DownloadStatusUiState
import com.artt.minibrowser.ui.DownloadsScreenContent
import com.artt.minibrowser.ui.DownloadsScreenUiState
import java.io.File
import java.net.URI

/** Downloads stays in the same Compose navigation layer as Settings/History/Bookmarks. */
@Composable
internal fun MotionDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val repository = remember(applicationContext) { DownloadsRepository(applicationContext) }
    val factory = remember(repository) { DownloadsViewModel.factory(repository) }
    val downloadsViewModel: DownloadsViewModel = viewModel(factory = factory)
    val state by downloadsViewModel.uiState.collectAsStateWithLifecycle()

    DownloadsScreenContent(
        state = DownloadsScreenUiState(
            downloads = state.downloads.map(BrowserDownload::toUiState),
            isRestoring = state.isRestoring,
        ),
        onBack = onBack,
        onClear = downloadsViewModel::clear,
        onOpen = { id ->
            state.downloads.firstOrNull { it.id == id }?.let { openDownload(context, it) }
        },
    )
}

private fun BrowserDownload.toUiState(): DownloadItemUiState = DownloadItemUiState(
    id = id,
    name = name,
    sourceUrl = sourceUrl,
    status = status.toUiState(),
    startedAt = startedAt,
    finishedAt = finishedAt,
    bytes = bytes,
    canOpen = status == DownloadStatus.Completed && location?.let(::isSupportedDownloadLocation) == true,
    failureReason = failureReason.toUiState(),
)

private fun DownloadStatus.toUiState(): DownloadStatusUiState = when (this) {
    DownloadStatus.Downloading -> DownloadStatusUiState.Downloading
    DownloadStatus.Completed -> DownloadStatusUiState.Completed
    DownloadStatus.Failed -> DownloadStatusUiState.Failed
}

private fun DownloadFailureReason?.toUiState(): DownloadFailureUiState = when (this) {
    DownloadFailureReason.Interrupted -> DownloadFailureUiState.Interrupted
    DownloadFailureReason.SaveFailed -> DownloadFailureUiState.SaveFailed
    null -> DownloadFailureUiState.Unknown
}

internal fun isSupportedDownloadLocation(value: String): Boolean = runCatching {
    val uri = URI(value)
    when (uri.scheme?.lowercase()) {
        "content" -> isMediaStoreDownloadRow(uri)
        "file" -> !uri.path.isNullOrBlank()
        else -> false
    }
}.getOrDefault(false)

private fun isMediaStoreDownloadRow(uri: URI): Boolean {
    if (!uri.rawAuthority.equals(MediaStore.AUTHORITY, ignoreCase = true)) return false
    if (uri.rawQuery != null || uri.rawFragment != null) return false
    val segments = uri.rawPath
        ?.split('/')
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    return segments.size == 3 &&
        segments[0].isNotBlank() &&
        segments[1].equals("downloads", ignoreCase = true) &&
        segments[2].toLongOrNull()?.let { it >= 0L } == true
}

private fun openDownload(context: Context, item: BrowserDownload) {
    val location = item.location?.takeIf(::isSupportedDownloadLocation) ?: return
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
        launch(normalizeDownloadMime(item.mime)) || launch("*/*")
    }.getOrDefault(false)
    if (!opened) {
        Toast.makeText(context, context.getString(R.string.no_app_to_open_file), Toast.LENGTH_SHORT).show()
    }
}
