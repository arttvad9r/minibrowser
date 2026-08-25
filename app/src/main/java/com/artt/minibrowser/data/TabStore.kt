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
)

@Serializable
data class PersistedBrowserState(
    val selectedId: Long? = null,
    val tabs: List<PersistedTab> = emptyList(),
)

object TabStore {
    private const val FILE_NAME = "open_tabs.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(dir: File, urls: List<String>) {
        saveState(dir, PersistedBrowserState(
            tabs = urls.mapIndexed { index, url -> PersistedTab(index.toLong() + 1, url) },
        ))
    }

    fun saveState(dir: File, state: PersistedBrowserState) {
        dir.mkdirs()
        val target = File(dir, FILE_NAME)
        val temp = File(dir, "$FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            output.write(json.encodeToString(PersistedBrowserState.serializer(), state).toByteArray())
            output.fd.sync()
        }
        runCatching {
            Files.move(
                temp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            if (!temp.renameTo(target)) error("Could not atomically replace ${target.name}")
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
