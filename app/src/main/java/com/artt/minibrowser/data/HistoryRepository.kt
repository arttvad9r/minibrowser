package com.artt.minibrowser.data

internal fun isHistoryUrl(url: String): Boolean {
    val value = url.trim()
    return value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
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
        val rows = (if (q.isBlank()) dao.recentHistory(30) else dao.searchHistory(q.trim()))
            .filter { isHistoryUrl(it.url) }
        val marks: List<String> = (if (q.isBlank()) dao.bookmarks() else dao.bookmarksMatching(q.trim()))
            .map { it.url }
        return rankSuggestions(rows.map { Scored(it.url, it.title, it.visitedAt) }, marks, q)
    }

    // Для экрана истории: полный список без капа подсказок. Фильтр также скрывает
    // старые about:blank/internal-записи, которые могли уже попасть в БД.
    suspend fun recent(limit: Int): List<HistoryEntry> =
        dao.recentHistory(limit.coerceAtLeast(1) + 32)
            .asSequence()
            .filter { isHistoryUrl(it.url) }
            .take(limit)
            .toList()

    suspend fun clear() = dao.clearHistory()
}
