package com.artt.minibrowser.data

import android.content.Context
import android.net.Uri
import com.artt.minibrowser.engine.SearchEngine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val BACKUP_SCHEMA_VERSION = 1
private const val MAX_BACKUP_BYTES = 8 * 1024 * 1024

@Serializable
private data class BackupSettings(
    val searchEngine: String,
    val theme: Int,
    val adblockEnabled: Boolean,
    val votEnabled: Boolean,
    val translateTarget: String,
)

@Serializable
private data class BackupBookmark(
    val url: String,
    val title: String,
    val position: Int,
)

@Serializable
private data class BackupHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long,
    val visits: Int,
)

@Serializable
private data class LocalBackupDocument(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long,
    val settings: BackupSettings,
    val bookmarks: List<BackupBookmark>,
    val history: List<BackupHistoryEntry>,
)

/**
 * Explicit user-controlled backup. The JSON intentionally excludes cookies, site storage,
 * downloads and credentials; those have different privacy/security semantics and must never be
 * silently copied into an ordinary document chosen through the Storage Access Framework.
 */
internal class LocalBackupController(context: Context) {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository(appContext)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = DbHolder.db.dao()
            val prefs = settings.snapshot()
            val document = LocalBackupDocument(
                exportedAt = System.currentTimeMillis(),
                settings = BackupSettings(
                    searchEngine = prefs.searchEngine.name,
                    theme = prefs.theme,
                    adblockEnabled = prefs.adblockEnabled,
                    votEnabled = prefs.votEnabled,
                    translateTarget = prefs.translateTarget,
                ),
                bookmarks = webBookmarks(dao.bookmarks()).map { bookmark ->
                    BackupBookmark(
                        url = bookmark.url,
                        title = bookmark.title,
                        position = bookmark.position,
                    )
                },
                history = webHistoryEntries(dao.allHistory()).map { entry ->
                    BackupHistoryEntry(
                        url = entry.url,
                        title = entry.title,
                        visitedAt = entry.visitedAt,
                        visits = entry.visits,
                    )
                },
            )
            val encoded = json.encodeToString(document)
            appContext.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(encoded)
            } ?: error("Unable to open backup destination")
        }
    }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = appContext.contentResolver.openInputStream(uri)?.use { input ->
                input.readUtf8Limited(MAX_BACKUP_BYTES)
            } ?: error("Unable to open backup source")
            val document = json.decodeFromString<LocalBackupDocument>(encoded)
            require(document.schemaVersion == BACKUP_SCHEMA_VERSION) {
                "Unsupported backup schema ${document.schemaVersion}"
            }

            // Validate and normalize the entire document before changing any local state.
            val restoredBookmarks = document.bookmarks
                .sortedBy { it.position }
                .mapIndexedNotNull { index, bookmark ->
                    bookmarkForPersistence(bookmark.url, bookmark.title, index)
                }
                .distinctBy { it.url }

            val restoredHistory = LinkedHashMap<String, HistoryEntry>()
            document.history.forEach { entry ->
                val safeUrl = com.artt.minibrowser.net.sanitizeWebUriForPersistence(entry.url) ?: return@forEach
                val safeTitle = historyTitleForPersistence(entry.title).orEmpty().ifBlank { safeUrl }
                val safeEntry = HistoryEntry(
                    url = safeUrl,
                    title = safeTitle,
                    visitedAt = entry.visitedAt.coerceAtLeast(0L),
                    visits = entry.visits.coerceAtLeast(1),
                )
                val previous = restoredHistory[safeUrl]
                if (previous == null || safeEntry.visitedAt >= previous.visitedAt) {
                    restoredHistory[safeUrl] = safeEntry
                }
            }

            val restoredPrefs = Prefs(
                searchEngine = runCatching { SearchEngine.valueOf(document.settings.searchEngine) }
                    .getOrDefault(SearchEngine.YANDEX),
                theme = normalizeThemePreference(document.settings.theme),
                adblockEnabled = document.settings.adblockEnabled,
                votEnabled = document.settings.votEnabled,
                translateTarget = com.artt.minibrowser.engine.normalizeTranslationTarget(
                    document.settings.translateTarget,
                ) ?: "ru",
            )

            DbHolder.db.dao().replaceUserData(
                history = restoredHistory.values.sortedByDescending { it.visitedAt },
                bookmarks = restoredBookmarks,
            )
            settings.replace(restoredPrefs)
        }
    }
}

private fun InputStream.readUtf8Limited(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Backup file is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}
