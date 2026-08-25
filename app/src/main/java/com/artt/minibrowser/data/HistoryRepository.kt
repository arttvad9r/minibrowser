package com.artt.minibrowser.data

class HistoryRepository(private val dao: AppDao) {
    suspend fun record(url: String, title: String?) {
        dao.recordVisit(url, title, System.currentTimeMillis())
    }

    // Заголовок приходит после onVisited — обновляем существующую запись, визит не дублируем.
    suspend fun updateTitle(url: String, title: String?) {
        if (title.isNullOrBlank()) return
        dao.updateHistoryTitle(url, title, System.currentTimeMillis())
    }

    suspend fun suggest(q: String): List<Suggestion> {
        val rows = if (q.isBlank()) dao.recentHistory(30) else dao.searchHistory(q.trim())
        val marks: List<String> = (if (q.isBlank()) dao.bookmarks() else dao.bookmarksMatching(q.trim()))
            .map { it.url }
        return rankSuggestions(rows.map { Scored(it.url, it.title, it.visitedAt) }, marks, q)
    }

    // Для экрана истории: полный список без капа подсказок.
    suspend fun recent(limit: Int): List<HistoryEntry> = dao.recentHistory(limit)

    suspend fun clear() = dao.clearHistory()
}
