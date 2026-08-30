package com.artt.minibrowser.data

import com.artt.minibrowser.net.sanitizeWebUriForPersistence
import java.net.URI

private fun sanitizedBookmark(entry: Bookmark): Bookmark? {
    val safeUrl = sanitizeWebUriForPersistence(entry.url) ?: return null
    if (safeUrl == entry.url) return entry
    val safeHost = runCatching { URI(safeUrl).host.orEmpty() }.getOrDefault("")
    return entry.copy(
        url = safeUrl,
        title = if (entry.title == entry.url) safeUrl else entry.title,
        host = safeHost,
    )
}

/** Drops malformed rows and strips HTTP user-info before stored bookmarks reach browser UI. */
internal fun webBookmarks(entries: List<Bookmark>): List<Bookmark> {
    var result: ArrayList<Bookmark>? = null
    val seenUrls = HashSet<String>()
    for (index in entries.indices) {
        val entry = entries[index]
        val safe = sanitizedBookmark(entry)
        val keep = safe != null && seenUrls.add(safe.url)
        if (result == null && safe === entry && keep) continue

        if (result == null) {
            result = ArrayList(entries.size)
            for (retainedIndex in 0 until index) result.add(entries[retainedIndex])
        }
        if (keep) result.add(requireNotNull(safe))
    }
    return result ?: entries
}

class BookmarksRepository(private val dao: AppDao) {
    suspend fun all(): List<Bookmark> {
        val stored = dao.bookmarks()
        val safe = webBookmarks(stored)
        if (safe !== stored) migrateLegacyRows(stored)
        return safe
    }

    suspend fun add(url: String, title: String) {
        val safeUrl = sanitizeWebUriForPersistence(url) ?: return
        val host = runCatching { URI(safeUrl).host ?: "" }.getOrDefault("")
        val safeTitle = when {
            title.isBlank() -> host
            title == url -> safeUrl
            else -> title
        }
        val max = dao.maxBookmarkPosition()
        dao.upsertBookmark(Bookmark(safeUrl, safeTitle, host, max + 1))
    }

    suspend fun remove(url: String) {
        sanitizeWebUriForPersistence(url)?.let { dao.deleteBookmark(it) }
    }

    suspend fun clearAll() = dao.clearBookmarks()

    suspend fun rename(url: String, title: String) {
        sanitizeWebUriForPersistence(url)?.let { dao.renameBookmark(it, title) }
    }

    suspend fun isBookmarked(url: String): Boolean {
        val safeUrl = sanitizeWebUriForPersistence(url) ?: return false
        return dao.bookmarkCount(safeUrl) > 0
    }

    /**
     * Legacy builds could persist valid HTTP(S) URLs containing user-info credentials. Publish only
     * sanitized copies immediately, then converge storage in the background path used to load the
     * bookmark screen. Upsert the safe key before deleting the old key so process death cannot lose
     * a bookmark; if the safe key already exists, simply discard the credential-bearing duplicate.
     */
    private suspend fun migrateLegacyRows(stored: List<Bookmark>) {
        stored.forEach { entry ->
            val safe = sanitizedBookmark(entry)
            when {
                safe == null -> dao.deleteBookmark(entry.url)
                safe.url != entry.url -> {
                    if (dao.bookmarkCount(safe.url) == 0) dao.upsertBookmark(safe)
                    dao.deleteBookmark(entry.url)
                }
            }
        }
    }
}
