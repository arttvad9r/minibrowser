package com.artt.minibrowser.data

import java.net.URI

private val HISTORY_WHITESPACE = Regex("\\s+")

internal fun isHistoryUrl(url: String): Boolean {
    val value = url.trim()
    return value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
}

/**
 * Normal writes already reject non-web URLs. Reuse the Room result directly in that common case;
 * allocate a filtered copy only when an old/legacy database actually contains an internal row.
 */
internal fun webHistoryEntries(entries: List<HistoryEntry>): List<HistoryEntry> {
    var result: ArrayList<HistoryEntry>? = null
    for (index in entries.indices) {
        val entry = entries[index]
        if (isHistoryUrl(entry.url)) {
            result?.add(entry)
        } else if (result == null) {
            result = ArrayList(entries.size - 1)
            for (retainedIndex in 0 until index) result.add(entries[retainedIndex])
        }
    }
    return result ?: entries
}

private fun historyHost(url: String): String =
    runCatching { URI(url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
        .lowercase()

private fun historyDisplayKey(entry: HistoryEntry): String {
    val title = entry.title
        .trim()
        .lowercase()
        .replace(HISTORY_WHITESPACE, " ")
    return "${historyHost(entry.url)}|$title"
}

/**
 * Room history queries already return visitedAt DESC. Avoid allocating and sorting a second list
 * on that hot path, while retaining the old ordering semantics for arbitrary callers/tests.
 */
private fun historyDescending(entries: List<HistoryEntry>): List<HistoryEntry> {
    for (index in 1 until entries.size) {
        if (entries[index - 1].visitedAt < entries[index].visitedAt) {
            return entries.sortedByDescending { it.visitedAt }
        }
    }
    return entries
}

/**
 * Gecko/SPA navigation can expose several top-level URLs for what is visually the same page.
 * Keep the real URLs in storage, but collapse adjacent same-site/same-title transitions that
 * happened within a short window. This removes redirect/navigation noise without merging
 * genuinely different pages on the same site.
 */
internal fun collapseHistoryNoise(
    entries: List<HistoryEntry>,
    windowMs: Long = 2 * 60 * 1000L,
): List<HistoryEntry> {
    if (entries.size < 2) return entries
    val sorted = historyDescending(entries)
    var result: ArrayList<HistoryEntry>? = null
    var previousKey: String? = null
    var previousVisitedAt: Long? = null

    for (index in sorted.indices) {
        val entry = sorted[index]
        val key = historyDisplayKey(entry)
        val delta = previousVisitedAt?.let { it - entry.visitedAt }
        val isNavigationNoise = previousKey == key && delta != null && delta in 0..windowMs
        if (isNavigationNoise) {
            if (result == null) {
                result = ArrayList(sorted.size)
                for (retainedIndex in 0 until index) result.add(sorted[retainedIndex])
            }
            continue
        }

        result?.add(entry)
        previousKey = key
        previousVisitedAt = entry.visitedAt
    }
    return result ?: sorted
}

/**
 * The Start page is a launcher, not a timeline. For its small recent list prefer distinct sites
 * so a redirect-heavy site such as Drive cannot occupy every visible slot.
 */
internal fun distinctRecentSites(entries: List<HistoryEntry>, limit: Int): List<HistoryEntry> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<String>()
    return entries.asSequence()
        .filter { entry ->
            val key = historyHost(entry.url).ifBlank { entry.url.lowercase() }
            seen.add(key)
        }
        .take(limit)
        .toList()
}

class HistoryRepository(private val dao: AppDao) {
    suspend fun record(url: String, title: String?) {
        if (!isHistoryUrl(url)) return
        dao.recordVisit(url, title, System.currentTimeMillis())
    }

    // Заголовок приходит после onVisited — обновляем только существующую запись, визит не дублируем.
    suspend fun updateTitle(url: String, title: String?) {
        if (!isHistoryUrl(url) || title.isNullOrBlank()) return
        dao.updateHistoryTitle(url, title)
    }

    suspend fun suggest(q: String): List<Suggestion> {
        val query = q.trim()
        // Пустой omnibox на новой вкладке должен оставаться чистым; история уже видна на Start page.
        if (query.isEmpty()) return emptyList()
        val rows = webHistoryEntries(dao.searchHistory(query))
            .let(::collapseHistoryNoise)
        val bookmarks = dao.bookmarksMatching(query).map {
            Suggestion(it.title.ifBlank { it.url }, it.url)
        }
        val history = rankSuggestions(
            rows.map { Scored(it.url, it.title, it.visitedAt) },
            query,
        )
        return (bookmarks + history).distinctBy { it.url }.take(8)
    }

    // Читаем с запасом: после удаления internal URL и схлопывания быстрых SPA/redirect-переходов
    // экран всё равно получает запрошенное число содержательных записей, если они есть в БД.
    // Маленький лимит используется домашней страницей — там показываем разные сайты.
    suspend fun recent(limit: Int): List<HistoryEntry> {
        if (limit <= 0) return emptyList()
        val fetchLimit = (limit * 4 + 40).coerceAtMost(1000)
        val cleaned = webHistoryEntries(dao.recentHistory(fetchLimit))
            .let(::collapseHistoryNoise)
        return if (limit <= 8) distinctRecentSites(cleaned, limit) else cleaned.take(limit)
    }

    suspend fun clear() = HistorySink.clear()
}
