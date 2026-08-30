package com.artt.minibrowser.data

import com.artt.minibrowser.net.sanitizeWebUriForPersistence
import com.artt.minibrowser.net.webUriHost

private val HISTORY_WHITESPACE = Regex("\\s+")

internal fun isHistoryUrl(url: String): Boolean = sanitizeWebUriForPersistence(url) != null

/** Removes HTTP(S) user-info from URL-shaped titles without altering ordinary page titles. */
internal fun historyTitleForPersistence(title: String?): String? {
    val value = title?.takeIf { it.isNotBlank() } ?: return null
    return sanitizeWebUriForPersistence(value) ?: value
}

/**
 * Normal writes already sanitize web URLs and URL-shaped titles. Reuse the Room result directly in
 * that common case; allocate a cleaned copy only when an old/legacy database contains unsafe data.
 */
internal fun webHistoryEntries(entries: List<HistoryEntry>): List<HistoryEntry> {
    var result: ArrayList<HistoryEntry>? = null
    for (index in entries.indices) {
        val entry = entries[index]
        val safeUrl = sanitizeWebUriForPersistence(entry.url)
        val safeTitle = historyTitleForPersistence(entry.title).orEmpty()
        when {
            safeUrl == null -> {
                if (result == null) {
                    result = ArrayList(entries.size - 1)
                    for (retainedIndex in 0 until index) result.add(entries[retainedIndex])
                }
            }
            safeUrl == entry.url && safeTitle == entry.title -> result?.add(entry)
            else -> {
                if (result == null) {
                    result = ArrayList(entries.size)
                    for (retainedIndex in 0 until index) result.add(entries[retainedIndex])
                }
                result.add(entry.copy(url = safeUrl, title = safeTitle))
            }
        }
    }
    return result ?: entries
}

private fun historyHost(url: String): String =
    webUriHost(url).orEmpty().lowercase().removePrefix("www.")

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

/** Bookmarks keep priority over history; build only the final bounded omnibox list. */
internal fun mergeSuggestions(
    bookmarks: List<Bookmark>,
    history: List<Suggestion>,
    limit: Int = 8,
): List<Suggestion> {
    if (limit <= 0) return emptyList()
    val safeBookmarks = webBookmarks(bookmarks)
    val result = ArrayList<Suggestion>(minOf(limit, safeBookmarks.size + history.size))
    val seenUrls = HashSet<String>()

    for (bookmark in safeBookmarks) {
        if (seenUrls.add(bookmark.url)) {
            result += Suggestion(bookmark.title.ifBlank { bookmark.url }, bookmark.url)
            if (result.size == limit) return result
        }
    }
    for (suggestion in history) {
        val safeUrl = sanitizeWebUriForPersistence(suggestion.url) ?: continue
        val safeLabel = historyTitleForPersistence(suggestion.label) ?: suggestion.label
        if (seenUrls.add(safeUrl)) {
            result += if (safeUrl == suggestion.url && safeLabel == suggestion.label) {
                suggestion
            } else {
                suggestion.copy(label = safeLabel, url = safeUrl)
            }
            if (result.size == limit) break
        }
    }
    return result
}

class HistoryRepository(private val dao: AppDao) {
    suspend fun record(url: String, title: String?) {
        val safeUrl = sanitizeWebUriForPersistence(url) ?: return
        dao.recordVisit(safeUrl, historyTitleForPersistence(title), System.currentTimeMillis())
    }

    // Заголовок приходит после onVisited — обновляем только существующую запись, визит не дублируем.
    suspend fun updateTitle(url: String, title: String?) {
        val safeUrl = sanitizeWebUriForPersistence(url) ?: return
        val safeTitle = historyTitleForPersistence(title) ?: return
        dao.updateHistoryTitle(safeUrl, safeTitle)
    }

    suspend fun suggest(q: String): List<Suggestion> {
        val query = q.trim()
        // Пустой omnibox на новой вкладке должен оставаться чистым; история уже видна на Start page.
        if (query.isEmpty()) return emptyList()

        // Bookmarks are rendered first. Once all eight slots are filled, avoid the history query
        // and all of its filtering/ranking work entirely.
        val bookmarks = webBookmarks(dao.bookmarksMatching(query))
        if (bookmarks.size >= 8) return mergeSuggestions(bookmarks, emptyList())

        val rows = webHistoryEntries(dao.searchHistory(query))
            .let(::collapseHistoryNoise)
        val history = rankSuggestions(rows, query)
        return mergeSuggestions(bookmarks, history)
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
