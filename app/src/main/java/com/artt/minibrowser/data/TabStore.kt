package com.artt.minibrowser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedTab(val url: String, val position: Int)

object TabStore {
    // Дефолтный каталог; выставляется один раз (MainActivity/TabManager).
    @Volatile var dir: File? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedTab.serializer())

    fun save(dir: File, urls: List<String>) {
        dir.mkdirs()
        File(dir, "open_tabs.json").writeText(json.encodeToString(serializer, urls.mapIndexed { i, u -> SavedTab(u, i) }))
    }

    fun load(dir: File): List<String> =
        runCatching {
            json.decodeFromString(serializer, File(dir, "open_tabs.json").readText())
                .sortedBy { it.position }.map { it.url }
        }.getOrDefault(emptyList())

    fun save(urls: List<String>) = save(requireNotNull(dir) { "TabStore.dir not set" }, urls)
    fun load(): List<String> = load(requireNotNull(dir) { "TabStore.dir not set" })
}
