package com.artt.minibrowser.data

import android.os.Looper
import android.os.Trace
import android.util.Log
import com.artt.minibrowser.net.sanitizeWebUriForPersistence
import com.artt.minibrowser.net.sanitizeWebUriUserInfoInText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors

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
    // Future A-C-owned sessions persist EngineSessionState separately from the legacy raw Gecko
    // string above. These fields remain null while TabManager is the live session owner.
    val engineSessionState: EngineSessionStateEnvelope? = null,
    val engineSessionStateUrl: String? = null,
)

@Serializable
data class PersistedBrowserState(
    val selectedId: Long? = null,
    val tabs: List<PersistedTab> = emptyList(),
)

internal const val TAB_STORE_LOAD_TRACE = "TabStore.loadState"
private const val TAB_STORE_IO_THREAD = "minibrowser-tab-store"

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

private fun isAndroidMainThread(): Boolean =
    runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(false)

private fun sanitizePersistedSessionUrl(value: String?): String? = when {
    value == null -> null
    value.isEmpty() -> ""
    value.equals("about:blank", ignoreCase = true) -> "about:blank"
    else -> sanitizeWebUriForPersistence(value)
}

private fun sanitizePersistedTitle(value: String): String =
    sanitizeWebUriUserInfoInText(value)

internal fun sanitizePersistedBrowserState(state: PersistedBrowserState): PersistedBrowserState {
    val seenIds = mutableSetOf<Long>()
    val tabs = state.tabs.mapNotNull { tab ->
        val safeUrl = when {
            tab.url.isEmpty() -> ""
            tab.url.equals("about:blank", ignoreCase = true) -> "about:blank"
            else -> sanitizeWebUriForPersistence(tab.url)
        } ?: return@mapNotNull null
        val safeTitle = sanitizePersistedTitle(tab.title)

        var normalized = if (safeUrl == tab.url && safeTitle == tab.title) {
            tab
        } else {
            tab.copy(url = safeUrl, title = safeTitle)
        }

        val safeSessionStateUrl = sanitizePersistedSessionUrl(tab.sessionStateUrl)
        val safeEngineSessionStateUrl = sanitizePersistedSessionUrl(tab.engineSessionStateUrl)
        if (
            safeUrl != tab.url ||
            safeTitle != tab.title ||
            safeSessionStateUrl != tab.sessionStateUrl ||
            safeEngineSessionStateUrl != tab.engineSessionStateUrl
        ) {
            // Both raw Gecko and A-C engine session snapshots are opaque and can contain the
            // original URL/title. If credentials were removed from persisted browser metadata,
            // discard every bound snapshot and reload the sanitized URL instead of retaining an
            // opaque sensitive copy on disk.
            normalized = normalized.copy(
                sessionState = null,
                sessionStateUrl = null,
                engineSessionState = null,
                engineSessionStateUrl = null,
            )
        }

        normalized.takeIf { it.id > 0L && seenIds.add(it.id) }
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
    private val allocatedRevisionByTarget = mutableMapOf<String, Long>()
    private val preloadedStateByTarget = mutableMapOf<String, PersistedBrowserState>()
    private val ioExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, TAB_STORE_IO_THREAD).apply { isDaemon = true }
        }
    }

    private fun targetKey(dir: File): String = File(dir, FILE_NAME).absolutePath

    private fun <T> orderedIo(block: () -> T): T {
        if (Thread.currentThread().name == TAB_STORE_IO_THREAD) return block()
        return ioExecutor.submit(Callable(block)).get()
    }

    fun save(dir: File, urls: List<String>) {
        saveState(dir, PersistedBrowserState(
            tabs = urls.mapIndexed { index, url -> PersistedTab(index.toLong() + 1, url) },
        ))
    }

    /**
     * Returns a process-wide monotonic revision for this tab store. TabManager instances are
     * Activity-owned and can be recreated while the process stays alive, so a manager-local counter
     * would restart at zero and make its fresh writes look older than the previous manager's final
     * snapshot.
     */
    internal fun nextRevision(dir: File): Long = synchronized(writeLock) {
        val key = targetKey(dir)
        val latest = maxOf(
            newestRevisionByTarget[key] ?: 0L,
            allocatedRevisionByTarget[key] ?: 0L,
        )
        (latest + 1L).also { allocatedRevisionByTarget[key] = it }
    }

    /**
     * Process-local disk operations are serialized on one dedicated executor. This makes lifecycle
     * ordering deterministic across Activity recreation while keeping filesystem work off main.
     */
    fun saveState(dir: File, state: PersistedBrowserState) = orderedIo {
        synchronized(writeLock) {
            writeStateLocked(dir, state)
        }
    }

    /**
     * Writes a snapshot only if it is not older than the newest snapshot already published for
     * this store in the current process. On Android main this becomes an ordered non-blocking final
     * write: the revision barrier is reserved synchronously, then the filesystem operation is queued.
     */
    fun saveStateVersioned(dir: File, state: PersistedBrowserState, revision: Long): Boolean {
        if (isAndroidMainThread()) return enqueueStateVersioned(dir, state, revision)
        return orderedIo {
            synchronized(writeLock) {
                saveStateVersionedLocked(dir, state, revision, reserveFirst = true)
            }
        }
    }

    /** Visible to unit tests so shutdown ordering can be verified without an Android Looper. */
    internal fun enqueueStateVersioned(
        dir: File,
        state: PersistedBrowserState,
        revision: Long,
    ): Boolean {
        val key = targetKey(dir)
        synchronized(writeLock) {
            allocatedRevisionByTarget[key] = maxOf(allocatedRevisionByTarget[key] ?: 0L, revision)
            val newest = newestRevisionByTarget[key]
            if (newest != null && revision < newest) return false
            // Reserve the barrier before returning to main. Older async writes are rejected even if
            // they race with the queued disk operation below.
            newestRevisionByTarget[key] = revision
            preloadedStateByTarget.remove(key)
        }
        ioExecutor.execute {
            runCatching {
                synchronized(writeLock) {
                    saveStateVersionedLocked(dir, state, revision, reserveFirst = false)
                }
            }.onFailure {
                Log.e("MinibrowserTabs", "Failed to persist queued tab metadata", it)
            }
        }
        return true
    }

    private fun saveStateVersionedLocked(
        dir: File,
        state: PersistedBrowserState,
        revision: Long,
        reserveFirst: Boolean,
    ): Boolean {
        val key = targetKey(dir)
        if (reserveFirst) {
            allocatedRevisionByTarget[key] = maxOf(allocatedRevisionByTarget[key] ?: 0L, revision)
            val newest = newestRevisionByTarget[key]
            if (newest != null && revision < newest) return false
        } else if ((newestRevisionByTarget[key] ?: revision) > revision) {
            return false
        }
        writeStateLocked(dir, state)
        newestRevisionByTarget[key] = maxOf(newestRevisionByTarget[key] ?: revision, revision)
        return true
    }

    private fun writeStateLocked(dir: File, state: PersistedBrowserState) {
        dir.mkdirs()
        val target = File(dir, FILE_NAME)
        val temp = File(dir, "$FILE_NAME.tmp")
        val sanitized = sanitizePersistedBrowserState(state)
        try {
            FileOutputStream(temp).use { output ->
                output.write(json.encodeToString(PersistedBrowserState.serializer(), sanitized).toByteArray())
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
            // A state preloaded for an Activity must never survive a newer write from an older
            // Activity instance during recreation. Falling back to disk is slower but correct.
            preloadedStateByTarget.remove(target.absolutePath)
            File(dir, CORRUPT_FILE_NAME).delete()
        } finally {
            temp.delete()
        }
    }

    fun load(dir: File): List<String> = loadState(dir).tabs.map { it.url }

    /**
     * Reads and validates the tab state through the same ordered IO executor as final persistence,
     * then makes that exact snapshot a one-shot handoff for the next synchronous [loadState].
     * MainActivity invokes this before constructing TabManager, so restore performs no disk IO on
     * main and Activity recreation cannot overtake the previous manager's queued final snapshot.
     */
    internal fun preloadStateForNextRestore(dir: File): PersistedBrowserState = orderedIo {
        tracedTabStoreLoad {
            synchronized(writeLock) {
                val state = readStateLocked(dir)
                preloadedStateByTarget[targetKey(dir)] = state
                state
            }
        }
    }

    fun loadState(dir: File): PersistedBrowserState {
        synchronized(writeLock) {
            preloadedStateByTarget.remove(targetKey(dir))?.let { return it }
        }
        return orderedIo {
            tracedTabStoreLoad {
                synchronized(writeLock) {
                    readStateLocked(dir)
                }
            }
        }
    }

    private fun readStateLocked(dir: File): PersistedBrowserState {
        val target = File(dir, FILE_NAME)
        if (!target.isFile) return PersistedBrowserState()
        val text = target.readText()
        var needsRewrite = false
        val decoded = runCatching {
            json.decodeFromString(PersistedBrowserState.serializer(), text)
        }.getOrElse {
            runCatching {
                val legacy = json.decodeFromString(ListSerializer(String.serializer()), text)
                needsRewrite = true
                PersistedBrowserState(
                    tabs = legacy.mapIndexed { index, url -> PersistedTab(index.toLong() + 1, url) },
                )
            }.getOrElse {
                quarantineCorruptFile(target, File(dir, CORRUPT_FILE_NAME))
                return PersistedBrowserState()
            }
        }
        val sanitized = sanitizePersistedBrowserState(decoded)
        if (needsRewrite || sanitized != decoded) {
            runCatching { writeStateLocked(dir, sanitized) }
        }
        return sanitized
    }
}
