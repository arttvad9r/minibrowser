package com.artt.minibrowser.data

import java.net.URI

class BookmarksRepository(private val dao: AppDao) {
    suspend fun all(): List<Bookmark> = dao.bookmarks()
    suspend fun add(url: String, title: String) {
        val host = runCatching { URI(url).host ?: "" }.getOrDefault("")
        val max = dao.maxBookmarkPosition()
        dao.upsertBookmark(Bookmark(url, title.ifBlank { host }, host, max + 1))
    }
    suspend fun remove(url: String) = dao.deleteBookmark(url)
    suspend fun clearAll() = dao.clearBookmarks()
    suspend fun rename(url: String, title: String) = dao.renameBookmark(url, title)
    suspend fun isBookmarked(url: String) = dao.bookmarkCount(url) > 0
}
