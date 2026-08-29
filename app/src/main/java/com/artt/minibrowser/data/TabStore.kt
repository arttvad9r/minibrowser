package com.artt.minibrowser.data

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

object TabStore {
    private const val FILE_NAME = "open_tabs.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Any()

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

    fun loadState(dir: File): PersistedBrowserState {
        val target = File(dir, FILE_NAME)
        if (!target.isFile) return PersistedBrowserState()
        val text = target.readText()
        return runCatching {
            json.decodeFromString(PersistedBrowserState.serializer(), text)
        }.getOrElse {
            runCatching {
                val legacy = json.decodeFromString(ListSerializer(String.serializer()), text)
                PersistedBrowserState(
                    tabs = legacy.mapIndexed { index, url -> PersistedTab(index.toLong() + 1, url) },
                )
            }.getOrElse {
                target.renameTo(File(dir, "$FILE_NAME.corrupt-${System.currentTimeMillis()}"))
                PersistedBrowserState()
            }
        }
    }
}
