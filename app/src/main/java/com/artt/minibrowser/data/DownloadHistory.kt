package com.artt.minibrowser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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

/** Keep only the origin for display; signed download URLs frequently contain credentials in query parameters. */
internal fun downloadSourceForHistory(value: String): String = runCatching {
    val uri = URI(value)
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return@runCatching ""
    URI(uri.scheme.lowercase(), null, uri.host, uri.port, null, null, null).toString()
}.getOrDefault("")

/**
 * Writes a complete replacement before publishing it. The target is never truncated in place,
 * so a process death during temp-file creation leaves the previous valid history untouched.
 */
internal fun writeTextAtomically(target: File, value: String) {
    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "${target.name}.tmp")
    try {
        FileOutputStream(temp).use { output ->
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            writer.write(value)
            writer.flush()
            output.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING)
        }
    } finally {
        temp.delete()
    }
}

/** Small bounded app-owned history for files downloaded by Minibrowser only. */
object DownloadHistory {
    private const val MAX_ITEMS = 200
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
                val sanitized = item.copy(sourceUrl = downloadSourceForHistory(item.sourceUrl))
                if (sanitized.status == DownloadStatus.Downloading) {
                    sanitized.copy(
                        status = DownloadStatus.Failed,
                        finishedAt = now,
                        error = "Загрузка была прервана",
                    )
                } else sanitized
            }.take(MAX_ITEMS)
            _items.value = loaded
            // Rewrites legacy entries as well, removing query/fragment secrets from disk.
            persistLocked()
        }
    }

    fun start(name: String, sourceUrl: String, mime: String): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val item = BrowserDownload(
            id = id,
            name = name,
            sourceUrl = downloadSourceForHistory(sourceUrl),
            mime = mime,
            status = DownloadStatus.Downloading,
            startedAt = System.currentTimeMillis(),
        )
        _items.value = (listOf(item) + _items.value).take(MAX_ITEMS)
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
            _items.value = _items.value.map { if (it.id == id) transform(it) else it }.take(MAX_ITEMS)
            persistLocked()
        }
    }

    private fun loadLocked(): List<BrowserDownload> {
        val file = storeFile ?: return emptyList()
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until minOf(array.length(), MAX_ITEMS)) {
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
        _items.value.take(MAX_ITEMS).forEach { item ->
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
        runCatching { writeTextAtomically(file, array.toString()) }
    }
}
