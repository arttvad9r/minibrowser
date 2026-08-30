package com.artt.minibrowser.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
enum class DownloadFailureReason { Interrupted, SaveFailed }

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
    val failureReason: DownloadFailureReason? = null,
)

// Read-only migration sentinel from the pre-structured history format. Never shown directly.
private const val LEGACY_INTERRUPTED_ERROR = "Загрузка была прервана"

/** Keep only the origin for display; signed download URLs frequently contain credentials in query parameters. */
internal fun downloadSourceForHistory(value: String): String = runCatching {
    val uri = URI(value)
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return@runCatching ""
    URI(uri.scheme.lowercase(), null, uri.host, uri.port, null, null, null).toString()
}.getOrDefault("")

internal fun normalizeRestoredDownload(item: BrowserDownload, now: Long): BrowserDownload {
    val sanitized = item.copy(sourceUrl = downloadSourceForHistory(item.sourceUrl))
    return when (sanitized.status) {
        DownloadStatus.Downloading -> sanitized.copy(
            status = DownloadStatus.Failed,
            finishedAt = now,
            error = null,
            failureReason = DownloadFailureReason.Interrupted,
        )
        DownloadStatus.Failed -> sanitized.copy(
            error = null,
            failureReason = sanitized.failureReason ?: if (sanitized.error == LEGACY_INTERRUPTED_ERROR) {
                DownloadFailureReason.Interrupted
            } else {
                DownloadFailureReason.SaveFailed
            },
        )
        DownloadStatus.Completed -> sanitized
    }
}

/** Live callbacks win by id; restored history then fills the remaining bounded slots. */
internal fun mergeRestoredDownloads(
    live: List<BrowserDownload>,
    restored: List<BrowserDownload>,
    limit: Int = 200,
): List<BrowserDownload> =
    (live + restored).distinctBy { it.id }.take(limit.coerceAtLeast(0))

internal fun shouldPersistRestoredDownloadMerge(
    live: List<BrowserDownload>,
    rawRestored: List<BrowserDownload>,
    normalizedRestored: List<BrowserDownload>,
    discardRestoredHistory: Boolean,
): Boolean =
    discardRestoredHistory || live.isNotEmpty() || rawRestored != normalizedRestored

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

/**
 * Small bounded app-owned history for files downloaded by Minibrowser only.
 *
 * UI-visible state changes synchronously, but file restore, JSON serialization and fsync all run on
 * IO. The writer waits for the initial restore so an early download callback cannot overwrite the
 * previous process' history with a partial snapshot.
 */
object DownloadHistory {
    private const val MAX_ITEMS = 200
    private val lock = Any()
    private val _items = MutableStateFlow<List<BrowserDownload>>(emptyList())
    val items: StateFlow<List<BrowserDownload>> = _items.asStateFlow()
    private val _restoreCompleted = MutableStateFlow(false)
    val restoreCompleted: StateFlow<Boolean> = _restoreCompleted.asStateFlow()
    private var storeFile: File? = null
    private var discardRestoredHistory = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val restoreComplete = CompletableDeferred<Unit>()
    private val persistRequests = Channel<List<BrowserDownload>>(Channel.CONFLATED)

    init {
        ioScope.launch {
            restoreComplete.await()
            for (snapshot in persistRequests) persistSnapshot(snapshot)
        }
    }

    fun init(context: Context) {
        val file = synchronized(lock) {
            if (storeFile != null) return
            File(context.filesDir, "downloads.json").also { storeFile = it }
        }
        ioScope.launch {
            try {
                val now = System.currentTimeMillis()
                val rawRestored = load(file).take(MAX_ITEMS)
                val restored = rawRestored.map { normalizeRestoredDownload(it, now) }
                synchronized(lock) {
                    val live = _items.value
                    val discard = discardRestoredHistory
                    _items.value = if (discard) {
                        live.take(MAX_ITEMS)
                    } else {
                        mergeRestoredDownloads(live, restored, MAX_ITEMS)
                    }
                    // Do not rewrite an already-sanitized, settled file on every cold start. A
                    // rewrite is needed only when normalization changed data, a live callback raced
                    // the restore, or the user cleared history before the restore completed.
                    if (shouldPersistRestoredDownloadMerge(live, rawRestored, restored, discard)) {
                        // Publish the merged/sanitized snapshot before unblocking the writer. Because
                        // the channel is conflated, any pre-restore partial snapshot is replaced by it.
                        schedulePersistLocked()
                    }
                }
            } finally {
                _restoreCompleted.value = true
                restoreComplete.complete(Unit)
            }
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
        schedulePersistLocked()
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
                failureReason = null,
            )
        }
    }

    fun fail(id: String) {
        update(id) {
            it.copy(
                status = DownloadStatus.Failed,
                finishedAt = System.currentTimeMillis(),
                error = null,
                failureReason = DownloadFailureReason.SaveFailed,
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            if (!restoreComplete.isCompleted) discardRestoredHistory = true
            _items.value = emptyList()
            schedulePersistLocked()
        }
    }

    private fun update(id: String, transform: (BrowserDownload) -> BrowserDownload) {
        synchronized(lock) {
            _items.value = _items.value.map { if (it.id == id) transform(it) else it }.take(MAX_ITEMS)
            schedulePersistLocked()
        }
    }

    private fun schedulePersistLocked() {
        persistRequests.trySend(_items.value)
    }

    private fun persistSnapshot(snapshot: List<BrowserDownload>) {
        val file = synchronized(lock) { storeFile } ?: return
        val array = JSONArray()
        snapshot.take(MAX_ITEMS).forEach { item ->
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
                    item.failureReason?.let { put("failureReason", it.name) }
                },
            )
        }
        runCatching { writeTextAtomically(file, array.toString()) }
    }

    private fun load(file: File): List<BrowserDownload> {
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
                            failureReason = o.optString("failureReason")
                                .takeIf { it.isNotBlank() }
                                ?.let { value ->
                                    runCatching { DownloadFailureReason.valueOf(value) }.getOrNull()
                                },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
