package com.artt.minibrowser.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

object TabStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun save(dir: File, urls: List<String>) {
        dir.mkdirs()
        File(dir, "open_tabs.json").writeText(json.encodeToString(serializer, urls))
    }

    // Старый формат (объекты {url, position}) не читается — открытые вкладки один раз потеряются.
    fun load(dir: File): List<String> =
        runCatching { json.decodeFromString(serializer, File(dir, "open_tabs.json").readText()) }
            .getOrDefault(emptyList())
}
