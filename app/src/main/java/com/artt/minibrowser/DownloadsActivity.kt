package com.artt.minibrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.data.BrowserDownload
import com.artt.minibrowser.data.DownloadHistory
import com.artt.minibrowser.data.DownloadStatus
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.data.formatDownloadSize
import com.artt.minibrowser.ui.AppIcons
import com.artt.minibrowser.ui.EmptyState
import com.artt.minibrowser.ui.MinibrowserTheme
import java.io.File
import java.text.DateFormat
import java.util.Date

class DownloadsActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by settingsRepo.prefs.collectAsStateWithLifecycle(Prefs())
            val downloads by DownloadHistory.items.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            MinibrowserTheme(darkTheme = darkTheme) {
                DownloadsScreen(
                    downloads = downloads,
                    onBack = ::finish,
                    onOpen = ::openDownload,
                    onClear = DownloadHistory::clear,
                )
            }
        }
    }

    private fun openDownload(item: BrowserDownload) {
        val location = item.location ?: return
        val stored = runCatching { Uri.parse(location) }.getOrNull() ?: return
        val uri = runCatching {
            if (stored.scheme == "file") {
                val path = stored.path ?: error("Missing file path")
                FileProvider.getUriForFile(this, "$packageName.files", File(path))
            } else stored
        }.getOrElse {
            Toast.makeText(this, "Файл больше недоступен", Toast.LENGTH_SHORT).show()
            return
        }

        fun launch(mime: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(packageManager) == null) return false
            startActivity(Intent.createChooser(intent, "Открыть файл"))
            return true
        }

        val opened = runCatching {
            launch(item.mime.ifBlank { "application/octet-stream" }) || launch("*/*")
        }.getOrDefault(false)
        if (!opened) Toast.makeText(this, "Нет приложения для открытия файла", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DownloadsScreen(
    downloads: List<BrowserDownload>,
    onBack: () -> Unit,
    onOpen: (BrowserDownload) -> Unit,
    onClear: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text("Загрузки", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (downloads.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Очистить") }
            }
        }

        if (downloads.isEmpty()) {
            EmptyState(AppIcons.Download, "Загрузок пока нет", "Скачанные через Minibrowser файлы появятся здесь.")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(downloads, key = { it.id }) { item ->
                val canOpen = item.status == DownloadStatus.Completed && !item.location.isNullOrBlank()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canOpen) { onOpen(item) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.Download,
                        null,
                        Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(2.dp))
                        val status = when (item.status) {
                            DownloadStatus.Downloading -> "Скачивается…"
                            DownloadStatus.Completed -> {
                                val whenDone = item.finishedAt ?: item.startedAt
                                "${formatDownloadSize(item.bytes)} · ${dateFormat.format(Date(whenDone))}"
                            }
                            DownloadStatus.Failed -> "Ошибка · ${item.error ?: "Не удалось скачать"}"
                        }
                        Text(
                            status,
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
        }
    }
}
