package com.artt.minibrowser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class DownloadStatus { Downloading, Completed, Failed }

data class BrowserDownload(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val mime: String,
    val status: DownloadStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val bytes: Long = 0L,
    val location: String? = null,
    val error: String? = null,
)

fun formatDownloadSize(bytes: Long): String {
    if (bytes < 0L) return "—"
    if (bytes < 1024L) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return if (kb < 10) "%.1f КБ".format(kb) else "%.0f КБ".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return if (mb < 10) "%.1f МБ".format(mb) else "%.0f МБ".format(mb)
    val gb = mb / 1024.0
    return if (gb < 10) "%.1f ГБ".format(gb) else "%.0f ГБ".format(gb)
}

/** Small app-owned history for files downloaded by Minibrowser only. */
object DownloadHistory {
    private val lock = Any()
    private val _items = MutableStateFlow<List<BrowserDownload>>(emptyList())
    val items: StateFlow<List<BrowserDownload>> = _items.asStateFlow()
    private var storeFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            if (storeFile != null) return
            storeFile = File(context.filesDir, "downloads.json")
            val now = System.currentTimeMillis()
            val loaded = loadLocked().map { item ->
                if (item.status == DownloadStatus.Downloading) {
                    item.copy(
                        status = DownloadStatus.Failed,
                        finishedAt = now,
                        error = "Загрузка была прервана",
                    )
                } else item
            }
            _items.value = loaded
            persistLocked()
        }
    }

    fun start(name: String, sourceUrl: String, mime: String): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val item = BrowserDownload(
            id = id,
            name = name,
            sourceUrl = sourceUrl,
            mime = mime,
            status = DownloadStatus.Downloading,
            startedAt = System.currentTimeMillis(),
        )
        _items.value = listOf(item) + _items.value
        persistLocked()
        id
    }

    fun complete(id: String, location: String, bytes: Long) {
        update(id) {
            it.copy(
                status = DownloadStatus.Completed,
                finishedAt = System.currentTimeMillis(),
                bytes = bytes,
                location = location,
                error = null,
            )
        }
    }

    fun fail(id: String, error: String) {
        update(id) {
            it.copy(
                status = DownloadStatus.Failed,
                finishedAt = System.currentTimeMillis(),
                error = error,
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            _items.value = emptyList()
            persistLocked()
        }
    }

    private fun update(id: String, transform: (BrowserDownload) -> BrowserDownload) {
        synchronized(lock) {
            _items.value = _items.value.map { if (it.id == id) transform(it) else it }
            persistLocked()
        }
    }

    private fun loadLocked(): List<BrowserDownload> {
        val file = storeFile ?: return emptyList()
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val o = array.getJSONObject(index)
                    add(
                        BrowserDownload(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            sourceUrl = o.optString("sourceUrl"),
                            mime = o.optString("mime", "application/octet-stream"),
                            status = runCatching { DownloadStatus.valueOf(o.getString("status")) }
                                .getOrDefault(DownloadStatus.Failed),
                            startedAt = o.optLong("startedAt"),
                            finishedAt = o.optLong("finishedAt").takeIf { o.has("finishedAt") },
                            bytes = o.optLong("bytes"),
                            location = o.optString("location").takeIf { it.isNotBlank() },
                            error = o.optString("error").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLocked() {
        val file = storeFile ?: return
        val array = JSONArray()
        _items.value.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("sourceUrl", item.sourceUrl)
                    put("mime", item.mime)
                    put("status", item.status.name)
                    put("startedAt", item.startedAt)
                    item.finishedAt?.let { put("finishedAt", it) }
                    put("bytes", item.bytes)
                    item.location?.let { put("location", it) }
                    item.error?.let { put("error", it) }
                },
            )
        }
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(file)) {
                file.writeText(array.toString())
                temp.delete()
            }
        }
    }
}
