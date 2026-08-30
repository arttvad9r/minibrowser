package com.artt.minibrowser.data

import com.artt.minibrowser.net.isValidWebUri
import java.net.URI

/** Drops malformed legacy rows before they can be opened from browser UI or omnibox suggestions. */
internal fun webBookmarks(entries: List<Bookmark>): List<Bookmark> {
    var result: ArrayList<Bookmark>? = null
    for (index in entries.indices) {
        val entry = entries[index]
        if (isValidWebUri(entry.url)) {
            result?.add(entry)
        } else if (result == null) {
            result = ArrayList(entries.size - 1)
            for (retainedIndex in 0 until index) result.add(entries[retainedIndex])
        }
    }
    return result ?: entries
}

class BookmarksRepository(private val dao: AppDao) {
    suspend fun all(): List<Bookmark> = webBookmarks(dao.bookmarks())
    suspend fun add(url: String, title: String) {
        if (!isValidWebUri(url)) return
        val host = runCatching { URI(url).host ?: "" }.getOrDefault("")
        val max = dao.maxBookmarkPosition()
        dao.upsertBookmark(Bookmark(url, title.ifBlank { host }, host, max + 1))
    }
    suspend fun remove(url: String) = dao.deleteBookmark(url)
    suspend fun clearAll() = dao.clearBookmarks()
    suspend fun rename(url: String, title: String) = dao.renameBookmark(url, title)
    suspend fun isBookmarked(url: String) = isValidWebUri(url) && dao.bookmarkCount(url) > 0
}
