package com.artt.minibrowser.data

import java.net.URI

internal fun isHistoryUrl(url: String): Boolean {
    val value = url.trim()
    return value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
}

private fun historyDisplayKey(entry: HistoryEntry): String {
    val host = runCatching { URI(entry.url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
        .lowercase()
    val title = entry.title
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
    return "$host|$title"
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
    val sorted = entries.sortedByDescending { it.visitedAt }
    val result = ArrayList<HistoryEntry>(sorted.size)
    sorted.forEach { entry ->
        val previous = result.lastOrNull()
        val delta = previous?.let { it.visitedAt - entry.visitedAt }
        val isNavigationNoise = previous != null &&
            historyDisplayKey(previous) == historyDisplayKey(entry) &&
            delta != null && delta in 0..windowMs
        if (!isNavigationNoise) result += entry
    }
    return result
}

class HistoryRepository(private val dao: AppDao) {
    suspend fun record(url: String, title: String?) {
        if (!isHistoryUrl(url)) return
        dao.recordVisit(url, title, System.currentTimeMillis())
    }

    // Заголовок приходит после onVisited — обновляем существующую запись, визит не дублируем.
    suspend fun updateTitle(url: String, title: String?) {
        if (!isHistoryUrl(url) || title.isNullOrBlank()) return
        dao.updateHistoryTitle(url, title, System.currentTimeMillis())
    }

    suspend fun suggest(q: String): List<Suggestion> {
        val rows = (if (q.isBlank()) dao.recentHistory(40) else dao.searchHistory(q.trim()))
            .filter { isHistoryUrl(it.url) }
            .let(::collapseHistoryNoise)
        val marks: List<String> = (if (q.isBlank()) dao.bookmarks() else dao.bookmarksMatching(q.trim()))
            .map { it.url }
        return rankSuggestions(rows.map { Scored(it.url, it.title, it.visitedAt) }, marks, q)
    }

    // Читаем с запасом: после удаления internal URL и схлопывания быстрых SPA/redirect-переходов
    // экран всё равно получает запрошенное число содержательных записей, если они есть в БД.
    suspend fun recent(limit: Int): List<HistoryEntry> {
        if (limit <= 0) return emptyList()
        val fetchLimit = (limit * 3 + 32).coerceAtMost(1000)
        return dao.recentHistory(fetchLimit)
            .asSequence()
            .filter { isHistoryUrl(it.url) }
            .toList()
            .let(::collapseHistoryNoise)
            .take(limit)
    }

    suspend fun clear() = dao.clearHistory()
}
