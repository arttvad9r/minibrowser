package com.artt.minibrowser.data

import android.os.Trace
import com.artt.minibrowser.net.isValidWebUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class PersistedTab(
    val id: Long,
    val url: String,
    val title: String = "",
    val desktop: Boolean = false,
    val sessionState: String? = null,
    val lastAccess: Long = 0L,
    // URL the Gecko session state belongs to. Older persisted files do not have this field;
    // their unbound session state is deliberately ignored by TabManager and the URL is reloaded.
    val sessionStateUrl: String? = null,
)

@Serializable
data class PersistedBrowserState(
    val selectedId: Long? = null,
    val tabs: List<PersistedTab> = emptyList(),
)

internal const val TAB_STORE_LOAD_TRACE = "TabStore.loadState"

/** android.os.Trace is a no-op measurement aid; JVM unit tests use Android stubs that may throw. */
private inline fun <T> tracedTabStoreLoad(block: () -> T): T {
    val started = runCatching {
        Trace.beginSection(TAB_STORE_LOAD_TRACE)
        true
    }.getOrDefault(false)
    return try {
        block()
    } finally {
        if (started) runCatching { Trace.endSection() }
    }
}

internal fun sanitizePersistedBrowserState(state: PersistedBrowserState): PersistedBrowserState {
    val tabs = state.tabs.mapNotNull { tab ->
        when {
            tab.url.isEmpty() -> tab
            tab.url.equals("about:blank", ignoreCase = true) -> tab.copy(url = "about:blank")
            isValidWebUri(tab.url) -> tab
            else -> null
        }
    }
    val selectedId = state.selectedId?.takeIf { selected -> tabs.any { it.id == selected } }
    return if (tabs == state.tabs && selectedId == state.selectedId) {
        state
    } else {
        state.copy(selectedId = selectedId, tabs = tabs)
    }
}

object TabStore {
    private const val FILE_NAME = "open_tabs.json"
    private const val CORRUPT_FILE_NAME = "$FILE_NAME.corrupt"
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Any()
    private val newestRevisionByTarget = mutableMapOf<String, Long>()

    fun save(dir: File, urls: List<String>) {
        saveState(dir, PersistedBrowserState(
            tabs = urls.mapIndexed { index, url -> PersistedTab(index.toLong() + 1, url) },
        ))
    }

    /**
     * Process-local writes are serialized because lifecycle shutdown can flush while the IO
     * persistence loop is still finishing its previous write. The live JSON is never truncated;
     * a synced temp file is published with atomic move when supported and replace-move otherwise.
     */
    fun saveState(dir: File, state: PersistedBrowserState) = synchronized(writeLock) {
        writeStateLocked(dir, state)
    }

    /**
     * Writes a snapshot only if it is not older than the newest snapshot already published for
     * this store in the current process. This is the clear/shutdown barrier: a persist coroutine
     * that captured old tabs before a destructive action cannot restore them after the newer empty
     * snapshot has been committed.
     */
    fun saveStateVersioned(dir: File, state: PersistedBrowserState, revision: Long): Boolean =
        synchronized(writeLock) {
            val key = File(dir, FILE_NAME).absolutePath
            val newest = newestRevisionByTarget[key]
            if (newest != null && revision < newest) {
                false
            } else {
                writeStateLocked(dir, state)
                newestRevisionByTarget[key] = revision
                true
            }
        }

    private fun writeStateLocked(dir: File, state: PersistedBrowserState) {
        dir.mkdirs()
        val target = File(dir, FILE_NAME)
        val temp = File(dir, "$FILE_NAME.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(json.encodeToString(PersistedBrowserState.serializer(), state).toByteArray())
                output.fd.sync()
            }
            runCatching {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.recoverCatching {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
        } finally {
            temp.delete()
        }
    }

    fun load(dir: File): List<String> = loadState(dir).tabs.map { it.url }

    fun loadState(dir: File): PersistedBrowserState = tracedTabStoreLoad {
        val target = File(dir, FILE_NAME)
        if (!target.isFile) return@tracedTabStoreLoad PersistedBrowserState()
        val text = target.readText()
        val decoded = runCatching {
            json.decodeFromString(PersistedBrowserState.serializer(), text)
        }.getOrElse {
            runCatching {
                val legacy = json.decodeFromString(ListSerializer(String.serializer()), text)
                PersistedBrowserState(
                    tabs = legacy.mapIndexed { index, url -> PersistedTab(index.toLong() + 1, url) },
                )
            }.getOrElse {
                quarantineCorruptStore(target, File(dir, CORRUPT_FILE_NAME))
                return@tracedTabStoreLoad PersistedBrowserState()
            }
        }
        sanitizePersistedBrowserState(decoded)
    }

    private fun quarantineCorruptStore(target: File, backup: File) {
        val moved = runCatching {
            Files.move(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrDefault(false)
        if (!moved) target.delete()
    }
}
